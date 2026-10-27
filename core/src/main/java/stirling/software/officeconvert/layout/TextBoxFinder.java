package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.List;

import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.extract.PageGraphics;

final class TextBoxFinder {

    record Container(PageGraphics.VectorMark mark, Box box, int fill, int line, float lineWidth, boolean rounded) {}

    record Result(List<PageLayout.TextBoxItem> boxes, List<Container> claimed) {}

    private final ParagraphBuilder paragraphs;

    TextBoxFinder(ParagraphBuilder paragraphs) {
        this.paragraphs = paragraphs;
    }

    private static final float PANEL_WIDTH = 120f;
    private static final float PANEL_HEIGHT = 60f;

    static List<Container> containers(List<PageGraphics.VectorMark> marks, List<Line> segments, boolean tinted) {
        List<Container> out = new ArrayList<>();
        for (PageGraphics.VectorMark m : marks) {
            boolean panel = m.boxy() && m.width() >= PANEL_WIDTH && m.height() >= PANEL_HEIGHT
                    && FigureFinder.isProse(new Box(m.x(), m.top(), m.right(), m.bottom()), segments);
            if (m.shading() || m.sparse() || m.segments() > 180 || m.diagonal() && !m.curved() && !panel || m.width() < 40
                    || m.height() < 20) {
                continue;
            }
            Box b = new Box(m.x(), m.top(), m.right(), m.bottom());
            int inside = 0;
            for (Line l : segments) {
                if (l.x >= b.x() && l.right <= b.right() && l.top >= b.top() && l.bottom <= b.bottom()) {
                    inside++;
                }
            }
            if (inside == 0 || diagramPanel(m, b, marks, segments)) {
                continue;
            }
            int fill = m.filled() && (tinted || !Decorations.isWhite(m.rgb())) ? m.rgb() : -1;
            int line = m.stroked() && !Decorations.isWhite(m.strokeRgb()) ? m.strokeRgb() : -1;
            if (fill < 0 && line < 0 && !m.filled()) {
                continue;
            }
            out.add(new Container(m, b, fill, line, m.lineWidth(), m.curved()));
        }
        return out;
    }

    private static final int PANEL_ART = 8;

    private static boolean diagramPanel(PageGraphics.VectorMark m, Box b, List<PageGraphics.VectorMark> marks, List<Line> segments) {
        int art = 0;
        int connectors = 0;
        for (PageGraphics.VectorMark o : marks) {
            if (o != m && !o.round() && b.contains((o.x() + o.right()) / 2f, (o.top() + o.bottom()) / 2f)
                    && o.width() * o.height() < 0.5f * b.area()) {
                art++;
                connectors += o.diagonal() && !o.boxy() ? 1 : 0;
            }
        }
        return art >= PANEL_ART && connectors >= 2 && !FigureFinder.isProse(b, segments);
    }

    /** What a page draws besides text, which a panel can't hold: its pictures and its drawings (marks, fills, rules). */
    record PanelArt(float pageHeight, List<Box> pictures, List<Box> drawings) {}

    static PanelArt panelArt(float pageHeight, List<PageGraphics.ImageDraw> images, List<PageGraphics.VectorMark> marks,
            List<Fill> fills, List<Rule> rules) {
        List<Box> pictures = new ArrayList<>();
        for (PageGraphics.ImageDraw img : images) {
            pictures.add(new Box(img.clipX(), img.clipTop(), img.clipRight(), img.clipBottom()));
        }
        List<Box> drawings = new ArrayList<>();
        for (PageGraphics.VectorMark m : marks) {
            drawings.add(new Box(m.x(), m.top(), m.right(), m.bottom()));
        }
        for (Fill f : fills) {
            drawings.add(Regions.of(f));
        }
        for (Rule r : rules) {
            drawings.add(Regions.of(r));
        }
        return new PanelArt(pageHeight, pictures, drawings);
    }

