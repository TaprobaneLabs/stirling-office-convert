package stirling.software.officeconvert.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

/** A long stroked line a little off level or upright is a rule, not a mark drawn fixed to the page. */
class NearLevelRuleTest {

    @Test
    void aLongLineFallingALittleIsARule() throws IOException {
        // DOE OIG's budget page: a 537.6 pt rule drops 1.6 pt (0.17 degrees). As a mark it became a veil fixed to
        // the page, and ran through the two rows above it, which Word sets lower than the PDF does
        PageGraphics gfx = read(cs -> line(cs, 50, 400, 550, 398.5f));
        assertEquals(1, gfx.rules().size(), "a rule");
        assertTrue(gfx.marks().isEmpty(), "not a mark");
        PageGraphics.Rule rule = gfx.rules().getFirst();
        assertTrue(rule.horizontal());
        assertEquals(500f, rule.length(), 0.01f);
        assertEquals(792f - 399.25f, rule.pos(), 0.01f, "level halfway between its ends");
    }

    @Test
    void aTallLineLeaningALittleIsARule() throws IOException {
        PageGraphics gfx = read(cs -> line(cs, 300, 100, 301.6f, 700));
        assertEquals(1, gfx.rules().size(), "a rule");
        assertTrue(gfx.marks().isEmpty(), "not a mark");
        assertFalse(gfx.rules().getFirst().horizontal());
    }

    @Test
    void aLineFallingMoreThanItsShareStaysAMark() throws IOException {
        // 5 pt over 500 pt is 1%, a slope a reader sees
        PageGraphics gfx = read(cs -> line(cs, 50, 400, 550, 395));
        assertTrue(gfx.rules().isEmpty(), "not a rule");
        assertEquals(1, gfx.marks().size(), "a mark");
    }

    @Test
    void aShortLineKeepsThePointTolerance() throws IOException {
        // 0.5% of 100 pt is under the 1 pt every line is allowed, so 1.5 pt off is still a slope
        PageGraphics gfx = read(cs -> line(cs, 50, 400, 150, 398.5f));
        assertTrue(gfx.rules().isEmpty(), "not a rule");
        assertEquals(1, gfx.marks().size(), "a mark");
    }

    private static void line(PDPageContentStream cs, float x0, float y0, float x1, float y1) throws IOException {
        cs.setLineWidth(0.75f);
        cs.moveTo(x0, y0);
        cs.lineTo(x1, y1);
        cs.stroke();
    }

    private interface Drawing {
        void draw(PDPageContentStream cs) throws IOException;
    }

    private static PageGraphics read(Drawing drawing) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                drawing.draw(cs);
            }
            return GraphicsCollector.read(page, PageReader.displayTransform(page.getCropBox(), 0), 612, 792,
                    new ParsedStreams());
        }
    }
}
