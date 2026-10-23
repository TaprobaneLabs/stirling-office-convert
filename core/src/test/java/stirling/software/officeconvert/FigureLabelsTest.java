package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A figure's labels stay text in a Word document: the figure is drawn without its text, and each label is a text box set
 * where the PDF has it, anchored with the picture. Baked into the picture, an infographic's or a map's words were lost to
 * search, copying and editing (USGS fact sheets lost up to 16% of their words).
 */
class FigureLabelsTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[] LABELS = {"North 40%", "South 35%", "West 25%"};
    private static final float[][] AT = {{320, 560}, {210, 490}, {320, 450}};

    @TempDir Path dir;

    @Test
    void aChartsLabelsAreTextBoxesWhereThePdfHasThem() throws Exception {
        String xml = convert();
        List<String> boxes = new ArrayList<>();
        Matcher m = Pattern.compile("<wp:anchor .*?</wp:anchor>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            if (m.group().contains("<w:txbxContent>")) {
                boxes.add(m.group());
            }
        }
        for (int i = 0; i < LABELS.length; i++) {
            String label = LABELS[i];
            String box = boxes.stream().filter(b -> b.contains(label)).findFirst().orElse(null);
            assertTrue(box != null, label + " is a text box");
            Matcher h = Pattern.compile("<wp:positionH relativeFrom=\"page\"><wp:posOffset>(-?\\d+)</wp:posOffset>").matcher(box);
            assertTrue(h.find(), "set across the page");
            assertEquals(AT[i][0], Long.parseLong(h.group(1)) / 12700f, 2f, label + " where the PDF has it across the page");
        }
        assertTrue(xml.matches("(?s).*<wp:docPr [^>]*descr=\"[^\"]*North 40%.*"), "the chart is still a picture described by its words");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("chart.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 760, 14, "Sales by region");
                text(cs, 72, 740, 11, "The chart below splits the year's sales between three regions.");
                float cx = 300;
                float cy = 520;
                float r = 120;
                float[][] colours = {{0.2f, 0.4f, 0.8f}, {0.9f, 0.5f, 0.1f}, {0.3f, 0.7f, 0.3f}};
                float start = 0;
                float[] shares = {0.40f, 0.35f, 0.25f};
                for (int i = 0; i < shares.length; i++) {
                    cs.setNonStrokingColor(colours[i][0], colours[i][1], colours[i][2]);
                    wedge(cs, cx, cy, r, start, start + shares[i] * 360);
                    cs.fill();
                    start += shares[i] * 360;
                }
                cs.setNonStrokingColor(1f);
                for (int i = 0; i < LABELS.length; i++) {
                    text(cs, AT[i][0], AT[i][1], 10, LABELS[i]);
                }
                cs.setNonStrokingColor(0f);
                text(cs, 72, 360, 11, "Figures are for the calendar year and exclude returns.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("chart.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
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
