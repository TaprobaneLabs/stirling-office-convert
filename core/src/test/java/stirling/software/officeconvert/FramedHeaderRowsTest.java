package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * A framed table's header rows are its own: no other table is found in their lines. A scanned bank statement frames
 * its summary and transactions with rules at their top and foot; the table was found under its first 25 rows, which
 * became its header rows, and the column pass found those lines again as a second table, whose 21 rows came out empty
 * and took a page of their own in Word.
 */
class FramedHeaderRowsTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float LEFT = 54;
    private static final float RIGHT = 558;
    private static final float[] COLUMNS = {LEFT + 4, 130, 330, 430};

    @TempDir Path dir;

    @Test
    void rowsAboveAFramedTableAreFoundOnce() throws Exception {
        String xml = convert();
        assertEquals(1, count(xml, "<w:tbl>"), "one table: no second one made of the header rows' lines");
        for (String word : new String[] {"Bakery", "Pharmacy", "Hardware", "Payroll", "Utilities"}) {
            assertEquals(1, count(xml, word), word + " comes out once");
        }
    }

    private static int count(String s, String word) {
        int n = 0;
        for (int i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) {
            n++;
        }
        return n;
    }

    private String convert() throws IOException {
        String[] upper = {"Bakery", "Pharmacy", "Hardware", "Bookshop", "Florist", "Garage", "Dentist", "Tailor"};
        String[] lower = {"Payroll", "Utilities", "Insurance", "Mortgage", "Transfer", "Interest", "Refund", "Dividend"};
        Path pdf = dir.resolve("statement.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, LEFT, 80, 14, "Account statement");
                // The frame: rules across the table's width at its top and its foot
                rule(cs, 120, 1.5f);
                rule(cs, 120 + 16 * 17 + 8, 1.5f);
                // Rows with no rules of their own, then rows ruled under each, in the same columns
                for (int i = 0; i < upper.length; i++) {
                    row(cs, 136 + 16 * i, i + 1, upper[i], 100 + 7 * i);
                }
                for (int i = 0; i < lower.length; i++) {
                    float y = 136 + 16 * (upper.length + i);
                    row(cs, y, upper.length + i + 1, lower[i], 200 + 11 * i);
                    rule(cs, y + 5, 0.25f);
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

    private static void row(PDPageContentStream cs, float baseline, int day, String payee, int cents) throws IOException {
        text(cs, COLUMNS[0], baseline, 9, String.format("10/%02d", day));
        text(cs, COLUMNS[1], baseline, 9, payee + " purchase");
        amount(cs, COLUMNS[3] - 10, baseline, String.format("%d.%02d", cents / 3, cents % 100));
        amount(cs, RIGHT - 6, baseline, String.format("%,d.%02d", 4000 - 37 * day, (cents * 7) % 100));
    }

    private static void amount(PDPageContentStream cs, float right, float baseline, String s) throws IOException {
        float w = FONT.getStringWidth(s) / 1000 * 9;
        text(cs, right - w, baseline, 9, s);
    }

    private static void rule(PDPageContentStream cs, float y, float width) throws IOException {
        cs.setLineWidth(width);
        cs.moveTo(LEFT, H - y);
        cs.lineTo(RIGHT, H - y);
        cs.stroke();
    }

    /** Text with its baseline at {@code baseline} from the page's top edge. */
    private static void text(PDPageContentStream cs, float x, float baseline, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, H - baseline);
        cs.showText(s);
        cs.endText();
    }
}
