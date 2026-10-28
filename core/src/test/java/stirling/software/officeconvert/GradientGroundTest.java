package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        String xml = convert("tint", cs -> {
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
        String xml = convert("header", cs -> {
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

    /** Whether the document has a picture behind its text of about {@code width} by {@code height} points. */
    private static boolean behind(String xml, float width, float height) {
        Matcher anchor = Pattern.compile("<wp:anchor[^>]*behindDoc=\"1\"[^>]*>.*?</wp:anchor>", Pattern.DOTALL).matcher(xml);
        while (anchor.find()) {
            Matcher e = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(anchor.group());
            if (anchor.group().contains("<pic:pic") && e.find()
                    && Math.abs(Long.parseLong(e.group(1)) / 12700f - width) < 2
                    && Math.abs(Long.parseLong(e.group(2)) / 12700f - height) < 2) {
                return true;
            }
        }
        return false;
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
        void draw(PDPageContentStream cs) throws IOException;
    }

    private String convert(String name, Page page) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p = new PDPage(PDRectangle.LETTER);
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                page.draw(cs);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve(name + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
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
