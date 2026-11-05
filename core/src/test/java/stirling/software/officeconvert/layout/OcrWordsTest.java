package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.model.ScriptWidths;

/**
 * OCRmyPDF draws each word in its NotoSans with its own stretch and places the next word where the scan has it. Each OCR
 * word is set again in Arial's widths with one scale that takes the word and Word's space after it to where the next
 * word starts; a word before a wider gap keeps its own box, and invisible text in a real face stays as drawn.
 */
class OcrWordsTest {

    private static final FontInfo NOTO = new FontInfo("Arial", false, false, false, false, false, "MPDFAA+NotoSans", true);

    private static final FontInfo HELVETICA = new FontInfo("Arial", false, false, false, false, false, "Helvetica", false);

    private static final FontInfo OCCULTA = new FontInfo("Arial", false, false, false, false, false, "MPDFAA+Occulta", true);

    private static final FontInfo GLYPHLESS = new FontInfo("Arial", false, false, false, false, false, "GlyphLessFont", true);

    private static final float SIZE = 7.2f;

    @Test
    void theWordsOfALineShareOneScaleAndEndWhereItsBoxDoes() {
        List<Glyph> glyphs = new ArrayList<>();
        float end = word(glyphs, "Our", 72, 18, NOTO);
        float second = end + 4.5f;
        float secondEnd = word(glyphs, "safety", second, 30, NOTO);
        // A gap of 3 ems: the next cell of a table
        float third = secondEnd + 3 * SIZE;
        float thirdEnd = word(glyphs, "record", third, 32, NOTO);

        List<Glyph> out = OcrWords.fit(glyphs);

        float our = SubstituteMetrics.width("Our", "Arial", false, false, SIZE);
        float space = SubstituteMetrics.width(" ", "Arial", false, false, SIZE);
        float safety = SubstituteMetrics.width("safety", "Arial", false, false, SIZE);
        float scale = out.get(0).hscale;
        assertEquals(Math.floor((secondEnd - 72) / (our + space + safety) * 100) / 100, scale, 1e-4,
                "Our and safety take the scale that takes them from Our's start to safety's end");
        assertEquals(scale, out.get(4).hscale, 1e-6, "one scale for the line's words");
        assertEquals(72f, out.get(0).x, 1e-3);
        assertEquals(our * scale, out.get(2).right() - 72, 1e-3, "the letters take Arial's widths");
        assertEquals(72 + (our + space) * scale, out.get(4).x, 1e-3, "safety starts after Our and Word's space");
        float safetyEnd = out.get(9).right();
        assertTrue(safetyEnd <= secondEnd + 1e-3 && safetyEnd > secondEnd - 0.01f * (secondEnd - 72),
                "the line ends where its last box does");
        // "record" is past a wide gap: a line of its own, at its own box
        float recordWidth = SubstituteMetrics.width("record", "Arial", false, false, SIZE);
        assertEquals(Math.floor((thirdEnd - third) / recordWidth * 100) / 100, out.get(11).hscale, 1e-4);
        assertEquals(third, out.get(11).x, 1e-3);
    }

    @Test
    void wordsBoxedRoughlyComeOutOneWidth() {
        // RapidOCR's word boxes in one line of a phone photo: stretches of 1.1, 1.5 and 1.2 of Arial's widths
        String[] texts = {"health", "is", "common"};
        float[] stretch = {1.1f, 1.5f, 1.2f};
        float space = SubstituteMetrics.width(" ", "Arial", false, false, SIZE);
        List<Glyph> glyphs = new ArrayList<>();
        float x = 72;
        for (int i = 0; i < texts.length; i++) {
            float w = SubstituteMetrics.width(texts[i], "Arial", false, false, SIZE);
            word(glyphs, texts[i], x, w * stretch[i], NOTO);
            x += (w + (i + 1 < texts.length ? space : 0f)) * stretch[i];
        }

        List<Glyph> out = OcrWords.fit(glyphs);

        float first = out.get(0).hscale;
        for (Glyph g : out) {
            assertEquals(first, g.hscale, 1e-6, "every letter and space of the line at one scale");
        }
        assertTrue(first > 1.1f && first < 1.5f, "between the words' own stretches");
        for (int i = 1; i < out.size(); i++) {
            assertTrue(out.get(i).x >= out.get(i - 1).x, "the words follow one another");
        }
    }

