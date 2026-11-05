package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * OCRmyPDF draws each word of a page it turns at its own horizontal scaling (Tz), in a form turned a quarter onto the
 * page. A glyph's size and stretch come from its text rendering matrix's y and x scales, which hold for text turned any
 * way: swapped for turned text, a phone photo's words came out with their stretch as their size (6 to 12.5 points in one
 * line set at 6.68) and its inverse as their scale.
 */
class TurnedStretchTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final Pattern RUN = Pattern.compile("<w:r(?:\\s[^>]*)?>(.*?)</w:r>", Pattern.DOTALL);

    @TempDir Path dir;

    @Test
    void aStretchedWordOnATurnedPageKeepsItsSizeAndItsStretch() throws Exception {
        String xml = convert(true);
        assertEquals(size(xml, "Stretched"), size(xml, "plain"), "both words at their 11 points: " + xml);
        assertTrue(scale(xml, "Stretched") >= 130, "the stretched word keeps its 150% as its scale: " + xml);
    }

    @Test
    void anUnturnedPageReadsTheSame() throws Exception {
        String xml = convert(false);
        assertEquals(size(xml, "Stretched"), size(xml, "plain"), xml);
        assertTrue(scale(xml, "Stretched") >= 130, xml);
    }

    /** The w:sz of the run holding {@code word} (0 when the run sets none, taking the paragraph's). */
    private static int size(String xml, String word) {
        String run = run(xml, word);
        Matcher m = Pattern.compile("<w:sz w:val=\"(\\d+)\"").matcher(run);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** The w:w of the run holding {@code word} (100 when it sets none). */
    private static int scale(String xml, String word) {
        String run = run(xml, word);
        Matcher m = Pattern.compile("<w:w w:val=\"(\\d+)\"").matcher(run);
        return m.find() ? Integer.parseInt(m.group(1)) : 100;
    }

    private static String run(String xml, String word) {
        Matcher m = RUN.matcher(xml);
        while (m.find()) {
            if (m.group(1).contains(word)) {
                return m.group(1);
            }
        }
        throw new AssertionError("no run holds " + word + ": " + xml);
    }

    /**
     * A Letter page with a line of two words at 11 points, the first at 150% horizontal scaling: on a page turned a quarter
     * (/Rotate 90) in a form drawn with OCRmyPDF's matrix, or on an upright page.
     */
    private String convert(boolean turned) throws Exception {
        Path pdf = dir.resolve("stretch-" + turned + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            if (turned) {
                page.setRotation(90);
            }
            doc.addPage(page);
            PDFormXObject form = new PDFormXObject(new PDStream(doc));
            // The unturned page's box, as OCRmyPDF draws the form; the line sits well inside it either way
            form.setBBox(new PDRectangle(612, 792));
            PDResources resources = new PDResources();
            COSName font = resources.add(FONT);
            form.setResources(resources);
            String text = "BT /" + font.getName() + " 11 Tf 1 0 0 1 72 400 Tm 150 Tz (Stretched) Tj 100 Tz 90 0 Td"
                    + " (plain) Tj ET\n";
            try (OutputStream out = form.getContentStream().createOutputStream()) {
                out.write(text.getBytes(StandardCharsets.US_ASCII));
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                if (turned) {
                    cs.transform(new Matrix(0, 1, -1, 0, 612, 0));
                }
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("stretch-" + turned + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
