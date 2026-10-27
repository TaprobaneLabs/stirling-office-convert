package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
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
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A list whose bullets are set larger than its text keeps the PDF's line pitch. The bullet counted among the paragraph's
 * sizes, so an item looked like text of mixed sizes and got "at least" line spacing, and in Word each item's first line
 * grew to the bullet's height: NOAA's GeoXO fact sheet sets its bullets at 18 pt beside 11 pt text, and its column ran
 * seven points lower an item. Word draws the bullet from the list's level, outside the item's text.
 */
class LargeBulletLineHeightTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[][] ITEMS = {
        {"Real-time, high-resolution visible and infrared imagery", "for monitoring the weather and environment, including",
            "severe storms, hurricanes and natural hazards."},
        {"Lightning detection to analyze severe storms, predict the", "intensity of hurricanes and respond to wildfires."},
        {"Real-time information about the vertical distribution of", "moisture, winds and temperature for better numerical",
            "weather prediction and short-term forecasts."},
    };

    @TempDir Path dir;

    @Test
    void itemsWithLargerBulletsKeepAnExactLinePitch() throws Exception {
        String xml = convert(makePdf());
        List<String> items = new ArrayList<>();
        Matcher p = Pattern.compile("<w:p>(?:(?!</w:p>).)*?<w:numPr>(?:(?!</w:p>).)*?</w:p>").matcher(xml);
        while (p.find()) {
            items.add(p.group());
        }
        assertEquals(ITEMS.length, items.size(), "each item is a list paragraph");
        for (String item : items) {
            Matcher rule = Pattern.compile("w:lineRule=\"(\\w+)\"").matcher(item);
            assertTrue(rule.find(), "the item sets its line spacing");
            assertEquals("exact", rule.group(1), "the item keeps the PDF's 13 pt pitch: " + item.substring(0, 300));
        }
    }

    /**
     * In a right-to-left list the marker is the line's rightmost word, and the item's own larger word still counts: here
     * the first item's last word, its leftmost, is set at 18 pt beside 11 pt text and an 11 pt bullet, so the item keeps
     * "at least" spacing and Word doesn't cut that word's top.
     */
    @Test
    void aRightToLeftItemKeepsItsOwnLargerWordMeasured() throws Exception {
        String xml = convert(makeRtlPdf());
        Matcher p = Pattern.compile("<w:p>(?:(?!</w:p>).)*?<w:numPr>(?:(?!</w:p>).)*?</w:p>").matcher(xml);
        assertTrue(p.find(), "the first item is a list paragraph");
        Matcher rule = Pattern.compile("w:lineRule=\"(\\w+)\"").matcher(p.group());
        assertTrue(rule.find(), "the item sets its line spacing");
        assertEquals("atLeast", rule.group(1), "the item's 18 pt word gets the room it needs");
    }

    /**
     * Three Hebrew items set right to left, a bullet at each item's right; the first item's last word, drawn leftmost, is
     * set at 18 pt.
     */
    private Path makeRtlPdf() throws IOException {
        String[][][] items = {
            {{"\u05D4\u05D2\u05E8\u05E1\u05D4", "\u05D4\u05D7\u05D3\u05E9\u05D4", "\u05E9\u05DC", "\u05D4\u05DE\u05E2\u05E8\u05DB\u05EA",
                "\u05E2\u05D5\u05DC\u05DD"}, {"\u05DB\u05DC", "\u05D1\u05E0\u05D9", "\u05D0\u05D3\u05DD", "\u05E0\u05D5\u05DC\u05D3\u05D5"}},
            {{"\u05D1\u05E0\u05D9", "\u05D7\u05D5\u05E8\u05D9\u05DF", "\u05E9\u05DC\u05D5\u05DD", "\u05D4\u05D2\u05E8\u05E1\u05D4"},
                {"\u05D4\u05D7\u05D3\u05E9\u05D4", "\u05E9\u05DC", "\u05E2\u05D5\u05DC\u05DD"}},
            {{"\u05DB\u05DC", "\u05D1\u05E0\u05D9", "\u05D0\u05D3\u05DD", "\u05E0\u05D5\u05DC\u05D3\u05D5", "\u05D1\u05E0\u05D9"},
                {"\u05D7\u05D5\u05E8\u05D9\u05DF", "\u05E9\u05DC\u05D5\u05DD"}},
        };
        Path pdf = dir.resolve("rtl.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont hebrew;
            try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                hebrew = PDType0Font.load(doc, in, false);
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 680;
                for (int i = 0; i < items.length; i++) {
                    text(cs, hebrew, 11, 532, y, "\u2022");
                    for (int li = 0; li < items[i].length; li++) {
                        // Right to left: each word's letters in visual order, each word left of the one before
                        float x = 524;
                        String[] words = items[i][li];
                        for (int wi = 0; wi < words.length; wi++) {
                            float size = i == 0 && li == 0 && wi == words.length - 1 ? 18 : 11;
                            String visual = new StringBuilder(words[wi]).reverse().toString();
                            x -= hebrew.getStringWidth(visual) / 1000f * size;
                            text(cs, hebrew, size, x, y, visual);
                            x -= hebrew.getStringWidth(" ") / 1000f * 11;
                        }
                        y -= 20;
                    }
                    y -= 5;
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** Three bulleted items at 11 pt on a 13 pt pitch, each bullet drawn at 18 pt on its item's first baseline. */
    private Path makePdf() throws IOException {
        Path pdf = dir.resolve("list.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 11, 72, 700, "What will the new satellites provide? A paragraph above the list to start the page.");
                float y = 670;
                for (String[] item : ITEMS) {
                    text(cs, 18, 72, y, "•");
                    for (String line : item) {
                        text(cs, 11, 86, y, line);
                        y -= 13;
                    }
                    y -= 5;
                }
                text(cs, 11, 72, y - 20, "A paragraph after the list, at the same size as its items' text, to end the page.");
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static void text(PDPageContentStream cs, float size, float x, float y, String s) throws IOException {
        text(cs, FONT, size, x, y, s);
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private String convert(Path pdf) throws IOException {
        Path docx = dir.resolve("list.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
