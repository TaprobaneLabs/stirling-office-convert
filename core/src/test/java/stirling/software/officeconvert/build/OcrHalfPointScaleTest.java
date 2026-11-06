package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

/**
 * An OCR layer's text, sized line by line from its boxes, set at Word's half-point sizes: its character scale gives back
 * what the rounding takes or adds, so Word's lines keep the scan's widths against each other (IRS Form 8822's scan, whose
 * lines at 5.04 points came out 1.2% short of their neighbours rounded up from 4.8).
 */
class OcrHalfPointScaleTest {

    /** OCRmyPDF's NotoSans, which Word doesn't have: set in Arial. */
    private static final FontInfo OCR = new FontInfo("Arial", false, false, false, false, false, "MPDFAA+NotoSans", true);

    private static final FontInfo EXACT = new FontInfo("Arial", false, false, false, false, false, "ArialMT", false);

    private static Glyph glyph(FontInfo font, float size, float hscale, boolean invisible) {
        Glyph g = new Glyph("e", 0, 3, 100, size, 0.8f * size, 0.2f * size, font, 0, 0, 1.4f, false, false);
        g.hscale = hscale;
        g.invisible = invisible;
        return g;
    }

    private static float halfPoint(float size) {
        return Math.max(1f, Math.round(size * 2f) / 2f);
    }

    @Test
    void ocrTextGivesBackWhatWordsHalfPointSizeTakesOrAdds() {
        // At 5.04 points Word's 5 makes the line 0.8% narrower; at 4.8, 4.2% wider
        assertEquals(146, RunBuilder.scaleOf(glyph(OCR, 5.04f, 1.45f, true)));
        assertEquals(139, RunBuilder.scaleOf(glyph(OCR, 4.80f, 1.45f, true)));
        // A size Word sets as it is keeps its scale, rounded down
        assertEquals(145, RunBuilder.scaleOf(glyph(OCR, 5.0f, 1.45f, true)));
        assertEquals(145, RunBuilder.scaleOf(glyph(OCR, 5.0f, 1.4596f, true)));
        assertEquals(143, RunBuilder.scaleOf(glyph(OCR, 5.5f, 1.43f, true)));
    }

    @Test
    void wordsWidthComesWithinAPercentOfTheLayersAndNeverPastIt() {
        for (int hundredths = 300; hundredths <= 1400; hundredths++) {
            float size = hundredths / 100f;
            for (float hscale : new float[] {0.8f, 1f, 1.21f, 1.45f, 1.9f}) {
                int scale = RunBuilder.scaleOf(glyph(OCR, size, hscale, true));
                float word = halfPoint(size) * scale / 100f;
                float layer = size * hscale;
                String at = size + " pt at " + hscale;
                // Up to the hair scaleOf() adds before rounding down
                assertTrue(word <= layer + 1e-4f, "never wider than the scan's: " + at);
                assertTrue(word >= layer - halfPoint(size) / 100f - 1e-4f, "short by under a percent of Word's: " + at);
            }
        }
    }

    @Test
    void invisibleTextInAFaceWordHasGivesItBackToo() {
        // OCRmyPDF's NotoSans is set in Arial, which Word has: the fork takes it for that face
        assertEquals(146, RunBuilder.scaleOf(glyph(EXACT, 5.04f, 1.45f, true)));
    }

    @Test
    void visibleTextAndScriptsKeepTheirScale() {
        assertEquals(145, RunBuilder.scaleOf(glyph(OCR, 5.04f, 1.45f, false)));
        assertEquals(100, RunBuilder.scaleOf(glyph(OCR, 5.04f, 1.01f, false)));
        assertEquals(145, RunBuilder.scaleOf(glyph(EXACT, 5.04f, 1.45f, false)));
        // A superscript is set at its host's size, not its own
        Glyph script = glyph(OCR, 5.04f, 1.45f, true);
        script.vertAlign = 1;
        assertEquals(145, RunBuilder.scaleOf(script));
    }
}
