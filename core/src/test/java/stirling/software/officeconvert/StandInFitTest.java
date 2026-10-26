package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A paragraph set in a face Word lacks keeps the PDF's line breaks. The DOCX names the stand-in (Arial) and gives its
 * runs the character spacing that makes up the difference in width, so Word's widths are still known exactly and the
 * paragraph's edge can be fitted to the PDF's breaks, as for a face Word has. Designed pages set in Helvetica Neue or
 * Myriad Pro (IRS and CDC publications) had their lines rewrapped.
 */
class StandInFitTest {

    private static final PDType1Font ARIAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    /** The PDF face's widths against its stand-in's. */
    private static final float WIDER = 1.04f;
    private static final float SIZE = 10;
    private static final float PITCH = 12;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    /** A list item's text starts this far right of its bullet. */
    private static final float BULLET_GAP = 14;
    private static final String TEXT = "Agencies should review the guidance each year and report on how the plan was carried out, "
            + "which offices took part, what the work cost and whether the results matched the goals set at the start. "
            + "Where a program missed its targets, the report explains why and what will change before the next review. "
            + "Staff who manage grants keep records of every award, the dates on which funds were paid and the names of "
            + "the people who approved each payment, so that auditors can trace a decision back to its source without "
            + "asking for more papers. Offices that share systems agree in writing on who owns the data, who may change "
            + "it and how long it is kept, and they test their backups at least twice a year.";

    @TempDir Path dir;

