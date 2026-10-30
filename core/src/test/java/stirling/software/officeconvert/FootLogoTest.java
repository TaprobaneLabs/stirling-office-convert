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

class FootLogoTest {

    @TempDir Path dir;

    @Test
    void aLogoAndItsLinesAtThePagesFootStayPut() throws Exception {
        // MMWR's first page: two columns of text, then at the foot HHS's and CDC's logos with their names beside them
        Path pdf = dir.resolve("report.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font plain = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font sans = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 44; i++) {
                    float baseline = 90 + 12.5f * i;
                    text(cs, plain, 10, 36, h - baseline, "the outbreak was linked to raw morels served at");
                    text(cs, plain, 10, 318, h - baseline, "restaurant patrons who ate the special sushi roll");
                }
                cs.drawImage(image(doc), 144, h - 757, 105, 59.5f);
                text(cs, sans, 10, 257.4f, h - 722, "U.S. Department of Health and Human Services");
                text(cs, plain, 10, 257.4f, h - 734.5f, "Centers for Disease Control and Prevention");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("report.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : new String[] {"Department of Health and Human Services", "Centers for Disease Control and Prevention"}) {
            int at = xml.indexOf(line);
            assertTrue(at >= 0, line);
            int box = xml.lastIndexOf("<w:txbxContent", at);
            assertTrue(box >= 0 && xml.indexOf("</w:txbxContent>", box) > at,
                    "\"" + line + "\" is placed in a text box at the foot, not in the flow under the columns");
        }
    }

    private static PDImageXObject image(PDDocument doc) throws IOException {
        BufferedImage img = new BufferedImage(210, 119, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 210, 119);
        g.setColor(new Color(20, 20, 20));
        g.fillRoundRect(4, 4, 200, 110, 30, 30);
        g.setColor(Color.WHITE);
        g.fillOval(30, 20, 70, 70);
        g.dispose();
        return LosslessFactory.createFromImage(doc, img);
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
