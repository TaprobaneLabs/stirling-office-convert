package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * A statistical release's ruled table whose narrow first column numbers the lines, with no rules between its numbers, so
 * they come out as one paragraph in one tall cell, a number to a line.
 */
class StackedCellLinesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[] LABELS = {
        "Personal income", "Previously published", "Disposable personal income", "Previously published",
        "Personal consumption expenditures", "Previously published", "Real disposable personal income",
        "Previously published", "Real personal consumption expenditures", "Previously published", "Personal saving",
        "Previously published",
    };
    private static final float LEFT = 72;
    private static final float NUMBER = 12;
    private static final float LABEL = 170;
    private static final float FIGURE = 50;
    private static final float TOP = 600;
    private static final float ROW = 12.6f;

    @TempDir Path dir;

    @Test
    void numbersKeepALineEach() throws Exception {
        String xml = convert();
        Matcher cell = Pattern.compile("<w:tc>(?:(?!</w:tc>).)*[> ]10[< ](?:(?!</w:tc>).)*</w:tc>").matcher(xml);
        assertTrue(cell.find(), "the line numbers' cell");
        String text = cell.group().replaceAll("<w:br/>", "\n").replaceAll("</w:p>", "\n").replaceAll("<[^>]+>", "");
        assertTrue(text.contains("9\n10\n11\n12"), "a number to a line, kept with line breaks: " + cell.group());
        Matcher para = Pattern.compile("<w:p><w:pPr>(?:(?!</w:pPr>).)*</w:pPr>(?:(?!</w:p>).)*<w:br/>(?:(?!</w:p>).)*</w:p>")
                .matcher(cell.group());
        assertTrue(para.find(), "a paragraph of broken lines");
        assertTrue(para.group().matches("(?s).*<w:ind w:left=\"-\\d+\".*"), "it may run past the cell's edge: " + para.group());
    }

    private String convert() throws Exception {
        Path pdf = dir.resolve("release.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 720;
                for (int i = 0; i < 6; i++) {
                    text(cs, LEFT, y, 9, "Personal income increased $" + (90 + i) + ".4 billion (0.4 percent at a monthly rate) in"
                            + " August, according to estimates released today.");
                    y -= 13;
                }
                float right = LEFT + NUMBER + LABEL + 3 * FIGURE;
                float bottom = TOP - LABELS.length * ROW;
                cs.setLineWidth(0.5f);
                cs.moveTo(LEFT, TOP);
                cs.lineTo(right, TOP);
                cs.moveTo(LEFT, bottom);
                cs.lineTo(right, bottom);
                for (int r = 1; r < LABELS.length; r++) {
                    cs.moveTo(LEFT + NUMBER, TOP - r * ROW);
                    cs.lineTo(right, TOP - r * ROW);
                }
                for (float x : new float[] {LEFT, LEFT + NUMBER, LEFT + NUMBER + LABEL, LEFT + NUMBER + LABEL + FIGURE,
                    LEFT + NUMBER + LABEL + 2 * FIGURE, right}) {
                    cs.moveTo(x, TOP);
                    cs.lineTo(x, bottom);
                }
                cs.stroke();
                for (int r = 0; r < LABELS.length; r++) {
                    float base = TOP - r * ROW - 9;
                    String n = String.valueOf(r + 1);
                    text(cs, LEFT + NUMBER - 1.5f - FONT.getStringWidth(n) / 1000f * 7, base, 7, n);
                    text(cs, LEFT + NUMBER + 3, base, 7, LABELS[r]);
                    for (int c = 0; c < 3; c++) {
                        String v = String.format("%.1f", (r * 7 + c * 3) % 11 - 2.5);
                        text(cs, LEFT + NUMBER + LABEL + (c + 1) * FIGURE - 3 - FONT.getStringWidth(v) / 1000f * 7, base, 7, v);
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("release.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