    Result find(List<Fill> fills, List<Container> containers, List<Line> flow, float frameLeft, float frameRight,
            PanelArt panelArt, List<Box> pictures) {
        List<PageLayout.TextBoxItem> out = new ArrayList<>();
        List<Container> claimed = new ArrayList<>();
        float colWidth = frameRight - frameLeft;
        List<Container> candidates = new ArrayList<>(containers);
        for (Fill f : fills) {
            if (!Decorations.isWhite(f.rgb())) {
                candidates.add(new Container(null, Regions.of(f), f.rgb(), -1, 0, false));
            }
        }
        candidates.removeIf(c -> c.box().width() > colWidth * 0.85f || c.box().width() < 36 || c.box().height() < 18
                || Backdrops.isBackdrop(c.box(), flow) || framesPicture(c.box(), pictures));
        List<Container> holding = new ArrayList<>();
        for (Container cand : candidates) {
            if (panel(cand, flow, panelArt)) {
                holding.add(cand);
            }
        }
        for (Container cand : candidates) {
            Box box = cand.box();
            Fill f = cand.mark() == null ? new Fill(box.x(), box.top(), box.right(), box.bottom(), cand.fill()) : null;
            List<Line> inside = new ArrayList<>();
            boolean beside = false;
            boolean besideRight = false;
            float gap = Float.MAX_VALUE;
            for (Line l : flow) {
                if (holds(box, l)) {
                    inside.add(l);
                    continue;
                }
                boolean overlapsV = l.top < box.bottom() - 1 && l.bottom > box.top() + 1;
                if (overlapsV && (l.right <= box.x() + 1 || l.x >= box.right() - 1) && !insideOther(l, cand, candidates)) {
                    beside = true;
                    besideRight |= l.right > box.x() + 1;
                    gap = Math.min(gap, l.right <= box.x() + 1 ? box.x() - l.right : l.x - box.right());
                }
            }
            float panelGap = beside || !holding.contains(cand) ? Float.MAX_VALUE : panelGap(cand, holding);
            boolean inRow = panelGap != Float.MAX_VALUE;
            // A sidebar takes two lines or more; a panel in a row may hold one, as a row of labelled pills does.
            if (inside.size() < (inRow ? 1 : 2) || (!beside || gap < 6f) && !inRow
                    || f != null && touchesSameFill(f, fills)) {
                continue;
            }
            if (!beside) {
                gap = panelGap;
            } else if (!besideRight) {
                // Text that ends beside the box, on its left, keeps it out of the room up to that text less what a
                // fitted line may run past its PDF line: at the PDF's gap, Word broke lines beside the box a word early
                // where its widths came out wider (USGS Fact Sheet 2024-3021's references, beside the box in the other
                // column). Text on its right starts at the gap, so that keeps it
                gap = Math.max(0f, gap - BESIDE_SLACK);
            }
            flow.removeAll(inside);
            out.add(item(cand, inside, gap));
            claimed.add(cand);
        }
        return new Result(out, claimed);
    }

    /** A box drawn around a picture, holding its labels, is the picture's frame: most of the box is the picture. */
    private static boolean framesPicture(Box box, List<Box> pictures) {
        for (Box p : pictures) {
            if (box.overlapArea(p) >= PICTURE_FRAME * box.area()) {
                return true;
            }
        }
        return false;
    }

    private static final float PICTURE_FRAME = 0.8f;

    private static boolean holds(Box box, Line l) {
        return l.x >= box.x() - 1 && l.right <= box.right() + 1 && l.top >= box.top() - 2 && l.bottom <= box.bottom() + 2;
    }

    /** Boxes set apart from each other in a row hold no text running beside them, only each other: a row of panels. */
    private static final float PANEL_GAP = 4f;

    /**
     * How far the text ending beside a box may run past the PDF's lines in Word: the most a fitted paragraph's edge may
     * move out (5 pt) and a point.
     */
    private static final float BESIDE_SLACK = 6f;

    /** A panel is a short box of text: at most this share of the page's height and this many lines. */
    private static final float PANEL_PAGE_SHARE = 1f / 3f;

    private static final int PANEL_LINES = 15;

    /** A picture covering this share of a box, or this many drawn marks inside it, make it a figure, not a panel. */
    private static final float PANEL_ART_SHARE = 0.02f;

    private static final int PANEL_DRAWINGS = 3;

    private static final float PANEL_EDGE = 2f;

