package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A map drawn as an image, with a frame and an inset key ruled over it, stays a picture: its frame and key are the
 * picture's, not a table or a sidebar. Read as tables over floating pictures, USGS Fact Sheet 2024-3021's four maps of
 * sea-level rise took a page and a half in Word for the PDF's one.
 */
class FramedPictureTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[] KEY = {"Percentage of", "park land", "99.04", "0.07", "0.90"};

    @TempDir Path dir;

    @Test
    void aMapsFrameAndKeyStayInItsPicture() throws Exception {
        String xml = convert(false);
        assertFalse(xml.contains("<w:tbl>"), "no table over the map");
        assertTrue(xml.contains("<pic:pic"), "the map is a picture");
    }

    @Test
    void aFramedMapWithItsKeyBesideItIsNoSidebar() throws Exception {
        // Its frame holds its labels and text runs beside it, as a sidebar's would.
        String xml = convert(true);
        assertFalse(xml.matches("(?s).*<w:txbxContent>(?:(?!</w:txbxContent>).)*SARGASSO SEA.*"),
                "the map's labels aren't a sidebar's text");
        assertTrue(xml.contains("<pic:pic"), "the map is a picture");
    }

    /** {@code beside}: the map's key runs beside its frame, as figure 5's does, instead of in a box inside it. */
    private String convert(boolean beside) throws IOException {
        Path pdf = dir.resolve("maps.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDImageXObject map = LosslessFactory.createFromImage(doc, map(433, 328));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 720, 12, "Figure 4 shows the park land each scenario floods, from the intermediate-low to the high.");
                text(cs, 72, 705, 12, "The maps below give the percentage of park land in each class of inundation probability.");
                float x = 92;
                float bottom = 470;
                float w = 208;
                float h = 157;
                // As USGS draws its maps: a tinted box under the image, then the image, then a frame around both.
                cs.setNonStrokingColor(0.88f, 0.9f, 0.92f);
                cs.addRect(x + 0.4f, bottom + 0.4f, w - 0.8f, h - 0.8f);
                cs.fill();
                cs.drawImage(map, x, bottom, w, h);
                cs.setStrokingColor(0f);
                cs.setLineWidth(0.5f);
                cs.addRect(x - 0.1f, bottom - 0.1f, w + 0.2f, h + 0.2f);
                cs.stroke();
                cs.setNonStrokingColor(0f);
                if (!beside) {
                    // The key: a box in the top right corner, a rule under each of its rows.
                    float kx = x + w - 52;
                    float rowH = 11;
                    float kBottom = bottom + h - KEY.length * rowH;
                    cs.moveTo(kx, bottom + h);
                    cs.lineTo(kx, kBottom);
                    cs.lineTo(x + w, kBottom);
                    for (int i = 1; i < KEY.length; i++) {
                        cs.moveTo(kx, bottom + h - i * rowH);
                        cs.lineTo(x + w, bottom + h - i * rowH);
                    }
                    cs.stroke();
                    for (int i = 0; i < KEY.length; i++) {
                        text(cs, kx + 3, bottom + h - (i + 1) * rowH + 3, 7, KEY[i]);
                    }
                }
                text(cs, x + 40, bottom + h - 30, 7, "SARGASSO SEA");
                text(cs, x + 20, bottom + 40, 7, "Bahia de San Juan");
                if (beside) {
                    String[] key = {"EXPLANATION", "Inundation probability", "Unlikely", "Likely as not", "Likely",
                        "Existing water area", "Visitor center", "Historic site"};
                    for (int i = 0; i < key.length; i++) {
                        text(cs, x + w + 30, bottom + h - 20 - i * 11, 8, key[i]);
                    }
                }
                text(cs, 72, 440, 10, "Figure 4. Sea-level rise inundation probabilities for San Juan National Historic Site.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("maps.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Land and sea, as a map image is. */
    private static BufferedImage map(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(225, 228, 230));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(250, 240, 180));
        g.fillOval(w / 6, h / 3, w / 2, h / 2);
        g.setColor(new Color(40, 60, 150));
        g.fillRect(w * 2 / 3, h * 2 / 3, w / 4, h / 6);
        g.dispose();
        return img;
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
