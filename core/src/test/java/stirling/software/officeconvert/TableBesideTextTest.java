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
 * A table with text beside it, as a statement sets its account summary beside its payment box, comes out as two columns
 * over their height: the table in the first and the text in the second. Read as one column, the text went under the
 * table, so the page ran longer and everything below it moved down (Regulation Z's model statement, CFPB's loan
 * estimate).
 */
class TableBesideTextTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[][] ROWS = {{"Previous Balance", "$535.07"}, {"Payments", "-$450.00"}, {"Other Credits", "-$13.45"},
        {"Purchases", "+$529.57"}, {"Balance Transfers", "+$785.00"}, {"Cash Advances", "+$318.00"}, {"Past Due Amount", "+$0.00"},
        {"Fees Charged", "+$69.45"}, {"Interest Charged", "+$10.89"}, {"New Balance", "$1,784.53"}, {"Credit limit", "$2,000.00"},
        {"Available credit", "$215.47"}};
    private static final String[] BESIDE = {"Late Payment Warning: If we do not receive your", "minimum payment by the date listed above, you",
        "may have to pay a late fee of up to $35 and your", "rates may be increased up to the Penalty rate.",
        "Minimum Payment Warning: If you make only the", "minimum payment each period, you will pay more",
        "in interest and it will take you longer to pay off", "your balance. For an estimate of the time it would",
        "take to repay your balance, call the number below."};

    @TempDir Path dir;

    @Test
    void aTableAndTheTextBesideItStandInTwoColumns() throws Exception {
        String xml = convert();
        Matcher cols = Pattern.compile("<w:cols w:num=\"2\"").matcher(xml);
        assertTrue(cols.find(), "a two-column section");
        int table = xml.indexOf("Previous Balance");
        int beside = xml.indexOf("Late Payment Warning");
        int below = xml.indexOf("Questions about your account");
        String between = xml.substring(table, beside);
        assertTrue(between.contains("w:type=\"column\""), "the text beside the table starts the second column");
        assertTrue(below > beside, "the text below both follows them");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("statement.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, h - 60, 14, "Account Statement, February 21 to March 22");
                text(cs, 72, h - 80, 10, "This statement covers your account's activity for the period shown above.");
                float top = h - 110;
                cs.setStrokingColor(0.3f);
                cs.setLineWidth(0.5f);
                for (int r = 0; r <= ROWS.length; r++) {
                    cs.moveTo(72, top - r * 16);
                    cs.lineTo(290, top - r * 16);
                }
                for (float x : new float[] {72, 200, 290}) {
                    cs.moveTo(x, top);
                    cs.lineTo(x, top - ROWS.length * 16);
                }
                cs.stroke();
                for (int r = 0; r < ROWS.length; r++) {
                    text(cs, 76, top - r * 16 - 12, 9, ROWS[r][0]);
                    text(cs, 204, top - r * 16 - 12, 9, ROWS[r][1]);
                }
                for (int i = 0; i < BESIDE.length; i++) {
                    text(cs, 320, top - 12 - i * 13, 9, BESIDE[i]);
                }
                float below = top - ROWS.length * 16 - 30;
                text(cs, 72, below, 10, "Questions about your account? Call customer service at the number on the back of your card.");
                text(cs, 72, below - 14, 10, "Send billing inquiries and correspondence to the address shown on your payment coupon.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("statement.docx");
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
