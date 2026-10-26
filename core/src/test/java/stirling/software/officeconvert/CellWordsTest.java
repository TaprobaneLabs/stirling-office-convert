package stirling.software.officeconvert;

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
 * A table's words go to the column they sit in, though a section title in the column before runs into it, and the title
 * spans the empty cells it runs over. In Regulation Z's model statement "Interest Charged" starts in the post-date column
 * and runs into the descriptions, so that column's cells reached past the descriptions' first words: "Store" went to the
 * post dates, which then needed 80 pt in a 51 pt column, and the table ran past the page's edge, where Word cut the
 * amounts off. With the words in their columns, the title alone widened the post dates, and every column after them
 * moved right.
 */
class CellWordsTest {

    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float H = PDRectangle.LEGAL.getHeight();
    private static final float[] COLUMNS = {86.2f, 205.9f, 258.9f, 312.3f};
    private static final float AMOUNT_RIGHT = 516.2f;

    @TempDir Path dir;

    @Test
    void eachWordStaysInItsColumn() throws Exception {
        List<String> cells = cells(convert());
        for (String cell : cells) {
            if (cell.contains("2/26")) {
                assertFalse(cell.contains("Store"), "the post date's cell holds no description: " + cell);
            }
        }
        assertTrue(cells.stream().anyMatch(c -> c.contains("Store #6")), "the description is whole: " + cells);
        assertTrue(cells.stream().anyMatch(c -> c.contains("Interest Charged")), "the section title is whole: " + cells);
    }

    @Test
    void theTitleSpansTheEmptyCellAfterItAndTheTableKeepsItsWidth() throws Exception {
        String xml = convert();
        int at = xml.indexOf("Interest Charged");
        int cell = xml.lastIndexOf("<w:tc>", at);
        assertTrue(xml.substring(cell, at).contains("<w:gridSpan "), "the title's cell spans the empty cell after it");
        Matcher grid = Pattern.compile("<w:tblGrid>(.*?)</w:tblGrid>").matcher(xml);
        assertTrue(grid.find(), "the transactions are a table");
        float width = 0;
        Matcher col = Pattern.compile("w:w=\"(\\d+)\"").matcher(grid.group(1));
        while (col.find()) {
            width += Integer.parseInt(col.group(1)) / 20f;
        }
        float pdfWidth = AMOUNT_RIGHT + 4 - COLUMNS[0];
        assertTrue(width < pdfWidth + 10, "the table is about as wide as the PDF's (" + pdfWidth + " pt), not " + width);
    }

    /** The text of each table cell, its paragraphs' runs joined. */
    private static List<String> cells(String xml) {
        List<String> out = new ArrayList<>();
        Matcher tc = Pattern.compile("<w:tc>(.*?)</w:tc>", Pattern.DOTALL).matcher(xml);
        while (tc.find()) {
            StringBuilder sb = new StringBuilder();
            Matcher t = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>").matcher(tc.group(1));
            while (t.find()) {
                sb.append(t.group(1));
            }
            out.add(sb.toString());
        }
        return out;
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("statement.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LEGAL);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 6; i++) {
                    text(cs, PLAIN, 87, 60 + 13 * i, 9, "Statement line " + (i + 1) + " above the transactions, set across the page");
                }
                float y = 160;
                text(cs, BOLD, COLUMNS[0], y, 8, "Reference Number");
                text(cs, BOLD, COLUMNS[1], y, 8, "Trans Date");
                text(cs, BOLD, COLUMNS[2], y, 8, "Post Date");
                text(cs, BOLD, COLUMNS[3], y, 8, "Description of Transaction or Credit");
                right(cs, BOLD, y, "Amount");
                String[][] rows = {
                    {"564891561545KOSHD", "2/25", "2/26", "Store #6", "$14.35"},
                    {"841517877845AKOJIO", "2/25", "2/26", "Store #7", "$40.35"},
                    {"895848561561894KOH", "2/26", "2/27", "Store #8", "$27.68"},
                    {"1871556189456SAMKL", "2/26", "2/27", "Store #9", "$124.76"},
                    {"1542202074TWWZV48", "2/26", "2/26", "Cash Advance", "$121.50"},
                };
                float base = y;
                for (String[] row : rows) {
                    base += 10.8f;
                    for (int c = 0; c < 4; c++) {
                        text(cs, PLAIN, COLUMNS[c], base, 8, row[c]);
                    }
                    right(cs, PLAIN, base, row[4]);
                }
                base += 10.8f;
                // The section's title on a grey bar across the table, as the statement sets it
                cs.setNonStrokingColor(new java.awt.Color(200, 200, 200));
                cs.addRect(COLUMNS[0], H - base - 2.5f, AMOUNT_RIGHT + 4 - COLUMNS[0], 10.5f);
                cs.fill();
                cs.setNonStrokingColor(java.awt.Color.BLACK);
                text(cs, BOLD, 271.9f, base, 8, "Interest Charged");
                String[][] interest = {{"Interest Charge on Purchases", "$4.58"}, {"Interest Charge on Cash Advances", "$6.31"}};
                for (String[] row : interest) {
                    base += 10.8f;
                    text(cs, PLAIN, COLUMNS[3], base, 8, row[0]);
                    right(cs, PLAIN, base, row[1]);
                }
                for (int i = 0; i < 4; i++) {
                    text(cs, PLAIN, 87, base + 40 + 13 * i, 9, "Notice line " + (i + 1) + " under the transactions, set across the page");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("statement.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void right(PDPageContentStream cs, PDType1Font font, float baseline, String s) throws IOException {
        text(cs, font, AMOUNT_RIGHT - font.getStringWidth(s) / 1000 * 8, baseline, 8, s);
    }

    /** Text with its baseline at {@code baseline} from the page's top edge. */
    private static void text(PDPageContentStream cs, PDType1Font font, float x, float baseline, float size, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, H - baseline);
        cs.showText(s);
        cs.endText();
    }
}
