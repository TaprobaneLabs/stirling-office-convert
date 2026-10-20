package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stirling.software.officeconvert.PdfToDocx;

class OfficeWidthsTest {

    // Caladea's letters against Cambria's, near enough for a stand-in drawn narrower than the family Word uses
    private static final float CALADEA = 0.957f;

    @TempDir Path dir;

    @Test
    void measuresOfficeFamilies() {
        // Cambria's figures are tabular, 0.554 em each; Calibri's 0.507
        assertEquals(55.4f, OfficeWidths.width("0123456789", "Cambria", false, false, 10), 0.2f);
        assertEquals(50.7f, OfficeWidths.width("0123456789", "Calibri", false, false, 10), 0.2f);
        assertTrue(OfficeWidths.width("Harbour", "Cambria", true, false, 11) > OfficeWidths.width("Harbour", "Cambria", false, false, 11));
        assertTrue(Float.isNaN(OfficeWidths.width("abc", "No Such Family", false, false, 10)));
        assertTrue(Float.isNaN(OfficeWidths.width("க", "Cambria", false, false, 10)), "a character the table lacks");
        assertEquals(OfficeWidths.width("Hello", "Georgia", false, false, 12),
                SubstituteMetrics.width("Hello", "Georgia", false, false, 12, true), 0.001f, "a stand-in is measured");
        assertTrue(Float.isNaN(SubstituteMetrics.width("Hello", "Georgia", false, false, 12)),
                "the PDF's own Georgia keeps its widths, which are Word's");
    }

    @Test
    void caladeaTextIsFittedToCambria() throws Exception {
        Path pdf = dir.resolve("caladea.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDTrueTypeFont(caladea()));
            page.setResources(resources);
            StringBuilder content = new StringBuilder();
            String line = "Harbour and Finch Logistics moved containers through its three terminals this year";
            for (int i = 0; i < 20; i++) {
                content.append("BT /F1 11 Tf 72 ").append(720 - i * 14).append(" Td (").append(line).append(") Tj ET ");
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.toString().getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("caladea.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        String styles;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            styles = new String(zip.getInputStream(zip.getEntry("word/styles.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue((xml + styles).contains("w:ascii=\"Cambria\""), "Word draws Caladea text in Cambria");
        Matcher m = Pattern.compile("<w:spacing w:val=\"(-\\d+)\"/>").matcher(xml);
        assertTrue(m.find(), "Cambria is wider, so its characters are drawn closer");
        int twips = Integer.parseInt(m.group(1));
        assertTrue(twips <= -3 && twips >= -8, "about a quarter point at 11 pt: " + twips);
    }

    /** A Caladea that isn't embedded: its name and widths a bit narrower than Cambria's. */
    private static COSDictionary caladea() {
        COSArray widths = new COSArray();
        for (int c = 32; c <= 126; c++) {
            widths.add(COSInteger.get(Math.round(OfficeWidths.width(String.valueOf((char) c), "Cambria", false, false, 1000) * CALADEA)));
        }
        COSDictionary descriptor = new COSDictionary();
        descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, "Caladea-Regular");
        descriptor.setInt(COSName.FLAGS, 34);
        COSArray box = new COSArray();
        for (float v : new float[] {-1100, -270, 2200, 950}) {
            box.add(new COSFloat(v));
        }
        descriptor.setItem(COSName.FONT_BBOX, box);
        descriptor.setInt(COSName.ITALIC_ANGLE, 0);
        descriptor.setInt(COSName.ASCENT, 950);
        descriptor.setInt(COSName.DESCENT, -222);
        descriptor.setInt(COSName.CAP_HEIGHT, 667);
        descriptor.setInt(COSName.STEM_V, 80);
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TRUE_TYPE);
        font.setName(COSName.BASE_FONT, "Caladea-Regular");
        font.setInt(COSName.FIRST_CHAR, 32);
        font.setInt(COSName.LAST_CHAR, 126);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);
        font.setItem(COSName.FONT_DESC, descriptor);
        return font;
    }
}
