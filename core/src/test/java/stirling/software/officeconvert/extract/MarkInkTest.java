package stirling.software.officeconvert.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

/** What a mark paints ({@link PageGraphics#ink}), as a veil draws it. */
class MarkInkTest {

    @Test
    void inkIsCutToARectangularClipThatCutsTheMark() throws IOException {
        // A pointer whose right half a rectangle clips away: the mark's box is already cut to the clip's box, so the
        // clip must be tested against the path itself (from Codex's review)
        PageGraphics gfx = read(cs -> {
            cs.saveGraphicsState();
            cs.addRect(0, 0, 200, 792);
            cs.clip();
            cs.setLineWidth(2f);
            cs.moveTo(100, 700);
            cs.lineTo(300, 600);
            cs.stroke();
            cs.restoreGraphicsState();
        });
        assertEquals(1, gfx.marks().size());
        List<PageGraphics.Outline> ink = gfx.ink(gfx.marks().getFirst());
        assertEquals(1, ink.size(), "a stroke");
        Rectangle2D painted = ink.getFirst().path().getBounds2D();
        assertTrue(painted.getMaxX() <= 200.5, "nothing past the clip: " + painted);
        assertTrue(painted.getMinX() <= 100, "the rest is kept: " + painted);
    }

    @Test
    void aFilledAndStrokedShapeKeepsBoth() throws IOException {
        PageGraphics gfx = read(cs -> {
            cs.setNonStrokingColor(0f, 0.36f, 0.67f);
            cs.setStrokingColor(0.6f, 0f, 0f);
            cs.moveTo(100, 600);
            cs.lineTo(140, 640);
            cs.lineTo(180, 600);
            cs.closePath();
            cs.fillAndStroke();
        });
        List<PageGraphics.Outline> ink = gfx.ink(gfx.marks().getFirst());
        assertEquals(2, ink.size(), "its fill and its stroke");
        int fill = ink.get(0).rgb();
        assertTrue((fill >> 16 & 0xFF) < 0x08 && (fill & 0xFF) > 0xA0, "the blue fill: " + Integer.toHexString(fill));
        assertEquals(0x990000, ink.get(1).rgb(), "the red stroke");
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