    @Test
    void aLinesLastWordBoxedFarTooWideDoesntStretchTheLine() {
        // The last word of a line has no next word to stop it: boxed four times its width, it would widen every word
        String[] texts = {"health", "is", "common"};
        float[] stretch = {1.2f, 1.2f, 4f};
        float space = SubstituteMetrics.width(" ", "Arial", false, false, SIZE);
        List<Glyph> glyphs = new ArrayList<>();
        float x = 72;
        for (int i = 0; i < texts.length; i++) {
            float w = SubstituteMetrics.width(texts[i], "Arial", false, false, SIZE);
            word(glyphs, texts[i], x, w * stretch[i], NOTO);
            x += (w + (i + 1 < texts.length ? space : 0f)) * stretch[i];
        }

        List<Glyph> out = OcrWords.fit(glyphs);

        assertEquals(1.2f, out.get(0).hscale, 0.011f, "the line at its other words' stretch");
        assertEquals(out.get(0).hscale, out.getLast().hscale, 1e-6, "common too");
    }

    @Test
    void aWordBoxedOverTheNextWordsReachesOnlyToTheNextOne() {
        // Tesseract boxed "Lieferungen" over the words after it: drawn stretched 3.8 times, it stays as drawn no more
        List<Glyph> glyphs = new ArrayList<>();
        float lieferungen = SubstituteMetrics.width("Lieferungen", "Arial", false, false, SIZE);
        float space = SubstituteMetrics.width(" ", "Arial", false, false, SIZE);
        float deutsch = SubstituteMetrics.width("Deutsch:", "Arial", false, false, SIZE);
        float deutschEnd = word(glyphs, "Deutsch:", 72, 1.15f * deutsch, NOTO);
        float start = deutschEnd + 1.15f * space;
        word(glyphs, "Lieferungen", start, 3.8f * lieferungen, NOTO);
        float an = start + 1.15f * (lieferungen + space);
        float anEnd = word(glyphs, "an", an, 1.15f * SubstituteMetrics.width("an", "Arial", false, false, SIZE), NOTO);
        int firstOfLieferungen = "Deutsch:".length() + 1;
        int lastOfLieferungen = firstOfLieferungen + "Lieferungen".length() - 1;

        List<Glyph> out = OcrWords.fit(glyphs);

        float scale = out.get(firstOfLieferungen).hscale;
        assertEquals(out.get(0).hscale, scale, 1e-6, "Lieferungen takes its line's scale");
        assertTrue(scale > 1.1f && scale < 1.2f, "about the scan's 115%, not 380%");
        assertTrue(out.get(lastOfLieferungen).right() <= an + 1e-3, "it ends before the next word starts");
        // "an" and its space follow Lieferungen's: its last letter is the line's
        assertTrue(out.get(lastOfLieferungen + 3).right() <= anEnd + 1e-3, "the line still ends where its last box does");
    }

    @Test
    void otherTextStaysAsDrawn() {
        List<Glyph> glyphs = new ArrayList<>();
        float end = word(glyphs, "Adobe", 72, 25, HELVETICA);
        word(glyphs, "layer", end + 3, 20, HELVETICA);
        Glyph visible = new Glyph("V", 200, 5, 700, SIZE, 0.9f * SIZE, 0.2f * SIZE, NOTO, 0, 99, 2, false, false);
        glyphs.add(visible);
        List<Glyph> out = OcrWords.fit(glyphs);
        for (int i = 0; i < glyphs.size(); i++) {
            assertSame(glyphs.get(i), out.get(i));
        }
    }

