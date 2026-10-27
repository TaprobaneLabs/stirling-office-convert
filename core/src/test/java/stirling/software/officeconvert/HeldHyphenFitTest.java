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
 * A hyphenated word the PDF moves whole to the next line stays whole there in Word. Where its first part fitted on the
 * line before, within the paragraph's longest line, Word broke the word at its hyphen and took that part up: USGS Fact
 * Sheet 2024-3036 ended a line with "humanity's ever-" where the PDF set "ever-increasing" on the next. Too much room for a
 * little letter spacing to fill, the word keeps its hyphens unbroken.
 */
class HeldHyphenFitTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float SIZE = 10;
    private static final String WORD = "ever-increasing";
    private static final String TEXT = "Space weather can disrupt the satellites, power grids and radio links that support "
            + "the global economy and its " + WORD + " reliance on electric power, so the survey works with its partners "
            + "to watch the sun and forecast the storms that reach the Earth, giving the operators of grids and networks "
            + "time to protect the systems the public relies on each day, and helping researchers learn how often the "
            + "strongest storms come.";

    @TempDir Path dir;

    @Test
    void aHyphenatedWordMovedWholeStaysWhole() throws Exception {
        List<List<String>> lines = held();
        assertNotNull(lines, "a measure where the word's first part fits well within the longest line");
        String xml = convert(makePdf(lines));
        String para = paragraph(xml, "Space weather");
        assertEquals(text(lines), wordBreaks(xml, para), "Word breaks the paragraph where the PDF does");
    }

    /**
     * Lines broken where whole words fit a measure, where the hyphenated word starts a line and its first part, "ever-",
     * would have fitted on the line before with more room to spare than letter spacing can fill. Null when no measure
     * gives that.
     */
    private static List<List<String>> held() {
        List<String> words = Arrays.asList(TEXT.split(" "));
        for (float m = 200; m < 500; m += 0.25f) {
            List<List<String>> lines = breakLines(words, m);
            float widest = 0;
            for (int j = 0; j + 1 < lines.size(); j++) {
                widest = Math.max(widest, width(String.join(" ", lines.get(j))));
            }
            for (int i = 0; i + 1 < lines.size(); i++) {
                if (lines.get(i + 1).getFirst().equals(WORD)) {
                    String line = String.join(" ", lines.get(i));
                    float room = widest - width(line + " " + WORD.substring(0, WORD.indexOf('-') + 1));
                    if (room >= 0.2f * line.length() + 2 && room <= 0.2f * line.length() + 20) {
                        return lines;
                    }
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

    /**
     * The paragraph broken as Word breaks it: at spaces, which hang, and after a hyphen between letters or digits, not
     * after a no-break hyphen.
     */
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
        // Each character with its run's spacing; a no-break hyphen is U+2011, as wide as a hyphen
        StringBuilder chars = new StringBuilder();
        List<Float> spacing = new ArrayList<>();
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?(.*?)</w:r>").matcher(para);
        while (run.find()) {
            Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(run.group(1) == null ? "" : run.group(1));
            float extra = sp.find() ? Integer.parseInt(sp.group(1)) / 20f : 0;
            Matcher t = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>|<w:noBreakHyphen/>").matcher(run.group(2));
            while (t.find()) {
                for (char c : (t.group(1) == null ? "‑" : t.group(1)).toCharArray()) {
                    chars.append(c);
                    spacing.add(extra);
                }
            }
        }
        // Units Word won't break inside: up to a space or a breaking hyphen
        List<String> units = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        List<Float> hanging = new ArrayList<>();
        int i = 0;
        while (i < chars.length()) {
            int end = i;
            float w = 0;
            while (end < chars.length() && chars.charAt(end) != ' ') {
                w += charWidth(chars.charAt(end)) + spacing.get(end);
                end++;
                if (chars.charAt(end - 1) == '-' && end - 1 > i && end < chars.length()
                        && Character.isLetterOrDigit(chars.charAt(end - 2)) && Character.isLetterOrDigit(chars.charAt(end))) {
                    break;
                }
            }
            float hang = 0;
            if (end < chars.length() && chars.charAt(end) == ' ') {
                hang = charWidth(' ') + spacing.get(end);
                end++;
            }
            units.add(chars.substring(i, end));
            widths.add(w);
            hanging.add(hang);
            i = end;
        }
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        float used = 0;
        for (int u = 0; u < units.size(); u++) {
            if (line.length() > 0 && used + widths.get(u) > room) {
                out.add(line.toString().strip());
                line.setLength(0);
                used = 0;
            }
            line.append(units.get(u));
            used += widths.get(u) + hanging.get(u);
        }
        out.add(line.toString().strip());
        return out.stream().map(l -> l.replace('‑', '-')).toList();
    }

    private static float charWidth(char c) {
        return width(String.valueOf(c == '‑' ? '-' : c));
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
                    cs.newLineAtOffset(72, 700 - i * 12);
                    cs.showText(String.join(" ", lines.get(i)));
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }
}
