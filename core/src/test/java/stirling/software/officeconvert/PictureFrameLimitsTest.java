package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a framed picture's hold on its rules and words ends (see {@code FramedPictureTest}): a key of labelled values in
 * the picture stays the picture's too, while a table on a framed panel of one colour, or over a picture whose edges
 * carry only rules over the same half of them, stays a table.
 */
class PictureFrameLimitsTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float X = 92;
    private static final float BOTTOM = 470;
    private static final float W = 260;
    private static final float H = 157;

    private enum Over { KEY_VALUES, FLAT_INLINE_PANEL, FLAT_RAW_PANEL, UNREADABLE_PANEL, CLIPPED_PANEL, RETRACED_RULES }

    @TempDir Path dir;

    @Test
    void labelledValuesInAFramedMapStayInItsPicture() throws Exception {
        assertFalse(convert(Over.KEY_VALUES).contains("<w:tbl>"), "no table of the map's key");
    }

    @Test
    void aTableOnAPanelOfOneColourStaysATable() throws Exception {
        String xml = convert(Over.FLAT_INLINE_PANEL);
        assertTrue(xml.matches("(?s).*<w:tbl>.*Annual benefits.*</w:tbl>.*"), "the panel's table is a table");
    }

    @Test
    void aTableOnAnUncompressedPanelOfOneColourStaysATable() throws Exception {
        String xml = convert(Over.FLAT_RAW_PANEL);
        assertTrue(xml.matches("(?s).*<w:tbl>.*Annual benefits.*</w:tbl>.*"), "the panel's table is a table");
    }

    @Test
    void aTableOnAPanelTooLargeToDecodeStaysATable() throws Exception {
        // Its pixels can't be read, so it isn't taken for a picture.
        String xml = convert(Over.UNREADABLE_PANEL);
        assertTrue(xml.matches("(?s).*<w:tbl>.*Annual benefits.*</w:tbl>.*"), "the panel's table is a table");
    }

    @Test
    void aTableOnTheOneColourPartOfAClippedImageStaysATable() throws Exception {
        // The image holds a map beside a plain panel, and the page clips it to the panel.
        String xml = convert(Over.CLIPPED_PANEL);
        assertTrue(xml.matches("(?s).*<w:tbl>.*Annual benefits.*</w:tbl>.*"), "the panel's table is a table");
    }

    @Test
    void overlappingRulesOverHalfAnEdgeDontFrameAPicture() throws Exception {
        String xml = convert(Over.RETRACED_RULES);
        assertTrue(xml.matches("(?s).*<w:tbl>.*Annual benefits.*</w:tbl>.*"), "the table over the picture is a table");
    }

    private String convert(Over over) throws IOException {
        Path pdf = dir.resolve("frames.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 720, 12, "The figure below sums up what the elevation program brings to the state each year.");
                text(cs, 72, 705, 12, "Its figures come from the national enhanced elevation assessment of 2012.");
                cs.setStrokingColor(0f);
                cs.setLineWidth(0.5f);
                switch (over) {
                    case KEY_VALUES -> {
                        cs.setNonStrokingColor(0.88f, 0.9f, 0.92f);
                        cs.addRect(X + 0.4f, BOTTOM + 0.4f, W - 0.8f, H - 0.8f);
                        cs.fill();
                        cs.drawImage(LosslessFactory.createFromImage(doc, map(433, 262)), X, BOTTOM, W, H);
                        cs.addRect(X - 0.1f, BOTTOM - 0.1f, W + 0.2f, H + 0.2f);
                        cs.stroke();
                        cs.setNonStrokingColor(0f);
                        String[][] key = {{"Area:", "0.29 square kilometer"}, {"Sites:", "12 historic buildings"},
                            {"Water:", "2015 to 2017 survey"}};
                        for (int i = 0; i < key.length; i++) {
                            text(cs, X + 10, BOTTOM + 40 - i * 11, 7, key[i][0]);
                            text(cs, X + 50, BOTTOM + 40 - i * 11, 7, key[i][1]);
                        }
                        text(cs, X + 40, BOTTOM + H - 30, 7, "SARGASSO SEA");
                    }
                    case RETRACED_RULES -> {
                        cs.drawImage(LosslessFactory.createFromImage(doc, map(433, 262)), X, BOTTOM, W, H);
                        // Along three edges, two rules over the same half of the edge, one a little longer.
                        for (float end : new float[] {0.45f, 0.5f}) {
                            cs.moveTo(X, BOTTOM + H);
                            cs.lineTo(X + end * W, BOTTOM + H);
                            cs.stroke();
                            cs.moveTo(X, BOTTOM);
                            cs.lineTo(X + end * W, BOTTOM);
                            cs.stroke();
                            cs.moveTo(X, BOTTOM);
                            cs.lineTo(X, BOTTOM + end * H);
                            cs.stroke();
                        }
                        table(cs);
                    }
                    case FLAT_RAW_PANEL -> {
                        cs.drawImage(flatRaw(doc), X, BOTTOM, W, H);
                        cs.addRect(X - 0.1f, BOTTOM - 0.1f, W + 0.2f, H + 0.2f);
                        cs.stroke();
                        table(cs);
                    }
                    case UNREADABLE_PANEL -> {
                        // 40,000 pixels square: 4.8 GB decoded, past what the converter decodes.
                        PDImageXObject huge = new PDImageXObject(doc, new ByteArrayInputStream(new byte[3]), null, 40_000,
                                40_000, 8, PDDeviceRGB.INSTANCE);
                        cs.drawImage(huge, X, BOTTOM, W, H);
                        cs.addRect(X - 0.1f, BOTTOM - 0.1f, W + 0.2f, H + 0.2f);
                        cs.stroke();
                        table(cs);
                    }
                    case CLIPPED_PANEL -> {
                        cs.saveGraphicsState();
                        cs.addRect(X, BOTTOM, W, H);
                        cs.clip();
                        cs.drawImage(LosslessFactory.createFromImage(doc, mapBesidePanel(866, 262)), X - W, BOTTOM, 2 * W, H);
                        cs.restoreGraphicsState();
                        cs.addRect(X - 0.1f, BOTTOM - 0.1f, W + 0.2f, H + 0.2f);
                        cs.stroke();
                        table(cs);
                    }
                    case FLAT_INLINE_PANEL -> {
                        cs.drawImage(flatInline(doc), X, BOTTOM, W, H);
                        cs.addRect(X - 0.1f, BOTTOM - 0.1f, W + 0.2f, H + 0.2f);
                        cs.stroke();
                        table(cs);
                    }
                }
                text(cs, 72, 440, 10, "Figure 2. What the elevation program brings to the state.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("frames.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A ruled table of three rows inside the panel, clear of its edges. */
    private static void table(PDPageContentStream cs) throws IOException {
        String[][] rows = {{"Annual benefits", "$1.68 million"}, {"Quality level 2", "100 percent"}, {"Lidar coverage", "98 percent"}};
        float left = X + 20;
        float right = X + W - 20;
        float mid = left + 120;
        float top = BOTTOM + H - 25;
        float rowH = 18;
        cs.setNonStrokingColor(0f);
        for (int r = 0; r <= rows.length; r++) {
            cs.moveTo(left, top - r * rowH);
            cs.lineTo(right, top - r * rowH);
        }
        for (float x : new float[] {left, mid, right}) {
            cs.moveTo(x, top);
            cs.lineTo(x, top - rows.length * rowH);
        }
        cs.stroke();
        for (int r = 0; r < rows.length; r++) {
            text(cs, left + 4, top - r * rowH - 13, 9, rows[r][0]);
            text(cs, mid + 4, top - r * rowH - 13, 9, rows[r][1]);
        }
    }

    /** A light blue panel drawn as an image of one colour stored without compression, 300 by 180 pixels. */
    private static PDImageXObject flatRaw(PDDocument doc) throws IOException {
        int w = 300;
        int h = 180;
        byte[] data = new byte[w * h * 3];
        for (int i = 0; i < data.length; i += 3) {
            data[i] = (byte) 214;
            data[i + 1] = (byte) 230;
            data[i + 2] = (byte) 242;
        }
        return new PDImageXObject(doc, new ByteArrayInputStream(data), null, w, h, 8, PDDeviceRGB.INSTANCE);
    }

    /** A light blue panel drawn as an inline image of one colour. */
    private static PDInlineImage flatInline(PDDocument doc) throws IOException {
        int w = 8;
        int h = 4;
        byte[] data = new byte[w * h * 3];
        for (int i = 0; i < data.length; i += 3) {
            data[i] = (byte) 214;
            data[i + 1] = (byte) 230;
            data[i + 2] = (byte) 242;
        }
        COSDictionary params = new COSDictionary();
        params.setInt(COSName.W, w);
        params.setInt(COSName.H, h);
        params.setInt(COSName.BPC, 8);
        params.setItem(COSName.CS, COSName.getPDFName("RGB"));
        return new PDInlineImage(params, data, null);
    }

    /** A map in the left half, a light blue panel in the right. */
    private static BufferedImage mapBesidePanel(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(map(w / 2, h), 0, 0, null);
        g.setColor(new Color(214, 230, 242));
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
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
