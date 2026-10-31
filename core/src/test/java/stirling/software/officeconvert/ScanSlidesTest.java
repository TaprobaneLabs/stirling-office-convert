package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Slides from a scanned page with an OCR layer: the OCR text is the slide's text, visible, and the scan under it isn't
 * drawn, as in documents. The pictures inside the scan are still pictures, and a page picture under text that shows
 * stays.
 */
class ScanSlidesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SCALE = 150f / 72f;

    /** A US Letter slide's width in EMU. */
    private static final long SLIDE_WIDTH = 612L * 12700;

    @TempDir Path dir;

    @Test
    void aScansOcrTextShowsInsteadOfTheScan() throws Exception {
        String slide = slide(page(textOnly(), true));
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        assertTrue(slide.contains("readable OCR layer line 17"), slide);
        assertFalse(slide.contains("<a:alpha val=\"0\"/>"), "the OCR text shows: " + slide);
        assertEquals(List.of(), pictureWidths(slide), "the scan isn't drawn under its own words");
    }

    @Test
    void aPhotoInAScanStaysAPicture() throws Exception {
        BufferedImage img = whitePaper();
        Graphics2D g = img.createGraphics();
        for (int y = px(110); y < px(290); y++) {
            int shade = 70 + (y - px(110)) * 60 / px(180);
            g.setColor(new Color(shade, shade - 4, shade - 8));
            g.drawLine(px(72), y, px(340), y);
        }
        lines(g, 330, 12);
        g.dispose();
        String slide = slide(page(img, true, 330, 12));
        assertTrue(slide.contains("readable OCR layer line 6"), slide);
        assertFalse(slide.contains("<a:alpha val=\"0\"/>"), slide);
        List<Long> widths = pictureWidths(slide);
        assertEquals(1, widths.size(), "the photo is a picture: " + slide);
        assertTrue(widths.getFirst() < SLIDE_WIDTH / 2, "cut from the scan, not the whole page: " + widths);
    }

    @Test
    void aPagePictureUnderTextThatShowsStays() throws Exception {
        String slide = slide(page(textOnly(), false));
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        List<Long> widths = pictureWidths(slide);
        assertEquals(1, widths.size(), slide);
        assertTrue(widths.getFirst() > SLIDE_WIDTH * 9 / 10, "the page's picture: " + widths);
    }

    @Test
    void impressSlidesLeaveTheScanOutToo() throws Exception {
        Path pdf = page(textOnly(), true);
        Path odp = dir.resolve("out.odp");
        PdfToOdp.convert(pdf, odp, PdfToPptx.Options.defaults());
        String content = entry(odp, "content.xml");
        assertTrue(content.contains("readable OCR layer line 3"), content);
        assertFalse(content.contains("<draw:image"), content);
    }

    private static BufferedImage textOnly() {
        BufferedImage img = whitePaper();
        Graphics2D g = img.createGraphics();
        lines(g, 120, 20);
        g.dispose();
        return img;
    }

    /** Paper as a copier scans it: near white, with the light a little uneven. */
    private static BufferedImage whitePaper() {
        BufferedImage img = new BufferedImage(px(612), px(792), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < img.getHeight(); y++) {
            int level = 253 - 4 * y / img.getHeight();
            g.setColor(new Color(level, level, level));
            g.drawLine(0, y, img.getWidth(), y);
        }
        g.dispose();
        return img;
    }

    private static void lines(Graphics2D g, float top, int count) {
        g.setColor(new Color(40, 40, 40));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        for (int i = 0; i < count; i++) {
            g.drawString(line(i), px(72), px(top + i * 16));
        }
    }

    private static String line(int i) {
        return "Body text of a scanned page with a readable OCR layer line " + i;
    }

    private static int px(float pt) {
        return Math.round(pt * SCALE);
    }

    private Path page(BufferedImage img, boolean ocr) throws IOException {
        return page(img, ocr, 120, 20);
    }

    /** The scan, and its lines as text: invisible over it when {@code ocr}, else drawn. */
    private Path page(BufferedImage img, boolean ocr, float top, int count) throws IOException {
        Path pdf = dir.resolve(ocr ? "scan.pdf" : "picture.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, img), 0, 0, 612, 792);
                for (int i = 0; i < count; i++) {
                    cs.beginText();
                    cs.setRenderingMode(ocr ? RenderingMode.NEITHER : RenderingMode.FILL);
                    cs.setFont(FONT, 11);
                    cs.newLineAtOffset(72, 792 - (top + i * 16));
                    cs.showText(line(i));
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private String slide(Path pdf) throws Exception {
        Path pptx = dir.resolve("out.pptx");
        PdfToPptx.convert(pdf, pptx, PdfToPptx.Options.defaults());
        return entry(pptx, "ppt/slides/slide1.xml");
    }

    private static String entry(Path zip, String name) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            return new String(z.getInputStream(z.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Each picture's width on the slide, in EMU. */
    private static List<Long> pictureWidths(String slide) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("<p:pic>.*?<a:ext cx=\"(\\d+)\"").matcher(slide);
        while (m.find()) {
            out.add(Long.parseLong(m.group(1)));
        }
        return out;
    }
}