    @Test
    void aStandInFaceKeepsThePdfLineBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        float measure = takesAWord(words);
        List<List<String>> lines = breakLines(words, measure);
        String xml = convert(lines);
        assertTrue(xml.contains("Arial"), "the stand-in is Arial");
        assertTrue(spacings(paragraph(xml, lines)).stream().anyMatch(s -> s > 0),
                "its runs carry spacing for the wider face");
        assertEquals(text(lines), wordBreaks(xml, lines), "Word breaks the paragraph where the PDF does");
    }

    /**
     * A bulleted item keeps them too. Its bullet is Word's list number, a tab from its text, so the item is measured from
     * where its text starts: unfitted, it took a word Word could fit, and a column holding a list wasn't pinned, so Word ran
     * the next column's first lines into it (the IRS's Korean Publication 1, page 2).
     */
    @Test
    void aListItemInAStandInFaceKeepsThePdfLineBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        List<List<String>> lines = breakLines(words, takesAWord(words));
        String xml = convert(lines, true);
        assertTrue(paragraph(xml, lines).contains("<w:numPr>"), "the item is a list item");
        assertEquals(text(lines), wordBreaks(xml, lines), "Word breaks the item where the PDF does");
    }

    /**
     * A measure at which every PDF line leaves a few points for the edge (more than the stand-in's widths can be off by
     * on one line), but the tightest leaves less than the slack Word gets past the widest line without a fit, so an
     * unfitted paragraph takes a word there.
     */
    private static float takesAWord(List<String> words) {
        for (float m = 330; m < 470; m += 0.05f) {
            List<List<String>> lines = breakLines(words, m);
            float widest = 0;
            float tightest = Float.MAX_VALUE;
            for (int i = 0; i < lines.size(); i++) {
                widest = Math.max(widest, pdfWidth(String.join(" ", lines.get(i))));
                if (i + 1 < lines.size()) {
                    tightest = Math.min(tightest, pdfWidth(String.join(" ", lines.get(i)) + " " + lines.get(i + 1).getFirst()));
                }
            }
            if (lines.size() >= 8 && tightest - widest > 2.5f && tightest - widest < 3.5f) {
                return m;
            }
        }
        throw new AssertionError("no measure with a window of a few points");
    }

    private static List<List<String>> breakLines(List<String> words, float measure) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        for (String word : words) {
            List<String> longer = new ArrayList<>(line);
            longer.add(word);
            if (!line.isEmpty() && pdfWidth(String.join(" ", longer)) > measure) {
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

    private String convert(List<List<String>> lines) throws IOException {
        return convert(lines, false);
    }

    private String convert(List<List<String>> lines, boolean bullet) throws IOException {
        Path pdf = dir.resolve("stand-in.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDTrueTypeFont(widerFace()));
            resources.put(COSName.getPDFName("F2"), ARIAL);
            page.setResources(resources);
            StringBuilder content = new StringBuilder();
            float x = bullet ? LEFT + BULLET_GAP : LEFT;
            if (bullet) {
                content.append(String.format(Locale.ROOT, "BT /F2 %.0f Tf %.2f %.2f Td (\\225) Tj ET%n", SIZE, LEFT, TOP));
            }
            for (int i = 0; i < lines.size(); i++) {
                content.append(String.format(Locale.ROOT, "BT /F1 %.0f Tf %.2f %.2f Td (%s) Tj ET%n", SIZE, x, TOP - i * PITCH,
                        String.join(" ", lines.get(i))));
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.toString().getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("stand-in.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            String body = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            String styles = new String(zip.getInputStream(zip.getEntry("word/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
            return body + styles;
        }
    }

    /** Myriad Pro as a PDF names it without embedding it, with widths 4% wider than Arial's. */
    private static COSDictionary widerFace() throws IOException {
        COSArray widths = new COSArray();
        for (int c = 32; c <= 126; c++) {
            widths.add(COSInteger.get(Math.round(ARIAL.getWidth(c) * WIDER)));
        }
        COSDictionary descriptor = new COSDictionary();
        descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, "MyriadPro-Regular");
        descriptor.setInt(COSName.FLAGS, 32);
        descriptor.setItem(COSName.FONT_BBOX, numbers(-157, -250, 1126, 952));
        descriptor.setInt(COSName.ITALIC_ANGLE, 0);
        descriptor.setInt(COSName.ASCENT, 750);
        descriptor.setInt(COSName.DESCENT, -250);
        descriptor.setInt(COSName.CAP_HEIGHT, 674);
        descriptor.setInt(COSName.STEM_V, 88);
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TRUE_TYPE);
        font.setName(COSName.BASE_FONT, "MyriadPro-Regular");
        font.setInt(COSName.FIRST_CHAR, 32);
        font.setInt(COSName.LAST_CHAR, 126);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);
        font.setItem(COSName.FONT_DESC, descriptor);
        return font;
    }

    private static COSArray numbers(float... v) {
        COSArray a = new COSArray();
        for (float f : v) {
            a.add(new COSFloat(f));
        }
        return a;
    }

    private static String paragraph(String xml, List<List<String>> lines) {
        String first = String.join(" ", lines.getFirst().subList(0, 3));
        Matcher m = Pattern.compile("<w:p>(?:(?!</w:p>).)*?" + Pattern.quote(first) + ".*?</w:p>", Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "the paragraph");
        return m.group();
    }

    private static List<Float> spacings(String para) {
        List<Float> out = new ArrayList<>();
        Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(para);
        while (sp.find()) {
            out.add(Integer.parseInt(sp.group(1)) / 20f);
        }
        return out;
    }

    /** The paragraph broken as Word breaks it: whole words while they fit, with Arial's widths, its runs' character
     *  spacing (after every character, spaces included) and the paragraph's edge. */
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
                word += wordWidth(String.valueOf(chars.charAt(k))) + spacing.get(k);
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
                used += wordWidth(" ") + spacing.get(end);
            }
            i = end + 1;
        }
        out.add(line.toString().strip());
        return out;
    }

    /** Arial's width in Word. */
    private static float wordWidth(String s) {
        try {
            return ARIAL.getStringWidth(s) / 1000f * SIZE;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The PDF face's width. */
    private static float pdfWidth(String s) {
        float w = 0;
        try {
            for (char c : s.toCharArray()) {
                w += Math.round(ARIAL.getWidth(c) * WIDER) / 1000f * SIZE;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return w;
    }
}
