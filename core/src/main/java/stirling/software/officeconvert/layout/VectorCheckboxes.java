package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

/**
 * Checkboxes a form draws as small stroked squares beside their labels, with a cross set as text in the ticked ones
 * (CFPB's Loan Estimate and Closing Disclosure). Their four sides come out of the page as rules, which nothing
 * writes: an empty box went missing, and a ticked one left its cross alone, underlined by the box's bottom side. Each
 * becomes a ballot box glyph where it stands, ☐, or ☒ in place of its cross (☑ for a tick), and its sides are used.
 */
final class VectorCheckboxes {

    /** The page's glyphs with the boxes written as glyphs and their marks gone, and the rules the boxes were drawn with. */
    record Found(List<Glyph> glyphs, Set<Rule> rules) {}

    static final String EMPTY = "☐";
    static final String CROSSED = "☒";
    static final String TICKED = "☑";

    /**
     * The face Word's own checkboxes use, whose line is an em high: in Segoe UI Symbol, 1.33 ems, each line holding a
     * box grew, and IRS Form SS-4 lost 23 points of its words in place in Word.
     */
    static final FontInfo SYMBOLS = new FontInfo("MS Gothic", false, false, false, false, false, "MS-Gothic", true);

    private static final String CROSSES = "xX×✗✘";
    private static final String TICKS = "✓✔√";

    /** A box's least and most side, in points. */
    private static final float MIN_SIDE = 4f;
    private static final float MAX_SIDE = 14f;
    /** How far a box's sides may miss each other's ends, in points. */
    private static final float JOIN = 1f;
    /** How far from its box a label starts or ends, at most, in box sides. */
    private static final float REACH = 2.5f;
    /** MS Gothic's ballot boxes, in ems: the square's side, its left bearing and the glyph's advance. */
    private static final float INK = 0.737f;
    private static final float BEARING = 0.133f;
    private static final float ADVANCE = 1f;
    /** The largest a box's glyph is set, in its label's size: its line, an em in MS Gothic, no taller than the label's. */
    private static final float MAX_SIZE = 1.15f;

    private VectorCheckboxes() {}

    /**
     * The boxes on a page, or null when it draws none. A square painted inside is a chart's or map's legend key, its
     * outline stroked over its colour, not a checkbox: MMWR's outbreak charts and a deck's "This year" key became ☐.
     */
    static Found find(List<Glyph> glyphs, List<Rule> rules, List<Fill> fills, List<VectorMark> marks) {
        List<Rule> across = new ArrayList<>();
        Map<Long, List<Rule>> upright = new HashMap<>();
        for (Rule r : rules) {
            if (r.length() < MIN_SIDE - JOIN || r.length() > MAX_SIDE + JOIN) {
                continue;
            }
            if (r.horizontal()) {
                across.add(r);
            } else {
                upright.computeIfAbsent(key(r.pos()), k -> new ArrayList<>()).add(r);
            }
        }
        if (across.size() < 2 || upright.isEmpty()) {
            return null;
        }
        across.sort((a, b) -> Float.compare(a.pos(), b.pos()));
        List<float[]> boxes = new ArrayList<>();
        List<Rule[]> sides = new ArrayList<>();
        for (int i = 0; i < across.size(); i++) {
            Rule top = across.get(i);
            for (int j = i + 1; j < across.size(); j++) {
                Rule bottom = across.get(j);
                float h = bottom.pos() - top.pos();
                if (h > MAX_SIDE + JOIN) {
                    break;
                }
                if (Math.abs(bottom.start() - top.start()) > JOIN || Math.abs(bottom.end() - top.end()) > JOIN) {
                    continue;
                }
                float w = (top.length() + bottom.length()) / 2f;
                if (h < MIN_SIDE || w < MIN_SIDE || w > MAX_SIDE || h > 1.25f * w || w > 1.25f * h) {
                    continue;
                }
                Rule left = side(upright, (top.start() + bottom.start()) / 2f, top.pos(), bottom.pos());
                Rule right = side(upright, (top.end() + bottom.end()) / 2f, top.pos(), bottom.pos());
                if (left != null && right != null && alone(new Rule[] {top, bottom, left, right}, rules)) {
                    boxes.add(new float[] {left.pos(), top.pos(), right.pos(), bottom.pos()});
                    sides.add(new Rule[] {top, bottom, left, right});
                }
            }
        }
        if (boxes.isEmpty()) {
            return null;
        }
        List<Glyph> out = new ArrayList<>(glyphs);
        Set<Glyph> crosses = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Rule> used = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean[] painted = new boolean[boxes.size()];
        for (int k = 0; k < boxes.size(); k++) {
            painted[k] = painted(boxes.get(k), fills, marks);
        }
        for (int k = 0; k < boxes.size(); k++) {
            float[] b = boxes.get(k);
            if (comb(boxes, k) || used.contains(sides.get(k)[0]) || used.contains(sides.get(k)[1])
                    || inLegend(boxes, painted, k) || inked(b, sides.get(k), rules, marks)) {
                continue;
            }
            Glyph mark = null;
            boolean other = false;
            for (Glyph g : glyphs) {
                if (!g.isSpace() && inside(g, b)) {
                    other |= mark != null || !(CROSSES.contains(g.text) || TICKS.contains(g.text)) || g.text.isEmpty();
                    mark = g;
                }
            }
            if (other) {
                continue;
            }
            Glyph label = label(glyphs, b);
            if (label == null) {
                continue;
            }
            String symbol = mark == null ? EMPTY : TICKS.contains(mark.text) ? TICKED : CROSSED;
            boolean after = label.x >= b[2];
            // Sized so its square is the drawn one, at its own advance short of a label after it: a box starting an item
            // whose text is set off after it is the item's bullet (Marker), as the Closing Disclosure's loan disclosures
            // lay theirs out, and one set close to its label ("☐NO ☒YES") stays in the line
            float size = Math.min((b[3] - b[1]) / INK, MAX_SIZE * label.size);
            float x = b[0] - BEARING * size;
            float width = after ? Math.min(ADVANCE * size, label.x - x) : ADVANCE * size;
            Glyph box = new Glyph(symbol, x, width, label.baseline, size, label.ascent, label.descent, SYMBOLS,
                    sides.get(k)[0].rgb(), after ? label.seq - 1 : label.seq + 1, label.spaceWidth, false, false);
            out.add(box);
            if (mark != null) {
                crosses.add(mark);
            }
            Collections.addAll(used, sides.get(k));
        }
        if (used.isEmpty()) {
            return null;
        }
        out.removeIf(crosses::contains);
        return new Found(out, used);
    }