    @Test
    void scriptLettersTakeTheirScriptFacesWidths() {
        // A Tamil word, one Occulta glyph a cluster (வா டி க் கை), which OCRmyPDF stretches 2.6 times to its box
        String[] clusters = {"வா", "டி", "க்", "கை"};
        float em = ScriptWidths.width(String.join("", clusters), Character.UnicodeScript.TAMIL, false);
        assertTrue(em > 0, "Tamil has script widths");
        float reach = 1.3f * em * SIZE;
        List<Glyph> glyphs = new ArrayList<>();
        for (String c : clusters) {
            Glyph g = glyph(c, 72 + glyphs.size() * reach / clusters.length, reach / clusters.length, OCCULTA, glyphs.size());
            g.hscale = 2.6f;
            glyphs.add(g);
        }
        List<Glyph> out = OcrWords.fit(glyphs);
        assertEquals(Math.floor(reach / (em * SIZE) * 100) / 100, out.get(0).hscale, 1e-4, "the stretch Tamil's widths need");
        assertEquals(72f + reach, out.getLast().right(), 0.02f * reach, "the word still ends where its box does");
    }

    @Test
    void aConjunctSplitAcrossGlyphsIsMeasuredWhole() {
        // क्ष drawn as क् and ष (a combining mark joins the letter before it, the next consonant stays apart)
        String[] parts = {"क्", "ष"};
        float whole = ScriptWidths.width(String.join("", parts), Character.UnicodeScript.DEVANAGARI, false);
        float apart = ScriptWidths.width(parts[0], Character.UnicodeScript.DEVANAGARI, false)
                + ScriptWidths.width(parts[1], Character.UnicodeScript.DEVANAGARI, false);
        assertTrue(whole > 0 && Math.abs(apart - whole) > 0.1f * whole, "the conjunct is narrower than its parts");
        float reach = 1.3f * whole * SIZE;
        List<Glyph> glyphs = new ArrayList<>();
        for (String c : parts) {
            Glyph g = glyph(c, 72 + glyphs.size() * reach / parts.length, reach / parts.length, OCCULTA, glyphs.size());
            g.hscale = 2.6f;
            glyphs.add(g);
        }
        List<Glyph> out = OcrWords.fit(glyphs);
        assertEquals(Math.floor(reach / (whole * SIZE) * 100) / 100, out.get(0).hscale, 1e-4, "the conjunct's stretch");
    }

    @Test
    void aWordWithNoKnownWidthsLosesTheOcrFontsStretch() {
        // Hangul in Tesseract's GlyphLessFont, stretched twice over: Word would set Malgun Gothic's syllables twice as wide
        List<Glyph> glyphs = new ArrayList<>();
        for (String c : new String[] {"한", "국", "어"}) {
            Glyph g = glyph(c, 72 + glyphs.size() * SIZE, SIZE, GLYPHLESS, glyphs.size());
            g.hscale = 2f;
            glyphs.add(g);
        }
        List<Glyph> out = OcrWords.fit(glyphs);
        for (int i = 0; i < glyphs.size(); i++) {
            assertEquals(1f, out.get(i).hscale, 1e-6);
            assertEquals(glyphs.get(i).x, out.get(i).x, 1e-6, "each syllable stays where it's drawn");
        }
    }

    @Test
    void ocrFontsAreKnownByTheirNamesStart() {
        assertTrue(OcrWords.ocrText(glyph("a", 72, 5, NOTO, 0)));
        assertTrue(OcrWords.ocrText(glyph("a", 72, 5, font("GlyphLessFont"), 0)));
        assertTrue(OcrWords.ocrText(glyph("a", 72, 5, font("ABCDEF+Occulta"), 0)));
        assertFalse(OcrWords.ocrText(glyph("a", 72, 5, font("ABCDEF+Helvetica"), 0)));
        assertFalse(OcrWords.ocrText(glyph("a", 72, 5, font("ABCDEF+MyNotoSans"), 0)));
    }

