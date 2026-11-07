package stirling.software.officeconvert.pptx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.ScriptWidths;

class TextXmlSpacingTest {

    private static RunStyle scaled(int scale) {
        return new RunStyle("Times New Roman", 11, false, false, false, false, 0, -1, 0, false, 0f, scale, false);
    }

    @Test
    void narrowedIndicRunsSpaceEachClusterByItsOwnWidth() {
        String text = "\u0C2E\u0C3E\u0C28\u0C35\u0C41\u0C32\u0C41 \u0C38\u0C2E\u0C3E\u0C28\u0C41\u0C32\u0C41";
        float unit = ScriptWidths.perUnit(text, false);
        assertEquals(Math.round(-0.1f * unit * 11 * 100), TextXml.spacing(scaled(90), text));
        assertEquals(0, TextXml.spacing(scaled(100), text));
    }

    /**
     * The Federal Register's Melior, set in Times New Roman at 114%: the spacing makes a line as wide as the scale would.
     * Half an em a character made each 9-point line about 4 points wider than the PDF's, and PowerPoint wrapped it.
     */
    @Test
    void aScaledStandInRunIsSpacedByItsOwnWidths() {
        String text = "beneficial owners of the foreign reporting company and exempt entities";
        RunStyle style = new RunStyle("Times New Roman", 9, false, false, false, false, 0, -1, 0, false, 0f, 114, false);
        float width = SubstituteMetrics.width(text, "Times New Roman", false, false, 9);
        float spaced = width + text.length() * TextXml.spacing(style, text) / 100f;
        assertEquals(1.14f * width, spaced, 0.005f * text.length());
    }

    @Test
    void aFaceWithoutMetricsKeepsHalfAnEm() {
        RunStyle style = new RunStyle("Garamond", 11, false, false, false, false, 0, -1, 0, false, 0f, 90, false);
        assertEquals(Math.round(-0.1f * 0.5f * 11 * 100), TextXml.spacing(style, "Latin"));
    }
}
