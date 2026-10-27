package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 * A ragged line the PDF breaks before Word would keeps its break. InDesign's paragraph composer breaks lines early to even
 * out a ragged edge, so a line can leave room for the next line's first word within the width of the paragraph's longest
 * line, and no one edge gives Word every break: Word took the word up, and every later line's first word with it (USGS
 * Fact Sheet 2024-3052's page 3). The line gets the least extra character spacing that keeps the word out, a twentieth of a
 * point a letter or two, which doesn't show; a line broken far earlier gets none.
 */
class EarlyBreakFitTest {

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
            + "asking for more papers.";

    @TempDir Path dir;

    @Test
    void aLineBrokenALittleEarlyKeepsItsBreak() throws Exception {
        List<List<String>> lines = earlyBreak(0.5f, 2f);
        assertNotNull(lines, "a line broken early whose next word fits within the paragraph's longest line");
        String xml = convert(makePdf(lines));
        String para = paragraph(xml, String.join(" ", lines.getFirst().subList(0, 2)));
        assertEquals(text(lines), wordBreaks(xml, para), "Word breaks the paragraph where the PDF does");
    }

    @Test
    void aLineBrokenFarEarlyGetsNoVisibleSpacing() throws Exception {
        List<List<String>> lines = earlyBreak(12f, 30f);
        assertNotNull(lines, "a line broken a long word early");
        String para = paragraph(convert(makePdf(lines)), String.join(" ", lines.getFirst().subList(0, 2)));
        Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(para);
        while (sp.find()) {
            assertTrue(Integer.parseInt(sp.group(1)) <= 3, "at most 0.15 pt a letter: " + sp.group());
        }
    }

    /**
     * Lines broken where they fit a measure, but for one, which leaves its last word to the next line, so that it and that
     * word together fall between {@code least} and {@code most} points short of the widest other line. Null when no
     * measure gives one.
     */
    private static List<List<String>> earlyBreak(float least, float most) {
        List<String> words = Arrays.asList(TEXT.split(" "));
        for (float m = 330; m < 460; m += 0.25f) {
            List<List<String>> greedy = breakLines(words, m);
            for (int i = 1; i + 2 < greedy.size(); i++) {
                List<String> before = new ArrayList<>();
                greedy.subList(0, i + 1).forEach(before::addAll);
                List<List<String>> lines = new ArrayList<>(greedy.subList(0, i));
                List<String> early = greedy.get(i).subList(0, greedy.get(i).size() - 1);
                lines.add(early);
                lines.addAll(breakLines(words.subList(before.size() - 1, words.size()), m));
                float widest = 0;
                for (int j = 0; j + 1 < lines.size(); j++) {
                    widest = j == i ? widest : Math.max(widest, width(String.join(" ", lines.get(j))));
                }
                float gap = widest - width(String.join(" ", early) + " " + lines.get(i + 1).getFirst());
                if (gap >= least && gap <= most && String.join(" ", early).length() >= 40) {
                    return lines;
                }
            }
        }
        return null;
    }

    private static List<List<String>> breakLines(List<String> words, float measure) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        for (String word : words) {
            if (!line.isEmpty() && width(String.join(" ", line) + " " + word) > measure) {
                lines.add(line);
                line = new ArrayList<>();
            }
            line.add(word);
        }
        lines.add(line);
        return lines;
    }

    private static List<String> text(List<List<String>> lines) {
        return lines.stream().map(l -> String.join(" ", l)).toList();
    }

    /** The paragraph broken as Word breaks it: whole words while they fit, trailing spaces hanging. */
    private static List<String> wordBreaks(String xml, String para) {
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
        // Each character with its run's spacing: Word adds w:spacing after every character, spaces included
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

    private static String paragraph(String xml, String start) {
        int at = xml.indexOf(start);
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

    /** Ragged lines at the top of a page. */
    private Path makePdf(List<List<String>> lines) throws IOException {
        Path pdf = dir.resolve("para.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.size(); i++) {
                    cs.beginText();
                    cs.setFont(FONT, SIZE);
                    cs.newLineAtOffset(LEFT, TOP - i * PITCH);
                    cs.showText(String.join(" ", lines.get(i)));
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }
}
