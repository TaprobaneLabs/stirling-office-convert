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
        cs.beginText();
        cs.setFont(FONT, size);
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
