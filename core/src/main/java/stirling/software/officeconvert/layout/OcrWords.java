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
 * Here each OCR word is set again in its stand-in face's own widths. The words of a line, each a word space from the next,
 * take one horizontal scale, the one that takes them and the spaces Word sets between them from the first word's start
 * to where the last one's box ends (fit()); a lone word, and a word before a wider gap (a table's next cell), keep their
 * own box. Word's lines are then as long as the scan's, so a fitted paragraph keeps its line breaks, and a line's words
 * share one width however roughly the OCR tool boxed them. The OCR layer's size stays: an OCR layer's size is a
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

    /**
     * A word stretched more than this many times its line's median is boxed wrong, and counts at the median toward its
     * line's scale (setLine()). Within a phone photo's lines, the widest word was stretched 1.2 times the narrowest at the
     * median and 1.45 at p90 (RapidOCR); Tesseract boxed some words 2 to 4 times over.
     */
    static final float OUTLIER = 1.5f;

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

    /**
     * A word measured in its stand-in face: its letters' widths (em()), its space's, its size, where it starts, how far it
     * reaches (to the next word's start when that's a word space away, else to its box's end), and whether a space
     * follows it; natural() is its width at 100%, with that space.
     */
    private record Measure(float[] ems, float spaceEm, float size, float start, float reach, boolean spaced) {
        float natural() {
            float total = 0;
            for (float em : ems) {
                total += em;
            }
            return (total + (spaced ? spaceEm : 0f)) * size;
        }
    }

    /**
     * {@code glyphs} (an OCR layer's, in drawing order) with each OCR word set out again.
     *
     * <p>The words of a line, each a word space from the next on one baseline, take one scale: the one that takes them all,
     * set out one after another, from the first's start to where the last one's box ends. Each word took its own before,
     * the one that took it to the next word's start, and an OCR tool's word boxes are rough: RapidOCR's words in one line
     * of a phone photo were stretched from 0.8 to 2.3 times (their widest over their narrowest 1.2 times at the median,
     * 1.45 at p90), and Word set neighbouring words in visibly different widths. The line still ends where the scan's
     * does, so Word keeps its breaks. A lone word, and a word before a wider gap (a table's next cell), keeps its own.
     */
    static List<Glyph> fit(List<Glyph> drawn) {
        List<Glyph> glyphs = joinSyllables(drawn);
        List<Word> words = words(glyphs);
        if (words.isEmpty()) {
            return glyphs;
        }
        List<Glyph> out = new ArrayList<>(glyphs);
        Measure[] measures = new Measure[words.size()];
        for (int i = 0; i < words.size(); i++) {
            measures[i] = measure(glyphs, words.get(i), next(glyphs, words, i));
        }
        int start = 0;
        for (int i = 0; i < words.size(); i++) {
            if (measures[i] == null) {
                setLine(glyphs, out, words, measures, start, i);
                // No widths to set it in: where it's drawn, without the OCR font's stretch (or, with no space width, as is)
                if (lacksWidths(glyphs, words.get(i))) {
                    unstretched(glyphs, out, words.get(i));
                }
                start = i + 1;
            } else if (!measures[i].spaced() || i + 1 == words.size() || measures[i + 1] == null) {
                setLine(glyphs, out, words, measures, start, i + 1);
                start = i + 1;
            }
        }
        return out;
    }

    /**
     * Where word {@code i}'s next word starts when it's on the same line a word space away, else NaN. A next word that
     * starts inside this one's box counts too: Tesseract boxed a phone photo's "Lieferungen" over the next three words,
     * stretched it 3.8 times, and Word's PDF read the two lines of letters interleaved ("Liefe and r as u Binn").
     */
    private static float next(List<Glyph> glyphs, List<Word> words, int i) {
        if (i + 1 >= words.size()) {
            return Float.NaN;
        }
        Word w = words.get(i);
        Glyph first = glyphs.get(w.letters().getFirst());
        Glyph last = glyphs.get(w.letters().getLast());
        Glyph after = glyphs.get(words.get(i + 1).letters().getFirst());
        if (Math.abs(after.baseline - last.baseline) > TOUCHING * last.size + TOUCHING_PT) {
            return Float.NaN;
        }
        float gap = after.x - last.right();
        boolean spaced = gap > 0 && gap <= WORD_SPACE * last.size;
        boolean inside = gap <= 0 && after.x > first.x;
        return spaced || inside ? after.x : Float.NaN;
    }

    /** A word's measure, or null when its stand-in face (or script face) has no widths for it or for a space. */
    private static Measure measure(List<Glyph> glyphs, Word w, float next) {
        Glyph first = glyphs.get(w.letters().getFirst());
        Glyph last = glyphs.get(w.letters().getLast());
        String family = first.font.family();
        float[] ems = new float[w.letters().size()];
        for (int k = 0; k < ems.length; k++) {
            ems[k] = em(glyphs.get(w.letters().get(k)), family);
            if (!(ems[k] > 0)) {
                return null;
            }
        }
        inContext(glyphs, w, ems, family);
        float spaceEm = spaceEm(first);
        if (!(spaceEm > 0)) {
            return null;
        }
        boolean spaced = !Float.isNaN(next);
        float reach = (spaced ? next : last.right()) - first.x;
        return new Measure(ems, spaceEm, first.size, first.x, reach, spaced);
    }

    private static float spaceEm(Glyph first) {
        Character.UnicodeScript script = ScriptWidths.script(first.text);
        return script != null ? ScriptWidths.space(script, first.bold)
                : SubstituteMetrics.width(" ", first.font.family(), first.bold, first.italic, 1f);
    }

    /**
     * Whether a word with no measure has a letter its faces have no widths for, so it's set unstretched; a word that
     * lacks only a space width stays as drawn.
     */
    private static boolean lacksWidths(List<Glyph> glyphs, Word w) {
        for (int k : w.letters()) {
            if (!(em(glyphs.get(k), glyphs.get(k).font.family()) > 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Words {@code from} (inclusive) to {@code to} (exclusive), one line, set out one after another from the first's start
     * at the scale that takes them to the last one's reach, in a whole percent as Word writes it: the line comes out no
     * longer than the scan's. When that scale is no word's (outside MIN_SCALE to MAX_SCALE), each word takes its own, or
     * stays as drawn when that is no word's either.
     */
    private static void setLine(List<Glyph> glyphs, List<Glyph> out, List<Word> words, Measure[] measures, int from,
            int to) {
        if (from >= to) {
            return;
        }
        float[] own = new float[to - from];
        for (int i = from; i < to; i++) {
            own[i - from] = measures[i].reach() / measures[i].natural();
        }
        float typical = median(own);
        float reach = 0;
        float natural = 0;
        for (int i = from; i < to; i++) {
            // A word boxed far wider than its line's others (a line's last word has no next word to stop it) counts at
            // their stretch, so it can't stretch the whole line
            boolean wrong = own[i - from] > typical * OUTLIER;
            reach += wrong ? measures[i].natural() * typical : measures[i].reach();
            natural += measures[i].natural();
        }
        float scale = whole(reach / natural);
        if (!(scale >= MIN_SCALE && scale <= MAX_SCALE)) {
            for (int i = from; i < to; i++) {
                float alone = whole(own[i - from]);
                if (alone >= MIN_SCALE && alone <= MAX_SCALE) {
                    // As each word was set before lines took one scale: its space reaching the next word
                    set(glyphs, out, words.get(i), measures[i], measures[i].start(), alone, true);
                }
            }
            return;
        }
        float x = measures[from].start();
        for (int i = from; i < to; i++) {
            x = set(glyphs, out, words.get(i), measures[i], x, scale, i + 1 == to);
        }
    }

    private static float whole(float scale) {
        return (float) Math.floor(scale * 100f) / 100f;
    }

    private static float median(float[] values) {
        float[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2f;
    }

    /**
     * Sets out one word (and its space) at {@code x} in the stand-in face's widths at {@code scale}, and returns where the
     * next word of its line starts. The last word of a line keeps its space as drawn, and its space reaches the next word
     * when there is one (a word with no widths, which keeps its place).
     */
    private static float set(List<Glyph> glyphs, List<Glyph> out, Word w, Measure m, float x, float scale,
            boolean lastOfLine) {
        float size = m.size();
        float spaceWidth = m.spaceEm() * size * scale;
        for (int k = 0; k < m.ems().length; k++) {
            int i = w.letters().get(k);
            float width = m.ems()[k] * size * scale;
            out.set(i, reset(glyphs.get(i), x, width, scale, spaceWidth));
            x += width;
        }
        if (w.space() >= 0) {
            Glyph space = glyphs.get(w.space());
            float end = !lastOfLine ? x + spaceWidth
                    : m.spaced() ? Math.max(m.start() + m.reach(), x) : Math.max(space.right(), x + spaceWidth);
            out.set(w.space(), reset(space, x, Math.max(0f, end - x), scale, spaceWidth));
        }
        return x + (m.spaced() ? spaceWidth : 0f);
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
