package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

final class VectorBullets {

    /** The most a mark may measure, either way, to be grouped with the marks it overlaps. */
    private static final float GROUP_MAX = 27f;
    /** How far apart two marks' boxes may lie and still count as overlapping, in points. */
    private static final float TOUCH = 0.5f;
    /** The most a bullet, with the marks overlapping it, may measure either way. */
    private static final float BULLET_MAX = 9f;

    private VectorBullets() {}

    static List<Glyph> synthesize(List<Glyph> glyphs, List<VectorMark> marks, List<Fill> fills, Set<Object> used) {
        List<Glyph> out = new ArrayList<>();
        GlyphIndex index = null;
        Set<VectorMark> drawn = null;
        for (VectorMark m : marks) {
            if ((m.filled() || m.curved()) && sized(m.x(), m.top(), m.right(), m.bottom())) {
                drawn = drawn == null ? inDrawings(marks) : drawn;
                if (drawn.contains(m)) {
                    continue;
                }
                index = index == null ? new GlyphIndex(glyphs) : index;
                boolean after = index.textBefore(m.x(), (m.top() + m.bottom()) / 2f);
                if (after && !m.round() && !m.boxy()) {
                    continue;
                }
                Glyph g = bulletFor(m.x(), m.top(), m.right(), m.bottom(), m.curved() ? "•" : "▪", m.rgb(), index);
                if (g != null && after && m.bottom() >= g.baseline - 0.06f * g.size) {
                    continue;
                }
                if (g != null) {
                    out.add(g);
                    used.add(m);
                }
            }
        }
        for (Fill f : fills) {
            if (!sized(f.x(), f.top(), f.right(), f.bottom())) {
                continue;
            }
            index = index == null ? new GlyphIndex(glyphs) : index;
            Glyph g = bulletFor(f.x(), f.top(), f.right(), f.bottom(), "▪", f.rgb(), index);
            if (g != null) {
                out.add(g);
                used.add(f);
            }
        }
        return out;
    }

