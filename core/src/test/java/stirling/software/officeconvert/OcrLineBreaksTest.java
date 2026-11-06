package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A scanned letter's OCR layer, as OCRmyPDF 17 writes it: each word in its invisible NotoSans at about 0.72 of the type's
 * size, stretched to the word's box, and the next word placed where the scan has it. Word must break the paragraph where
 * the scan does. Set in Arial with the OCR stretch, its lines came out several points shorter than the scan's, and Word
 * took each next line's first word.
 */
class OcrLineBreaksTest {

    /** The scan's type: Times, 11 points. */
    private static final PDType1Font TYPE = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SIZE = 11;

    private static final float OCR_SIZE = 0.72f * SIZE;

    /** A letter's side bearing, in ems, outside the ink an OCR word's box holds. */
    private static final float BEARING = 0.05f;

    private static final PDType1Font ARIAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final String TEXT = "Our safety record improved again this year: the lost-time injury rate fell to "
            + "its lowest level since the depot opened, and two customers who left after the price review came back "
            + "once the new cold-store project was finished. Rail connections to the inland depot carried more of the "
            + "import boxes than the road fleet, and the training academy enrolled three hundred new drivers before the "
            + "busy season began. Maintenance spending stayed within the budget the board approved in March.";

    /** IRS Form 8822's Privacy Act notice (a US federal work), set in a form's 7-point type in a 150-point column. */
    private static final String NOTICE = "Generally, tax returns and return information are confidential, as required "
            + "by section 6103. However, we may give the information to the Department of Justice and to other federal "
            + "agencies, as provided by law. We may give it to cities, states, the District of Columbia, and U.S. "
            + "commonwealths or possessions to carry out their tax laws. We may also disclose this information to other "
            + "countries under a tax treaty, to federal and state agencies to enforce federal nontax criminal laws, or "
            + "to federal law enforcement and intelligence agencies to combat terrorism.";

    @TempDir Path dir;

