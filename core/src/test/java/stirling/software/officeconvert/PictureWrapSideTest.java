package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Text wraps round a picture only on the sides the PDF sets it, on pages without flowing columns. Square wrapping on both
 * sides let Word set a word in the room between a picture and the page's margin: the first word of each line beside USGS
 * Fact Sheet 2024-3033's icons, which stand inside a column that starts left of them, went to the icon's left. Where
 * columns flow, a picture's anchor can run on into the next column, and wrapping on one side then pushed that column's
 * text below the picture (an arXiv paper gained a page), so text there still wraps on both sides.
 */
class PictureWrapSideTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final String[] BESIDE = {
        "Research and planning. The survey can lead the research",
        "on the fish and bring its partners together to set up a",
        "plan for understanding how the population is changing",
        "and informing how it is managed in the rivers that feed",
        "the bay, with the states and the fishing community.",
    };
    private static final String[] BELOW = {
        "The survey keeps watch over the rivers that feed the bay with gauges and sampling stations that send their",
        "readings to the science centers each day, and staff review the records for signs that the fish are moving into",
        "new waters or that their numbers are rising faster than the managers expected when the plan was first set out.",
    };

    @TempDir Path dir;

    @Test
    void textOnAPicturesRightWrapsOnItsRightOnly() throws Exception {
        assertEquals(List.of("right"), wraps(convert(false)), "the icon's text runs on its right only");
    }

    @Test
    void textOnAPicturesLeftWrapsOnItsLeftOnly() throws Exception {
        assertEquals(List.of("left"), wraps(convert(true)), "the icon's text runs on its left only");
    }

    @Test
    void wherePageColumnsFlowTextWrapsOnBothSides() throws Exception {
        assertEquals(List.of("bothSides"), wraps(convertColumns()), "the picture in a flowing column keeps both sides");
    }

    private static List<String> wraps(String xml) {
        Matcher m = Pattern.compile("<wp:wrapSquare wrapText=\"(\\w+)\"").matcher(xml);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        assertTrue(!out.isEmpty(), "the icon is a picture text wraps round");
        return out;
    }

    /**
     * A heading and a paragraph that run from the left margin to the right, and between them an icon 54 pt in from one
     * side of that column with five lines beside it, on its right or (on the right) on its left.
     */
    private String convert(boolean pictureOnRight) throws IOException {
        Path pdf = dir.resolve("icon.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            BufferedImage icon = new BufferedImage(60, 70, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = icon.createGraphics();
            g.setColor(new Color(40, 90, 150));
            g.fillRect(0, 0, 60, 70);
            g.setColor(new Color(230, 180, 60));
            g.fillOval(10, 15, 40, 40);
            g.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(doc, icon);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 14, 36, 720, "Agency programs for the rivers that feed the bay");
                float pictureX = pictureOnRight ? 576 - 54 - 60 : 36 + 54;
                cs.drawImage(image, pictureX, 600, 60, 70);
                float textX = pictureOnRight ? 36 : pictureX + 60 + 15;
                for (int i = 0; i < BESIDE.length; i++) {
                    text(cs, 10, textX, 658 - 12 * i, BESIDE[i]);
                }
                for (int i = 0; i < BELOW.length; i++) {
                    text(cs, 10, 36, 570 - 12 * i, BELOW[i]);
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("icon.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Two columns that run the page's height, the first flowing on into the second, and in the first an icon at the
     * column's left edge with the lines beside it on its right.
     */
    private String convertColumns() throws IOException {
        Path pdf = dir.resolve("columns.pdf");
        String[] words = (String.join(" ", BELOW) + " " + String.join(" ", BESIDE)).split(" ");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            BufferedImage icon = new BufferedImage(60, 70, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = icon.createGraphics();
            g.setColor(new Color(40, 90, 150));
            g.fillRect(0, 0, 60, 70);
            g.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(doc, icon);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 36, 670, 60, 70);
                int w = 0;
                for (int col = 0; col < 2; col++) {
                    float left = col == 0 ? 36 : 324;
                    for (float y = 730; y >= 60; y -= 12) {
                        boolean beside = col == 0 && y > 660;
                        float x = beside ? left + 72 : left;
                        float room = (beside ? 252 - 72 : 252) - 6;
                        StringBuilder line = new StringBuilder(words[w++ % words.length]);
                        while (width(line + " " + words[w % words.length]) <= room) {
                            line.append(' ').append(words[w++ % words.length]);
                        }
                        text(cs, 10, x, y, line.toString());
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("columns.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float width(String s) {
        try {
            return FONT.getStringWidth(s) / 1000f * 10;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void text(PDPageContentStream cs, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
