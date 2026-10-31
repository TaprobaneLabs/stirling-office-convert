package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

final class FigureFinder {

    private static final float JOIN_GAP = 4f;

    static final float MIN_SIDE = 12f;
    private static final float MARK_MIN = 2.5f;

    private FigureFinder() {}

    static List<Box> strongRegions(List<VectorMark> marks, float pageW, float pageH) {
        List<Box> boxes = new ArrayList<>();
        for (VectorMark m : marks) {
            if (m.width() < 0.5f && m.height() < 0.5f) {
                continue;
            }
            if (m.width() > pageW * 0.9f && m.height() > pageH * 0.9f) {
                continue;
            }
            boxes.add(new Box(m.x(), m.top(), m.right(), m.bottom()));
        }
        return cluster(boxes, JOIN_GAP);
    }

    static List<Box> figures(
            List<Box> strong,
            List<Fill> fills,
            List<Rule> rules,
            List<Line> segments,
            List<VectorMark> marks,
            List<Box> labelled,
            float bodySize,
            float pageW,
            float pageH) {
        List<Box> weak = new ArrayList<>();
        for (Fill f : fills) {
            if (f.width() > pageW * 0.8f && f.height() > pageH * 0.5f || isWhite(f.rgb())) {
                continue;
            }
            weak.add(new Box(f.x(), f.top(), f.right(), f.bottom()));
        }
        int fillCount = weak.size();
        for (Rule r : rules) {
            if (r.length() > pageW * 0.6f) {
                continue;
            }
            float h = Math.max(r.thickness(), 0.5f) / 2f;
            weak.add(
                    r.horizontal()
                            ? new Box(r.start(), r.pos() - h, r.end(), r.pos() + h)
                            : new Box(r.pos() - h, r.start(), r.pos() + h, r.end()));
        }
        List<Box> grown = new ArrayList<>(strong);
        List<Box> pending = new ArrayList<>();
        for (int k = 0; k < weak.size(); k++) {
            Box w = weak.get(k);
            boolean absorbed = false;
            for (int i = 0; i < grown.size(); i++) {
                Box g = grown.get(i);
                if (g.near(w, JOIN_GAP)) {
                    if (k < fillCount && labelled(g, labelled) && holdsOwnText(w, g, segments, bodySize)) {
                        continue;
                    }
                    grown.set(i, g.union(w));
                    absorbed = true;
                    break;
                }
            }
            if (!absorbed) {
                pending.add(w);
            }
        }
        grown = cluster(grown, JOIN_GAP);

        List<Box> out = new ArrayList<>();
        for (Box b : grown) {
            boolean big = b.width() >= MIN_SIDE && b.height() >= MIN_SIDE;
            if (big ? !isProse(b, segments) || labelled(b, labelled) && labelledDrawing(b, segments, marks)
                    : smallFigure(b, segments, marks)) {
                out.add(b);
            }
        }
        for (Box c : clusterCounted(pending, JOIN_GAP)) {
            if (c.width() >= 60 && c.height() >= 40 && !isProse(c, segments) && overlapsNone(c, out)
                    && countInside(pending, c) >= 8 && wordsInside(c, segments) <= countInside(pending, c)) {
                out.add(c);
            }
        }
        return cluster(out, 1f);
    }

    private static boolean isWhite(int rgb) {
        return ((rgb >> 16) & 0xFF) >= 0xFB && ((rgb >> 8) & 0xFF) >= 0xFB && (rgb & 0xFF) >= 0xFB;
    }

    private static boolean overlapsNone(Box c, List<Box> boxes) {
        for (Box b : boxes) {
            if (b.overlapArea(c) > 0) {
                return false;
            }
        }
        return true;
    }

    private static int countInside(List<Box> boxes, Box region) {
        int n = 0;
        for (Box b : boxes) {
            if (region.contains(b.centreX(), b.centreY())) {
                n++;
            }
        }
        return n;
    }