    /** Hangul syllables Tesseract boxed apart, each with the space its PDF writes after a box (the sandwich renderer). */
    private static List<Glyph> syllables(String text, float gap, FontInfo font) {
        List<Glyph> glyphs = new ArrayList<>();
        float x = 72;
        for (int i = 0; i < text.length(); i++) {
            glyphs.add(glyph(String.valueOf(text.charAt(i)), x, SIZE, font, glyphs.size()));
            glyphs.add(glyph(" ", x + SIZE, 0.26f * SIZE, font, glyphs.size()));
            x += SIZE + 0.26f * SIZE + gap;
        }
        return glyphs;
    }

    @Test
    void hangulSyllablesDrawnCloseMakeOneWord() {
        List<Glyph> glyphs = syllables("지원", 0.1f * SIZE, GLYPHLESS);

        List<Glyph> joined = OcrWords.joinSyllables(glyphs);

        assertEquals(3, joined.size(), "the space between the syllables goes, the one after the word stays");
        assertEquals("지", joined.get(0).text);
        assertEquals(glyphs.get(2).x, joined.get(0).right(), 1e-3, "the first syllable reaches the second");
        assertEquals("원", joined.get(1).text);
        assertTrue(joined.get(2).isSpace());
    }

    @Test
    void fitSetsCloseSyllablesAsOneWord() {
        List<Glyph> out = OcrWords.fit(syllables("지원", 0.1f * SIZE, GLYPHLESS));

        List<String> text = out.stream().map(g -> g.text).toList();
        assertEquals(List.of("지", "원", " "), text, "no space inside the word");
    }

    @Test
    void hangulSyllablesFurtherApartStayTwoWords() {
        List<Glyph> glyphs = syllables("지원", 0.5f * SIZE, GLYPHLESS);
        assertSame(glyphs, OcrWords.joinSyllables(glyphs));
    }

    @Test
    void onlyHangulOcrTextIsJoined() {
        List<Glyph> latin = syllables("ab", 0.1f * SIZE, GLYPHLESS);
        assertSame(latin, OcrWords.joinSyllables(latin), "Latin letters keep their spaces");
        List<Glyph> drawn = syllables("지원", 0.1f * SIZE, HELVETICA);
        assertSame(drawn, OcrWords.joinSyllables(drawn), "text in a real face isn't an OCR layer's");
    }

    @Test
    void onlyTesseractsOwnTextIsJoined() {
        // OCRmyPDF's own layers can give a space the whole gap to the next word: two words, however close the next
        List<Glyph> occulta = syllables("지원", 0.1f * SIZE, OCCULTA);
        assertSame(occulta, OcrWords.joinSyllables(occulta));
        List<Glyph> noto = syllables("지원", 0.1f * SIZE, NOTO);
        assertSame(noto, OcrWords.joinSyllables(noto));
        assertTrue(OcrWords.tesseractText(glyph("지", 72, SIZE, font("GlyphLessFont"), 0)));
        assertTrue(OcrWords.tesseractText(glyph("지", 72, SIZE, font("ABCDEF+GlyphLessFont"), 0)));
        assertFalse(OcrWords.tesseractText(glyph("지", 72, SIZE, font("ABCDEF+Occulta"), 0)));
    }

    private static FontInfo font(String ps) {
        return new FontInfo("Arial", false, false, false, false, false, ps, true);
    }

    /** A word drawn as OCRmyPDF draws it: equal letters across {@code width}, then a space; returns where it ends. */
    private static float word(List<Glyph> glyphs, String text, float x, float width, FontInfo font) {
        float each = width / text.length();
        for (int i = 0; i < text.length(); i++) {
            glyphs.add(glyph(String.valueOf(text.charAt(i)), x + i * each, each, font, glyphs.size()));
        }
        glyphs.add(glyph(" ", x + width, 0.26f * SIZE, font, glyphs.size()));
        return x + width;
    }

    private static Glyph glyph(String text, float x, float width, FontInfo font, int seq) {
        Glyph g = new Glyph(text, x, width, 700, SIZE, 0.9f * SIZE, 0.2f * SIZE, font, 0, seq, 0.26f * SIZE, false, false);
        g.invisible = true;
        return g;
    }
}
