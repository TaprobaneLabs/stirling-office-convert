package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A phone's document scanner keeps the room's light, so the paper in its photo is grey, not white (luma 175 to 210),
 * and a little lighter at the top than at the foot. Ink is judged against that paper: the page isn't one picture
 * that takes its OCR text along, a ruled table is still found, and a photo on the page is still a picture.
 */
class GreyPaperScanTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SCALE = 150f / 72f;

    @TempDir Path dir;

    @Test
    void ocrTextOnGreyPaperStaysText() throws Exception {
        BufferedImage img = greyPaper();
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(58, 56, 54));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        for (int i = 0; i < 20; i++) {
            g.drawString("Body text of a photographed page with a readable OCR layer line " + i, px(72), px(120 + i * 16));
        }
        g.dispose();
        Path pdf = scan(img, cs -> {
            for (int i = 0; i < 20; i++) {
                hidden(cs, 72, 792 - (120 + i * 16), "Body text of a photographed page with a readable OCR layer line " + i);
            }
        });
        String body = body(pdf);
        assertTrue(body.contains("readable OCR layer line 3"), body);
        assertTrue(body.contains("readable OCR layer line 17"), body);
    }

    @Test
    void aRuledTableOnGreyPaperBecomesATable() throws Exception {
        float left = 72;
        float top = 150;
        float[] cols = {0, 60, 260, 340};
        float row = 20;
        BufferedImage img = greyPaper();
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 40, 38));
        g.setStroke(new BasicStroke(3));
        for (int r = 0; r <= 6; r++) {
            g.drawLine(px(left), px(top + r * row), px(left + cols[3]), px(top + r * row));
        }
        for (float c : cols) {
            g.drawLine(px(left + c), px(top), px(left + c), px(top + 6 * row));
        }
        g.dispose();
        Path pdf = scan(img, cs -> {
            for (int r = 0; r < 6; r++) {
                float base = 792 - (top + r * row + 14);
                hidden(cs, left + 4, base, String.valueOf(r + 1));
                hidden(cs, left + cols[1] + 4, base, "Service item " + (r + 1));
                hidden(cs, left + cols[2] + 4, base, (r + 1) * 10 + ".00");
            }
            hidden(cs, 72, 792 - 400, "Total due after the table is listed here.");
        });
        String body = body(pdf);
        assertTrue(body.contains("<w:tbl>"), body);
        assertTrue(body.contains("Service item 3"), body);
        assertTrue(body.contains("Total due after the table"), body);
    }

    @Test
    void aPhotoOnGreyPaperStaysAPicture() throws Exception {
        BufferedImage img = greyPaper();
        Graphics2D g = img.createGraphics();
        for (int y = px(110); y < px(290); y++) {
            int shade = 70 + (y - px(110)) * 60 / px(180);
            g.setColor(new Color(shade, shade - 4, shade - 8));
            g.drawLine(px(72), y, px(340), y);
        }
        g.setColor(new Color(58, 56, 54));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        for (int i = 0; i < 12; i++) {
            g.drawString("Caption and body text under the photo with an OCR layer line " + i, px(72), px(330 + i * 16));
        }
        g.dispose();
        Path pdf = scan(img, cs -> {
            for (int i = 0; i < 12; i++) {
                hidden(cs, 72, 792 - (330 + i * 16), "Caption and body text under the photo with an OCR layer line " + i);
            }
        });
        String body = body(pdf);
        assertTrue(body.contains("<w:drawing>"), body);
        assertTrue(body.contains("an OCR layer line 6"), body);
    }

    private interface Layer {
        void draw(PDPageContentStream cs) throws IOException;
    }

    /** Paper as a phone photographs it: grey with a slight warm cast, lighter at the top (luma 194) than the foot (178). */
    private static BufferedImage greyPaper() {
        BufferedImage img = new BufferedImage(px(612), px(792), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < img.getHeight(); y++) {
            int level = 195 - 16 * y / img.getHeight();
            g.setColor(new Color(level + 3, level, level - 6));
            g.drawLine(0, y, img.getWidth(), y);
        }
        g.dispose();
        return img;
    }

    private static int px(float pt) {
        return Math.round(pt * SCALE);
    }

    private Path scan(BufferedImage img, Layer ocr) throws IOException {
        Path pdf = dir.resolve("scan.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDImageXObject x = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(x, 0, 0, 612, 792);
                ocr.draw(cs);
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static void hidden(PDPageContentStream cs, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setRenderingMode(RenderingMode.NEITHER);
        cs.setFont(FONT, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private String body(Path pdf) throws Exception {
        Path docx = dir.resolve("out.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
