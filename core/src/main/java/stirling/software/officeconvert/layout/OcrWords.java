package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.List;

import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.model.ScriptWidths;

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
 * Letters the stand-in face lacks (Sinhala, Tamil, Devanagari) take their script face's widths (ScriptWidths). OCRmyPDF
 * draws those scripts in its glyphless Occulta, stretched to their box: kept, that stretch (two to three and a half
 * times) widened Word's Tamil letters as much, and a one-page notice ran to two. A word whose widths neither has
 * (Hangul, Han) keeps its place at its face's own widths, without the OCR font's stretch. Invisible text in a real face
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

    /** The fonts OCR tools draw their text in: OCRmyPDF's NotoSans and Occulta, Tesseract's GlyphLessFont. */
    private static final String[] OCR_FONTS = {"notosans", "occulta", "glyphless"};

    /**
     * Whether a glyph is an OCR tool's invisible text, by its font's name after a subset tag (six letters and a plus).
     * Only the name's start is compared, in place: the name comes from the PDF, and this runs for every glyph.
     */
    static boolean ocrText(Glyph g) {
        if (!g.invisible || g.font.postScriptName() == null) {
            return false;
        }
        for (String name : OCR_FONTS) {
            if (named(g, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * OCR text in Tesseract's own GlyphLessFont, as its PDF renderer draws it (OCRmyPDF's sandwich renderer passes it
     * on): each word box with a space after it, the two stretched over the box.
     */
    static boolean tesseractText(Glyph g) {
        return ocrText(g) && named(g, "glyphless");
    }

    /** Whether the glyph's font is named {@code name}, after any subset prefix, ignoring case. */
    private static boolean named(Glyph g, String name) {
        String ps = g.font.postScriptName();
        int start = ps.length() > 7 && ps.charAt(6) == '+' ? 7 : 0;
        return ps.regionMatches(true, start, name, 0, name.length());
    }

    /** A word's letters and the space drawn right after it (-1 when none), as indexes into the glyphs. */
    private record Word(List<Integer> letters, int space) {}

    /** {@code glyphs} (an OCR layer's, in drawing order) with each OCR word set out again. */
    static List<Glyph> fit(List<Glyph> drawn) {
        List<Glyph> glyphs = joinSyllables(drawn);
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

    /**
     * How close, in their size, two Hangul syllables Tesseract boxed apart are drawn when they belong to one word.
     * Tesseract boxes Korean text syllable by syllable, and its PDF (OCRmyPDF's sandwich renderer, which the engine uses
     * for Korean) writes every box as a word with a space after it: "지원합니다." came out "지 원 합 니다.", and a Korean
     * scan kept 38% of its words in Word where Tesseract's own text holds 78%. The gaps inside words and between them
     * overlap; joining syllables drawn closer than this took the copier's Korean scan to 53% and the phone's from 53% to
     * 56%.
     */
    static final float JOIN_HANGUL = 0.3f;

    /**
     * {@code glyphs} with the space between two Hangul OCR syllables dropped when they're drawn closer than
     * JOIN_HANGUL of their size on one line, the first widened to reach the second, so words() makes them one word.
     * Only in Tesseract's own text: in OCRmyPDF's NotoSans and Occulta layers a space's advance can reach the next
     * word, so the gap after it says nothing about the gap between the words.
     */
    static List<Glyph> joinSyllables(List<Glyph> glyphs) {
        List<Glyph> out = null;
        for (int i = 0; i + 2 < glyphs.size(); i++) {
            Glyph g = glyphs.get(i);
            Glyph space = glyphs.get(i + 1);
            Glyph next = glyphs.get(i + 2);
            if (!tesseractText(g) || !hangul(g.text) || !tesseractText(space) || !space.isSpace()
                    || !tesseractText(next) || !hangul(next.text)) {
                continue;
            }
            float near = TOUCHING * Math.max(g.size, next.size) + TOUCHING_PT;
            // Tesseract stretches a word and the space after it over the word's box: the box ends where the space does
            float gap = next.x - space.right();
            if (Math.abs(next.baseline - g.baseline) > near || gap < -near || gap >= JOIN_HANGUL * g.size) {
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(glyphs);
            }
            out.set(i, g.withWidth(next.x - g.x));
            out.set(i + 1, null);
        }
        if (out == null) {
            return glyphs;
        }
        out.removeIf(java.util.Objects::isNull);
        return out;
    }

    private static boolean hangul(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '가' && c <= '힣') {
                return true;
            }
        }
        return false;
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
            ems[k] = em(g, family);
            if (!(ems[k] > 0)) {
                unstretched(glyphs, out, w);
                return;
            }
        }
        inContext(glyphs, w, ems, family);
        for (float em : ems) {
            total += em;
        }
        Character.UnicodeScript script = ScriptWidths.script(first.text);
        float spaceEm = script != null ? ScriptWidths.space(script, first.bold)
                : SubstituteMetrics.width(" ", family, first.bold, first.italic, 1f);
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

    /** A letter's width in 1/1000 em: the stand-in face's, else its script face's, else NaN. */
    private static float em(Glyph g, String family) {
        float em = SubstituteMetrics.width(g.text, family, g.bold, g.italic, 1f);
        if (em > 0) {
            return em;
        }
        Character.UnicodeScript script = ScriptWidths.script(g.text);
        return script == null ? Float.NaN : ScriptWidths.width(g.text, script, g.bold);
    }

    /**
     * Shapes a run of letters of one script as a whole: a conjunct can be split across glyphs (क् and ष), and its parts
     * measured apart came to 1.33 em where Word sets the conjunct in 0.74. Each run's width is shared out over its
     * glyphs by their own widths.
     */
    private static void inContext(List<Glyph> glyphs, Word w, float[] ems, String family) {
        Character.UnicodeScript[] scripts = new Character.UnicodeScript[ems.length];
        for (int k = 0; k < ems.length; k++) {
            Glyph g = glyphs.get(w.letters().get(k));
            scripts[k] = SubstituteMetrics.width(g.text, family, g.bold, g.italic, 1f) > 0 ? null
                    : ScriptWidths.script(g.text);
        }
        for (int k = 0; k < ems.length; ) {
            int end = k + 1;
            while (scripts[k] != null && end < ems.length && scripts[end] == scripts[k]) {
                end++;
            }
            if (scripts[k] != null && end - k > 1) {
                StringBuilder run = new StringBuilder();
                float parts = 0;
                for (int j = k; j < end; j++) {
                    run.append(glyphs.get(w.letters().get(j)).text);
                    parts += ems[j];
                }
                float whole = ScriptWidths.width(run.toString(), scripts[k], glyphs.get(w.letters().get(k)).bold);
                if (whole > 0 && parts > 0) {
                    for (int j = k; j < end; j++) {
                        ems[j] *= whole / parts;
                    }
                }
            }
            k = end;
        }
    }

    /** A word with no known widths, where it's drawn but without the OCR font's stretch. */
    private static void unstretched(List<Glyph> glyphs, List<Glyph> out, Word w) {
        List<Integer> all = new ArrayList<>(w.letters());
        if (w.space() >= 0) {
            all.add(w.space());
        }
        for (int i : all) {
            Glyph g = glyphs.get(i);
            out.set(i, reset(g, g.x, g.width, 1f, g.spaceWidth));
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
