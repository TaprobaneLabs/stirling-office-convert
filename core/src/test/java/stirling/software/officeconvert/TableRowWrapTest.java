package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** A statistical table as government releases set them: row labels led by dots to ruled columns of figures. */
class TableRowWrapTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[][] ROWS = {
        {"Total private", "9,671", "7,845"},
        {"Mining and logging", "42", "23"},
        {"Trade, transportation, and utilities", "1,531", "1,299"},
        {"Wholesale trade", "213", "217"},
        {"Retail trade", "852", "550"},
        {"Information", "213", "127"},
    };
    private static final float LEFT = 72;
    private static final float LABEL = 190;
    private static final float FIGURE = 60;
    private static final float TOP = 700;
    private static final float ROW = 14;

    @TempDir Path dir;

    @Test
    void dotLeadersEndingALabelBecomeATab() throws Exception {
        String xml = convert();
        assertTrue(xml.contains("<w:tbl>"), "the ruled grid is a table");
        Matcher m = Pattern.compile("<w:tc>(?:(?!</w:tc>).)*Trade, transportation(?:(?!</w:tc>).)*</w:tc>").matcher(xml);
        assertTrue(m.find(), "the long label's cell");
        String cell = m.group();
        assertTrue(cell.contains("w:leader=\"dot\""), "a dot leader tab: " + cell);
        assertFalse(cell.replaceAll("<[^>]+>", "").contains(". . ."), "no dots typed as text");
    }

    @Test
    void oneLineCellsMayRunPastTheirEdge() throws Exception {
        String xml = convert();
        Matcher label = Pattern.compile("<w:p><w:pPr>(?:(?!</w:p>).)*<w:ind w:left=\"\\d+\" w:right=\"(-\\d+)\"(?:(?!</w:p>).)*Wholesale trade")
                .matcher(xml);
        assertTrue(label.find(), "a left-aligned label opens room on its right");
        Matcher figure = Pattern.compile("<w:ind w:left=\"(-\\d+)\"[^>]*/><w:jc w:val=\"right\"/>(?:(?!</w:p>).)*>213<").matcher(xml);
        assertTrue(figure.find(), "a right-aligned figure opens room on its left");
    }

    private String convert() throws Exception {
        Path pdf = dir.resolve("table.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, LEFT, TOP + 30, 11, "Table A. Job openings by industry, seasonally adjusted (in thousands)");
                float right = LEFT + LABEL + 2 * FIGURE;
                cs.setLineWidth(0.5f);
                for (int r = 0; r <= ROWS.length; r++) {
                    float y = TOP + 10 - r * ROW;
                    cs.moveTo(LEFT, y);
                    cs.lineTo(right, y);
                }
                for (float x : new float[] {LEFT, LEFT + LABEL, LEFT + LABEL + FIGURE, right}) {
                    cs.moveTo(x, TOP + 10);
                    cs.lineTo(x, TOP + 10 - ROWS.length * ROW);
                }
                cs.stroke();
                for (int r = 0; r < ROWS.length; r++) {
                    float y = TOP + 10 - r * ROW - 10;
                    text(cs, LEFT + 3, y, 8, ROWS[r][0]);
                    // Dots one by one, a little tighter than "dot space" in the font, up to the column rule
                    float x = LEFT + 3 + FONT.getStringWidth(ROWS[r][0]) / 1000f * 8 + 3;
                    for (; x < LEFT + LABEL - 5; x += 3.6f) {
                        text(cs, x, y, 8, ".");
                    }
                    for (int c = 1; c < 3; c++) {
                        float w = FONT.getStringWidth(ROWS[r][c]) / 1000f * 8;
                        text(cs, LEFT + LABEL + c * FIGURE - 4 - w, y, 8, ROWS[r][c]);
                    }
                }
                text(cs, LEFT, TOP - 10 - ROWS.length * ROW, 9, "Figures are preliminary and revised each month.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("table.docx");
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
