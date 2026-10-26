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
 * A table column that holds no text gives its room to the column before it rather than the table growing. The Federal
 * Reserve's Regulation Z model statement lists transactions whose description column most rows fill only on the left;
 * found as two columns, with the description's header all in the first, the table grew 94 pt past the page's edge and
 * Word cut the amounts off there ("$450.0").
 */
class TableRoomTest {

    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float H = PDRectangle.LEGAL.getHeight();
    /** Where the table's columns start, and where its amounts end. */
    private static final float[] COLUMNS = {86.9f, 206.8f, 259f, 313.1f};
    private static final float AMOUNT_RIGHT = 523f;

    @TempDir Path dir;

    @Test
    void theTableKeepsItsWidth() throws Exception {
        String xml = convert();
        Matcher grid = Pattern.compile("<w:tblGrid>(.*?)</w:tblGrid>").matcher(xml);
        float widest = 0;
        while (grid.find()) {
            float width = 0;
            Matcher col = Pattern.compile("w:w=\"(\\d+)\"").matcher(grid.group(1));
            while (col.find()) {
                width += Integer.parseInt(col.group(1)) / 20f;
            }
            widest = Math.max(widest, width);
        }
        float pdfWidth = AMOUNT_RIGHT + 4 - COLUMNS[0];
        assertTrue(widest > 0, "the transactions are a table");
        assertTrue(widest < pdfWidth + 20, "the table is about as wide as the PDF's (" + pdfWidth + " pt), not " + widest);
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("statement.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LEGAL);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, BOLD, 87, 560, 10, "Transactions");
                for (int i = 0; i < 8; i++) {
                    text(cs, PLAIN, 87, 120 + 14 * i, 9, "Account summary line " + (i + 1) + " above the transactions, set across the page");
                }
                float y = 604;
                text(cs, BOLD, COLUMNS[0], y, 8, "Reference Number");
                text(cs, BOLD, COLUMNS[1], y, 8, "Trans Date");
                text(cs, BOLD, COLUMNS[2], y, 8, "Post Date");
                text(cs, BOLD, COLUMNS[3], y, 8, "Description of Transaction or Credit");
                right(cs, BOLD, y, "Amount");
                String[][] rows = {
                    {"5884186PS0388W6YM", "2/22", "2/23", "Store #1", "$2.05"},
                    {"0544400060ZLV72VL", "2/24", "2/25", "Store #2", "$12.11"},
                    {"55541860705RDYD0X", "2/24", "2/25", "Store #3", "$4.63"},
                    {"554328608008W90M0", "2/24", "2/25", "Store #4", "$114.95"},
                    {"054830709LYMRPT4L", "2/24", "2/25", "Store #5", "$7.35"},
                    {"854338203FS8OO0Z5", "2/25", "2/25", "Pymt Thank You", "$450.00"},
                };
                for (int r = 0; r < rows.length; r++) {
                    float base = y + 10.8f * (r + 1);
                    for (int c = 0; c < 4; c++) {
                        text(cs, PLAIN, COLUMNS[c], base, 8, rows[r][c]);
                    }
                    right(cs, PLAIN, base, rows[r][4]);
                }
                text(cs, PLAIN, 388, 690, 8, "(transactions continued on next page)");
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
