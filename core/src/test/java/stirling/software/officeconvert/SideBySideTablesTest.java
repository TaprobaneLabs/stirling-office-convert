package stirling.software.officeconvert;

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
 * A form's header with two blocks of labelled values side by side keeps them side by side: a band of two columns, each
 * with its table. In one column Word set the second table under the first, and the CFPB loan estimate's first page ran
 * over onto a second.
 */
class SideBySideTablesTest {

    private static final PDType1Font LABEL = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font VALUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();

    @TempDir Path dir;

    @Test
    void theBlocksStaySideBySide() throws Exception {
        String xml = convert();
        int left = xml.indexOf("APPLICANTS");
        int right = xml.indexOf("LOAN TERM");
        assertTrue(left > 0 && right > 0, "both blocks are there");
        int columnBreak = xml.indexOf("w:type=\"column\"", left);
        assertTrue(columnBreak > left && columnBreak < right,
                "the left block, a column break, then the right block: " + left + ", " + columnBreak + ", " + right);
        assertTrue(xml.contains("<w:cols w:num=\"2\""), "in a section of two columns");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("form.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            String[][] left = {
                {"DATE ISSUED", "2/15/2013"}, {"APPLICANTS", "Michael Jones and Mary Stone"}, {"", "123 Anywhere Street"},
                {"", "Anytown, ST 12345"}, {"PROPERTY", "456 Somewhere Avenue"}, {"", "Anytown, ST 12345"},
                {"SALE PRICE", "$240,000"},
            };
            String[][] right = {
                {"LOAN TERM", "30 years"}, {"PURPOSE", "Purchase"}, {"PRODUCT", "5 Year Interest Only, 5/3 Adjustable Rate"},
                {"LOAN TYPE", "Conventional"}, {"LOAN ID #", "123456789"}, {"RATE LOCK", "Yes, until 4/16/2013 at 5:00 p.m."},
            };
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, VALUE, 42, 40, 22, "FICUS BANK");
                text(cs, VALUE, 42, 62, 8, "4321 Random Boulevard, Somecity, ST 12340");
                text(cs, VALUE, 300, 62, 8, "Save this Loan Estimate to compare with your Closing Disclosure.");
                text(cs, LABEL, 42, 92, 16, "Loan Estimate");
                // Two ruled tables, the left one starting two rows lower, with 72 pt between them
                table(cs, 300.7f, 82, new float[] {54, 200}, right);
                table(cs, 42, 106, new float[] {60, 126}, left);
                for (int i = 0; i < 6; i++) {
                    text(cs, VALUE, 42, 230 + 14 * i, 10, "Body text line " + (i + 1)
                            + " under the header runs across the whole width of the page to its right margin here.");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("form.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A ruled table of label and value rows 12 pt high, its top {@code top} from the page's top edge. */
    private static void table(PDPageContentStream cs, float x, float top, float[] widths, String[][] rows)
            throws IOException {
        float width = widths[0] + widths[1];
        cs.setLineWidth(0.5f);
        for (int i = 0; i <= rows.length; i++) {
            cs.moveTo(x, H - top - 12 * i);
            cs.lineTo(x + width, H - top - 12 * i);
        }
        for (float edge : new float[] {x, x + widths[0], x + width}) {
            cs.moveTo(edge, H - top);
            cs.lineTo(edge, H - top - 12 * rows.length);
        }
        cs.stroke();
        for (int i = 0; i < rows.length; i++) {
            text(cs, LABEL, x + 2, top + 12 * i + 9, 7.5f, rows[i][0]);
            text(cs, VALUE, x + widths[0] + 2, top + 12 * i + 9, 8, rows[i][1]);
        }
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
