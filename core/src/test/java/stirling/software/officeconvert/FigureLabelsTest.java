package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        Path docx = dir.resolve("chart.docx");
        PdfToDocx.convert(pdf(charts, textBelow), docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path pdf(List<Chart> charts, boolean textBelow) throws IOException {
        Path pdf = dir.resolve("chart.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
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

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
