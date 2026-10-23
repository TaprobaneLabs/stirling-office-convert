package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rows of boxed panels with nothing running beside them, as on a fact sheet that sets its points in coloured boxes
 * between photos (NOAA's GeoXO sheet). Each panel becomes a text box of its size where the PDF has it, instead of the
 * row's panels joining into lines of tab-separated text that Word wraps across the page. A panel holding a small chart,
 * or drawn as a photo of its size, stays with its figure.
 */
class PanelRowTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float WIDTH = 150;
    private static final float HEIGHT = 80;
    private static final float[] LEFTS = {50, 410};
    private static final float[] TOPS = {150, 300};

    @TempDir Path dir;

    @Test
    void panelsInARowBecomeTextBoxes() throws Exception {
        String xml = convert(Art.NONE);
        List<String> boxes = textBoxes(xml);
        assertEquals(4, boxes.size(), "a text box per panel");
        for (int r = 0; r < TOPS.length; r++) {
            for (int c = 0; c < LEFTS.length; c++) {
                String first = label(r, c, 0);
                String last = label(r, c, 2);
                assertTrue(boxes.stream().anyMatch(b -> b.contains(first) && b.contains(last)),
                        "panel " + r + "," + c + " keeps its lines in one box");
            }
        }
        Matcher p = Pattern.compile("<w:p>.*?</w:p>", Pattern.DOTALL).matcher(xml);
        while (p.find()) {
            String para = p.group();
            assertFalse(para.contains(label(0, 0, 0)) && para.contains(label(0, 1, 0)),
                    "no paragraph runs across the row");
        }
    }

    @Test
    void oneLinePanelsInARowBecomeTextBoxes() throws Exception {
        List<String> boxes = textBoxes(convert(Art.NONE, 1));
        assertEquals(4, boxes.size(), "a text box per one-line panel");
        for (int r = 0; r < TOPS.length; r++) {
            for (int c = 0; c < LEFTS.length; c++) {
                String only = label(r, c, 0);
                assertTrue(boxes.stream().anyMatch(b -> b.contains(only)), "panel " + r + "," + c + " is a text box");
            }
        }
    }

    @Test
    void aPanelHoldingBarsStaysOutOfTextBoxes() throws Exception {
        assertNotInATextBox(convert(Art.BARS), "the panel with bars drawn in it");
    }

    @Test
    void aPanelHoldingARuledChartStaysOutOfTextBoxes() throws Exception {
        assertNotInATextBox(convert(Art.RULES), "the panel with a chart's axis and grid lines in it");
    }

    @Test
    void aPanelThatIsAPhotoStaysOutOfTextBoxes() throws Exception {
        assertNotInATextBox(convert(Art.PHOTO), "the panel drawn as a photo of its size");
    }

    private static void assertNotInATextBox(String xml, String what) {
        for (String box : textBoxes(xml)) {
            assertFalse(box.contains(label(0, 0, 0)), what + " isn't a text box");
        }
    }

    /** What the first panel holds besides its text. */
    private enum Art { NONE, BARS, RULES, PHOTO }

    /** A small chart (bars, or axis and grid lines) or a photo filling the panel, its lower left corner at x, y. */
    private static void drawArt(PDDocument doc, PDPageContentStream cs, Art art, float x, float y) throws IOException {
        switch (art) {
            case NONE -> { }
            case BARS -> {
                cs.setNonStrokingColor(1f, 0.8f, 0.2f);
                for (int b = 0; b < 4; b++) {
                    cs.addRect(x + 100 + b * 10, y + 8, 6, 12 + b * 8);
                    cs.fill();
                }
            }
            case RULES -> {
                cs.setStrokingColor(1f);
                cs.setLineWidth(0.75f);
                for (int g = 0; g < 4; g++) {
                    cs.moveTo(x + 100, y + 10 + g * 12);
                    cs.lineTo(x + 140, y + 10 + g * 12);
                }
                cs.moveTo(x + 100, y + 10);
                cs.lineTo(x + 100, y + 50);
                cs.stroke();
            }
            case PHOTO -> {
                BufferedImage photo = new BufferedImage(60, 32, BufferedImage.TYPE_INT_RGB);
                for (int i = 0; i < 60; i++) {
                    for (int j = 0; j < 32; j++) {
                        photo.setRGB(i, j, (i * 4) << 16 | (j * 8) << 8 | 90);
                    }
                }
                cs.drawImage(LosslessFactory.createFromImage(doc, photo), x, y, WIDTH, HEIGHT);
            }
        }
    }

    private static String label(int row, int col, int line) {
        return "Panel " + (char) ('A' + row * 2 + col) + " point " + (line + 1);
    }

    private static List<String> textBoxes(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:txbxContent>(.*?)</w:txbxContent>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private String convert(Art art) throws IOException {
        return convert(art, 3);
    }

    private String convert(Art art, int lines) throws IOException {
        Path pdf = dir.resolve("panels.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(FONT, 18);
                cs.newLineAtOffset(50, h - 80);
                cs.showText("Why the program matters");
                cs.endText();
                for (int r = 0; r < TOPS.length; r++) {
                    for (int c = 0; c < LEFTS.length; c++) {
                        float x = LEFTS[c];
                        float top = TOPS[r];
                        cs.setNonStrokingColor(0.1f, 0.35f, 0.7f);
                        cs.addRect(x, h - top - HEIGHT, WIDTH, HEIGHT);
                        cs.fill();
                        if (r == 0 && c == 0) {
                            drawArt(doc, cs, art, x, h - top - HEIGHT);
                        }
                        cs.setNonStrokingColor(1f);
                        for (int l = 0; l < lines; l++) {
                            cs.beginText();
                            cs.setFont(FONT, 10);
                            cs.newLineAtOffset(x + 8, h - top - 20 - l * 14);
                            cs.showText(label(r, c, l));
                            cs.endText();
                        }
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("panels.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
