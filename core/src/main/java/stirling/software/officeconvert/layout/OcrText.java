package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageData;

public final class OcrText {

    private OcrText() {}

    public static boolean dominates(PageData page) {
        if (page.glyphs().isEmpty()) {
            return !page.hidden().isEmpty();
        }
        int hidden = printing(page.hidden());
        return hidden > 0 && hidden > 4 * printing(page.glyphs());
    }

    public static List<Glyph> pageGlyphs(PageData page) {
        if (!dominates(page)) {
            return page.glyphs();
        }
        List<Glyph> out = OcrWords.fit(glyphs(page.hidden()));
        Grid hidden = new Grid(page.hidden());
        for (Glyph g : page.glyphs()) {
            if (!hidden.covers(g.centreX(), g.baseline - g.size * 0.35f)) {
                out.add(g);
            }
        }
        return out;
    }

    private static final class Grid {

        private static final float CELL = 24f;
        private static final int MAX_CELLS = 64;

        private final Map<Long, List<Glyph>> cells = new HashMap<>();
        private final List<Glyph> large = new ArrayList<>();

        Grid(List<Glyph> glyphs) {
            for (Glyph h : glyphs) {
                if (h.isSpace()) {
                    continue;
                }
                long x0 = cell(h.x);
                long x1 = cell(h.right());
                long y0 = cell(h.top());
                long y1 = cell(h.bottom());
                long w = x1 - x0 + 1;
                long ht = y1 - y0 + 1;
                if (w <= 0 || ht <= 0 || w > MAX_CELLS || ht > MAX_CELLS || w * ht > MAX_CELLS) {
                    large.add(h);
                    continue;
                }
                for (long cx = x0; cx <= x1; cx++) {
                    for (long cy = y0; cy <= y1; cy++) {
                        cells.computeIfAbsent(cx << 32 ^ (cy & 0xFFFFFFFFL), k -> new ArrayList<>()).add(h);
                    }
                }
            }
        }

        private static long cell(float v) {
            return (long) Math.floor(v / CELL);
        }

        boolean covers(float x, float y) {
            List<Glyph> near = cells.get(cell(x) << 32 ^ (cell(y) & 0xFFFFFFFFL));
            return near != null && contains(near, x, y) || contains(large, x, y);
        }

        private static boolean contains(List<Glyph> glyphs, float x, float y) {
            for (Glyph h : glyphs) {
                if (x >= h.x && x <= h.right() && y >= h.top() && y <= h.bottom()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static int printing(List<Glyph> glyphs) {
        int n = 0;
        for (Glyph g : glyphs) {
            n += g.isSpace() ? 0 : 1;
        }
        return n;
    }

    /**
     * The hidden glyphs worth reading: spaces, glyphs at least a little wide, and marks joined to the glyph drawn before
     * them. An OCR layer's text is in logical order and draws a vowel sign or a joiner after its letter with no advance
     * (OCRmyPDF's glyphless font): left out as too narrow, Sinhala and Tamil words lost their vowel signs.
     */
    public static List<Glyph> glyphs(List<Glyph> hidden) {
        List<Glyph> out = new ArrayList<>(hidden.size());
        for (Glyph g : hidden) {
            if (g.isSpace() || g.width >= 0.12f * g.size) {
                out.add(g);
            } else if (joining(g.text) && !out.isEmpty() && follows(out.getLast(), g)) {
                out.set(out.size() - 1, out.getLast().withText(out.getLast().text + g.text));
            }
        }
        return out;
    }

    /** Combining marks and joiners: characters that belong to the letter before them. */
    private static boolean joining(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int type = Character.getType(cp);
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK && cp != 0x200C && cp != 0x200D) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return !text.isEmpty();
    }

    /** Whether {@code mark} is drawn on {@code before}'s line, where it ends. */
    private static boolean follows(Glyph before, Glyph mark) {
        float near = 0.5f * Math.max(before.size, mark.size);
        return !before.isSpace() && Math.abs(before.baseline - mark.baseline) <= near
                && mark.x >= before.x - near && mark.x <= before.right() + near;
    }
}
