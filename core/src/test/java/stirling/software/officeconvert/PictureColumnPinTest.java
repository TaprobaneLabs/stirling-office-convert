package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

class PictureColumnPinTest {

    @TempDir Path dir;

    private static final String[] WORDS = ("we replaced forty six diesel yard tractors with electric models charged from the "
            + "warehouse roof panels and the new routing system cut the average dwell time at the terminal").split(" ");

    @Test
    void aColumnHoldingAPictureIsStillPinned() throws Exception {
        // A newsletter's page: the left column holds a chart between its paragraphs and ends a line short of the page's
        // foot, as the PDF breaks it; the right column starts a paragraph of its own at the top
        Path pdf = dir.resolve("newsletter.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = paragraph(cs, font, 54, 240, 80, 18, 0, h);
                cs.drawImage(chart(doc), 70, h - (y + 90), 160, 90);
                y = paragraph(cs, font, 54, 240, y + 100, 26, 5, h);
                text(cs, font, 54, h - y, "Every invoice now");
                paragraph(cs, font, 318, 240, 80, 40, 9, h);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("newsletter.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        int end = xml.indexOf("Every invoice now");
        assertTrue(end >= 0);
        assertTrue(xml.indexOf("<w:br w:type=\"column\"/>", end) >= 0,
                "a column break ends the left column after its last line, so Word can't pull the right column's lines into it");
    }

    private static PDImageXObject chart(PDDocument doc) throws IOException {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 320, 180);
        g.setColor(new Color(30, 90, 180));
        for (int i = 0; i < 7; i++) {
            int bar = 40 + 18 * i;
            g.fillRect(20 + 42 * i, 170 - bar, 30, bar);
        }
        g.dispose();
        return LosslessFactory.createFromImage(doc, img);
    }

    /** Ragged left-aligned lines of about the column's width, from word {@code seed} of the list on. */
    private static float paragraph(PDPageContentStream cs, PDType1Font font, float x, float width, float y, int lines, int seed,
            float h) throws IOException {
        int at = seed;
        for (int i = 0; i < lines; i++) {
            StringBuilder line = new StringBuilder();
            while (true) {
                String next = WORDS[at % WORDS.length];
                String tried = line.isEmpty() ? next : line + " " + next;
                if (font.getStringWidth(tried) / 1000 * 10 > width - 12) {
                    break;
                }
                line.setLength(0);
                line.append(tried);
                at++;
            }
            text(cs, font, x + (i == 0 ? 10 : 0), h - y, line.toString());
            y += 12;
        }
        return y + 6;
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
