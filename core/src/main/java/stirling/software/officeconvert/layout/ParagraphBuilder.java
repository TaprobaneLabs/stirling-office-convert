package stirling.software.officeconvert.layout;

import static stirling.software.officeconvert.layout.LineTraits.EDGE;
import static stirling.software.officeconvert.layout.LineTraits.allBold;
import static stirling.software.officeconvert.layout.LineTraits.continuesNumbering;
import static stirling.software.officeconvert.layout.LineTraits.endsWithHyphen;
import static stirling.software.officeconvert.layout.LineTraits.isFillLine;
import static stirling.software.officeconvert.layout.LineTraits.noneBold;
import static stirling.software.officeconvert.layout.LineTraits.rtl;
import static stirling.software.officeconvert.layout.LineTraits.spaceWidth;
import static stirling.software.officeconvert.layout.LineTraits.startsListItem;

import java.util.ArrayList;
import java.util.List;

import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.model.Paragraph.Align;

public final class ParagraphBuilder {

    /** {@code wraps}: the output wraps text around it (a picture, a text box); an overlay it doesn't. */
    public record Obstacle(Box box, float gapLeft, float gapRight, boolean wraps) {}

    private final DocStats stats;
    private final ParagraphMeasure measure;

    public ParagraphBuilder(DocStats stats) {
        this.stats = stats;
        this.measure = new ParagraphMeasure(stats);
    }

    public List<ParaDraft> build(List<Line> lines, float colLeft, float colRight) {
        return build(lines, colLeft, colRight, List.of());
    }

    public List<ParaDraft> build(List<Line> lines, float colLeft, float colRight, List<Obstacle> obstacles) {
        return build(lines, colLeft, colRight, obstacles, 0);
    }

    public List<ParaDraft> build(List<Line> lines, float colLeft, float colRight, List<Obstacle> obstacles, float pitch) {
        List<ParaDraft> out = new ArrayList<>();
        if (lines.isEmpty()) {
            return out;
        }
        LineRoom room = new LineRoom(lines, colLeft, colRight, obstacles);
        ParaDraft para = null;
        for (Line line : lines) {
            line.narrowed = room.narrowed(line);
            line.roomRight = room.right(line);
            line.narrowedRight = line.roomRight < colRight - 0.5f;
            if (para == null || breaksBefore(room, para, line, pitch)) {
                para = new ParaDraft(colLeft, colRight);
                out.add(para);
            } else if (isHardBreak(room, para.last(), line)) {
                para.hardBreaks.set(para.lines.size());
            }
            para.lines.add(line);
        }
        for (ParaDraft p : out) {
            if (block(p)) {
                p.hardBreaks.clear();
            }
            // A line the page cuts off ends there: its text runs on off the page, not into the next line. The paragraph's
            // room reaches past the edge, so its other lines keep their ends too
            boolean cut = false;
            for (Line l : p.lines) {
                cut |= l.runsOffPage() && !rtl(l);
            }
            if (cut) {
                p.hardBreaks.set(1, p.lines.size());
            }
            measure.measure(room, p);
            if (cut && (p.align != Align.LEFT || p.rtl)) {
                startAtLeft(room, p);
            }
            p.roomShift = roomShift(room, p);
        }
        ParagraphMeasure.alignRtlStarts(room, out);
        return out;
    }

    /**
     * Sets a paragraph holding a line the page cuts off flush left, where its lines start. Such a line reaches the page's
     * edge, so the measure can take it for justified, set right or centred, and in the room past the edge Word would then
     * spread or move it.
     */
    private static void startAtLeft(LineRoom room, ParaDraft p) {
        List<Line> lines = p.lines;
        float body = room.rel(lines.getFirst());
        if (lines.size() > 1) {
            body = Float.MAX_VALUE;
            for (int i = 1; i < lines.size(); i++) {
                body = Math.min(body, room.rel(lines.get(i)));
            }
        }
        float widest = -Float.MAX_VALUE;
        for (Line l : lines) {
            widest = Math.max(widest, l.right);
        }
        p.align = Align.LEFT;
        p.rtl = false;
        p.justifySlack = Float.NaN;
        p.left = body;
        p.first = room.rel(lines.getFirst()) - body;
        p.right = Math.min(p.right, p.colRight - widest);
    }

    /**
     * How far past a float's wrap edge every line of a paragraph written from the column starts, at least. LibreOffice
     * measures all the indents of a paragraph from a float's edge when one of its lines starts inside the float, on the edge
     * of a text box too, and from the column when they all start past it, a twip being enough.
     */
    private static final float PAST_EDGE = 0.25f;

    /**
     * How far right of the column a paragraph's indents are measured from, when one obstacle on the left narrows each of
     * its lines alike; else 0, and they're measured from the room beside the obstacle ({@link ParaDraft#left}).
     *
     * <p>Word starts a line beside a float at the float's edge or at the line's indent from the column, whichever is
     * further, so an indent measured from the room is lost there. LibreOffice adds the indents to the float's edge when a
     * line of the paragraph starts inside the float and measures them from the column when none does. A paragraph indented
     * past the edge is written from the column with all its lines past the edge, which both set where the PDF does; one
     * that starts at the edge keeps its indents from the room, which both set right too. Beside an overlay, which nothing
     * wraps around, the indents are always measured from the column.
     */
    private static float roomShift(LineRoom room, ParaDraft p) {
        if (p.rtl || p.lines.isEmpty()) {
            return 0f;
        }
        Line first = p.lines.getFirst();
        float left = room.left(first);
        boolean wraps = room.leftWraps(first);
        for (Line l : p.lines) {
            if (!room.narrowed(l) || room.leftWraps(l) != wraps || Math.abs(room.left(l) - left) > 1f) {
                return 0f;
            }
        }
        float shift = Math.max(0f, left - room.colLeft);
        if (!wraps || shift == 0f) {
            return shift;
        }
        if (p.left < 1f) {
            return 0f;
        }
        // A hanging first line that starts at the edge moves just past it, with the rest
        float nearest = p.left + Math.min(0f, p.first);
        return shift + Math.max(0f, PAST_EDGE - nearest);
    }

