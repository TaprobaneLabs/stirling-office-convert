package stirling.software.officeconvert.layout;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import stirling.software.officeconvert.extract.ImageBudget;
import stirling.software.officeconvert.extract.PageGraphics.ImageDraw;
import stirling.software.officeconvert.extract.PageGraphics.Rule;

final class ScanRules {

    private static final int MAX_SIDE = 3600;

    private static final int DARK = 210;

    private static final int GAP = 2;

    private static final float MIN_LENGTH = 40f;

    private static final float MAX_THICKNESS = 4f;

    private static final float BAND = 30f;

    private static final int MAX_RULES = 400;

    private ScanRules() {}

    static List<Rule> find(ImageDraw scan) {
        if (!ImageBudget.affordable(scan.image())) {
            return List.of();
        }
        BufferedImage img;
        try {
            int sub = Math.max(1, (Math.max(scan.image().getWidth(), scan.image().getHeight()) + MAX_SIDE - 1) / MAX_SIDE);
            img = scan.image().getImage(null, sub);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        if (img == null || img.getWidth() < 2 || img.getHeight() < 2) {
            return List.of();
        }
        img = ScanImage.shown(img, scan);
        if (img.getWidth() < 2 || img.getHeight() < 2) {
            return List.of();
        }
        int w = img.getWidth();
        int h = img.getHeight();
        // Grey paper (a phone's photo of the page) lowers the limit by as much as it is darker than white.
        int shift = ScanPaper.shift(img);
        int limit = DARK - shift;
        int faintLimit = FAINT - shift;
        boolean[] dark = new boolean[w * h];
        boolean[] faint = new boolean[w * h];
        byte[] luma = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int lum = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                dark[y * w + x] = lum < limit;
                faint[y * w + x] = lum < faintLimit;
                luma[y * w + x] = (byte) lum;
            }
        }
        float sx = (scan.right() - scan.x()) / w;
        float sy = (scan.bottom() - scan.top()) / h;
        List<Rule> out = new ArrayList<>();
        boolean tilted = false;
        for (int[] g : groups(dark, w, h, true, Math.round(MIN_LENGTH / sx))) {
            if (contrasted(luma, w, h, true, g)) {
                add(out, true, g, scan.top(), sy, scan.x(), sx);
                tilted |= tiltedLine(dark, w, g, sy);
            }
        }
        for (int[] g : groups(dark, w, h, false, Math.round(MIN_LENGTH / sy))) {
            if (contrasted(luma, w, h, false, g)) {
                add(out, false, g, scan.x(), sx, scan.top(), sy);
            }
        }
        if (out.size() > MAX_RULES) {
            return List.of();
        }
        // A light rule comes out in pieces, and parts of it not at all: its pieces are joined and its missing parts
        // looked for in fainter ink before it is judged, so it ends at its frame like a rule drawn whole
        Ink ink = new Ink(faint, w, h, scan.x(), scan.top(), sx, sy);
        List<Rule> joined = joined(completed(joined(out), ink));
        List<Rule> kept = new ArrayList<>();
        for (Rule r : joined) {
            if (anchored(r, joined) && !doubled(r, joined)) {
                kept.add(r);
            }
        }
        // A tilted page's rules are read as the edges of bands, not rows: sides between them made a grid of none
        List<Rule> sides = tilted ? List.of() : cellSides(kept, ink);
        if (sides.isEmpty()) {
            return kept;
        }
        kept.addAll(sides);
        List<Rule> whole = joined(kept);
        List<Rule> done = new ArrayList<>();
        for (Rule r : whole) {
            if (!doubled(r, whole)) {
                done.add(r);
            }
        }
        return done;
    }

    /** A scan's faint ink: a light grey rule or a copier's halftone dots, too light for {@link #DARK}. */
    static final int FAINT = 228;

    /**
     * The share of an edge's length that must hold a line of faint ink for the edge to be drawn. On step G's table scans
     * the edges a copier faded held 1.00; bold text along a row, which leaves thin strokes such as an e's bar, 0.62.
     */
    static final float COVER = 0.85f;

    /** Edges shorter than this between two rules aren't looked for. */
    static final float MIN_EDGE = 12f;

    /** How far across its expected place an edge's ink may lie: a slight tilt moves a rule by about a point. */
    static final float ACROSS = 1.2f;

    /**
     * A scan's faint ink, and whether a line of it runs along a given stretch: at each step along, ink within
     * {@link ScanRules#ACROSS} of the line (or two pixels along), and not a broad patch reaching past it on both sides, which is a
     * letter's stroke crossing the place, not a rule.
     */
    record Ink(boolean[] faint, int w, int h, float x0, float y0, float sx, float sy) {

        float share(boolean horizontal, float pos, float from, float to) {
            float alongScale = horizontal ? sx : sy;
            float acrossScale = horizontal ? sy : sx;
            float along0 = horizontal ? x0 : y0;
            float across0 = horizontal ? y0 : x0;
            int lines = horizontal ? h : w;
            int length = horizontal ? w : h;
            int c = Math.round((pos - across0) / acrossScale);
            int k = Math.max(1, Math.round(ACROSS / acrossScale));
            int a = Math.max(0, Math.round((from - along0) / alongScale));
            int b = Math.min(length - 1, Math.round((to - along0) / alongScale));
            if (b - a < 4 || c - k - 5 < 0 || c + k + 5 >= lines) {
                return 0f;
            }
            boolean[] line = new boolean[b - a + 1];
            for (int p = a; p <= b; p++) {
                boolean centre = false;
                for (int d = -k; d <= k && !centre; d++) {
                    centre = at(horizontal, c + d, p);
                }
                if (centre && !(beside(horizontal, c - k - 5, c - k - 2, p) && beside(horizontal, c + k + 2, c + k + 5, p))) {
                    line[p - a] = true;
                }
            }
            int count = 0;
            for (int i = 0; i < line.length; i++) {
                boolean near = false;
                for (int d = -2; d <= 2 && !near; d++) {
                    near = i + d >= 0 && i + d < line.length && line[i + d];
                }
                count += near ? 1 : 0;
            }
            return count / (float) line.length;
        }

        /**
         * Where thin faint ink runs down unbroken (gaps of up to {@link ScanRules#GAP} pixels: halftone dots) from {@code top} to
         * {@code bottom}, a row's rules, give or take {@link ScanRules#SIDE_REACH}, between {@code from} and {@code to}: runs of
         * such columns at most {@link ScanRules#SIDE_WIDTH} wide, with bare paper a little way off on both sides.
         */
        List<Float> sides(float top, float bottom, float from, float to) {
            int ya = Math.max(0, Math.round((top + SIDE_REACH - y0) / sy));
            int yb = Math.min(h - 1, Math.round((bottom - SIDE_REACH - y0) / sy));
            int xa = Math.max(0, Math.round((from - x0) / sx));
            int xb = Math.min(w - 1, Math.round((to - x0) / sx));
            if (yb - ya < 4 || xb - xa < 8) {
                return List.of();
            }
            boolean[] whole = new boolean[xb - xa + 1];
            float[] cover = new float[xb - xa + 1];
            for (int x = xa; x <= xb; x++) {
                int count = 0;
                int gap = 0;
                boolean broken = false;
                for (int y = ya; y <= yb; y++) {
                    if (faint[y * w + x]) {
                        count++;
                        gap = 0;
                    } else if (++gap > GAP) {
                        broken = true;
                    }
                }
                whole[x - xa] = !broken;
                cover[x - xa] = count / (float) (yb - ya + 1);
            }
            int widest = Math.max(1, Math.round(SIDE_WIDTH / sx));
            List<Float> out = new ArrayList<>();
            for (int i = 0; i < whole.length; i++) {
                if (!whole[i]) {
                    continue;
                }
                int j = i;
                while (j + 1 < whole.length && whole[j + 1]) {
                    j++;
                }
                if (j - i + 1 <= widest && bare(cover, i - 6, i - 3) && bare(cover, j + 3, j + 6)) {
                    out.add(x0 + (xa + (i + j) / 2f + 0.5f) * sx);
                }
                i = j;
            }
            return out;
        }

        private static boolean bare(float[] cover, int lo, int hi) {
            float sum = 0;
            int n = 0;
            for (int i = Math.max(0, lo); i <= Math.min(cover.length - 1, hi); i++) {
                sum += cover[i];
                n++;
            }
            return n > 0 && sum / n < 0.5f;
        }

        private boolean beside(boolean horizontal, int lo, int hi, int p) {
            for (int q = lo; q <= hi; q++) {
                if (at(horizontal, q, p)) {
                    return true;
                }
            }
            return false;
        }

        private boolean at(boolean horizontal, int across, int along) {
            return horizontal ? faint[across * w + along] : faint[along * w + across];
        }
    }

    /**
     * The rules with a frame's missing edges added where faint ink draws them. A copier drops much of a light table's
     * grid: one row rule came out only over its last column, and the others then held two rows' text in one cell. Each
     * rule is looked for between each two neighbouring rules that cross its line, horizontals first so that the
     * verticals are then looked for between every row rule.
     */
    static List<Rule> completed(List<Rule> rules, Ink ink) {
        List<Rule> out = new ArrayList<>(rules);
        for (boolean horizontal : new boolean[] {true, false}) {
            List<Rule> current = new ArrayList<>(out);
            List<Float> places = new ArrayList<>();
            for (Rule r : current) {
                if (r.horizontal() == horizontal && places.stream().noneMatch(p -> Math.abs(p - r.pos()) <= TIE)) {
                    places.add(r.pos());
                }
            }
            for (float pos : places) {
                List<Float> crossing = new ArrayList<>();
                for (Rule p : current) {
                    if (p.horizontal() != horizontal && pos >= p.start() - TIE && pos <= p.end() + TIE) {
                        crossing.add(p.pos());
                    }
                }
                crossing.sort(Float::compare);
                for (int i = 0; i + 1 < crossing.size(); i++) {
                    float from = crossing.get(i);
                    float to = crossing.get(i + 1);
                    if (to - from < MIN_EDGE || covered(current, horizontal, pos, from, to)) {
                        continue;
                    }
                    if (ink.share(horizontal, pos, from + 2f, to - 2f) >= COVER) {
                        out.add(new Rule(horizontal, pos, from, to, 0.5f, 0));
                    }
                }
            }
        }
        return out;
    }

    /** A band thicker than a rule whose box holds under this share of dark pixels is a tilted rule, not a fill. */
    static final float BAND_FILL = 0.5f;

    /**
     * Whether a group of horizontal runs too thick for a rule is a rule drawn across a tilted page: its runs step down
     * (or up) along it, so its box is mostly paper, where a filled band's is mostly ink. A scan tilted by half a degree
     * moves a rule across a page by four points or more.
     */
    static boolean tiltedLine(boolean[] dark, int w, int[] g, float sy) {
        int lines = g[3] - g[2] + 1;
        if (lines * sy <= MAX_THICKNESS || lines * sy > BAND) {
            return false;
        }
        long inked = 0;
        for (int y = g[2]; y <= g[3]; y++) {
            for (int x = g[0]; x <= g[1]; x++) {
                inked += dark[y * w + x] ? 1 : 0;
            }
        }
        return inked < BAND_FILL * (long) lines * (g[1] - g[0] + 1);
    }

    /** The tallest row whose cells' sides are looked for. */
    static final float MAX_ROW = 60f;

    /** The widest ink that counts as a cell's side. */
    static final float SIDE_WIDTH = 3f;

    /** How close sides in different rows must lie to be the same column's. */
    static final float SIDE_ALIGN = 2f;

    /** How far short of a row's rules a cell's side may stop (the rules' own thickness and a copier's blur). */
    static final float SIDE_REACH = 0.7f;

    /**
     * Cells' sides too short to be found as rules: the sides of a row in a table that rules each row's cells on their
     * own (a generated statement's and invoice's), each a row tall where a rule needs {@link #MIN_LENGTH}. A side is thin
     * ink that runs unbroken from one row rule to the next, at the same place in at least two rows. A figure's stroke
     * fills most of a tight row's height too, but stops short of both rules. Without the sides such a table was read
     * from its text alone: a header set left over figures set right went to the column on its left, and a row whose label
     * ran to two lines was dropped from the table.
     */
    static List<Rule> cellSides(List<Rule> rules, Ink ink) {
        List<Rule> hs = new ArrayList<>();
        for (Rule r : rules) {
            if (r.horizontal()) {
                hs.add(r);
            }
        }
        hs.sort(Comparator.comparingDouble(Rule::pos));
        List<Rule> sides = new ArrayList<>();
        for (int i = 0; i < hs.size(); i++) {
            Rule a = hs.get(i);
            for (int j = i + 1; j < hs.size(); j++) {
                Rule b = hs.get(j);
                float from = Math.max(a.start(), b.start());
                float to = Math.min(a.end(), b.end());
                if (b.pos() - a.pos() <= TIE || to - from < 0.5f * Math.min(a.length(), b.length())) {
                    continue;
                }
                if (b.pos() - a.pos() <= MAX_ROW) {
                    for (float x : ink.sides(a.pos(), b.pos(), from + TIE, to - TIE)) {
                        sides.add(new Rule(false, x, a.pos(), b.pos(), 0.5f, 0));
                    }
                }
                break;
            }
        }
        List<Rule> out = new ArrayList<>();
        for (Rule s : sides) {
            boolean again = sides.stream().anyMatch(o -> o != s && Math.abs(o.pos() - s.pos()) <= SIDE_ALIGN
                    && Math.abs(o.start() - s.start()) > TIE);
            boolean known = rules.stream().anyMatch(r -> !r.horizontal() && Math.abs(r.pos() - s.pos()) <= TIE
                    && r.start() <= s.start() + TIE && r.end() >= s.end() - TIE);
            if (again && !known) {
                out.add(s);
            }
        }
        return out;
    }

    /** Whether rules along this line already cover most of the stretch. */
    private static boolean covered(List<Rule> rules, boolean horizontal, float pos, float from, float to) {
        float drawn = 0;
        for (Rule r : rules) {
            if (r.horizontal() == horizontal && Math.abs(r.pos() - pos) <= TIE) {
                drawn += Math.max(0, Math.min(r.end(), to) - Math.max(r.start(), from));
            }
        }
        return drawn >= 0.9f * (to - from);
    }

    /** How much darker a rule, or a filled band, must be than the scan just beyond it on both sides. */
    static final int MIN_CONTRAST = 25;

    /**
     * Whether a run of dark pixels is darker than the scan a few pixels beyond it on both sides, as a rule or a filled
     * band is against its paper, judged by its darkest line at each step along it: a slightly tilted rule spans a few
     * points of height, and on average it is nearly as light as its paper. Shading as grey as {@link #DARK} (a table's
     * banded rows) has patches a little darker than its own grey wherever the noise lines up, which made rules through
     * the middle of a row.
     */
    static boolean contrasted(byte[] luma, int w, int h, boolean horizontal, int[] g) {
        int lines = horizontal ? h : w;
        int before = g[2] - 3;
        int after = g[3] + 3;
        if (before < 0 || after >= lines) {
            return true;
        }
        long own = 0;
        long above = 0;
        long below = 0;
        int n = 0;
        for (int k = g[0]; k <= g[1]; k += 2) {
            int darkest = 255;
            for (int line = g[2]; line <= g[3]; line++) {
                darkest = Math.min(darkest, lum(luma, w, horizontal, line, k));
            }
            own += darkest;
            above += lum(luma, w, horizontal, before, k);
            below += lum(luma, w, horizontal, after, k);
            n++;
        }
        return n == 0 || (Math.min(above, below) - own) / (float) n >= MIN_CONTRAST;
    }

    private static int lum(byte[] luma, int w, boolean horizontal, int line, int k) {
        return (horizontal ? luma[line * w + k] : luma[k * w + line]) & 0xFF;
    }

    /** Pieces of one rule at most this far apart across it (a scan's slight tilt moves a long rule by a point or so). */
    static final float JOIN_DRIFT = 1.5f;

    /** Pieces of one rule at most this far apart along it. */
    static final float JOIN_GAP = 10f;

    /**
     * The rules with the pieces of each one joined. A copier's light or halftone rule breaks where its ink fades, so a
     * table's row rule came out in two to four pieces a few points apart: each piece then crossed the table's verticals
     * without ending at one and was dropped as loose (a sideways table's rows and a meeting's action table lost every rule
     * under their first row, and the rows ran together).
     */
    static List<Rule> joined(List<Rule> rules) {
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing(Rule::horizontal).thenComparingDouble(Rule::start));
        List<Rule> out = new ArrayList<>();
        boolean[] used = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) {
                continue;
            }
            Rule a = sorted.get(i);
            float pos = a.pos() * a.length();
            float weight = a.length();
            float start = a.start();
            float end = a.end();
            float thickness = a.thickness();
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = i + 1; j < sorted.size(); j++) {
                    Rule b = sorted.get(j);
                    if (used[j] || b.horizontal() != a.horizontal() || b.start() > end + JOIN_GAP) {
                        continue;
                    }
                    if (Math.abs(b.pos() - pos / weight) <= JOIN_DRIFT && b.end() > start - JOIN_GAP) {
                        used[j] = true;
                        pos += b.pos() * b.length();
                        weight += b.length();
                        start = Math.min(start, b.start());
                        end = Math.max(end, b.end());
                        thickness = Math.max(thickness, b.thickness());
                        grew = true;
                    }
                }
            }
            out.add(weight == a.length() ? a : new Rule(a.horizontal(), pos / weight, start, end, thickness, a.rgb()));
        }
        return out;
    }

    private static boolean anchored(Rule r, List<Rule> all) {
        boolean crossed = false;
        boolean startTied = false;
        boolean endTied = false;
        for (Rule p : all) {
            if (p.horizontal() == r.horizontal() || p.pos() < r.start() - TIE || p.pos() > r.end() + TIE
                    || r.pos() < p.start() - TIE || r.pos() > p.end() + TIE) {
                continue;
            }
            crossed = true;
            startTied |= Math.abs(p.pos() - r.start()) <= TIE;
            endTied |= Math.abs(p.pos() - r.end()) <= TIE;
        }
        return !crossed || startTied && endTied;
    }

    private static final float TIE = 4f;

    private static boolean doubled(Rule r, List<Rule> all) {
        for (Rule p : all) {
            if (p != r && p.horizontal() == r.horizontal() && Math.abs(p.pos() - r.pos()) <= TIE && p.length() > r.length()
                    && p.start() <= r.start() + TIE && p.end() >= r.end() - TIE) {
                return true;
            }
        }
        return false;
    }

    private static void add(List<Rule> out, boolean horizontal, int[] g, float across0, float acrossScale, float along0,
            float alongScale) {
        float thickness = (g[3] - g[2] + 1) * acrossScale;
        float start = along0 + g[0] * alongScale;
        float end = along0 + (g[1] + 1) * alongScale;
        if (thickness <= MAX_THICKNESS) {
            out.add(new Rule(horizontal, across0 + (g[2] + g[3] + 1) / 2f * acrossScale, start, end, thickness, 0));
        } else if (thickness <= BAND) {
            out.add(new Rule(horizontal, across0 + (g[2] + 0.5f) * acrossScale, start, end, acrossScale, 0));
            out.add(new Rule(horizontal, across0 + (g[3] + 0.5f) * acrossScale, start, end, acrossScale, 0));
        }
    }

    private static List<int[]> groups(boolean[] dark, int w, int h, boolean horizontal, int minLength) {
        int lines = horizontal ? h : w;
        int along = horizontal ? w : h;
        List<int[]> open = new ArrayList<>();
        List<int[]> done = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            List<int[]> runs = runs(dark, w, horizontal, i, lines, along, Math.max(8, minLength));
            List<int[]> next = new ArrayList<>();
            for (int[] r : runs) {
                int[] host = null;
                for (int[] g : open) {
                    if (g[3] == i - 1 && Math.min(g[1], r[1]) - Math.max(g[0], r[0]) >= 0.5f * (r[1] - r[0])) {
                        host = g;
                        break;
                    }
                }
                if (host == null) {
                    host = new int[] {r[0], r[1], i, i};
                } else {
                    open.remove(host);
                    host[0] = Math.min(host[0], r[0]);
                    host[1] = Math.max(host[1], r[1]);
                    host[3] = i;
                }
                next.add(host);
            }
            done.addAll(open);
            open = next;
        }
        done.addAll(open);
        return done;
    }

    private static boolean at(boolean[] dark, int w, boolean horizontal, int line, int k) {
        return horizontal ? dark[line * w + k] : dark[k * w + line];
    }

    private static List<int[]> runs(boolean[] dark, int w, boolean horizontal, int line, int lines, int along,
            int minLength) {
        List<int[]> out = new ArrayList<>();
        int start = -1;
        int lastDark = -1;
        int count = 0;
        for (int k = 0; k <= along; k++) {
            boolean d = k < along && (at(dark, w, horizontal, line, k) || line + 1 < lines && at(dark, w, horizontal, line + 1, k));
            if (!d && k < along) {
                continue;
            }
            if (start >= 0 && (k == along || k - lastDark > GAP + 1)) {
                if (lastDark - start + 1 >= minLength && count >= SOLID * (lastDark - start + 1)) {
                    out.add(new int[] {start, lastDark});
                }
                start = -1;
            }
            if (d) {
                if (start < 0) {
                    start = k;
                    count = 0;
                }
                lastDark = k;
                count++;
            }
        }
        return out;
    }

    private static final float SOLID = 0.8f;
}
