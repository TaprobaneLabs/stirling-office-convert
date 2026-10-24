package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A picture at the foot of a column that reaches down beside the next band's text is set on the page, not in the column.
 * In the column it held the column open to its foot, Word started the next band only below that, and CDC's kidney disease
 * fact sheet sent the callout beside its drawing to a page of its own.
 */
class HangingPictureTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();

    @TempDir Path dir;

    @Test
    void thePictureIsSetOnThePage() throws Exception {
        String xml = convert();
        assertTrue(xml.contains("Talk to your doctor"), "the callout is there");
        int pic = xml.indexOf("<pic:pic");
        assertTrue(pic > 0, "the picture is there");
        int inline = xml.lastIndexOf("<wp:inline", pic);
        int anchor = xml.lastIndexOf("<wp:anchor", pic);
        assertTrue(anchor > inline, "the picture is anchored on the page, not in the column's text");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            String[] left = ("Kidney failure happens when kidney damage is severe and kidney function is very low and a "
                    + "transplant or dialysis is then needed for survival in most cases").split(" ");
            String[] right = ("Monitor and manage blood sugar and blood pressure and make lifestyle changes such as healthy "
                    + "eating and physical activity to prevent more kidney damage over the years").split(" ");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 36, 60, 14, "Chronic Kidney Disease");
                for (int i = 0; i < 24; i++) {
                    text(cs, 36, 90 + 14 * i, 10, line(left, i, 7));
                    text(cs, 324, 90 + 14 * i, 10, line(right, i, 7));
                }
                for (int i = 24; i < 30; i++) {
                    text(cs, 324, 90 + 14 * i, 10, line(right, i, 7));
                }
                // The drawing ends the left column and reaches down beside the callout below the columns
                cs.drawImage(LosslessFactory.createFromImage(doc, drawing()), 40, H - 760, 150, 150);
                text(cs, 240, 700, 11, "Talk to your doctor about how to protect your kidneys, and ask how often");
                text(cs, 240, 714, 11, "to have them checked if you have diabetes or high blood pressure.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String line(String[] words, int i, int n) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            sb.append(k == 0 ? "" : " ").append(words[(i * n + k) % words.length]);
        }
        return sb.toString();
    }

    private static BufferedImage drawing() {
        BufferedImage img = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 150, 150);
        g.setColor(new Color(20, 60, 140));
        g.fillOval(30, 10, 40, 40);
        g.fillRoundRect(15, 60, 70, 80, 20, 20);
        g.fillOval(90, 20, 40, 40);
        g.dispose();
        return img;
    }

    /** Text at {@code top} from the page's top edge. */
    private static void text(PDPageContentStream cs, float x, float top, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, H - top);
        cs.showText(s);
        cs.endText();
    }
}
