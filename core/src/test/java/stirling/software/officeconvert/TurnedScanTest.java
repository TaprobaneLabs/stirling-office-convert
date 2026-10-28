package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A scan on a page turned a quarter (/Rotate 90), as OCR tools leave a landscape table printed sideways on portrait paper
 * or a page photographed sideways: the scan image is drawn upright on the unturned page and the page shows it turned. Its
 * rules and pictures are found as on an upright scan.
 */
class TurnedScanTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SCALE = 150f / 72f;

    /** The page as shown: Letter turned a quarter. */
    private static final float SHOWN_W = 792;

    private static final float SHOWN_H = 612;

    @TempDir Path dir;

    @Test
    void aRuledTableOnATurnedScanBecomesARuledTable() throws Exception {
        float left = 72;
        float top = 120;
        float[] cols = {0, 60, 330, 470};
        float row = 20;
        BufferedImage shown = paper();
        Graphics2D g = shown.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(3));
        for (int r = 0; r <= 6; r++) {
            g.drawLine(px(left), px(top + r * row), px(left + cols[3]), px(top + r * row));
        }
        for (float c : cols) {
            g.drawLine(px(left + c), px(top), px(left + c), px(top + 6 * row));
        }
        g.dispose();
        String body = convert(shown, cs -> {
            for (int r = 0; r < 6; r++) {
                float base = top + r * row + 14;
                hidden(cs, left + 4, base, String.valueOf(r + 1));
                hidden(cs, left + cols[1] + 4, base, "Service item " + (r + 1));
                hidden(cs, left + cols[2] + 4, base, (r + 1) * 10 + ".00");
            }
            hidden(cs, 72, 400, "Total due after the table is listed here.");
        });
        assertTrue(body.contains("<w:tbl>"), body);
        assertTrue(body.contains("Service item 3"), body);
        assertTrue(body.contains("w:val=\"single\""), "the scan's rules are the table's borders");
    }

    @Test
    void aPhotoOnATurnedScanStaysAPicture() throws Exception {
        BufferedImage shown = paper();
        Graphics2D g = shown.createGraphics();
        for (int y = px(80); y < px(260); y++) {
            int shade = 60 + (y - px(80)) * 70 / px(180);
            g.setColor(new Color(shade, shade, shade));
            g.drawLine(px(72), y, px(400), y);
        }
        g.dispose();
        String body = convert(shown, cs -> {
            for (int i = 0; i < 10; i++) {
                hidden(cs, 72, 300 + i * 16, "Body text under the photo of a page turned on its side, line " + i);
            }
        });
        assertTrue(body.contains("<w:drawing>"), body);
        assertTrue(body.contains("on its side, line 4"), body);
    }

    private interface Layer {
        void draw(PDPageContentStream cs) throws IOException;
    }

    /** White paper the size of the page as shown. */
    private static BufferedImage paper() {
        BufferedImage img = new BufferedImage(px(SHOWN_W), px(SHOWN_H), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        return img;
    }

    private static int px(float pt) {
        return Math.round(pt * SCALE);
    }

    /**
     * A Letter page turned a quarter that shows {@code shown}: the image drawn on the unturned page is {@code shown} turned
     * a quarter anticlockwise, and the OCR layer, set out where the page shows it, is turned with it.
     */
    private String convert(BufferedImage shown, Layer ocr) throws Exception {
        int w = shown.getWidth();
        int h = shown.getHeight();
        BufferedImage raw = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                raw.setRGB(y, w - 1 - x, shown.getRGB(x, y));
            }
        }
        Path pdf = dir.resolve("turned.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            doc.addPage(page);
            PDImageXObject x = LosslessFactory.createFromImage(doc, raw);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(x, 0, 0, 612, 792);
                ocr.draw(cs);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("turned.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Invisible text whose baseline starts at ({@code x}, {@code baseline}) on the page as shown (from its top left). The
     * page turns its user space a quarter clockwise, so shown x is user y and shown y, down, is user x.
     */
    private static void hidden(PDPageContentStream cs, float x, float baseline, String s) throws IOException {
        cs.beginText();
        cs.setRenderingMode(RenderingMode.NEITHER);
        cs.setFont(FONT, 11);
        cs.setTextMatrix(new Matrix(0, 1, -1, 0, baseline, x));
        cs.showText(s);
        cs.endText();
    }
}
