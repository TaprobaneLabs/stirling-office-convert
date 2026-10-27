package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Body text set with slightly expanded character spacing keeps the PDF's line breaks in Word. The spacing was left out,
 * so each line came out several points shorter in Word, more than the fit may narrow a paragraph, and Word took the next
 * line's first word onto it: GAO's highlights, set with a tenth of a point a letter. Letterspaced capitals still drop
 * their spacing, which Word's PDFs read as separate letters.
 */
class ExpandedSpacingFitTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float SIZE = 10;
    private static final float PITCH = 12;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    private static final float TRACKING = 0.1f;
    private static final String TEXT = "Agencies should review the guidance each year and report on how the plan was carried out, "
            + "which offices took part, what the work cost and whether the results matched the goals set at the start. "
            + "Where a program missed its targets, the report explains why and what will change before the next review. "
            + "Staff who manage grants keep records of every award, the dates on which funds were paid and the names of "
            + "the people who approved each payment, so that auditors can trace a decision back to its source without "
            + "asking for more papers.";

    @TempDir Path dir;

    @Test
    void slightlyExpandedBodyTextKeepsThePdfLineBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        List<List<String>> lines = null;
        for (float m = 340; m < 460 && lines == null; m += 0.25f) {
            List<List<String>> broken = breakLines(words, m, TRACKING);
            lines = pullsUpAWord(broken) ? broken : null;
        }
        assertTrue(lines != null, "a measure where, without its spacing, a line has room for the next line's first word");
        String xml = convert(makePdf(lines, TRACKING));
        String para = paragraph(xml, String.join(" ", lines.getFirst().subList(0, 2)));
        assertEquals(text(lines), wordBreaks(xml, para), "Word breaks the paragraph where the PDF does");
        assertTrue(para.contains("<w:spacing w:val=\"2\"/>"), "the runs keep the PDF's tenth of a point a letter");
    }

    @Test
    void letterspacedCapitalsDropTheirSpacing() throws Exception {
        List<List<String>> lines = List.of(List.of("EXECUTIVE", "OFFICE", "OF", "THE", "PRESIDENT"));
        String xml = convert(makePdf(lines, 0.25f));
        String para = paragraph(xml, "EXECUTIVE OFFICE");
        assertFalse(Pattern.compile("<w:spacing w:val=\"[1-9]\\d*\"/>").matcher(para).find(),
                "capitals keep no expanded spacing: " + para);
    }

    /**
     * Whether a line, measured without its spacing, has room for the next line's first word by more than the fit may
     * narrow a paragraph (a few points).
     */
    private static boolean pullsUpAWord(List<List<String>> lines) {
        float widest = 0;
        for (List<String> l : lines) {
            widest = Math.max(widest, width(String.join(" ", l), TRACKING));
        }
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (width(String.join(" ", lines.get(i)) + " " + lines.get(i + 1).getFirst(), 0) < widest - 4) {
                return true;
            }
        }
        return false;
    }

    private static List<List<String>> breakLines(List<String> words, float measure, float tracking) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        for (String word : words) {
            if (!line.isEmpty() && width(String.join(" ", line) + " " + word, tracking) > measure) {
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
                word += width(String.valueOf(chars.charAt(k)), 0) + spacing.get(k);
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
                used += width(" ", 0) + spacing.get(end);
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

    /** The width of a string set with {@code tracking} after each character but the last. */
    private static float width(String s, float tracking) {
        try {
            return FONT.getStringWidth(s) / 1000f * SIZE + tracking * (s.length() - 1);
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

    /** Ragged lines set with {@code tracking} a character, at the top of a page. */
    private Path makePdf(List<List<String>> lines, float tracking) throws IOException {
        Path pdf = dir.resolve("para.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.size(); i++) {
                    cs.beginText();
                    cs.setFont(FONT, SIZE);
                    cs.setCharacterSpacing(tracking);
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
