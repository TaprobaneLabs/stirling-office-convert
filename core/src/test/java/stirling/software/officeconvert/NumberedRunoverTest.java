package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Numbered paragraphs whose runover lines start under the number, as LibreOffice and many memos set them. */
class NumberedRunoverTest {

    private static final String[][] ITEMS = {
        {"Our safety record improved again: the lost-time injury rate fell to 0.41 per 200,000 hours",
            "worked. The finance team closed the books in eight working days, two days faster than last",
            "year, and the audit committee met five times."},
        {"Our safety record improved again: the lost-time injury rate fell to 0.41 per 200,000 hours",
            "worked. The customer portal handled 61 percent of booking changes without a phone call.",
            "Our carbon emissions per container fell by 11 percent against the 2024 baseline."},
        {"The dividend proposed for the year is 2.40 per share, up from 2.10. Our safety record",
            "improved again: the lost-time injury rate fell to 0.41 per 200,000 hours worked."},
    };

    @TempDir Path dir;

    @Test
    void textAfterTheNumberStartsWhereTheSourcesDoes() throws Exception {
        String xml = convert(72f);
        List<String> items = listParagraphs(xml);
        assertEquals(3, items.size(), "each numbered paragraph is a Word list item");
        for (String p : items) {
            assertTrue(p.contains("w:firstLine=\"0\"") && p.contains("w:left=\"0\""), "runover lines under the number");
            Matcher tab = Pattern.compile("<w:tab w:val=\"left\" w:pos=\"(\\d+)\"/>").matcher(p);
            assertTrue(tab.find(), "a tab stop where the text starts, not Word's default half inch");
            assertEquals(280, Integer.parseInt(tab.group(1)), 20);
        }
    }

    @Test
    void hangingItemsNeedNoTabStop() throws Exception {
        String xml = convert(86f);
        List<String> items = listParagraphs(xml);
        assertEquals(3, items.size());
        for (String p : items) {
            assertTrue(p.contains("w:hanging="), "runover lines under the text hang");
            assertFalse(p.contains("<w:tabs>"), "the hanging indent already places the text");
        }
    }

    /** Converts the three items with their runover lines starting at {@code runoverX}. */
    private String convert(float runoverX) throws Exception {
        Path pdf = dir.resolve("numbered-" + (int) runoverX + ".pdf");
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (int i = 0; i < ITEMS.length; i++) {
                    for (int l = 0; l < ITEMS[i].length; l++) {
                        cs.beginText();
                        cs.setFont(font, 11);
                        if (l == 0) {
                            cs.newLineAtOffset(72, y);
                            cs.showText((i + 1) + ".");
                            cs.endText();
                            cs.beginText();
                            cs.setFont(font, 11);
                            cs.newLineAtOffset(86, y);
                        } else {
                            cs.newLineAtOffset(runoverX, y);
                        }
                        cs.showText(ITEMS[i][l]);
                        cs.endText();
                        y -= 14.5f;
                    }
                    y -= 10;
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("numbered-" + (int) runoverX + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> listParagraphs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:p>(?:(?!</w:p>).)*?<w:numPr>.*?</w:pPr>").matcher(xml);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
