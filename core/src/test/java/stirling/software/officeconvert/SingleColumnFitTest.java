package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Paragraphs on single-column pages keep the PDF's line breaks in Word. Each test draws one paragraph, converts it, then
 * breaks the DOCX paragraph again as Word does (greedily, with the font's widths, its runs' character spacing and the
 * paragraph's edge) and compares the lines with the PDF's.
 */
class SingleColumnFitTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float SIZE = 10;
    private static final float PITCH = 12;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    private static final String TEXT = "Agencies should review the guidance each year and report on how the plan was carried out, "
            + "which offices took part, what the work cost and whether the results matched the goals set at the start. "
            + "Where a program missed its targets, the report explains why and what will change before the next review. "
            + "Staff who manage grants keep records of every award, the dates on which funds were paid and the names of "
            + "the people who approved each payment, so that auditors can trace a decision back to its source without "
            + "asking for more papers. Offices that share systems agree in writing on who owns the data, who may change "
            + "it and how long it is kept, and they test their backups at least twice a year.";

    @TempDir Path dir;

    @Test
    void raggedLinesKeepTheirBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        float measure = narrowWindow(words);
        List<List<String>> lines = breakLines(words, measure);
        String xml = convert(makePdf(lines, measure, false));
        assertEquals(text(lines), wordBreaks(xml, lines), "Word breaks the paragraph where the PDF does");
    }

    @Test
    void aSqueezedJustifiedLineKeepsItsOwnSpacing() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        float measure = 0;
        List<List<String>> lines = null;
        String moved = null;
        for (float m = 340; m < 460 && lines == null; m += 0.05f) {
            List<List<String>> greedy = breakLines(words, m);
            for (int k = 1; k + 2 < greedy.size(); k++) {
                List<String> next = greedy.get(k + 1);
                float over = width(String.join(" ", greedy.get(k)) + " " + next.getFirst()) - m;
                boolean unique = words.indexOf(next.getFirst()) == words.lastIndexOf(next.getFirst());
                if (over > 2f && over < 3.5f && next.size() > 2 && unique) {
                    // Line k takes the next line's first word, its spaces squeezed as TeX does.
                    lines = new ArrayList<>(greedy);
                    List<String> squeezed = new ArrayList<>(greedy.get(k));
                    moved = next.getFirst();
                    squeezed.add(moved);
                    lines.set(k, squeezed);
                    lines.set(k + 1, next.subList(1, next.size()));
                    measure = m;
                    break;
                }
            }
        }
        assertTrue(lines != null, "a line that can take one more word squeezed");
        String xml = convert(makePdf(lines, measure, true));
        String para = paragraph(xml, lines);
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?((?:<w:t[^>]*>[^<]*</w:t>)+)</w:r>").matcher(para);
        Integer squeezed = null;
        Set<Integer> spacings = new HashSet<>();
        while (run.find()) {
            Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(run.group(1) == null ? "" : run.group(1));
            int value = sp.find() ? Integer.parseInt(sp.group(1)) : 0;
            spacings.add(value);
            if (run.group(2).contains(moved)) {
                squeezed = value;
            }
        }
        assertTrue(squeezed != null && squeezed < 0, "the squeezed line is condensed: " + squeezed);
        assertTrue(spacings.size() > 1, "the other lines keep their own spacing, not the squeezed line's: " + spacings);
    }

    /** A measure whose ragged lines leave Word less than a point and a half to place the edge. */
    private static float narrowWindow(List<String> words) {
        for (float m = 340; m < 460; m += 0.05f) {
            List<List<String>> lines = breakLines(words, m);
            float lower = -Float.MAX_VALUE;
            float upper = Float.MAX_VALUE;
            for (int i = 0; i + 1 < lines.size(); i++) {
                float w = width(String.join(" ", lines.get(i)));
                lower = Math.max(lower, w);
                upper = Math.min(upper, w + width(" " + lines.get(i + 1).getFirst()));
            }
            if (lines.size() >= 8 && upper - lower > 0.8f && upper - lower < 1.4f) {
                return m;
            }
        }
        throw new AssertionError("no measure with a narrow window");
    }

    private static List<List<String>> breakLines(List<String> words, float measure) {
        float space = width(" ");
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        float w = 0;
        for (String word : words) {
            float ww = width(word);
            if (!line.isEmpty() && w + space + ww > measure) {
                lines.add(line);
                line = new ArrayList<>();
                w = 0;
            }
            w += (line.isEmpty() ? 0 : space) + ww;
            line.add(word);
        }
        lines.add(line);
        return lines;
    }

    private static List<String> text(List<List<String>> lines) {
        return lines.stream().map(l -> String.join(" ", l)).toList();
    }

    /** The paragraph broken as Word breaks it: whole words while they fit, trailing spaces hanging. */
    private static List<String> wordBreaks(String xml, List<List<String>> lines) {
        String para = paragraph(xml, lines);
        Matcher margins = Pattern.compile("<w:pgMar [^>]*w:right=\"(\\d+)\"[^>]*w:left=\"(\\d+)\"").matcher(xml);
        assertTrue(margins.find(), "page margins");
        float right = Integer.parseInt(margins.group(1)) / 20f;
        float left = Integer.parseInt(margins.group(2)) / 20f;
        Matcher ind = Pattern.compile("<w:ind w:left=\"(-?\\d+)\" w:right=\"(-?\\d+)\"").matcher(para);
        float indLeft = 0;
        float indRight = 0;
        if (ind.find()) {
            indLeft = Integer.parseInt(ind.group(1)) / 20f;
            indRight = Integer.parseInt(ind.group(2)) / 20f;
        }
        float room = PDRectangle.LETTER.getWidth() - right - indRight - (left + indLeft);
        // Each character with its run's spacing: Word adds w:spacing after every character, spaces included.
        StringBuilder chars = new StringBuilder();
        List<Float> spacing = new ArrayList<>();
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?((?:<w:t[^>]*>[^<]*</w:t>)+)</w:r>").matcher(para);
        while (run.find()) {
            Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(run.group(1) == null ? "" : run.group(1));
            float extra = sp.find() ? Integer.parseInt(sp.group(1)) / 20f : 0;
            Matcher t = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(run.group(2));
            while (t.find()) {
                for (char c : t.group(1).toCharArray()) {
                    chars.append(c);
                    spacing.add(extra);
                }
            }
        }
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        float used = 0;
        int i = 0;
        while (i < chars.length()) {
            int end = i;
            while (end < chars.length() && chars.charAt(end) != ' ') {
                end++;
            }
            float word = 0;
            for (int k = i; k < end; k++) {
                word += width(String.valueOf(chars.charAt(k))) + spacing.get(k);
            }
            if (line.length() > 0 && used + word > room) {
                out.add(line.toString().strip());
                line.setLength(0);
                used = 0;
            }
            line.append(chars, i, end);
            used += word;
            if (end < chars.length()) {
                line.append(' ');
                used += width(" ") + spacing.get(end);
            }
            i = end + 1;
        }
        out.add(line.toString().strip());
        return out;
    }

    private static String paragraph(String xml, List<List<String>> lines) {
        String first = lines.getFirst().getFirst() + " " + lines.getFirst().get(1);
        int at = xml.indexOf(first);
        assertTrue(at >= 0, "paragraph found");
        return xml.substring(xml.lastIndexOf("<w:p>", at), xml.indexOf("</w:p>", at));
    }

    private static float width(String s) {
        try {
            return FONT.getStringWidth(s) / 1000f * SIZE;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String convert(Path pdf) throws IOException {
        Path docx = dir.resolve("para.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** One paragraph, ragged or justified to {@code measure} (its last line ragged), at the top of a page. */
    private Path makePdf(List<List<String>> lines, float measure, boolean justified) throws IOException {
        Path pdf = dir.resolve("para.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.size(); i++) {
                    String s = String.join(" ", lines.get(i));
                    int gaps = lines.get(i).size() - 1;
                    float spacing = justified && i + 1 < lines.size() && gaps > 0 ? (measure - width(s)) / gaps : 0;
                    cs.beginText();
                    cs.setFont(FONT, SIZE);
                    cs.setWordSpacing(spacing);
                    cs.newLineAtOffset(LEFT, TOP - i * PITCH);
                    cs.showText(s);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }
}
