package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

/**
 * An OCR layer's text is in logical order, and OCRmyPDF's glyphless font draws a Sinhala or Tamil vowel sign after its
 * letter with no advance and no ink. The sign belongs to the letter: left out as too narrow, every such word lost its
 * vowel signs ("කරුණාකර" came out "කරණකර").
 */
class OcrMarksTest {

    private static final FontInfo FONT = new FontInfo("Arial", false, false, false, false, false, "MPDFAA+Occulta", true);

    private static final float SIZE = 10;

    @Test
    void zeroWidthSignsJoinTheLetterBeforeThem() {
        // ක ර ු ණ ා ක ර: the vowel sign u (U+0DD4) is drawn with no advance after ර; ා (U+0DCF) has one
        List<Glyph> hidden = new ArrayList<>();
        float x = 72;
        int seq = 0;
        for (String c : new String[] {"\u0D9A", "\u0DBB", "\u0DD4", "\u0DAB", "\u0DCF", "\u0D9A", "\u0DBB"}) {
            boolean sign = c.equals("\u0DD4");
            float width = sign ? 0f : 5f;
            hidden.add(glyph(c, x, width, seq++));
            x += width;
        }
        assertEquals("\u0D9A\u0DBB\u0DD4\u0DAB\u0DCF\u0D9A\u0DBB", text(OcrText.glyphs(hidden)));
    }

    @Test
    void aJoinerStaysWithItsLetterAndStraySignsStayOut() {
        List<Glyph> hidden = new ArrayList<>();
        hidden.add(glyph("\u0D85", 72, 5, 0));
        hidden.add(glyph("\u0DCA", 77, 0, 1));
        hidden.add(glyph("\u200D", 77, 0, 2));
        hidden.add(glyph("\u0DBA", 77, 5, 3));
        // A sign far from any letter, as a stray mark on its own, is still left out
        hidden.add(glyph("\u0DD4", 300, 0, 4));
        assertEquals("\u0D85\u0DCA\u200D\u0DBA", text(OcrText.glyphs(hidden)));
    }

    private static Glyph glyph(String text, float x, float width, int seq) {
        Glyph g = new Glyph(text, x, width, 700, SIZE, 0.9f * SIZE, 0.2f * SIZE, FONT, 0, seq, 2.5f, false, false);
        g.invisible = true;
        return g;
    }

    private static String text(List<Glyph> glyphs) {
        StringBuilder sb = new StringBuilder();
        for (Glyph g : glyphs) {
            sb.append(g.text);
        }
        return sb.toString();
    }
}
