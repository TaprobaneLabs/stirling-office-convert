package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A figure's labels stay text in a Word document: the figure is drawn without its text, and each label is a text box set
 * where the PDF has it, anchored with the picture. Baked into the picture, an infographic's or a map's words were lost to
 * search, copying and editing (USGS fact sheets lost up to 16% of their words). A plain-text output reads them once, from
 * the picture's description.
 */
class FigureLabelsTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    /** A pie chart at (cx, cy) with radius r and three labels, each at its own place. */
    private record Chart(float cx, float cy, float r, String[] labels, float[][] at) {}

    private static final Chart MIDDLE = new Chart(300, 520, 120, new String[] {"North 40%", "South 35%", "West 25%"},
            new float[][] {{320, 560}, {210, 490}, {320, 450}});

    private static final String[] LEGEND = {"Pacific", "Atlantic"};

    @TempDir Path dir;

    @Test
    void aChartsLabelsAreTextBoxesWhereThePdfHasThem() throws Exception {
        String xml = docx(List.of(MIDDLE), true);
        assertLabelBoxes(xml, MIDDLE);
        assertTrue(xml.matches("(?s).*<wp:docPr [^>]*descr=\"[^\"]*North 40%.*"), "the chart is still a picture described by its words");
    }

    @Test
    void plainTextReadsEachLabelOnce() throws Exception {
        Path txt = dir.resolve("chart.txt");
        PdfToText.convert(pdf(List.of(MIDDLE), true), txt, PdfToDocx.Options.defaults());
        String text = Files.readString(txt, StandardCharsets.UTF_8);
        for (String label : MIDDLE.labels()) {
            assertEquals(1, count(text, label), label + " once in the text");
        }
    }

    @Test
    void aChartAtThePageFootKeepsItsLabelsAsText() throws Exception {
        Chart foot = new Chart(300, 200, 110, new String[] {"East 50%", "Coast 30%", "Inland 20%"},
                new float[][] {{320, 240}, {210, 170}, {320, 130}});
        assertLabelBoxes(docx(List.of(foot), false), foot);
    }

    @Test
    void chartsSideBySideKeepTheirLabelsAsText() throws Exception {
        Chart left = new Chart(170, 520, 90, new String[] {"Ports 60%", "Roads 40%"}, new float[][] {{175, 550}, {100, 490}});
        Chart right = new Chart(430, 520, 90, new String[] {"Rail 55%", "Air 45%"}, new float[][] {{435, 550}, {360, 490}});
        String xml = docx(List.of(left, right), true);
        assertLabelBoxes(xml, left);
        assertLabelBoxes(xml, right);
    }

    @Test
    void aLegendsSwatchesStayInItsPicture() throws Exception {
        String xml = docx(List.of(MIDDLE), true, Keys.SWATCHES);
        assertLabelBoxes(xml, MIDDLE);
        List<String> texts = new ArrayList<>();
        for (String box : textBoxes(xml)) {
            texts.add(box.replaceAll("<[^>]+>", ""));
        }
        for (String key : LEGEND) {
            assertTrue(texts.stream().anyMatch(t -> t.contains(key)), key + " is a text box");
        }
        assertTrue(texts.stream().noneMatch(t -> t.contains("▪") || t.contains("•")),
                "no label repeats a swatch the picture paints: " + texts);
    }

    @Test
    void anIconFontsKeysStayWithTheirLabels() throws Exception {
        // A picture drawn without its text leaves out an icon font's glyphs too, so the labels must carry them.
        String xml = docx(List.of(MIDDLE), true, Keys.ICONS);
        assertLabelBoxes(xml, MIDDLE);
        assertTrue(textBoxes(xml).stream().filter(b -> b.contains("<pic:pic")).count() >= LEGEND.length,
                "each key is a picture in a label");
    }

    private static List<String> textBoxes(String xml) {
        List<String> boxes = new ArrayList<>();
        Matcher box = Pattern.compile("<w:txbxContent>(.*?)</w:txbxContent>", Pattern.DOTALL).matcher(xml);
        while (box.find()) {
            boxes.add(box.group(1));
        }
        return boxes;
    }

    @Test
    void aChartWithTurnedTextKeepsItsLevelLabelsAsText() throws Exception {
        // A label set at an angle stays drawn in the picture; the level ones are text boxes as before.
        String xml = docx(List.of(MIDDLE), true, Keys.TURNED);
        assertLabelBoxes(xml, MIDDLE);
        assertTrue(!xml.matches("(?s).*<w:txbxContent>(?:(?!</w:txbxContent>).)*Coastal zone.*"), "the turned label isn't a text box");
        // The picture still draws it, in the corner of the chart's box below and left of the circle.
        int dark = darkInCorner(picture(dir.resolve("chart.docx"), "North 40%"));
        assertTrue(dark > 20, "the turned label is drawn in the picture: " + dark + " dark pixels");
    }

    @Test
    void aChartWithAVerticalColumnKeepsAllItsTextInItsPicture() throws Exception {
        // Upright vertical text is read level but moved to the turned text, so a picture drawing only turned text would
        // leave it out, and nothing else sets it. Such a figure keeps its text in its picture, as before labels.
        docx(List.of(MIDDLE), true, Keys.VERTICAL);
        int dark = darkInCorner(picture(dir.resolve("chart.docx"), "North 40%"));
        assertTrue(dark > 20, "the vertical column is drawn in the picture: " + dark + " dark pixels");
    }

    /** Dark pixels in the lower left fifth of an image, where the first chart's keys sit. */
    private static int darkInCorner(BufferedImage chart) {
        int dark = 0;
        int corner = chart.getWidth() / 5;
        for (int y = chart.getHeight() - corner; y < chart.getHeight(); y++) {
            for (int x = 0; x < corner; x++) {
                int rgb = chart.getRGB(x, y);
                dark += (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF) < 300 ? 1 : 0;
            }
        }
        return dark;
    }

    /** The image of the picture whose description holds {@code words}. */
    private static BufferedImage picture(Path docx, String words) throws IOException {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            String rels = new String(zip.getInputStream(zip.getEntry("word/_rels/document.xml.rels")).readAllBytes(),
                    StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("descr=\"[^\"]*" + Pattern.quote(words) + "(?:(?!</wp:inline>|</wp:anchor>).)*?r:embed=\"([^\"]+)\"",
                    Pattern.DOTALL).matcher(xml);
            assertTrue(m.find(), "a picture described by " + words);
            Matcher target = Pattern.compile("Id=\"" + Pattern.quote(m.group(1)) + "\"[^>]*Target=\"([^\"]+)\"").matcher(rels);
            assertTrue(target.find(), "its media");
            return ImageIO.read(zip.getInputStream(zip.getEntry("word/" + target.group(1))));
        }
    }

    /** Each label is a text box set within 2 pt of its PDF place across the page. */
    private static void assertLabelBoxes(String xml, Chart chart) {
        List<String> boxes = new ArrayList<>();
        Matcher m = Pattern.compile("<wp:anchor .*?</wp:anchor>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            if (m.group().contains("<w:txbxContent>")) {
                boxes.add(m.group());
            }
        }
        for (int i = 0; i < chart.labels().length; i++) {
            String label = chart.labels()[i];
            String box = boxes.stream().filter(b -> b.contains(label)).findFirst().orElse(null);
            assertTrue(box != null, label + " is a text box");
            Matcher h = Pattern.compile("<wp:positionH relativeFrom=\"page\"><wp:posOffset>(-?\\d+)</wp:posOffset>").matcher(box);
            assertTrue(h.find(), "set across the page");
            assertEquals(chart.at()[i][0], Long.parseLong(h.group(1)) / 12700f, 2f, label + " where the PDF has it across the page");
        }
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }

    private String docx(List<Chart> charts, boolean textBelow) throws IOException {
        return docx(charts, textBelow, Keys.NONE);
    }

    private String docx(List<Chart> charts, boolean textBelow, Keys keys) throws IOException {
        Path docx = dir.resolve("chart.docx");
        PdfToDocx.convert(pdf(charts, textBelow, keys), docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path pdf(List<Chart> charts, boolean textBelow) throws IOException {
        return pdf(charts, textBelow, Keys.NONE);
    }

    /**
     * What the first chart's lower left corner holds: nothing, a legend whose keys are drawn swatches or an icon font's
     * glyphs, a label at an angle, or a column of ideographs set one under another (upright vertical text).
     */
    private enum Keys { NONE, SWATCHES, ICONS, TURNED, VERTICAL }

    /** The ideographs of the column in the chart's corner, set in Helvetica's A to D through a ToUnicode map. */
    private static final String COLUMN = "\u4EBA\u751F\u81EA\u7531";

    private Path pdf(List<Chart> charts, boolean textBelow, Keys keys) throws IOException {
        Path pdf = dir.resolve("chart.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont icons = keys == Keys.ICONS ? iconFont(doc) : null;
            PDFont ideographs = keys == Keys.VERTICAL ? ideographFont(doc) : null;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 760, 14, "Sales by region");
                text(cs, 72, 740, 11, "The charts split the year's sales and freight between regions and routes.");
                float[][] colours = {{0.2f, 0.4f, 0.8f}, {0.9f, 0.5f, 0.1f}, {0.3f, 0.7f, 0.3f}};
                for (Chart c : charts) {
                    float start = 0;
                    int n = c.labels().length;
                    for (int i = 0; i < n; i++) {
                        float share = i + 1 < n ? 0.9f / n + 0.1f * (n - 1 - i) / n : 0;
                        float end = i + 1 < n ? start + share * 360 : 360;
                        cs.setNonStrokingColor(colours[i][0], colours[i][1], colours[i][2]);
                        wedge(cs, c.cx(), c.cy(), c.r(), start, end);
                        cs.fill();
                        start = end;
                    }
                    cs.setNonStrokingColor(1f);
                    for (int i = 0; i < n; i++) {
                        text(cs, c.at()[i][0], c.at()[i][1], 10, c.labels()[i]);
                    }
                    cs.setNonStrokingColor(0f);
                    if (keys == Keys.VERTICAL && c == charts.getFirst()) {
                        for (int i = 0; i < COLUMN.length(); i++) {
                            text(cs, ideographs, c.cx() - c.r() + 4, c.cy() - c.r() + 40 - 10 * i, 9, String.valueOf((char) ('A' + i)));
                        }
                    } else if (keys == Keys.TURNED && c == charts.getFirst()) {
                        cs.beginText();
                        cs.setFont(FONT, 9);
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(50), c.cx() - c.r() + 8, c.cy() - c.r() + 8));
                        cs.showText("Coastal zone");
                        cs.endText();
                    } else if (keys != Keys.NONE && c == charts.getFirst()) {
                        // In the corner of the chart's box, clear of the circle.
                        for (int i = 0; i < LEGEND.length; i++) {
                            float y = c.cy() - c.r() + 4 + 10 * (LEGEND.length - 1 - i);
                            cs.setNonStrokingColor(colours[i][0], colours[i][1], colours[i][2]);
                            if (icons != null) {
                                text(cs, icons, c.cx() - c.r() + 2, y, 8, "\u25A0");
                            } else {
                                cs.addRect(c.cx() - c.r() + 2, y, 5, 5);
                                cs.fill();
                            }
                            cs.setNonStrokingColor(0f);
                            text(cs, c.cx() - c.r() + 12, y, 8, LEGEND[i]);
                        }
                    }
                }
                if (textBelow) {
                    text(cs, 72, 360, 11, "Figures are for the calendar year and exclude returns.");
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static void wedge(PDPageContentStream cs, float cx, float cy, float r, float from, float to) throws IOException {
        cs.moveTo(cx, cy);
        cs.lineTo(cx + r * (float) Math.cos(Math.toRadians(from)), cy + r * (float) Math.sin(Math.toRadians(from)));
        for (float a = from; a < to; a += 30) {
            float b = Math.min(to, a + 30);
            double a0 = Math.toRadians(a);
            double a1 = Math.toRadians(b);
            float k = (float) (4.0 / 3.0 * Math.tan((a1 - a0) / 4));
            float x0 = cx + r * (float) Math.cos(a0);
            float y0 = cy + r * (float) Math.sin(a0);
            float x3 = cx + r * (float) Math.cos(a1);
            float y3 = cy + r * (float) Math.sin(a1);
            cs.curveTo(x0 - k * r * (float) Math.sin(a0), y0 + k * r * (float) Math.cos(a0),
                    x3 + k * r * (float) Math.sin(a1), y3 - k * r * (float) Math.cos(a1), x3, y3);
        }
        cs.closePath();
    }

    /** PDFBox's own Liberation Sans, named as an icon font is, so its glyphs are read as icons. */
    private static PDFont iconFont(PDDocument doc) throws IOException {
        try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(doc, in, false);
            font.getCOSObject().setName(COSName.BASE_FONT, "FontAwesome");
            return font;
        }
    }

    /** A Helvetica whose A to D read as {@link #COLUMN}'s ideographs, so no CJK font is needed. */
    private static PDFont ideographFont(PDDocument doc) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        StringBuilder map = new StringBuilder("/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n")
                .append("/CMapName /Adobe-Identity-UCS def /CMapType 2 def\n1 begincodespacerange <00> <FF> endcodespacerange\n")
                .append(COLUMN.length()).append(" beginbfchar\n");
        for (int i = 0; i < COLUMN.length(); i++) {
            map.append(String.format("<%02X> <%04X>\n", 'A' + i, (int) COLUMN.charAt(i)));
        }
        map.append("endbfchar\nendcmap CMapName currentdict /CMap defineresource pop end end\n");
        COSStream cmap = doc.getDocument().createCOSStream();
        try (OutputStream os = cmap.createOutputStream(COSName.FLATE_DECODE)) {
            os.write(map.toString().getBytes(StandardCharsets.US_ASCII));
        }
        font.getCOSObject().setItem(COSName.TO_UNICODE, cmap);
        return font;
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        text(cs, FONT, x, y, size, s);
    }

    private static void text(PDPageContentStream cs, PDFont font, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
