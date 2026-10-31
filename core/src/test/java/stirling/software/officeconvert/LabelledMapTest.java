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
 * USGS Fact Sheet 2024-3029's first page: a map of Africa on a light panel, its countries named in short labels, beside a
 * column of prose on a grey L-shaped panel that the map's panel overlaps. The map's panel and its labels became one text
 * box, and the map, grown into the grey panel's prose, was dropped as prose: Word showed the place names on an empty
 * panel.
 */
class LabelledMapTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final String[][] LABELS = {
        {"MALI", "250", "250"}, {"NIGER", "350", "236"}, {"CHAD", "455", "262"}, {"GUINEA", "220", "305"},
        {"NIGERIA", "330", "320"}, {"SUDAN", "480", "330"}, {"GHANA", "262", "372"}, {"GABON", "352", "402"},
        {"KENYA", "500", "395"}, {"ANGOLA", "300", "470"}, {"ZAMBIA", "415", "455"}, {"NAMIBIA", "355", "530"},
    };

    @TempDir Path dir;

    @Test
    void aMapNamedByItsLabelsIsOnePicture() throws Exception {
        Path pdf = makePdf();
        Path docx = dir.resolve("map.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, count(xml, "<pic:pic>"), "the map is one picture");
        assertTrue(xml.contains("descr=\"") && Pattern.compile("descr=\"[^\"]*NIGERIA").matcher(xml).find(),
                "the picture is the map, described by its labels");
        StringBuilder text = new StringBuilder();
        Matcher m = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>").matcher(xml);
        while (m.find()) {
            text.append(m.group(1)).append(' ');
        }
        for (String[] label : LABELS) {
            assertTrue(text.toString().contains(label[0]), label[0] + " is kept as text");
        }
        Matcher box = Pattern.compile("<w:txbxContent>(.*?)</w:txbxContent>", Pattern.DOTALL).matcher(xml);
        while (box.find()) {
            int labels = 0;
            for (String[] label : LABELS) {
                labels += box.group(1).contains(">" + label[0] + "<") ? 1 : 0;
            }
            assertTrue(labels <= 2, "the labels aren't one text box over an empty panel (" + labels + " in one)");
        }
        assertTrue(text.toString().contains("minerals"), "the column's prose stays text");
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }

    private Path makePdf() throws IOException {
        Path pdf = dir.resolve("map.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // The prose's grey panel, an L around the map's panel: two fills, one on the other
                cs.setNonStrokingColor(0.78f);
                rect(cs, 30, 150, 300, 280);
                rect(cs, 30, 280, 180, 700);
                // The map's light panel, over the grey one's corner
                cs.setNonStrokingColor(0.94f);
                rect(cs, 190, 205, 570, 625);
                // The countries: 64 outlines with slanted sides, touching
                cs.setNonStrokingColor(0.85f, 0.75f, 0.6f);
                cs.setStrokingColor(0.4f);
                cs.setLineWidth(0.5f);
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        country(cs, 210 + 42.5f * col, 215 + 42.5f * row);
                    }
                }
                cs.setNonStrokingColor(0f);
                for (String[] label : LABELS) {
                    text(cs, Float.parseFloat(label[1]), Float.parseFloat(label[2]), 7, label[0]);
                }
                text(cs, 200, 590, 6, "Base from Natural Earth");
                text(cs, 200, 598, 6, "Web Mercator projection");
                float top = 165;
                for (String line : column()) {
                    text(cs, 36, top, 9, line);
                    top += 12.5f;
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** About 40 lines of prose, each short enough to end before the map's panel. */
    private static List<String> column() {
        String words = ("Africa is emerging as a leading source for minerals used in the manufacture of batteries for "
                + "electric vehicles and in other renewable energy applications. New mines have or could be opened in "
                + "many countries from 2017 through 2026. ").repeat(4);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words.trim().split(" ")) {
            if (line.length() + w.length() + 1 > 27) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(w);
        }
        lines.add(line.toString());
        return lines.subList(0, Math.min(lines.size(), 42));
    }

    private static void country(PDPageContentStream cs, float x, float top) throws IOException {
        cs.moveTo(x + 1, PAGE_HEIGHT - (top + 8));
        cs.lineTo(x + 20, PAGE_HEIGHT - (top + 1));
        cs.lineTo(x + 41.5f, PAGE_HEIGHT - (top + 12));
        cs.lineTo(x + 36, PAGE_HEIGHT - (top + 41.5f));
        cs.lineTo(x + 6, PAGE_HEIGHT - (top + 36));
        cs.closePath();
        cs.fillAndStroke();
    }

    private static void rect(PDPageContentStream cs, float x, float top, float right, float bottom) throws IOException {
        cs.addRect(x, PAGE_HEIGHT - bottom, right - x, bottom - top);
        cs.fill();
    }

    private static void text(PDPageContentStream cs, float x, float top, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, PAGE_HEIGHT - top);
        cs.showText(s);
        cs.endText();
    }
}
