package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A gradient the PDF paints with the {@code sh} operator under text is drawn behind that text. The figure finder left
 * one out when it held running text, or when it met a page-tall strip and their union did: NOAA's GeoXO fact sheet lost
 * its header and footer bands (white type on white) and USGS Fact Sheet 2023-3022 an illustration and a tint.
 */
class GradientGroundTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float W = PDRectangle.LETTER.getWidth();
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final String[] BODY = {
        "American Samoa comprises the easternmost islands of a volcanic island chain in",
        "the South Pacific Ocean. Each island of American Samoa has a unique eruptive history",
        "and a possibility for future eruptions. The Geological Survey collaborates with the",
        "Pago Pago office of the National Oceanic and Atmospheric Administration to keep",
        "residents informed about the hazards that the islands and their volcanoes present."
    };

    @TempDir Path dir;

    @Test
    void aTintBehindRunningTextIsDrawnBehindIt() throws Exception {
        String xml = convert("tint", (doc, cs) -> {
            // a pale blue gradient from 200 pt to 500 pt up the page, five lines of body text on it
            band(cs, 200, 300, new float[] {0.85f, 0.9f, 1f}, new float[] {0.6f, 0.75f, 0.95f});
            body(cs, 72, 450);
        });
        assertTrue(behind(xml, W, 300), "a picture of the tint, the page's width and 300 pt high, behind the text: "
                + extents(xml));
        assertTrue(xml.contains("easternmost islands"), "the text on it is still text");
    }

    @Test
    void aHeaderBandBesideAPageTallStripIsDrawnBehindItsTitle() throws Exception {
        String xml = convert("header", (doc, cs) -> {
            // a strip down the left edge, from 40 pt to 700 pt, that the header band below overlaps
            cs.setNonStrokingColor(0f, 0.58f, 0.79f);
            cs.addRect(0, 40, 60, 660);
            cs.fill();
            band(cs, H - 110, 110, new float[] {0.05f, 0.1f, 0.3f}, new float[] {0.1f, 0.5f, 0.85f});
            cs.setNonStrokingColor(1f, 1f, 1f);
            text(cs, 90, H - 70, 26, "Geostationary Observations");
            cs.setNonStrokingColor(0f, 0f, 0f);
            body(cs, 90, 600);
        });
        assertTrue(behind(xml, W, 110), "a picture of the band, the page's width and 110 pt high, behind the title: "
                + extents(xml));
        assertTrue(xml.contains("Geostationary Observations"), "the title is still text");
    }

    @Test
    void aTranslucentPictureMostlyOffTheGroundIsPaintedOnce() throws Exception {
        // the picture from 150 pt to 230 pt up the page, 30 of its 80 pt on the tint: placed on its own, above it
        Path docx = crossing("off", 150, 80);
        BufferedImage ground = picture(docx, W, 300);
        // 350 pt across and 215 pt up the page, inside both: 285 pt below the tint's top
        Color shown = new Color(ground.getRGB(Math.round(350 / W * ground.getWidth()),
                Math.round(285 / 300f * ground.getHeight())));
        assertTrue(shown.getGreen() > 190 && shown.getBlue() > 220,
                "the ground shows only the tint where the picture, placed above it, crosses it: " + shown);
    }

    @Test
    void aPictureMostlyOnTheGroundIsTakenIn() throws Exception {
        // the picture from 180 pt to 300 pt up the page, 100 of its 120 pt on the tint: the ground grows to take it in
        Path docx = crossing("on", 180, 120);
        BufferedImage ground = picture(docx, W, 320);
        // 350 pt across and 250 pt up the page: 250 pt below the ground's top, now at 500 pt
        Color shown = new Color(ground.getRGB(Math.round(350 / W * ground.getWidth()),
                Math.round(250 / 320f * ground.getHeight())));
        assertTrue(shown.getRed() > 180 && shown.getGreen() < 170, "the ground draws the picture: " + shown);
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(!extents(xml).contains("100x120"), "and it isn't placed again: " + extents(xml));
    }

    @Test
    void aPictureMostlyOffTheGroundWithPaintOverItIsTakenIn() throws Exception {
        // the picture from 150 pt to 230 pt up the page, mostly off the tint, with a green box painted over its part on
        // it, from 205 pt to 225 pt: placed above the ground, the picture would hide the box
        Path docx = crossing("covered", 150, 80, cs -> {
            cs.setNonStrokingColor(0.1f, 0.7f, 0.2f);
            cs.addRect(320, 205, 60, 20);
            cs.fill();
        });
        BufferedImage ground = picture(docx, W, 350);
        // 350 pt across and 215 pt up the page: 285 pt below the ground's top, at 500 pt
        Color shown = new Color(ground.getRGB(Math.round(350 / W * ground.getWidth()),
                Math.round(285 / 350f * ground.getHeight())));
        assertTrue(shown.getGreen() > 150 && shown.getRed() < 80, "the ground draws the box over the picture: " + shown);
    }

    @Test
    void aStripAcrossTheGroundsEdgeWithPaintOverItIsTakenIn() throws Exception {
        Path docx = docx("strip", (doc, cs) -> {
            band(cs, 200, 300, new float[] {0.85f, 0.9f, 1f}, new float[] {0.6f, 0.75f, 0.95f});
            body(cs, 72, 450);
            // a dark strip from 120 pt to 260 pt up the page across the tint's lower edge, then a green box on its part
            // on the tint: as a shape of its own above the ground's picture, the strip would hide the box
            cs.setNonStrokingColor(0.2f, 0.2f, 0.3f);
            cs.addRect(280, 120, 60, 140);
            cs.fill();
            cs.setNonStrokingColor(0.1f, 0.7f, 0.2f);
            cs.addRect(290, 220, 40, 30);
            cs.fill();
        });
        BufferedImage ground = picture(docx, W, 380);
        // 310 pt across and 235 pt up the page: 265 pt below the ground's top, at 500 pt
        Color shown = new Color(ground.getRGB(Math.round(310 / W * ground.getWidth()),
                Math.round(265 / 380f * ground.getHeight())));
        assertTrue(shown.getGreen() > 150 && shown.getRed() < 80, "the ground draws the box over the strip: " + shown);
    }

    @Test
    void anImageDrawnTwiceUnderTwoClipsKeepsTheDrawOnTheGround() throws Exception {
        Path docx = docx("twice", (doc, cs) -> {
            band(cs, 200, 300, new float[] {0.85f, 0.9f, 1f}, new float[] {0.6f, 0.75f, 0.95f});
            body(cs, 72, 450);
            BufferedImage red = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    red.setRGB(x, y, 0xD02020);
                }
            }
            PDImageXObject picture = LosslessFactory.createFromImage(doc, red);
            // one image at 150 to 270 pt up the page, drawn clipped to its part on the tint (220 to 270 pt), which the
            // ground's picture draws, and again clipped mostly off it (150 to 215 pt), which is placed on its own
            for (float[] clip : new float[][] {{220, 50}, {150, 65}}) {
                cs.saveGraphicsState();
                cs.addRect(300, clip[0], 100, clip[1]);
                cs.clip();
                cs.drawImage(picture, 300, 150, 100, 120);
                cs.restoreGraphicsState();
            }
        });
        BufferedImage ground = picture(docx, W, 300);
        // 350 pt across and 245 pt up the page, in the first draw: 255 pt below the tint's top
        Color shown = new Color(ground.getRGB(Math.round(350 / W * ground.getWidth()),
                Math.round(255 / 300f * ground.getHeight())));
        assertTrue(shown.getRed() > 180 && shown.getGreen() < 80, "the ground keeps the draw on it: " + shown);
    }

    @Test
    void overlappingGroundsAreOneThatKeepsAnImageOnBoth() throws Exception {
        Path docx = docx("overlap", (doc, cs) -> {
            band(cs, 200, 300, new float[] {0.85f, 0.9f, 1f}, new float[] {0.6f, 0.75f, 0.95f});
            body(cs, 72, 450);
            // a second tint from 450 pt to 600 pt up the page over the first's top, with a heading on it
            band(cs, 450, 150, new float[] {0.95f, 0.9f, 0.8f}, new float[] {0.9f, 0.8f, 0.6f});
            text(cs, 72, 570, 16, "Volcanoes of the islands");
            // a red picture from 430 pt to 460 pt up the page: inside the first tint, a third of it across the second's
            // lower edge, so the second leaves it to the first; apart, its picture covered it where they overlap
            BufferedImage red = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    red.setRGB(x, y, 0xD02020);
                }
            }
            cs.drawImage(LosslessFactory.createFromImage(doc, red), 300, 430, 100, 30);
        });
        BufferedImage ground = picture(docx, W, 400);
        // 350 pt across and 455 pt up the page, where the two tints overlap: 145 pt below the ground's top, at 600 pt
        Color shown = new Color(ground.getRGB(Math.round(350 / W * ground.getWidth()),
                Math.round(145 / 400f * ground.getHeight())));
        assertTrue(shown.getRed() > 180 && shown.getGreen() < 80, "the one ground keeps the picture: " + shown);
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(!extents(xml).contains("612x150"), "no picture of the second tint covers it: " + extents(xml));
    }

    /**
     * The tint from 200 pt to 500 pt up the page under five lines of text, then a red picture at half opacity 100 pt
     * wide, {@code height} high from {@code bottom}, across its lower edge.
     */
    private Path crossing(String name, float bottom, float height) throws IOException {
        return crossing(name, bottom, height, cs -> {});
    }

    /** As {@link #crossing(String, float, float)}, then {@code over} painted after the picture. */
    private Path crossing(String name, float bottom, float height, Over over) throws IOException {
        return docx(name, (doc, cs) -> {
            band(cs, 200, 300, new float[] {0.85f, 0.9f, 1f}, new float[] {0.6f, 0.75f, 0.95f});
            body(cs, 72, 450);
            BufferedImage red = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    red.setRGB(x, y, 0xD02020);
                }
            }
            PDImageXObject picture = LosslessFactory.createFromImage(doc, red);
            PDExtendedGraphicsState half = new PDExtendedGraphicsState();
            half.setNonStrokingAlphaConstant(0.5f);
            cs.saveGraphicsState();
            cs.setGraphicsStateParameters(half);
            cs.drawImage(picture, 300, bottom, 100, height);
            cs.restoreGraphicsState();
            over.paint(cs);
        });
    }

    private interface Over {
        void paint(PDPageContentStream cs) throws IOException;
    }

    /** Whether the document has a picture behind its text of about {@code width} by {@code height} points. */
    private static boolean behind(String xml, float width, float height) {
        return groundAnchor(xml, width, height) != null;
    }

    private static String groundAnchor(String xml, float width, float height) {
        Matcher anchor = Pattern.compile("<wp:anchor[^>]*behindDoc=\"1\"[^>]*>.*?</wp:anchor>", Pattern.DOTALL).matcher(xml);
        while (anchor.find()) {
            Matcher e = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(anchor.group());
            if (anchor.group().contains("<pic:pic") && e.find()
                    && Math.abs(Long.parseLong(e.group(1)) / 12700f - width) < 2
                    && Math.abs(Long.parseLong(e.group(2)) / 12700f - height) < 2) {
                return anchor.group();
            }
        }
        return null;
    }

    /** The picture of about {@code width} by {@code height} points behind the text of {@code docx}. */
    private static BufferedImage picture(Path docx, float width, float height) throws IOException {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            String anchor = groundAnchor(xml, width, height);
            assertTrue(anchor != null, "a picture behind the text: " + extents(xml));
            Matcher embed = Pattern.compile("r:embed=\"([^\"]+)\"").matcher(anchor);
            assertTrue(embed.find(), "the picture's media");
            String rels = new String(zip.getInputStream(zip.getEntry("word/_rels/document.xml.rels")).readAllBytes(),
                    StandardCharsets.UTF_8);
            Matcher target = Pattern.compile("<Relationship [^>]*Id=\"" + embed.group(1) + "\"[^>]*Target=\"([^\"]+)\"")
                    .matcher(rels);
            assertTrue(target.find(), "the picture's file");
            return javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("word/" + target.group(1))));
        }
    }

    private static List<String> extents(String xml) {
        List<String> found = new ArrayList<>();
        Matcher e = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(xml);
        while (e.find()) {
            found.add(Math.round(Long.parseLong(e.group(1)) / 12700f) + "x" + Math.round(Long.parseLong(e.group(2)) / 12700f));
        }
        return found;
    }

    private interface Page {
        void draw(PDDocument doc, PDPageContentStream cs) throws IOException;
    }

    private Path docx(String name, Page page) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p = new PDPage(PDRectangle.LETTER);
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                page.draw(doc, cs);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve(name + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        return docx;
    }

    private String convert(String name, Page page) throws IOException {
        Path docx = docx(name, page);
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A vertical gradient across the page, painted with {@code sh} inside a clip from {@code y} up {@code height}. */
    private static void band(PDPageContentStream cs, float y, float height, float[] from, float[] to) throws IOException {
        cs.saveGraphicsState();
        cs.addRect(0, y, W, height);
        cs.clip();
        cs.shadingFill(gradient(0, y, 0, y + height, from, to));
        cs.restoreGraphicsState();
    }

    private static void body(PDPageContentStream cs, float x, float y) throws IOException {
        for (int i = 0; i < BODY.length; i++) {
            text(cs, x, y - 14 * i, 10, BODY[i]);
        }
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static PDShading gradient(float x0, float y0, float x1, float y1, float[] from, float[] to) {
        COSDictionary fn = new COSDictionary();
        fn.setInt(COSName.FUNCTION_TYPE, 2);
        COSArray domain = new COSArray();
        domain.add(COSInteger.get(0));
        domain.add(COSInteger.get(1));
        fn.setItem(COSName.DOMAIN, domain);
        fn.setItem(COSName.C0, floats(from));
        fn.setItem(COSName.C1, floats(to));
        fn.setInt(COSName.N, 1);
        PDShadingType2 shading = new PDShadingType2(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE2);
        shading.setColorSpace(PDDeviceRGB.INSTANCE);
        shading.setFunction(new PDFunctionType2(fn));
        shading.setCoords(floats(new float[] {x0, y0, x1, y1}));
        COSArray extend = new COSArray();
        extend.add(COSBoolean.TRUE);
        extend.add(COSBoolean.TRUE);
        shading.setExtend(extend);
        return shading;
    }

    private static COSArray floats(float[] v) {
        COSArray a = new COSArray();
        for (float f : v) {
            a.add(new COSFloat(f));
        }
        return a;
    }
}