    private static int wordsInside(Box region, List<Line> segments) {
        int n = 0;
        for (Line l : segments) {
            for (Word w : l.words) {
                if (region.contains((w.x + w.right) / 2f, l.baseline - l.size * 0.3f)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean smallFigure(Box b, List<Line> segments, List<VectorMark> marks) {
        int dots = 0;
        int other = 0;
        for (VectorMark m : marks) {
            if (b.contains((m.x() + m.right()) / 2f, (m.top() + m.bottom()) / 2f)) {
                if (m.round()) {
                    dots++;
                } else {
                    other++;
                }
            }
        }
        if (dots > 0) {
            return other > 0;
        }
        return besideText(b, segments);
    }

    private static boolean besideText(Box b, List<Line> segments) {
        if (Math.min(b.width(), b.height()) < MARK_MIN || Math.max(b.width(), b.height()) < 2 * MARK_MIN) {
            return false;
        }
        boolean level = false;
        for (Line l : segments) {
            if (b.centreY() < l.top || b.centreY() > l.bottom || b.height() > 2 * l.size) {
                continue;
            }
            for (Word w : l.words) {
                if (w.x < b.right() && w.right > b.x()) {
                    return false;
                }
                level |= Math.max(w.x - b.right(), b.x() - w.right) <= 12 * l.size;
            }
        }
        return level;
    }

    /**
     * A drawing of this many marks, at least this big a side, holding at least this many lines of text, of which at most
     * this many are long (six words or 40 characters) and at most this share stand one under another at a paragraph's
     * pitch, is a labelled drawing: a map or a diagram whose text is its labels, however many characters they add up to.
     * USGS Fact Sheet 2024-3029's map of Africa (230 marks, 32 short labels, none stacked) passes; 3033's fish infographic
     * (77% of its lines stacked) and 3022's volcano (three lines of six words) don't.
     */
    static final int LABELLED_MARKS = 40;

    static final float LABELLED_SIDE = 72f;

    static final int LABELLED_LINES = 3;

    static final int LABELLED_LONG_LINES = 1;

    static final float LABELLED_STACKED = 0.25f;

    /** The regions of a page's marks that are labelled drawings. */
    static List<Box> labelledDrawings(List<VectorMark> marks, List<Line> segments, float pageW, float pageH) {
        List<Box> out = new ArrayList<>();
        for (Box b : strongRegions(marks, pageW, pageH)) {
            if (labelledDrawing(b, segments, marks)) {
                out.add(b);
            }
        }
        return out;
    }

    static boolean labelledDrawing(Box region, List<Line> segments, List<VectorMark> marks) {
        if (region.width() < LABELLED_SIDE || region.height() < LABELLED_SIDE) {
            return false;
        }
        int inside = 0;
        for (VectorMark m : marks) {
            if (region.contains((m.x() + m.right()) / 2f, (m.top() + m.bottom()) / 2f) && ++inside >= LABELLED_MARKS) {
                break;
            }
        }
        if (inside < LABELLED_MARKS) {
            return false;
        }
        List<Line> lines = new ArrayList<>();
        int longLines = 0;
        for (Line l : segments) {
            if (region.contains(l.centre(), l.baseline - l.size * 0.3f)) {
                lines.add(l);
                if ((l.words.size() >= 6 || l.chars >= 40) && ++longLines > LABELLED_LONG_LINES) {
                    return false;
                }
            }
        }
        return lines.size() >= LABELLED_LINES && stackedShare(lines) <= LABELLED_STACKED;
    }

    /** Whether a region is mostly one of these labelled drawings, or holds one: half the smaller of the two overlaps. */
    static boolean labelled(Box region, List<Box> labelled) {
        for (Box l : labelled) {
            if (region.overlapArea(l) >= 0.5f * Math.min(region.area(), l.area())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The share of these lines that stand in a run of three or more, each under the last at a paragraph's pitch (the gap
     * under it at most 0.6 of its height, the two of a height and overlapping across): the lines of a paragraph or a list,
     * where a map's labels stand apart.
     */
    static float stackedShare(List<Line> lines) {
        List<Line> sorted = new ArrayList<>(lines);
        sorted.sort((a, b) -> Float.compare(a.top, b.top));
        int n = sorted.size();
        int[] next = new int[n];
        java.util.Arrays.fill(next, -1);
        for (int i = 0; i < n; i++) {
            Line a = sorted.get(i);
            float h = a.bottom - a.top;
            for (int j = i + 1; j < n; j++) {
                Line b = sorted.get(j);
                float hb = b.bottom - b.top;
                if (Math.abs(h - hb) > 0.3f * Math.max(h, hb)) {
                    continue;
                }
                float gap = b.top - a.bottom;
                if (gap > 0.6f * h) {
                    if (b.top - a.top > 2 * h) {
                        break;
                    }
                    continue;
                }
                if (b.x < a.right && a.x < b.right && gap > -0.3f * h) {
                    next[i] = j;
                    break;
                }
            }
        }
        boolean[] inRun = new boolean[n];
        for (int i = 0; i < n; i++) {
            int length = 1;
            for (int j = next[i]; j >= 0; j = next[j]) {
                length++;
            }
            if (length >= 3) {
                for (int j = i; j >= 0; j = next[j]) {
                    inRun[j] = true;
                }
            }
        }
        int stacked = 0;
        for (boolean s : inRun) {
            stacked += s ? 1 : 0;
        }
        return n == 0 ? 0 : (float) stacked / n;
    }

    /**
     * Whether a fill holds text of its own beside a region: two lines at body size, or one of six words, centred in it and
     * outside the region. A labelled drawing grows only through fills without it, so a map doesn't take in the panel of
     * prose beside it (3029's left column, on a grey panel the map's frame overlaps) and turn into prose itself.
     */
    static boolean holdsOwnText(Box fill, Box region, List<Line> segments, float bodySize) {
        int body = 0;
        for (Line l : segments) {
            float y = l.baseline - l.size * 0.3f;
            if (!fill.contains(l.centre(), y) || region.contains(l.centre(), y)) {
                continue;
            }
            if (l.words.size() >= 6 || l.size >= 0.9f * bodySize && ++body >= 2) {
                return true;
            }
        }
        return false;
    }

    static boolean isProse(Box region, List<Line> segments) {
        int longLines = 0;
        int chars = 0;
        for (Line l : segments) {
            if (!region.contains(l.centre(), l.baseline - l.size * 0.3f)) {
                continue;
            }
            chars += l.chars;
            if (l.chars >= 40 && l.words.size() >= 6) {
                longLines++;
            }
        }
        return longLines >= 3 || chars > 350;
    }

    static float runningShare(Box region, List<Line> segments) {
        int all = 0;
        int running = 0;
        for (Line l : segments) {
            int words = 0;
            int chars = 0;
            for (int i = 0; i <= l.words.size(); i++) {
                Word w = i < l.words.size() ? l.words.get(i) : null;
                boolean in = w != null && region.contains(Regions.wordX(w), Regions.wordY(l));
                if (!in || i > 0 && l.gaps[i] != Line.SPACE) {
                    if (words >= 6 && chars >= 40) {
                        running += chars;
                    }
                    words = 0;
                    chars = 0;
                }
                if (in) {
                    all += w.text.length();
                    words++;
                    chars += w.text.length();
                }
            }
        }
        return all == 0 ? 0 : (float) running / all;
    }

    private static final int MAX_CLUSTERED = 20_000;

    static List<Box> cluster(List<Box> boxes, float gap) {
        List<Box> current = new ArrayList<>(new LinkedHashSet<>(boxes));
        if (current.size() > MAX_CLUSTERED) {
            Box all = current.getFirst();
            for (Box b : current) {
                all = all.union(b);
            }
            return List.of(all);
        }
        while (true) {
            List<Box> merged = clusterOnce(current, gap);
            if (merged.size() == current.size()) {
                return merged;
            }
            current = merged;
        }
    }

    private static List<Box> clusterOnce(List<Box> boxes, float gap) {
        int n = boxes.size();
        if (n < 2) {
            return boxes;
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        final float cell = 48f;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Box b = boxes.get(i);
            int x0 = (int) Math.floor((b.x() - gap) / cell);
            int x1 = (int) Math.floor((b.right() + gap) / cell);
            int y0 = (int) Math.floor((b.top() - gap) / cell);
            int y1 = (int) Math.floor((b.bottom() + gap) / cell);
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) > 400) {
                for (int j = 0; j < n; j++) {
                    if (j != i && b.near(boxes.get(j), gap)) {
                        union(parent, i, j);
                    }
                }
                continue;
            }
            for (int gx = x0; gx <= x1; gx++) {
                for (int gy = y0; gy <= y1; gy++) {
                    long key = (((long) gx << 32) ^ (gy & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
                    List<Integer> bucket = grid.computeIfAbsent(key, k -> new ArrayList<>());
                    for (int j : bucket) {
                        if (find(parent, i) != find(parent, j) && b.near(boxes.get(j), gap)) {
                            union(parent, i, j);
                        }
                    }
                    bucket.add(i);
                }
            }
        }
        Map<Integer, Box> roots = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            roots.merge(find(parent, i), boxes.get(i), Box::union);
        }
        return new ArrayList<>(roots.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    private static List<Box> clusterCounted(List<Box> boxes, float gap) {
        return cluster(boxes, gap);
    }
}
