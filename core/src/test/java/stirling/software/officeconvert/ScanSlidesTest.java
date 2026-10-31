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
import org.apache.pdfbox.util.Matrix;
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

    /**
     * An OCR layer set smaller than the scan's type and stretched to its word boxes (8 points at 137%, as OCRmyPDF's
     * come out after OcrWords): its words take that stretch as size, not as letter spacing.
     */
    @Test
    void aStretchedOcrLayerTakesItsStretchAsSize() throws Exception {
        Path pdf = dir.resolve("stretched.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, textOnly()), 0, 0, 612, 792);
                for (int i = 0; i < 20; i++) {
                    cs.beginText();
                    cs.setRenderingMode(RenderingMode.NEITHER);
                    cs.setFont(FONT, 8);
                    cs.setTextMatrix(new Matrix(1.37f, 0, 0, 1, 72, 792 - (120 + i * 16)));
                    cs.showText(line(i));
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        String slide = slide(pdf);
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        Matcher run = Pattern.compile("<a:rPr[^>]*\\bsz=\"(\\d+)\"[^>]*>").matcher(slide);
        int runs = 0;
        while (run.find()) {
            runs++;
            assertEquals("1100", run.group(1), "8 points stretched 137% set at 11: " + run.group());
            Matcher spc = Pattern.compile(" spc=\"(-?\\d+)\"").matcher(run.group());
            assertTrue(!spc.find() || Math.abs(Integer.parseInt(spc.group(1))) < 30,
                    "no letter spacing to speak of: " + run.group());
        }
        assertTrue(runs > 0, slide);
    }

    /**
     * A ruled table on a scan whose cells' words are stretched by different amounts, as OCR boxes vary: every cell takes
     * the table's size, so the rows don't jump from one size to another, and no cell's letters are spread apart.
     */
    @Test
    void aScannedTablesCellsShareOneSize() throws Exception {
        float left = 72;
        float top = 150;
        float[] cols = {0, 60, 260, 340};
        float row = 20;
        BufferedImage img = whitePaper();
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 40, 38));
        g.setStroke(new java.awt.BasicStroke(3));
        for (int r = 0; r <= 6; r++) {
            g.drawLine(px(left), px(top + r * row), px(left + cols[3]), px(top + r * row));
        }
        for (float c : cols) {
            g.drawLine(px(left + c), px(top), px(left + c), px(top + 6 * row));
        }
        g.dispose();
        float[] stretch = {1.2f, 1.37f, 1.55f};
        Path pdf = dir.resolve("table.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, img), 0, 0, 612, 792);
                for (int r = 0; r < 6; r++) {
                    float base = 792 - (top + r * row + 14);
                    String[] cells = {String.valueOf(r + 1), "Service item " + (r + 1), (r + 1) * 10 + ".00"};
                    for (int c = 0; c < 3; c++) {
                        stretched(cs, left + cols[c] + 4, base, stretch[(r + c) % 3], cells[c]);
                    }
                }
                for (int i = 0; i < 10; i++) {
                    stretched(cs, 72, 792 - (330 + i * 16), 1.37f, line(i));
                }
            }
            doc.save(pdf.toFile());
        }
        String slide = slide(pdf);
        assertTrue(slide.contains("<a:tbl>"), slide);
        assertTrue(slide.contains("Service item 3"), slide);
        String table = slide.substring(slide.indexOf("<a:tbl>"), slide.indexOf("</a:tbl>"));
        Matcher run = Pattern.compile("<a:rPr[^>]*\\bsz=\"(\\d+)\"").matcher(table);
        java.util.Set<String> sizes = new java.util.TreeSet<>();
        while (run.find()) {
            sizes.add(run.group(1));
        }
        assertEquals(java.util.Set.of("1100"), sizes, "8 points at the table's 137%: " + table);
        Matcher spc = Pattern.compile(" spc=\"\\d+\"").matcher(table);
        assertFalse(spc.find(), "a cell's letters aren't spread: " + table);
    }

    /** A table printed on a page whose other text is an OCR layer keeps its sizes: only OCR text is grown. */
    @Test
    void aPrintedTableOnAnOcrPageKeepsItsSize() throws Exception {
        float left = 72;
        float top = 450;
        float[] cols = {0, 60, 260, 340};
        float row = 20;
        Path pdf = dir.resolve("printed-table.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, textOnly()), 0, 0, 612, 792);
                for (int i = 0; i < 20; i++) {
                    stretched(cs, 72, 792 - (120 + i * 16), 1.37f, line(i));
                }
                cs.setLineWidth(1);
                for (int r = 0; r <= 4; r++) {
                    cs.moveTo(left, 792 - (top + r * row));
                    cs.lineTo(left + cols[3], 792 - (top + r * row));
                }
                for (float c : cols) {
                    cs.moveTo(left + c, 792 - top);
                    cs.lineTo(left + c, 792 - (top + 4 * row));
                }
                cs.stroke();
                for (int r = 0; r < 4; r++) {
                    float base = 792 - (top + r * row + 14);
                    String[] cells = {String.valueOf(r + 1), "Printed item " + (r + 1), (r + 1) * 10 + ".00"};
                    for (int c = 0; c < 3; c++) {
                        cs.beginText();
                        cs.setFont(FONT, 9);
                        cs.newLineAtOffset(left + cols[c] + 4, base);
                        cs.showText(cells[c]);
                        cs.endText();
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        String slide = slide(pdf);
        assertTrue(slide.contains("<a:tbl>"), slide);
        String table = slide.substring(slide.indexOf("<a:tbl>"), slide.indexOf("</a:tbl>"));
        assertTrue(table.contains("Printed item 3"), table);
        Matcher run = Pattern.compile("<a:rPr[^>]*\\bsz=\"(\\d+)\"").matcher(table);
        java.util.Set<String> sizes = new java.util.TreeSet<>();
        while (run.find()) {
            sizes.add(run.group(1));
        }
        assertEquals(java.util.Set.of("900"), sizes, "the printed table's 9 points: " + table);
        assertTrue(slide.contains("sz=\"1100\""), "the OCR text around it is still grown: " + slide);
    }

    private static void stretched(PDPageContentStream cs, float x, float y, float scale, String s) throws IOException {
        cs.beginText();
        cs.setRenderingMode(RenderingMode.NEITHER);
        cs.setFont(FONT, 8);
        cs.setTextMatrix(new Matrix(scale, 0, 0, 1, x, y));
        cs.showText(s);
        cs.endText();
    }

    /** An OCR layer left white: never seen over its scan, it would be white on white without it. */
    @Test
    void anOcrLayerLeftWhiteComesOutBlack() throws Exception {
        Path pdf = page(textOnly(), true, 120, 20, Color.WHITE);
        String slide = slide(pdf);
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        assertFalse(slide.contains("FFFFFF"), slide);
        Path docx = dir.resolve("out.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String body = entry(docx, "word/document.xml");
        assertTrue(body.contains("readable OCR layer line 3"), body);
        assertFalse(body.contains("FFFFFF"), body);
    }

    /** An OCR layer in the scan's own colour (some OCR tools take it from the scan): kept, as it reads on white. */
    @Test
    void anOcrLayerInAColourKeepsIt() throws Exception {
        Path pdf = page(textOnly(), true, 120, 20, new Color(0x1F, 0x4E, 0x9A));
        assertTrue(slide(pdf).contains("1F4E9A"));
        Path docx = dir.resolve("out.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        assertTrue(entry(docx, "word/document.xml").contains("1F4E9A"));
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

    private Path page(BufferedImage img, boolean ocr, float top, int count) throws IOException {
        return page(img, ocr, top, count, Color.BLACK);
    }

    /** The scan, and its lines as text in {@code colour}: invisible over it when {@code ocr}, else drawn. */
    private Path page(BufferedImage img, boolean ocr, float top, int count, Color colour) throws IOException {
        Path pdf = dir.resolve(ocr ? "scan.pdf" : "picture.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, img), 0, 0, 612, 792);
                cs.setNonStrokingColor(colour);
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
