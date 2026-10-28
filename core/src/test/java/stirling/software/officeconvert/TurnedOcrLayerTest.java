package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OCRmyPDF grafts the text of a page it turns (a landscape table printed sideways on portrait paper, a page photographed
 * sideways) onto that page in a form whose BBox is the unturned page's, while the form's text is set out on the turned
 * page. The BBox cuts off every word past the unturned page's width, but invisible text is never painted, so no clip
 * hides it, and its words are read. Visible text a form's BBox cuts off stays left out.
 */
class TurnedOcrLayerTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    @TempDir Path dir;

    @Test
    void ocrTextPastTheUnturnedPagesWidthIsRead() throws Exception {
        String xml = convert(3);
        assertTrue(xml.contains("Category"), xml);
        assertTrue(xml.contains("Wholesale"), "a word in the turned page's first 612 pt is read");
        assertTrue(xml.contains("totals"), "a word past 612 pt, which the form's BBox cuts off, is read");
    }

    @Test
    void visibleTextTheBBoxCutsOffStaysOut() throws Exception {
        String xml = convert(0);
        assertTrue(xml.contains("Wholesale"), xml);
        assertFalse(xml.contains("totals"), "visible text the BBox cuts off isn't read");
    }

    /**
     * A Letter page turned a quarter (/Rotate 90), so it shows 792 pt wide, with a row of words set out on the turned page
     * from x = 72 to past x = 700, in a form drawn with OCRmyPDF's matrix and BBox. {@code mode} is the text's rendering
     * mode: 3 (invisible) as OCRmyPDF writes it, or 0.
     */
    private String convert(int mode) throws Exception {
        Path pdf = dir.resolve("turned-" + mode + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            doc.addPage(page);
            PDFormXObject form = new PDFormXObject(new PDStream(doc));
            form.setBBox(new PDRectangle(612, 792));
            PDResources resources = new PDResources();
            COSName font = resources.add(FONT);
            form.setResources(resources);
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            write(text, "BT " + mode + " Tr /" + font.getName() + " 11 Tf\n");
            float y = 540;
            for (String row : new String[] {
                    "Category Retail Wholesale Exports Imports Quarterly totals",
                    "Grain 4,120 3,880 2,940 1,215 12,155",
                    "Timber 2,310 1,975 1,640 905 6,830"}) {
                write(text, "1 0 0 1 72 " + y + " Tm\n");
                StringBuilder line = new StringBuilder();
                float x = 0;
                for (String word : row.split(" ")) {
                    line.append(String.format(java.util.Locale.ROOT, "%.2f 0 Td (%s) Tj ", x, word));
                    x = 104;
                }
                write(text, line + "\n");
                y -= 18;
            }
            write(text, "ET\n");
            try (OutputStream out = form.getContentStream().createOutputStream()) {
                out.write(text.toByteArray());
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                cs.transform(new Matrix(0, 1, -1, 0, 612, 0));
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("turned-" + mode + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
