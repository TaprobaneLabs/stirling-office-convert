package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A press release whose pages end in a row of agency logos, below the last line of text. */
class FootPicturesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String SENTENCE = "Privately-owned housing units authorized by building permits in August were at a "
            + "seasonally adjusted annual rate of 1,312,000, which is 3.7 percent below the revised July rate. ";

    @TempDir Path dir;

    @Test
    void logosRightUnderTheTextFollowIt() throws Exception {
        String xml = convert(true);
        assertFalse(xml.contains("<wp:inline"), "no logo sits in the text flow, where Word moves it to the next page when "
                + "it doesn't fit above the bottom margin");
        for (String anchor : logos(xml)) {
            assertTrue(anchor.contains("<wp:positionV relativeFrom=\"paragraph\">"),
                    "anchored where the text ends, so it keeps its distance below it: " + anchor);
            assertTrue(anchor.contains("<wp:wrapNone/>"), "it may reach into the bottom margin: " + anchor);
        }
    }

    @Test
    void logosApartFromTheTextStayWhereThePdfHasThem() throws Exception {
        String xml = convert(false);
        assertFalse(xml.contains("<wp:inline"), "no logo sits in the text flow");
        for (String anchor : logos(xml)) {
            assertTrue(anchor.contains("<wp:positionV relativeFrom=\"page\">"), "placed on the page: " + anchor);
            assertTrue(anchor.contains("<wp:wrapTopAndBottom/>"), "text that runs on to it goes below: " + anchor);
        }
    }

    private static java.util.List<String> logos(String xml) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("<wp:anchor [^>]*>(?:(?!</wp:anchor>).)*<pic:pic").matcher(xml);
        while (m.find()) {
            out.add(m.group());
        }
        assertEquals(2, out.size(), "both logos are placed");
        return out;
    }

    /** A page of text with two logos at its foot, right under the last line or with the lower third of the page between. */
    private String convert(boolean under) throws Exception {
        Path pdf = dir.resolve("release.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 740;
                text(cs, 72, y, 14, "MONTHLY NEW RESIDENTIAL CONSTRUCTION");
                y -= 30;
                for (int p = 0; p < (under ? 9 : 6); p++) {
                    for (String line : lines(SENTENCE.repeat(2), 468, 10)) {
                        text(cs, 72, y, 10, line);
                        y -= 13;
                    }
                    y -= 13;
                }
                float top = under ? y + 18 : 90;
                cs.drawImage(LosslessFactory.createFromImage(doc, logo(new Color(0x1F4E79))), 72, top - 48, 140, 48);
                cs.drawImage(LosslessFactory.createFromImage(doc, logo(new Color(0x2E7D32))), 470, top - 60, 70, 60);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("release.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static java.util.List<String> lines(String text, float width, float size) throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (FONT.getStringWidth(next) / 1000f * size > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        out.add(line.toString());
        return out;
    }

    private static BufferedImage logo(Color colour) {
        BufferedImage img = new BufferedImage(140, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 140, 60);
        g.setColor(colour);
        g.fillOval(4, 4, 52, 52);
        g.fillRect(64, 18, 70, 8);
        g.fillRect(64, 34, 50, 8);
        g.dispose();
        return img;
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