    /**
     * Whether something is drawn inside a box besides its sides and a white ground: a tick or cross drawn as strokes
     * (Codex's review). Its glyph would be an empty ☐ and the drawn tick would go, so such a box stays drawn.
     */
    private static boolean inked(float[] b, Rule[] sides, List<Rule> rules, List<VectorMark> marks) {
        float in = Math.max(JOIN, 0.15f * (b[2] - b[0]));
        float x0 = b[0] + in;
        float y0 = b[1] + in;
        float x1 = b[2] - in;
        float y1 = b[3] - in;
        for (VectorMark m : marks) {
            boolean outline = Math.abs(m.x() - b[0]) <= JOIN && Math.abs(m.right() - b[2]) <= JOIN
                    && Math.abs(m.top() - b[1]) <= JOIN && Math.abs(m.bottom() - b[3]) <= JOIN;
            boolean ink = !outline && (m.stroked() || m.shading() || m.filled() && !Decorations.isWhite(m.rgb()));
            float cx = (m.x() + m.right()) / 2f;
            float cy = (m.top() + m.bottom()) / 2f;
            if (ink && cx > x0 && cx < x1 && cy > y0 && cy < y1 && m.right() - m.x() <= b[2] - b[0] + JOIN) {
                return true;
            }
        }
        for (Rule r : rules) {
            if (r == sides[0] || r == sides[1] || r == sides[2] || r == sides[3]) {
                continue;
            }
            float mid = (r.start() + r.end()) / 2f;
            float cx = r.horizontal() ? mid : r.pos();
            float cy = r.horizontal() ? r.pos() : mid;
            if (cx > x0 && cx < x1 && cy > y0 && cy < y1 && r.length() <= b[2] - b[0] + JOIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether no other rule runs on in line with any of a box's sides: a table that draws its grid in a piece per cell
     * makes squares too. The Closing Disclosure's summaries table, drawn so, lost its rows' lines to "checkboxes" and
     * came apart.
     */
    private static boolean alone(Rule[] sides, List<Rule> rules) {
        for (Rule r : rules) {
            for (Rule s : sides) {
                if (r != s && r.horizontal() == s.horizontal() && Math.abs(r.pos() - s.pos()) <= JOIN
                        && r.start() <= s.end() + JOIN && r.end() >= s.start() - JOIN
                        && (r.start() < s.start() - JOIN || r.end() > s.end() + JOIN)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a box is painted inside, or is one of a legend's keys: a box of its size in its column or row, within
     * {@link #LEGEND_REACH} sides, is painted. MMWR's outbreak map keys its white class ("0") between grey and tan ones.
     */
    private static boolean inLegend(List<float[]> boxes, boolean[] painted, int k) {
        if (painted[k]) {
            return true;
        }
        float[] b = boxes.get(k);
        float side = b[2] - b[0];
        for (int i = 0; i < boxes.size(); i++) {
            float[] o = boxes.get(i);
            if (!painted[i] || Math.abs(o[2] - o[0] - side) > JOIN || Math.abs(o[3] - o[1] - (b[3] - b[1])) > JOIN) {
                continue;
            }
            boolean column = Math.abs(o[0] - b[0]) <= JOIN && Math.abs(o[1] - b[1]) <= LEGEND_REACH * side;
            boolean row = Math.abs(o[1] - b[1]) <= JOIN && Math.abs(o[0] - b[0]) <= LEGEND_REACH * side * 4;
            if (column || row) {
                return true;
            }
        }
        return false;
    }

    /** How far apart a legend's keys lie in a column, in sides; a row's keys, with their labels between, four times that. */
    private static final float LEGEND_REACH = 4f;

    /** Whether a colour other than white, a pattern or a gradient fills half the box or more. */
    private static boolean painted(float[] b, List<Fill> fills, List<VectorMark> marks) {
        float area = (b[2] - b[0]) * (b[3] - b[1]);
        for (Fill f : fills) {
            if (!Decorations.isWhite(f.rgb()) && overlap(b, f.x(), f.top(), f.right(), f.bottom()) >= area / 2f) {
                return true;
            }
        }
        for (VectorMark m : marks) {
            // A pattern or gradient reads as white but paints the box: the map's grey classes are hatched
            if (m.filled() && (m.shading() || !Decorations.isWhite(m.rgb()))
                    && overlap(b, m.x(), m.top(), m.right(), m.bottom()) >= area / 2f) {
                return true;
            }
        }
        return false;
    }

    private static float overlap(float[] b, float x, float top, float right, float bottom) {
        float w = Math.min(b[2], right) - Math.max(b[0], x);
        float h = Math.min(b[3], bottom) - Math.max(b[1], top);
        return w > 0 && h > 0 ? w * h : 0f;
    }

    private static long key(float pos) {
        return Math.round(pos);
    }

    /** An upright side at {@code x} running from {@code top} to {@code bottom}. */
    private static Rule side(Map<Long, List<Rule>> upright, float x, float top, float bottom) {
        for (long k = key(x) - 1; k <= key(x) + 1; k++) {
            for (Rule r : upright.getOrDefault(k, List.of())) {
                if (Math.abs(r.pos() - x) <= JOIN && Math.abs(r.start() - top) <= JOIN && Math.abs(r.end() - bottom) <= JOIN) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Whether a box has another on its row within half a side: a comb of boxes for a number's digits, not a
     * checkbox.
     */
    private static boolean comb(List<float[]> boxes, int k) {
        float[] b = boxes.get(k);
        float side = b[2] - b[0];
        for (int i = 0; i < boxes.size(); i++) {
            float[] o = boxes.get(i);
            if (i != k && Math.abs(o[1] - b[1]) <= JOIN
                    && (Math.abs(o[0] - b[2]) <= side / 2f || Math.abs(b[0] - o[2]) <= side / 2f)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(Glyph g, float[] b) {
        float mid = g.baseline - 0.35f * g.size;
        return g.centreX() >= b[0] && g.centreX() <= b[2] && mid >= b[1] && mid <= b[3];
    }

    /**
     * The nearest glyph on the box's row that starts after it or ends before it, within {@link #REACH} sides, of a
     * size the box suits; else null. One after the box wins.
     */
    private static Glyph label(List<Glyph> glyphs, float[] b) {
        float side = b[2] - b[0];
        float centre = (b[1] + b[3]) / 2f;
        Glyph after = null;
        Glyph before = null;
        for (Glyph g : glyphs) {
            if (g.isSpace() || inside(g, b) || Math.abs(g.baseline - 0.35f * g.size - centre) > 0.6f * side
                    || side > 1.6f * g.size || side < 0.4f * g.size) {
                continue;
            }
            if (g.x >= b[2] - 0.5f && g.x - b[2] <= REACH * side && (after == null || g.x < after.x)) {
                after = g;
            }
            if (g.right() <= b[0] + 0.5f && b[0] - g.right() <= REACH * side && (before == null || g.right() > before.right())) {
                before = g;
            }
        }
        return after != null ? after : before;
    }
}
