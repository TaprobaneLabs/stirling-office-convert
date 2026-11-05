package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import stirling.software.officeconvert.extract.PageGraphics;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

/**
 * Shapes no part of the page draws: marks outside every figure, backdrop, ground and table that no text box, bullet, pill
 * or panel took. Only round dots of 24 pt or less became ellipses, and everything else was dropped: CMS's notices lost the
 * digits in their callout discs and the pointers from each callout to the sample page, USGS fact sheets their logos'
 * swooshes and map pointers. Each group of touching marks is drawn as a veil, its marks' own ink on a transparent picture
 * behind the text, where the PDF has it: nothing in the flow moves and no text is pictured.
 */
final class StrayMarks {

    /** Marks smaller than this both ways are specks (a dotted rule's dots, a stray hairline end). */
    private static final float SPECK = 1.5f;

    /** Marks this close to each other are one group: a disc and the digit in it, a pointer and its arrowhead. */
    private static final float JOIN = 1.5f;

    /** Paint covering this share of the page is its paper, not a shape on it. */
    private static final float PAPER = 0.25f;

    /** A group whose box glyphs cover this share is text drawn twice: fake bold, a shadow, outlined lettering. */
    private static final float GLYPH_COVER = 0.5f;

    /** More groups than this on a page are a drawing the figure finder missed, not a few callouts: none are drawn. */
    private static final int MAX_GROUPS = 80;

    private StrayMarks() {}

    record Found(List<PageLayout.Veil> veils, List<VectorMark> taken) {}

    static Found find(List<VectorMark> marks, PageGraphics gfx, List<Box> drawn, List<Box> tables, List<Line> lines,
            float pageW, float pageH) {
        List<VectorMark> free = new ArrayList<>();
        for (VectorMark m : marks) {
            float cx = (m.x() + m.right()) / 2f;
            float cy = (m.top() + m.bottom()) / 2f;
            if (m.shading() || m.width() < SPECK && m.height() < SPECK || m.width() * m.height() >= PAPER * pageW * pageH
                    || gfx.ink(m).isEmpty() || Regions.insideAny(cx, cy, drawn, 1f)) {
                continue;
            }
            free.add(m);
        }
        List<List<VectorMark>> groups = groups(free);
        List<PageLayout.Veil> veils = new ArrayList<>();
        List<VectorMark> taken = new ArrayList<>();
        for (List<VectorMark> g : groups) {
            Box box = bounds(g);
            // A group stands in a table only when it lies wholly inside it, as a dot does (Decorations): CMS's disc at the
            // end of a total's row keeps its digit. Shapes in tables stay out for now, as Word moves their cells' text
            if (g.stream().allMatch(VectorMark::round) || Regions.insideAny(box, tables, 1f)
                    || glyphCover(box, lines) >= GLYPH_COVER * box.area()) {
                continue;
            }
            veils.add(new PageLayout.Veil(box, g, g.stream().mapToInt(gfx::order).min().orElse(Integer.MAX_VALUE)));
            taken.addAll(g);
        }
        return veils.size() > MAX_GROUPS ? new Found(List.of(), List.of()) : new Found(veils, taken);
    }

    /** Marks in groups, each mark joined to every other whose box comes within {@link #JOIN} of its own. */
    private static List<List<VectorMark>> groups(List<VectorMark> marks) {
        int n = marks.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            Box a = box(marks.get(i));
            for (int j = i + 1; j < n; j++) {
                if (a.near(box(marks.get(j)), JOIN)) {
                    parent[root(parent, i)] = root(parent, j);
                }
            }
        }
        Map<Integer, List<VectorMark>> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            out.computeIfAbsent(root(parent, i), k -> new ArrayList<>()).add(marks.get(i));
        }
        return new ArrayList<>(out.values());
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static Box box(VectorMark m) {
        return new Box(m.x(), m.top(), m.right(), m.bottom());
    }

    private static Box bounds(List<VectorMark> marks) {
        Box b = box(marks.getFirst());
        for (VectorMark m : marks) {
            b = b.union(box(m));
        }
        return b;
    }

    /** How much of a box the page's words cover, their boxes added up. */
    private static float glyphCover(Box box, List<Line> lines) {
        float covered = 0;
        for (Line l : lines) {
            if (l.right < box.x() || l.x > box.right() || l.bottom < box.top() || l.top > box.bottom()) {
                continue;
            }
            for (Word w : l.words) {
                covered += new Box(w.x, l.top, w.right, l.bottom).overlapArea(box);
            }
        }
        return covered;
    }
}