    /**
     * Whether Word sets this text in the room the PDF gives it. A panel keeps its size, so text Word sets wider or taller
     * (Tamil, Bengali or Thai in Word's own fonts) would be cut off at its foot.
     */
    private static boolean setAsInPdf(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            switch (Character.UnicodeScript.of(cp)) {
                case LATIN, GREEK, CYRILLIC, COMMON, INHERITED -> { }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean same(Box a, Box b) {
        return Math.abs(a.x() - b.x()) < 1 && Math.abs(a.top() - b.top()) < 1 && Math.abs(a.right() - b.right()) < 1
                && Math.abs(a.bottom() - b.bottom()) < 1;
    }

    /**
     * A box that can be a panel: it holds a few whole lines of text Word sets as the PDF does and no picture or drawing (a
     * framed chart or photo stays with its figure), and it's short and set apart from running text, so shading behind a
     * column isn't one.
     */
    private static boolean panel(Container c, List<Line> flow, PanelArt panelArt) {
        Box b = c.box();
        // A panel is a box: a pie chart's wedge, with its label inside it, is a drawing.
        if (c.mark() != null && !c.mark().boxy() || b.height() > PANEL_PAGE_SHARE * panelArt.pageHeight()) {
            return false;
        }
        int lines = 0;
        for (Line l : flow) {
            if (holds(b, l)) {
                if (!setAsInPdf(l.text())) {
                    return false;
                }
                lines++;
                continue;
            }
            boolean across = l.x < b.right() && l.right > b.x();
            // Running text the box only shades: a line it cuts through, or one going on right under it.
            if (across && l.top < b.bottom() && l.bottom > b.top()
                    || across && l.top >= b.bottom() && l.top - b.bottom() < 0.5f * (l.bottom - l.top)) {
                return false;
            }
        }
        if (lines == 0 || lines > PANEL_LINES) {
            return false;
        }
        for (Box a : panelArt.pictures()) {
            if (a.overlapArea(b) > PANEL_ART_SHARE * b.area() && a.area() < 2 * b.area()) {
                return false;
            }
        }
        // The box's own outline (a mark or rules along its edges) is no drawing in it.
        Box within = b.grow(-PANEL_EDGE);
        int drawn = 0;
        for (Box a : panelArt.drawings()) {
            if (!same(a, b) && a.area() < 0.5f * b.area() && a.overlapArea(within) > 0.5f * a.area()) {
                drawn++;
            }
        }
        return drawn < PANEL_DRAWINGS;
    }

    /**
     * The gap to the nearest other box holding text level with this one and set apart from it (a panel in a row of
     * panels, say a page of boxed facts between photos), or MAX_VALUE when there is none. Such a box is placed where the
     * PDF has it, as a text box of its size, instead of its text joining the next panel's lines across the row.
     */
    private static float panelGap(Container self, List<Container> holding) {
        Box b = self.box();
        float nearest = Float.MAX_VALUE;
        for (Container c : holding) {
            Box o = c.box();
            if (c == self || o.top() >= b.bottom() - 1 || o.bottom() <= b.top() + 1) {
                continue;
            }
            float shared = Math.min(o.bottom(), b.bottom()) - Math.max(o.top(), b.top());
            if (shared < 0.5f * Math.min(o.height(), b.height())) {
                continue;
            }
            float apart = o.x() >= b.right() ? o.x() - b.right() : b.x() >= o.right() ? b.x() - o.right() : -1;
            if (apart >= PANEL_GAP) {
                nearest = Math.min(nearest, apart);
            }
        }
        return nearest;
    }

    private PageLayout.TextBoxItem item(Container cand, List<Line> inside, float gap) {
        Box box = cand.box();
        float textLeft = Float.MAX_VALUE;
        float textRight = -Float.MAX_VALUE;
        for (Line l : inside) {
            textLeft = Math.min(textLeft, l.x);
            textRight = Math.max(textRight, l.right);
        }
        float inset = Math.max(0, textLeft - box.x());
        float innerRight = Math.max(textRight, box.right() - inset);
        List<ParaDraft> paras = paragraphs.build(LineGroups.joinRows(inside), box.x() + inset, innerRight);
        return new PageLayout.TextBoxItem(box, cand.fill(), box.x() + inset, innerRight, paras,
                gap == Float.MAX_VALUE ? 9f : Math.clamp(gap, 0f, 36f), cand.line(), cand.lineWidth(), cand.rounded(), false);
    }

    private static boolean touchesSameFill(Fill f, List<Fill> fills) {
        for (Fill o : fills) {
            if (o == f) {
                continue;
            }
            boolean vOverlap = o.top() < f.bottom() - 1 && o.bottom() > f.top() + 1;
            boolean hOverlap = o.x() < f.right() - 1 && o.right() > f.x() + 1;
            boolean sideBySide = vOverlap && (Math.abs(o.x() - f.right()) < 3 || Math.abs(f.x() - o.right()) < 3);
            boolean stacked = hOverlap && (Math.abs(o.top() - f.bottom()) < 3 || Math.abs(f.top() - o.bottom()) < 3);
            if (sideBySide || stacked) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideOther(Line l, Container self, List<Container> candidates) {
        for (Container c : candidates) {
            Box b = c.box();
            if (c != self && l.x >= b.x() - 1 && l.right <= b.right() + 1 && l.top >= b.top() - 2 && l.bottom <= b.bottom() + 2) {
                return true;
            }
        }
        return false;
    }
}
