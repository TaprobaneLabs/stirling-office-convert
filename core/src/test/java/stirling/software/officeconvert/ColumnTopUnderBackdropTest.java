package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
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
 * The second of two columns that flow on starts where the PDF's does, though a picture behind the page comes first in it.
 * Word drops a paragraph's space above at the top of a column, so the gap above the column's first paragraph is a
 * paragraph of its own; a backdrop listed first in the column had kept that from happening, and USGS Fact Sheet 2024-3036's
 * second column started under its banner, over its title.
 */
class ColumnTopUnderBackdropTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final String FIRST = "Second column begins here with the history of the sunspot";

    @TempDir Path dir;

    @Test
    void theSecondColumnsGapIsAParagraphOfItsOwn() throws Exception {
        String xml = convert();
        assertEquals(1, Pattern.compile("<w:cols w:num=\"2\"").matcher(xml).results().count(), "one two-column section");
        List<String> paras = new ArrayList<>();
        Matcher p = Pattern.compile("<w:p[ >].*?</w:p>", Pattern.DOTALL).matcher(xml);
        while (p.find()) {
            paras.add(p.group());
        }
        int first = -1;
        for (int i = 0; i < paras.size(); i++) {
            if (paras.get(i).contains(FIRST)) {
                first = i;
                break;
            }
        }
        assertTrue(first > 0, "the second column's first paragraph");
        String para = paras.get(first);
        Matcher before = Pattern.compile("w:before=\"(\\d+)\"").matcher(para);
        assertTrue(!before.find() || Integer.parseInt(before.group(1)) == 0, "no space above it, which Word drops: " + para);
        String above = paras.get(first - 1);
        assertTrue(above.replaceAll("<[^>]+>", "").isBlank() && above.contains("w:lineRule=\"exact\""),
                "the gap above it is a paragraph of its own: " + above);
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float w = PDRectangle.LETTER.getWidth();
            float h = PDRectangle.LETTER.getHeight();
            PDImageXObject paper = LosslessFactory.createFromImage(doc, image(new Color(250, 248, 240), 20, 26));
            PDImageXObject photo = LosslessFactory.createFromImage(doc, image(new Color(40, 90, 150), 230, 120));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(paper, 0, 0, w, h);
                text(cs, 50, h - 90, 24, "Hazards of the Solar Cycle for Power Grids");
                float top = h - 150;
                int lines = 50;
                for (int i = 0; i < lines; i++) {
                    float y = top - i * 12;
                    if (i >= 20 && i < 30) {
                        continue;
                    }
                    text(cs, 50, y, 10, "Column one runs on with line " + (i + 1) + " of the solar cycle text");
                }
                cs.drawImage(photo, 50, top - 30 * 12 + 8, 230, 110);
                text(cs, 300, top, 10, FIRST);
                for (int i = 1; i < lines; i++) {
                    text(cs, 300, top - i * 12, 10, "Column two carries on with line " + (i + 1) + " of the storm history");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static BufferedImage image(Color c, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.setColor(c.darker());
        g.drawLine(0, 0, w - 1, h - 1);
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
