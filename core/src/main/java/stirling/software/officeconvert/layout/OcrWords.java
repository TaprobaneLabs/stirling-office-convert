package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.extract.Glyph;

/**
 * An OCR tool's words set out as Word will set them. OCRmyPDF (17) and Tesseract draw each word in their own invisible
 * font, stretched to the word's box, and place the next word where the scan has it. Set in Word's stand-in face, a line
 * came out 4 to 8% shorter than in the scan: the face's letters were up to 4% narrower than the OCR font's, and the
 * space Word sets between words a third narrower than the scan's gaps. Word then took the next line's first word onto
 * each line, and every paragraph reflowed (an 11-point letter on a copier scan kept 55% of its words in place).
 *
 * Here each OCR word is set again in its stand-in face's own widths with one horizontal scale, the one that takes the word
 * and the space Word sets after it to where the next word on its line starts, when that word is a word space away. A
 * line's last word, and a word before a wider gap (a table's next cell), keep their own box. Word's lines are then as
 * long as the scan's, so a fitted paragraph keeps its line breaks. The OCR layer's size stays: an OCR layer's size is a
 * line box's (about 0.73 of the type's), but set larger, a table's rows grew in Word and pushed what follows down.
 * A word whose widths the stand-in metrics lack (other scripts) stays as drawn, and so does invisible text in a real face
 * (Adobe's OCR), which has its real widths.
 */
final class OcrWords {

    /** The widest gap to the next word, in the OCR layer's size, that is a word space and not a column's or cell's. */
    static final float WORD_SPACE = 0.8f;

    /** Scales outside these are no word's: such a word stays as drawn. */
    static final float MIN_SCALE = 0.5f;

    static final float MAX_SCALE = 2f;

    /** How far apart, in a glyph's size, two glyphs of a word or a word and its space may be drawn: an OCR layer draws
     *  them edge to edge. */
    private static final float TOUCHING = 0.02f;

    /** And in points, for rounding in the layer's numbers. */
    private static final float TOUCHING_PT = 0.05f;

    private OcrWords() {}

    /** Whether a glyph is an OCR tool's invisible text: OCRmyPDF's NotoSans and Occulta, Tesseract's GlyphLessFont. */
    static boolean ocrText(Glyph g) {
        if (!g.invisible || g.font.postScriptName() == null) {
            return false;
        }
        String ps = g.font.postScriptName();
        String base = ps.substring(ps.indexOf('+') + 1).toLowerCase(Locale.ROOT);
        return base.startsWith("glyphless") || base.startsWith("occulta") || base.startsWith("notosans");
    }

    /** A word's letters and the space drawn right after it (-1 when none), as indexes into the glyphs. */
    private record Word(List<Integer> letters, int space) {}

    /** {@code glyphs} (an OCR layer's, in drawing order) with each OCR word set out again. */
    static List<Glyph> fit(List<Glyph> glyphs) {
        List<Word> words = words(glyphs);
        if (words.isEmpty()) {
            return glyphs;
        }
        List<Glyph> out = new ArrayList<>(glyphs);
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            Glyph last = glyphs.get(w.letters().getLast());
            float next = Float.NaN;
            if (i + 1 < words.size()) {
                Glyph after = glyphs.get(words.get(i + 1).letters().getFirst());
                float gap = after.x - last.right();
                if (Math.abs(after.baseline - last.baseline) <= TOUCHING * last.size + TOUCHING_PT && gap > 0
                        && gap <= WORD_SPACE * last.size) {
                    next = after.x;
                }
            }
            set(glyphs, out, w, next);
        }
        return out;
    }

    /** OCR words: runs of letters drawn edge to edge on a line, each with the space drawn right after it, if any. */
    private static List<Word> words(List<Glyph> glyphs) {
        List<Word> words = new ArrayList<>();
        List<Integer> letters = new ArrayList<>();
        for (int i = 0; i <= glyphs.size(); i++) {
            Glyph g = i < glyphs.size() ? glyphs.get(i) : null;
            boolean letter = g != null && ocrText(g) && !g.isSpace();
            if (letter && !letters.isEmpty() && touches(glyphs.get(letters.getLast()), g)) {
                letters.add(i);
                continue;
            }
            if (!letters.isEmpty()) {
                Glyph last = glyphs.get(letters.getLast());
                int space = g != null && ocrText(g) && g.isSpace() && touches(last, g) ? i : -1;
                words.add(new Word(letters, space));
                letters = new ArrayList<>();
            }
            if (letter) {
                letters.add(i);
            }
        }
        return words;
    }

    private static boolean touches(Glyph before, Glyph after) {
        float near = TOUCHING * Math.max(before.size, after.size) + TOUCHING_PT;
        return Math.abs(after.baseline - before.baseline) <= near && Math.abs(after.x - before.right()) <= near
                && Math.abs(after.size - before.size) <= 0.01f * before.size;
    }

    /** Sets out one word (and its space) in the stand-in face's widths, reaching to {@code next} when it's a number. */
    private static void set(List<Glyph> glyphs, List<Glyph> out, Word w, float next) {
        Glyph first = glyphs.get(w.letters().getFirst());
        Glyph last = glyphs.get(w.letters().getLast());
        String family = first.font.family();
        float size = first.size;
        float[] ems = new float[w.letters().size()];
        float total = 0;
        for (int k = 0; k < ems.length; k++) {
            Glyph g = glyphs.get(w.letters().get(k));
            ems[k] = SubstituteMetrics.width(g.text, family, g.bold, g.italic, 1f);
            if (!(ems[k] > 0)) {
                return;
            }
            total += ems[k];
        }
        float spaceEm = SubstituteMetrics.width(" ", family, first.bold, first.italic, 1f);
        if (!(spaceEm > 0)) {
            return;
        }
        boolean spaced = !Float.isNaN(next);
        float reach = (spaced ? next : last.right()) - first.x;
        // A whole percent, as Word writes it: the word comes out no wider than the scan's, and the gap after it takes the
        // difference
        float scale = (float) Math.floor(reach / ((total + (spaced ? spaceEm : 0f)) * size) * 100f) / 100f;
        if (!(scale >= MIN_SCALE && scale <= MAX_SCALE)) {
            return;
        }
        float spaceWidth = spaceEm * size * scale;
        float x = first.x;
        for (int k = 0; k < ems.length; k++) {
            int i = w.letters().get(k);
            float width = ems[k] * size * scale;
            out.set(i, reset(glyphs.get(i), x, width, scale, spaceWidth));
            x += width;
        }
        if (w.space() >= 0) {
            Glyph space = glyphs.get(w.space());
            float end = spaced ? next : Math.max(space.right(), x + spaceWidth);
            out.set(w.space(), reset(space, x, Math.max(0f, end - x), scale, spaceWidth));
        }
    }

    private static Glyph reset(Glyph g, float x, float width, float scale, float spaceWidth) {
        Glyph r = new Glyph(g.text, x, width, g.baseline, g.size, g.ascent, g.descent, g.font, g.rgb, g.seq, spaceWidth,
                g.bold, g.italic);
        r.underline = g.underline;
        r.strike = g.strike;
        r.highlightRgb = g.highlightRgb;
        r.boxRgb = g.boxRgb;
        r.link = g.link;
        r.footnote = g.footnote;
        r.vertAlign = g.vertAlign;
        r.hscale = scale;
        r.upright = g.upright;
        r.runsOffPage = g.runsOffPage;
        r.invisible = g.invisible;
        r.icon = g.icon;
        return r;
    }
}