    /**
     * The small marks that overlap others into a group bigger than a bullet: parts of a drawing, not bullets. USGS Fact
     * Sheet 2023-3022 draws an earthquake symbol, rings and stones, just left of a figure's label; its parts became 13
     * bullets before the label, which pushed it along and onto a second line in Word.
     */
    private static Set<VectorMark> inDrawings(List<VectorMark> marks) {
        List<VectorMark> small = new ArrayList<>();
        for (VectorMark m : marks) {
            if (m.right() - m.x() <= GROUP_MAX && m.bottom() - m.top() <= GROUP_MAX) {
                small.add(m);
            }
        }
        small.sort(Comparator.comparingDouble(VectorMark::x));
        int n = small.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            VectorMark a = small.get(i);
            open.removeIf(j -> small.get(j).right() < a.x() - TOUCH);
            for (int j : open) {
                VectorMark b = small.get(j);
                if (b.top() <= a.bottom() + TOUCH && a.top() <= b.bottom() + TOUCH) {
                    parent[root(parent, i)] = root(parent, j);
                }
            }
            open.add(i);
        }
        float[][] box = new float[n][];
        for (int i = 0; i < n; i++) {
            VectorMark m = small.get(i);
            int r = root(parent, i);
            float[] b = box[r];
            box[r] = b == null ? new float[] {m.x(), m.top(), m.right(), m.bottom()}
                    : new float[] {Math.min(b[0], m.x()), Math.min(b[1], m.top()), Math.max(b[2], m.right()),
                        Math.max(b[3], m.bottom())};
        }
        Set<VectorMark> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < n; i++) {
            float[] b = box[root(parent, i)];
            if (b[2] - b[0] > BULLET_MAX || b[3] - b[1] > BULLET_MAX) {
                out.add(small.get(i));
            }
        }
        return out;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static boolean sized(float x, float top, float right, float bottom) {
        float w = right - x;
        float h = bottom - top;
        return !(w < 1.5f || h < 1.5f || w > 9f || h > 9f || w / h > 1.6f || h / w > 1.6f);
    }

    private static Glyph bulletFor(float x, float top, float right, float bottom, String symbol, int rgb, GlyphIndex index) {
        float w = right - x;
        Glyph next = index.textAfter(right, (top + bottom) / 2f);
        if (next == null || w > next.size * 0.8f || index.between(x, next)) {
            return null;
        }
        return new Glyph(symbol, x, w, next.baseline, next.size, next.ascent, next.descent, next.font, rgb,
                next.seq - 1, next.spaceWidth, false, false);
    }

    private static final class GlyphIndex {

        private final List<Glyph> glyphs;
        private final int[] byMid;
        private final float[] mids;
        private final int[] byBaseline;
        private final float[] baselines;
        private final List<Integer> odd = new ArrayList<>();
        private float maxSize;

        GlyphIndex(List<Glyph> all) {
            glyphs = all;
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                Glyph g = all.get(i);
                if (g.isSpace()) {
                    continue;
                }
                if (Float.isNaN(mid(g)) || Float.isNaN(g.size)) {
                    odd.add(i);
                } else {
                    ids.add(i);
                    maxSize = Math.max(maxSize, g.size);
                }
            }
            byMid = ids.stream().sorted(Comparator.comparingDouble(i -> mid(all.get(i)))).mapToInt(Integer::intValue).toArray();
            mids = new float[byMid.length];
            for (int k = 0; k < byMid.length; k++) {
                mids[k] = mid(all.get(byMid[k]));
            }
            byBaseline = ids.stream().sorted(Comparator.comparingDouble(i -> all.get(i).baseline)).mapToInt(Integer::intValue).toArray();
            baselines = new float[byBaseline.length];
            for (int k = 0; k < byBaseline.length; k++) {
                baselines[k] = all.get(byBaseline[k]).baseline;
            }
        }

        private static float mid(Glyph g) {
            return g.baseline - g.size * 0.35f;
        }

        Glyph textAfter(float right, float cy) {
            float reach = maxSize * 0.45f + 1;
            int best = -1;
            for (int k = lowerBound(mids, cy - reach); k < mids.length && mids[k] <= cy + reach; k++) {
                best = better(best, byMid[k], right, cy);
            }
            for (int i : odd) {
                best = better(best, i, right, cy);
            }
            return best < 0 ? null : glyphs.get(best);
        }

        boolean textBefore(float x, float cy) {
            float reach = maxSize * 0.45f + 1;
            for (int k = lowerBound(mids, cy - reach); k < mids.length && mids[k] <= cy + reach; k++) {
                if (touches(glyphs.get(byMid[k]), x, cy)) {
                    return true;
                }
            }
            for (int i : odd) {
                if (touches(glyphs.get(i), x, cy)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean touches(Glyph g, float x, float cy) {
            return Math.abs(mid(g) - cy) <= g.size * 0.45f && g.right() <= x + 0.5f && x - g.right() <= g.size * 0.5f;
        }

        private int better(int best, int i, float right, float cy) {
            Glyph g = glyphs.get(i);
            if (Math.abs(mid(g) - cy) > g.size * 0.45f || g.x < right || g.x - right > g.size * 3f) {
                return best;
            }
            if (best < 0) {
                return i;
            }
            Glyph b = glyphs.get(best);
            return g.x < b.x || g.x == b.x && i < best ? i : best;
        }

        boolean between(float x, Glyph next) {
            for (int k = lowerBound(baselines, next.baseline - 1.001f); k < baselines.length && baselines[k] <= next.baseline + 1.001f; k++) {
                if (sitsBetween(glyphs.get(byBaseline[k]), x, next)) {
                    return true;
                }
            }
            for (int i : odd) {
                if (sitsBetween(glyphs.get(i), x, next)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean sitsBetween(Glyph g, float x, Glyph next) {
            return g != next && Math.abs(g.baseline - next.baseline) < 1f && g.x >= x - 1 && g.right() <= next.x;
        }

        private static int lowerBound(float[] sorted, float value) {
            int lo = 0;
            int hi = sorted.length;
            while (lo < hi) {
                int m = (lo + hi) >>> 1;
                if (sorted[m] < value) {
                    lo = m + 1;
                } else {
                    hi = m;
                }
            }
            return lo;
        }
    }
}
