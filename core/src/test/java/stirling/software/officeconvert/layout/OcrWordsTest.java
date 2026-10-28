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
    void aWordAndItsSpaceReachTheNextWord() {
        List<Glyph> glyphs = new ArrayList<>();
        float end = word(glyphs, "Our", 72, 18, NOTO);
        float second = end + 4.5f;
        float secondEnd = word(glyphs, "safety", second, 30, NOTO);
        // A gap of 3 ems: the next cell of a table
        float third = secondEnd + 3 * SIZE;
        word(glyphs, "record", third, 32, NOTO);

        List<Glyph> out = OcrWords.fit(glyphs);

        float arial = SubstituteMetrics.width("Our ", "Arial", false, false, SIZE);
        Glyph o = out.get(0);
        float scale = o.hscale;
        assertEquals(Math.floor((second - 72) / arial * 100) / 100, scale, 1e-4, "Our and a space reach safety");
        assertEquals(72f, o.x, 1e-3);
        float ourEnd = out.get(2).right();
        assertEquals(SubstituteMetrics.width("Our", "Arial", false, false, SIZE) * scale, ourEnd - 72, 1e-3,
                "the letters take Arial's widths");
        assertEquals(second, out.get(3).right(), 1e-3, "the space ends where the next word starts");
        // "safety" is followed by a wide gap: it keeps its box
        float safety = out.get(4).x;
        float safetyEnd = out.get(9).right();
        assertEquals(second, safety, 1e-3);
        assertTrue(safetyEnd <= secondEnd + 1e-3 && safetyEnd > secondEnd - 0.01f * 30, "safety ends where its box does");
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
