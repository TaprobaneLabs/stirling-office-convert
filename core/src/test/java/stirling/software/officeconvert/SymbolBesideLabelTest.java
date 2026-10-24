package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A symbol drawn beside a label, from rings and dots that overlap one another, stays a drawing: its small parts aren't
 * bullets. USGS Fact Sheet 2023-3022's earthquake symbol, left of a figure's label, made 13 bullets before the label,
 * which pushed it along and onto a second line in Word. Bullets drawn alone before list items still make a list.
 */
class SymbolBesideLabelTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float K = 0.5523f;

    @TempDir Path dir;

    @Test
    void theSymbolsPartsAreNotBullets() throws Exception {
        String xml = convert();
        Element label = paragraph(xml, "Volcanic unrest");
        assertFalse(label.getTextContent().contains("•"), "no bullets before the label: " + label.getTextContent());
        assertTrue(label.getElementsByTagName("w:numPr").getLength() == 0, "the label isn't a list item");
        Element item = paragraph(xml, "Seismometers record");
        assertTrue(item.getElementsByTagName("w:numPr").getLength() > 0 || item.getTextContent().startsWith("•"),
                "a bullet drawn alone before a list item still makes it one");
    }

    private static Element paragraph(String xml, String words) throws Exception {
        NodeList ps = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:p");
        for (int i = 0; i < ps.getLength(); i++) {
            if (ps.item(i).getTextContent().contains(words)) {
                return (Element) ps.item(i);
            }
        }
        throw new AssertionError("no paragraph holds " + words);
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("figure.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 720, 14, "Types of volcanic activity");
                // The illustration the labels sit on: a lava stream, with text running across its edge
                cs.setNonStrokingColor(0.95f, 0.6f, 0.2f);
                cs.moveTo(100, 600);
                cs.curveTo(150, 700, 250, 600, 330, 700);
                cs.lineTo(340, 690);
                cs.curveTo(260, 590, 160, 690, 110, 590);
                cs.closePath();
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                text(cs, 120, 690, 10, "Lava flows downhill from the vent toward the sea in several streams.");
                text(cs, 120, 612, 10, "Ash and gas rise from the vent and drift with the wind for miles.");
                // The symbol: three rings and a few stones, centred left of the label
                float cx = 160;
                float cy = 653;
                cs.setLineWidth(0.6f);
                for (float r : new float[] {2.5f, 4.5f, 6.5f}) {
                    circle(cs, cx, cy, r);
                    cs.stroke();
                }
                cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
                for (float[] d : new float[][] {{-4, 3}, {3, -4}, {5, 2}, {-2, -5}}) {
                    circle(cs, cx + d[0], cy + d[1], 1.4f);
                    cs.fill();
                }
                cs.setNonStrokingColor(0f, 0f, 0f);
                text(cs, 175, 650, 8, "Volcanic unrest");
                // A list whose bullets are drawn circles
                String[] items = {
                    "Seismometers record the small earthquakes that come before most eruptions.",
                    "Tiltmeters show the ground swelling as magma rises under the summit.",
                    "Gas sensors measure the sulfur dioxide the volcano gives off each day.",
                };
                for (int i = 0; i < items.length; i++) {
                    float y = 560 - 18 * i;
                    circle(cs, 76, y + 3.5f, 2f);
                    cs.fill();
                    text(cs, 86, y, 10, items[i]);
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("figure.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void circle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = K * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
