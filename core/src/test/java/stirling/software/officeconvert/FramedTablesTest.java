package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Two tables in one frame of rules each keep their own rows: the lower one takes no header rows over the one above it.
 * The CFPB loan estimate frames each column of its second page with rules from top to foot; its cash-to-close table, found
 * under the column's fee tables, took every line up to the frame's top as header rows. Starting at the frame's top, it
 * came before the fees, and Word's page ran over.
 */
class FramedTablesTest {

    private static final PDType1Font LABEL = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font VALUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float LEFT = 312;
    private static final float RIGHT = 571;

    @TempDir Path dir;

    @Test
    void eachTableKeepsItsOwnRows() throws Exception {
        String xml = convert();
        for (String word : new String[] {"Recording", "Prepaids", "Homeowner", "Deposit"}) {
            assertEquals(1, count(xml, word), word + " comes out once");
        }
        int fees = xml.indexOf("Prepaids");
        int escrow = xml.indexOf("Initial Escrow");
        int cash = xml.indexOf("Deposit");
        assertTrue(fees < escrow && escrow < cash, "the fees, the lines under them, then the cash table: " + fees + ", "
                + escrow + ", " + cash);
    }

    private static int count(String s, String word) {
        int n = 0;
        for (int i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) {
            n++;
        }
        return n;
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("costs.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, LABEL, 42, 60, 14, "Closing Cost Details");
                // The frame: rules across the column at its top and its foot
                rule(cs, 90);
                rule(cs, 640);
                // Fees near the top, each section's head ruled under
                String[][] fees = {
                    {"E. Taxes and Other Government Fees", "$152"}, {"Recording Fees and Other Taxes", "$152"},
                    {"Transfer Taxes", ""}, {"F. Prepaids", "$1,352"}, {"Homeowner's Insurance Premium", "$1,000"},
                    {"Prepaid Interest", "$352"},
                };
                rows(cs, 104, fees, new int[] {0, 3});
                text(cs, LABEL, LEFT + 4, 260, 9, "G. Initial Escrow Payment at Closing");
                text(cs, VALUE, LEFT + 4, 272, 9, "Property taxes are paid from the escrow account.");
                // A second table lower in the same frame
                String[][] cash = {
                    {"Total Closing Costs (J)", "$8,791"}, {"Closing Costs Financed", "$0"},
                    {"Down Payment/Funds from Borrower", "$29,000"}, {"Deposit", "-$10,000"}, {"Funds for Borrower", "$0"},
                    {"Seller Credits", "$0"}, {"Adjustments and Other Credits", "$0"},
                };
                rows(cs, 480, cash, new int[] {});
                for (int i = 0; i < cash.length; i++) {
                    rule(cs, 480 + 14 * i + 4, 0.25f);
                }
                for (int i = 0; i < 8; i++) {
                    text(cs, VALUE, 42, 104 + 14 * i, 9, "Loan costs line " + (i + 1) + " in the left column");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("costs.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Label and amount rows 14 pt apart from {@code baseline}, the rows {@code heads} bold and ruled under. */
    private static void rows(PDPageContentStream cs, float baseline, String[][] rows, int[] heads) throws IOException {
        for (int i = 0; i < rows.length; i++) {
            boolean head = false;
            for (int h : heads) {
                head |= h == i;
            }
            float y = baseline + 14 * i;
            text(cs, head ? LABEL : VALUE, LEFT + 4, y, 9, rows[i][0]);
            if (!rows[i][1].isEmpty()) {
                PDType1Font font = head ? LABEL : VALUE;
                float w = font.getStringWidth(rows[i][1]) / 1000 * 9;
                text(cs, font, RIGHT - 6 - w, y, 9, rows[i][1]);
            }
            if (head) {
                rule(cs, y + 4, 0.75f);
            }
        }
    }

    private static void rule(PDPageContentStream cs, float y) throws IOException {
        rule(cs, y, 1.5f);
    }

    private static void rule(PDPageContentStream cs, float y, float width) throws IOException {
        cs.setLineWidth(width);
        cs.moveTo(LEFT, H - y);
        cs.lineTo(RIGHT, H - y);
        cs.stroke();
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
