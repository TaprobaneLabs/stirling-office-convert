package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rows of boxed panels with nothing running beside them, as on a fact sheet that sets its points in coloured boxes
 * between photos (NOAA's GeoXO sheet). Each panel becomes a text box of its size where the PDF has it, instead of the
 * row's panels joining into lines of tab-separated text that Word wraps across the page. A shaded box holding a picture,
 * or a tall shaded column of running text, stays as it was.
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
        String xml = convert(false);
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
    void aPanelHoldingAPictureStaysOutOfTextBoxes() throws Exception {
        String xml = convert(true);
        for (String box : textBoxes(xml)) {
            assertFalse(box.contains(label(0, 0, 0)), "the panel with a drawing in it isn't a text box");
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

    private String convert(boolean drawing) throws IOException {
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
                        if (drawing && r == 0 && c == 0) {
                            // A small chart inside the first panel: bars drawn as rectangles.
                            cs.setNonStrokingColor(1f, 0.8f, 0.2f);
                            for (int b = 0; b < 4; b++) {
                                cs.addRect(x + 100 + b * 10, h - top - HEIGHT + 8, 6, 12 + b * 8);
                                cs.fill();
                            }
                        }
                        cs.setNonStrokingColor(1f);
                        for (int l = 0; l < 3; l++) {
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