    private boolean breaksBefore(LineRoom room, ParaDraft para, Line cur, float ownPitch) {
        Line prev = para.last();
        float size = Math.max(prev.size, cur.size);

        float pitch = cur.baseline - prev.baseline;
        float expected = para.lines.size() >= 2 ? para.medianPitch() : ownPitch > 0 ? ownPitch : stats.pitchFor(prev.size);
        if (pitch > expected * 1.28f + 0.8f || pitch < prev.size * 0.5f) {
            return true;
        }
        if (changesLook(prev, cur, size) || startsListItem(cur) || isFillLine(cur) || isFillLine(prev)
                || continuesNumbering(para.lines.getFirst(), cur) || endsWithLeader(prev)) {
            return true;
        }

        boolean hyphenated = endsWithHyphen(prev);
        boolean centred = !room.narrowed(prev) && !room.narrowed(cur) && room.centred(prev) && room.centred(cur);
        boolean rtl = rtl(prev) && rtl(cur);
        float prevRel = startRel(room, prev, rtl);
        float curRel = startRel(room, cur, rtl);
        float bodyRel = para.lines.size() >= 2 ? startRel(room, para.lines.get(1), rtl) : prevRel;

        if (para.lines.size() >= 2) {
            if (curRel > bodyRel + 0.6f * size && !centred) {
                return true;
            }
            if (curRel < bodyRel - EDGE && !centred) {
                return true;
            }
        } else if (curRel > prevRel + EDGE && !centred && !prev.hasTabs() && !startsListItem(prev)) {
            if (rtl) {
                if (prev.x - spaceWidth(prev) - cur.words.getLast().width() > room.left(prev) + EDGE) {
                    return true;
                }
            } else {
                float wrap = Math.max(room.wrap(prev), Math.min(prev.right, cur.right));
                if (prev.right + spaceWidth(prev) + cur.words.getFirst().width() < wrap - EDGE && !hyphenated) {
                    return true;
                }
            }
        }
        if (centred || hyphenated) {
            return false;
        }
        if (rtl(prev) && rtl(cur)) {
            return rtlBreak(room, prev, cur);
        }
        float right = room.narrowed(prev) ? room.wrap(prev) : Math.max(room.wrap(prev), Math.max(prev.right, cur.right));
        Word firstWord = cur.words.getFirst();
        if (LineTraits.ideographic(cur)) {
            return ParagraphMeasure.textRight(prev) + leadingUnit(firstWord) < right - EDGE;
        }
        float unit = LineTraits.unspaced(cur) ? BreakUnits.first(firstWord) : firstWord.width();
        if (prev.right + spaceWidth(prev) + unit < right - EDGE) {
            return true;
        }
        return prev.hasTabs() != cur.hasTabs() && prev.hasTabs();
    }

    /**
     * A line whose last word follows a dot leader, as a contents entry and its page number: the entry ends there. Lines
     * of entries in one style fill the line to the same edge, so without this they ran on as one justified paragraph.
     */
    private static boolean endsWithLeader(Line l) {
        int n = l.words.size();
        return n >= 2 && l.gaps[n - 1] == Line.LEADER;
    }

    private static boolean changesLook(Line prev, Line cur, float size) {
        if (Math.abs(cur.size - prev.size) > 0.12f * size) {
            return true;
        }
        boolean prevAllBold = allBold(prev);
        boolean curAllBold = allBold(cur);
        if (prevAllBold != curAllBold && (prevAllBold ? noneBold(cur) : noneBold(prev))) {
            return true;
        }
        return prev.font.mono() != cur.font.mono();
    }

    private static float leadingUnit(Word w) {
        int n = w.glyphs.size() > 1 && ParagraphMeasure.HANGING.indexOf(w.glyphs.get(1).text.charAt(0)) >= 0 ? 2 : 1;
        return w.glyphs.get(n - 1).right() - w.first().x;
    }

    private static float startRel(LineRoom room, Line l, boolean rtl) {
        return rtl ? room.right(l) - l.right : room.rel(l);
    }

    private static boolean rtlBreak(LineRoom room, Line prev, Line cur) {
        if (startsListItem(cur)) {
            return true;
        }
        return prev.x - spaceWidth(prev) - cur.words.getLast().width() > room.left(prev) + EDGE;
    }

    private static boolean isHardBreak(LineRoom room, Line prev, Line cur) {
        if (room.narrowed(prev) || !room.centred(prev) || !room.centred(cur)) {
            return false;
        }
        float width = room.colRight - room.colLeft;
        return prev.width() + spaceWidth(prev) + cur.words.getFirst().width() < width - 2 * EDGE;
    }

    private static boolean block(ParaDraft p) {
        List<Line> lines = p.lines;
        if (p.hardBreaks.isEmpty() || lines.size() < 3) {
            return false;
        }
        for (int i = 1; i < lines.size(); i++) {
            Line a = lines.get(i - 1);
            Line b = lines.get(i);
            if (i + 1 < lines.size() ? !sameBlock(a, b) : Math.abs(a.x - b.x) > EDGE) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBlock(Line a, Line b) {
        return Math.abs(a.x - b.x) <= EDGE && Math.abs(a.right - b.right) <= EDGE;
    }
}
