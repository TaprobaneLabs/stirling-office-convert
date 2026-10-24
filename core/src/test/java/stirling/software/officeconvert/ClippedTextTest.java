package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * Text the page clips away whole isn't read. USGS Fact Sheet 2023-3022 places a figure whose file carries a label,
 * "AAXXXX_fig 01", below the rectangle the page clips the figure to: never shown, it was read as the last words of the
 * paragraph it lies over, and cost Word a line.
 */
class ClippedTextTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    @TempDir Path dir;

    @Test
    void textOutsideTheClipIsLeftOut() throws Exception {
        String xml = convert();
        assertFalse(xml.contains("AAXXXX"), "the label the clip hides isn't read");
        assertTrue(xml.contains("Lava flows"), "the figure's label inside the clip is");
        assertTrue(xml.contains("Edge label"), "a label the clip's edge crosses is");
        assertTrue(xml.contains("the summit craters."), "the paragraph under the hidden label keeps its words");
    }

    /**
     * A combining mark has no advance and draws to the left of its origin, over the letter before it: with its origin just
     * past the clip's edge, the clip still shows it.
     */
    @Test
    void anAccentTheClipShowsIsKept() throws Exception {
        Path pdf = dir.resolve("accent.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont sans;
            try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                sans = PDType0Font.load(doc, in, false);
            }
            float size = 40;
            // The accent's origin, where "Cafe" ends: 3 pt past the clip's right edge, its ink to the left, inside
            float end = 303;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.saveGraphicsState();
                cs.addRect(72, 470, 228, 230);
                cs.clip();
                cs.beginText();
                cs.setFont(sans, size);
                cs.newLineAtOffset(end - sans.getStringWidth("Cafe") / 1000 * size, 600);
                cs.showText("Cafe\u0301");
                cs.endText();
                cs.restoreGraphicsState();
                text(cs, 72, 430, "A line of body text under the clipped figure, to give the page a paragraph.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("accent.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(xml.contains("Caf\u00e9") || xml.contains("Cafe\u0301"), "the accent the clip shows is kept");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // The figure: clipped to 72..540 x 470..700 (from the page's foot), its file's label below that
                cs.saveGraphicsState();
                cs.addRect(72, 470, 468, 230);
                cs.clip();
                cs.setNonStrokingColor(0.85f, 0.9f, 0.95f);
                cs.addRect(72, 470, 468, 230);
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                text(cs, 120, 600, "Lava flows");
                text(cs, 500, 520, "Edge label runs past the clip");
                text(cs, 75, 428, "AAXXXX_fig 01");
                cs.restoreGraphicsState();
                String[] body = {
                    "The survey keeps watch over every volcano on the islands with seismometers, tiltmeters and",
                    "cameras that send their readings to the observatory each minute through the day and night,",
                    "while staff review the records for signs that magma is rising toward the summit craters.",
                    "Small swarms of earthquakes often come before an eruption begins on any of the islands, and",
                    "the observatory tells the territory's emergency managers when the alert level changes.",
                };
                for (int i = 0; i < body.length; i++) {
                    text(cs, 72, 452 - 14 * i, body[i]);
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

    private static void text(PDPageContentStream cs, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
