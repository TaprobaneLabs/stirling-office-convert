package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
 * Light lettering on a dark ground in a scan. A band of it, as at the foot of a CDC fact sheet (two lines that fill the
 * band's height, and a logo's box joined to its end), is one picture from its own left edge holding its words, as a
 * title banner is: before, the masks of its words cut it in two, and it came out as a picture from the middle of its
 * first line, beside the rest of its text set in black on white. A panel of many lines stays text, and so does a
 * table's shaded header row; the headings the OCR missed still show in a picture.
 */
class ScanBandTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SCALE = 150f / 72f;

    private static final Color WHITE_INK = new Color(248, 248, 248);

    private record Words(String text, float x, float baseline, float size) {}

    @TempDir Path dir;

    @Test
    void aBandOfWhiteTextIsOnePictureFromItsEdge() throws Exception {
        float left = 72;
        float right = 476;
        String first = "National Center for Banded Text, Copier Scans, and Other Prevention";
        String second = "Division of Footer Prevention";
        List<Words> words = bodyText();
        words.add(new Words(first, 82, 703, 11));
        words.add(new Words(second, 82, 717, 11));
        Path pdf = scan(g -> {
            g.setColor(new Color(96, 96, 94));
            g.fillRoundRect(px(left), px(688), px(right - left), px(36), px(16), px(16));
            g.setColor(new Color(70, 70, 70));
            g.fillRoundRect(px(472), px(668), px(70), px(76), px(10), px(10));
            g.setColor(WHITE_INK);
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 23));
            g.drawString(first, px(82), px(703));
            g.drawString(second, px(82), px(717));
        }, words);

        String slide = slide(pdf);
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        List<float[]> pictures = pictures(slide);
        assertEquals(1, pictures.size(), "the band and its logo, one picture: " + slide);
        float[] band = pictures.getFirst();
        assertTrue(band[0] <= left + 3, "from the band's own edge, not the middle of its first line: " + band[0]);
        assertTrue(band[0] + band[1] >= right, "to the logo at its end: " + (band[0] + band[1]));
        assertFalse(slide.contains("Division"), "the band's words are in its picture, not set again beside it");
        assertFalse(slide.contains("Copier"), slide);

        String body = document(pdf);
        assertTrue(body.contains("readable OCR layer line 3"), body);
        assertFalse(body.contains("Division"), body);
        assertTrue(widestPicture(body) >= (right - left) * 12700, body);
    }

    /**
     * A banner of white text with the line of the page's frame running down from its end, and a heading beside that
     * line: the banner's picture stops at the banner, and the heading stays text. Kept whole with the line, the picture
     * reached down over the heading and took it (a phone photo of CDC's fact sheet).
     */
    @Test
    void aFramesLineDoesNotStretchABannersPicture() throws Exception {
        // With a short title and no body text, the banner, the line and the heading hold few enough words, and the page
        // few enough parts, for the second pass to take them as one cluster
        for (String title : new String[] {"Banner Title of a Scanned Fact Sheet", "Banner Sheet"}) {
            List<Words> words = title.length() > 12 ? bodyText() : new ArrayList<>();
            words.add(new Words(title, 82, 84, 11));
            words.add(new Words("What is it?", 300, 122, 11));
            String slide = slide(scan(g -> {
                g.setColor(new Color(90, 90, 88));
                g.fillRoundRect(px(72), px(60), px(328), px(38), px(12), px(12));
                g.fillRect(px(396), px(96), px(3), px(40));
                g.setColor(WHITE_INK);
                g.setFont(new Font(Font.SERIF, Font.PLAIN, 23));
                g.drawString(title, px(82), px(84));
                g.setColor(new Color(40, 40, 40));
                g.drawString("What is it?", px(300), px(122));
            }, words));
            assertTrue(slide.contains("What is it?"), title + ": the heading beside the frame's line stays text: " + slide);
            List<float[]> pictures = pictures(slide);
            assertEquals(1, pictures.size(), title + ": the banner: " + slide);
            float foot = pictures.getFirst()[2] + pictures.getFirst()[3];
            assertTrue(foot <= 104, title + ": down to the banner's foot, not the line's: " + foot);
            assertFalse(slide.contains("Banner"), title + ": the banner's words are in its picture");
        }
    }

    /**
     * The same banner and line with dots of a chart beside them instead of a heading, too small for pictures of their
     * own: the second pass's cluster still takes the dots with the banner, as it holds no words beyond the banner's.
     */
    @Test
    void aBannersClusterKeepsArtworkBesideIt() throws Exception {
        String title = "Banner Sheet";
        List<Words> words = new ArrayList<>();
        words.add(new Words(title, 82, 84, 11));
        String slide = slide(scan(g -> {
            g.setColor(new Color(90, 90, 88));
            g.fillRoundRect(px(72), px(60), px(328), px(38), px(12), px(12));
            g.fillRect(px(396), px(96), px(3), px(40));
            g.setColor(new Color(40, 40, 40));
            for (int x = 110; x <= 230; x += 12) {
                for (int y = 104; y <= 140; y += 12) {
                    g.fillRect(px(x + (y % 24) / 2f), px(y), px(6), px(6));
                }
            }
            g.setColor(WHITE_INK);
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 23));
            g.drawString(title, px(82), px(84));
        }, words));
        List<float[]> pictures = pictures(slide);
        assertEquals(1, pictures.size(), "the banner with the chart's dots: " + slide);
        float foot = pictures.getFirst()[2] + pictures.getFirst()[3];
        assertTrue(foot >= 140, "down over the dots: " + foot);
    }

    /** Black type on white paper is still masked, however heavy: a heading stays text, with no picture of it. */
    @Test
    void aHeavyHeadingOnWhiteStaysText() throws Exception {
        List<Words> words = bodyText();
        words.add(new Words("QUARTERLY REPORT", 72, 110, 28));
        String slide = slide(scan(g -> {
            g.setColor(new Color(30, 30, 30));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 62));
            g.drawString("QUARTERLY REPORT", px(72), px(110));
        }, words));
        assertTrue(slide.contains("QUARTERLY"), slide);
        assertTrue(slide.contains("readable OCR layer line 3"), slide);
        assertEquals(List.of(), pictures(slide), slide);
    }

    /**
     * A dark panel of many lines of white text, each across its width (a sidebar), is not a band: its words stay text,
     * as before. Masked, they leave only its thin edges, which make no picture.
     */
    @Test
    void aDarkPanelOfManyLinesStaysText() throws Exception {
        List<Words> words = bodyText();
        for (int i = 0; i < 8; i++) {
            words.add(new Words(panelLine(i), 80, 494 + i * 16, 11));
        }
        String slide = slide(scan(g -> {
            g.setColor(new Color(80, 80, 78));
            g.fillRect(px(72), px(478), px(242), px(136));
            g.setColor(WHITE_INK);
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 23));
            for (int i = 0; i < 8; i++) {
                g.drawString(panelLine(i), px(80), px(494 + i * 16));
            }
        }, words));
        assertTrue(slide.contains("Panel line 2"), slide);
        assertTrue(slide.contains("Panel line 6"), slide);
        assertEquals(List.of(), pictures(slide), "no picture holding the panel's words: " + slide);
    }

    /**
     * A table ruled with hairlines whose header row is shaded dark, with white headings: the table sets its words, and
     * the shading is no picture of its own beside them. (Heavier rules join the shading to the table's grid, too sparse
     * to be a picture.)
     */
    @Test
    void aTablesDarkHeaderRowIsNoPicture() throws Exception {
        String slide = tableSlide(3);
        assertTrue(slide.contains("<a:tbl>"), slide);
        String table = slide.substring(slide.indexOf("<a:tbl>"), slide.indexOf("</a:tbl>"));
        assertTrue(table.contains("Description"), table);
        assertTrue(table.contains("Service item 3"), table);
        assertEquals(List.of(), pictures(slide), "the header row's shading isn't a picture beside its words: " + slide);
    }

    /**
     * The same header row with headings the OCR missed, as it can white words on a halftone, all of them or all but
     * one: the row is still a picture, the one place they show (from after the words it read, which are masked).
     */
    @Test
    void aHeaderRowTheOcrMissedStaysAPicture() throws Exception {
        for (int read : new int[] {0, 1}) {
            String slide = tableSlide(read);
            assertTrue(slide.contains("<a:tbl>"), slide);
            assertTrue(slide.contains("Service item 3"), slide);
            List<float[]> pictures = pictures(slide);
            assertEquals(1, pictures.size(), read + " read; the header row, the one place its headings show: " + slide);
            assertTrue(pictures.getFirst()[1] > 300, "across the row: " + pictures.getFirst()[1]);
        }
    }

    /** A scanned table with a dark header row of white headings, the first {@code headsRead} of them in its OCR layer. */
    private String tableSlide(int headsRead) throws Exception {
        float left = 72;
        float top = 480;
        float[] cols = {0, 60, 300, 400};
        float row = 24;
        List<Words> cells = new ArrayList<>();
        String[] heads = {"No", "Description", "Amount"};
        for (int c = 0; c < 3; c++) {
            cells.add(new Words(heads[c], left + cols[c] + 6, top + 16, 11));
        }
        for (int r = 1; r < 6; r++) {
            String[] texts = {String.valueOf(r), "Service item " + r, r * 10 + ".00"};
            for (int c = 0; c < 3; c++) {
                cells.add(new Words(texts[c], left + cols[c] + 6, top + r * row + 16, 11));
            }
        }
        List<Words> words = bodyText();
        words.addAll(cells.subList(0, headsRead));
        words.addAll(cells.subList(3, cells.size()));
        return slide(scan(g -> {
            g.setColor(new Color(70, 70, 68));
            g.fillRect(px(left), px(top), px(cols[3]), px(row));
            g.setColor(new Color(40, 40, 38));
            g.setStroke(new BasicStroke(1));
            for (int r = 0; r <= 6; r++) {
                g.drawLine(px(left), px(top + r * row), px(left + cols[3]), px(top + r * row));
            }
            for (float c : cols) {
                g.drawLine(px(left + c), px(top), px(left + c), px(top + 6 * row));
            }
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 23));
            for (Words w : cells) {
                g.setColor(w.baseline() < top + row ? WHITE_INK : new Color(40, 40, 40));
                g.drawString(w.text(), px(w.x()), px(w.baseline()));
            }
        }, words));
    }

    /** Twenty lines of body text from the top of the page: a copier scan's ordinary text, on white. */
    private static List<Words> bodyText() {
        List<Words> out = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            out.add(new Words("Body text of a scanned page with a readable OCR layer line " + i, 72, 150 + i * 16, 11));
        }
        return out;
    }

    private static String panelLine(int i) {
        return "Panel line " + i + " of white text set on a dark ground";
    }

    /**
     * A copier scan: the body text drawn on near-white paper when {@code words} hold it, then whatever {@code draw} adds,
     * under an OCR layer of invisible text holding every one of {@code words}.
     */
    private Path scan(Consumer<Graphics2D> draw, List<Words> words) throws IOException {
        BufferedImage img = new BufferedImage(px(612), px(792), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(252, 252, 251));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(40, 40, 40));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        if (words.containsAll(bodyText())) {
            for (Words w : bodyText()) {
                g.drawString(w.text(), px(w.x()), px(w.baseline()));
            }
        }
        draw.accept(g);
        g.dispose();

        Path pdf = dir.resolve("scan.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, img), 0, 0, 612, 792);
                for (Words w : words) {
                    cs.beginText();
                    cs.setRenderingMode(RenderingMode.NEITHER);
                    cs.setFont(FONT, w.size());
                    cs.newLineAtOffset(w.x(), 792 - w.baseline());
                    cs.showText(w.text());
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static int px(float pt) {
        return Math.round(pt * SCALE);
    }

    private String slide(Path pdf) throws Exception {
        Path out = dir.resolve("out.pptx");
        PdfToPptx.convert(pdf, out, PdfToPptx.Options.defaults());
        return entry(out, "ppt/slides/slide1.xml");
    }

    private String document(Path pdf) throws Exception {
        Path out = dir.resolve("out.docx");
        PdfToDocx.convert(pdf, out, PdfToDocx.Options.defaults());
        return entry(out, "word/document.xml");
    }

    private static String entry(Path zip, String name) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            return new String(z.getInputStream(z.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Each picture's left edge, width, top and height on the slide, in points. */
    private static List<float[]> pictures(String slide) {
        List<float[]> out = new ArrayList<>();
        Matcher m = Pattern.compile(
                "<p:pic>.*?<a:off x=\"(-?\\d+)\" y=\"(-?\\d+)\"/><a:ext cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(slide);
        while (m.find()) {
            out.add(new float[] {Long.parseLong(m.group(1)) / 12700f, Long.parseLong(m.group(3)) / 12700f,
                Long.parseLong(m.group(2)) / 12700f, Long.parseLong(m.group(4)) / 12700f});
        }
        return out;
    }

    /** The widest picture's width in a document, in EMU. */
    private static long widestPicture(String body) {
        long widest = 0;
        Matcher m = Pattern.compile("<wp:extent cx=\"(\\d+)\"").matcher(body);
        while (m.find()) {
            widest = Math.max(widest, Long.parseLong(m.group(1)));
        }
        return widest;
    }
}