    @Test
    void anOcrParagraphKeepsTheScansLineBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        List<List<String>> lines = breakLines(words, 420, SIZE);
        assertTrue(lines.size() >= 5, "a paragraph of several lines");
        String xml = convert(lines);
        List<String> expected = new ArrayList<>();
        for (List<String> l : lines) {
            expected.add(String.join(" ", l));
        }
        assertEquals(expected, wordBreaks(xml), "Word breaks the OCR paragraph where the scan does");
    }

    /**
     * An OCR tool sizes each line from its own box, so one paragraph's lines come at 4.79 to 5.24 points, which Word sets
     * at 5. Each line must come out in Word at the same share of its width in the OCR layer, within a percent, or Word
     * breaks the paragraph elsewhere. The width fix's spacing moves in twentieths of a point a character, 1.4% of a line
     * at 5 points: a line at 5.07 stayed 1.4% short while one at exactly 5 kept its width (IRS Form 8822's scan, whose
     * Privacy Act notice lost its first line's break).
     */
    @Test
    void anOcrParagraphsLinesKeepTheirWidthsWhicheverWayTheirSizesRound() throws Exception {
        float size = 7;
        List<List<String>> lines = breakLines(Arrays.asList(NOTICE.split(" ")), 150, size);
        assertTrue(lines.size() >= 8, "a paragraph of several lines");
        float[] cycle = {5f, 5.07f, 4.79f, 5.07f, 5f, 5.24f};
        float[] ocr = new float[lines.size()];
        for (int i = 0; i < ocr.length; i++) {
            ocr[i] = cycle[i % cycle.length];
        }
        Chars word = wordChars(convert(lines, size, ocr, 8.4f), "Generally");
        // Each line's width in Word over its width in the OCR layer, from its first box's start to its last box's end
        float least = Float.MAX_VALUE;
        float most = 0;
        int at = 0;
        for (List<String> line : lines) {
            String text = String.join(" ", line);
            at = word.text().indexOf(text, at);
            assertTrue(at >= 0, "the line's text in the paragraph: " + text);
            float inWord = 0;
            for (int k = at; k < at + text.length(); k++) {
                inWord += word.widths().get(k);
            }
            float layer = TYPE.getStringWidth(text) / 1000 * size - 2 * BEARING * size;
            least = Math.min(least, inWord / layer);
            most = Math.max(most, inWord / layer);
            at += text.length();
        }
        assertTrue(most - least <= 0.01f, "the lines' widths within a percent of each other: " + least + " to " + most);
        assertTrue(most <= 1.001f, "none longer than the scan's: " + most);
    }

    private String convert(List<List<String>> lines) throws Exception {
        float[] ocr = new float[lines.size()];
        Arrays.fill(ocr, OCR_SIZE);
        return convert(lines, SIZE, ocr, 13.5f);
    }

    /** The lines as an OCR layer over type of {@code size} points, line {@code i} drawn at {@code ocr[i]} points. */
    private String convert(List<List<String>> lines, float size, float[] ocr, float leading) throws Exception {
        Path pdf = dir.resolve("ocr.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font;
            try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                font = PDType0Font.load(doc, in, false);
            }
            font.getCOSObject().setName(COSName.BASE_FONT, "MPDFAA+NotoSans");
            float space = TYPE.getStringWidth(" ") / 1000 * size;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 680;
                for (int li = 0; li < lines.size(); li++) {
                    float x = 72;
                    for (String word : lines.get(li)) {
                        // Tesseract's box hugs the ink: inside the letters' side bearings, so the gaps between boxes
                        // are wider than the type's spaces
                        float set = TYPE.getStringWidth(word) / 1000 * size;
                        float box = set - 2 * BEARING * size;
                        float drawn = font.getStringWidth(word) / 1000 * ocr[li];
                        cs.beginText();
                        cs.setRenderingMode(RenderingMode.NEITHER);
                        cs.setFont(font, ocr[li]);
                        cs.setHorizontalScaling(100 * box / drawn);
                        cs.setTextMatrix(Matrix.getTranslateInstance(x + BEARING * size, y));
                        cs.showText(word + " ");
                        cs.endText();
                        x += set + space;
                    }
                    y -= leading;
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("ocr.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            String doc = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            String styles = new String(zip.getInputStream(zip.getEntry("word/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
            return styles + doc;
        }
    }

    private static List<List<String>> breakLines(List<String> words, float measure, float size) throws Exception {
        float space = TYPE.getStringWidth(" ") / 1000 * size;
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        float w = 0;
        for (String word : words) {
            float ww = TYPE.getStringWidth(word) / 1000 * size;
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

    /** A paragraph of the document, its text, and each character's width in Word. */
    private record Chars(String paragraph, StringBuilder text, List<Float> widths) {}

    /**
     * The paragraph holding {@code marker}, each character as wide as Arial makes it at its run's size (half points, else
     * the document's default) and character scale, plus its run's character spacing.
     */
    private static Chars wordChars(String xml, String marker) throws Exception {
        Matcher defaults = Pattern.compile("<w:rPrDefault>.*?<w:sz w:val=\"(\\d+)\"", Pattern.DOTALL).matcher(xml);
        float defaultSize = defaults.find() ? Integer.parseInt(defaults.group(1)) / 2f : 10;
        Matcher para = Pattern.compile("<w:p>(?:(?!</w:p>).)*?" + Pattern.quote(marker) + "(?:(?!</w:p>).)*</w:p>",
                Pattern.DOTALL).matcher(xml);
        assertTrue(para.find(), xml);
        String p = para.group();
        StringBuilder chars = new StringBuilder();
        List<Float> widths = new ArrayList<>();
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?((?:<w:t[^>]*>[^<]*</w:t>)+)</w:r>").matcher(p);
        while (run.find()) {
            String rPr = run.group(1) == null ? "" : run.group(1);
            Matcher sz = Pattern.compile("<w:sz w:val=\"(\\d+)\"/>").matcher(rPr);
            float size = sz.find() ? Integer.parseInt(sz.group(1)) / 2f : defaultSize;
            Matcher w = Pattern.compile("<w:w w:val=\"(\\d+)\"/>").matcher(rPr);
            float scale = w.find() ? Integer.parseInt(w.group(1)) / 100f : 1f;
            Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(rPr);
            float extra = sp.find() ? Integer.parseInt(sp.group(1)) / 20f : 0;
            Matcher t = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(run.group(2));
            while (t.find()) {
                for (char c : t.group(1).toCharArray()) {
                    chars.append(c);
                    widths.add(ARIAL.getStringWidth(String.valueOf(c)) / 1000 * size * scale + extra);
                }
            }
        }
        return new Chars(p, chars, widths);
    }

    /** The paragraph broken again as Word breaks it: greedily at spaces, each character as wide as Word sets it. */
    private static List<String> wordBreaks(String xml) throws Exception {
        Chars c = wordChars(xml, "Our ");
        String p = c.paragraph();
        Matcher margins = Pattern.compile("<w:pgMar [^>]*w:right=\"(\\d+)\"[^>]*w:left=\"(\\d+)\"").matcher(xml);
        assertTrue(margins.find(), "page margins");
        float right = Integer.parseInt(margins.group(1)) / 20f;
        float left = Integer.parseInt(margins.group(2)) / 20f;
        Matcher ind = Pattern.compile("<w:ind w:left=\"(-?\\d+)\" w:right=\"(-?\\d+)\"").matcher(p);
        float indLeft = 0;
        float indRight = 0;
        if (ind.find()) {
            indLeft = Integer.parseInt(ind.group(1)) / 20f;
            indRight = Integer.parseInt(ind.group(2)) / 20f;
        }
        float room = PDRectangle.LETTER.getWidth() - right - indRight - (left + indLeft);
        StringBuilder chars = c.text();
        List<Float> widths = c.widths();
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
                word += widths.get(k);
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
                used += widths.get(end);
            }
            i = end + 1;
        }
        out.add(line.toString().strip());
        return out;
    }
}
