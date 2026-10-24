package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A break point a PDF marks with a zero-width space stays inside its word. USGS fact sheets set each DOI's break points as a
 * space glyph whose ActualText is U+200B, with the next character drawn where the space starts. The space came out after
 * that character, as a space of its own: "doi.org/1 0.1029/2 019SW002250", which split the DOI's numbers and widened its
 * line, so Word set many references a line longer.
 */
class ZeroWidthBreakTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float SIZE = 9;
    private static final String[] PARTS = {"https://doi.org/", "10.1029/", "2019SW002250."};

    @TempDir Path dir;

    @Test
    void aDoisBreakPointsStayInsideIt() throws Exception {
        String text = convert().replaceAll("<[^>]+>", "");
        String joined = String.join("", PARTS);
        assertTrue(text.replace("​", "").contains(joined), "the DOI reads whole: " + excerpt(text));
        assertTrue(!text.contains("doi.org/1 ") && !text.contains("doi.org/​ ") && !text.contains("doi.org/1​ "),
                "no space inside the DOI: " + excerpt(text));
    }

    private static String excerpt(String text) {
        int i = text.indexOf("doi.org");
        return i < 0 ? text : text.substring(Math.max(0, i - 20), Math.min(text.length(), i + 60));
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("refs.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 46, h - 80, "Love, J.J., Hayakawa, H., and Cliver, E.W., 2019, Intensity and impact of the");
                text(cs, 56, h - 90, "New York Railroad Superstorm of May 1921: Space Weather, v. 17, no. 8,");
                float x = 56;
                String lead = "p. 1281–1292, accessed June 2024 at ";
                text(cs, x, h - 100, lead);
                x += width(lead);
                for (int i = 0; i < PARTS.length; i++) {
                    if (i > 0) {
                        // A space glyph standing for a zero-width space, the next part drawn where it starts: in the
                        // USGS sheets a hair to its left (0.001 to 0.008 pt), as each is placed on its own.
                        COSDictionary span = new COSDictionary();
                        span.setString(COSName.ACTUAL_TEXT, "​");
                        cs.beginMarkedContent(COSName.getPDFName("Span"), PDPropertyList.create(span));
                        text(cs, x + 0.008f, h - 100, " ");
                        cs.endMarkedContent();
                    }
                    text(cs, x, h - 100, PARTS[i]);
                    x += width(PARTS[i]);
                }
                text(cs, 46, h - 114, "Love, J.J., and Lewis, K.A., 2021, Geomagnetic monitoring in the mid-Atlantic");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("refs.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float width(String s) throws IOException {
        return FONT.getStringWidth(s) / 1000 * SIZE;
    }

    private static void text(PDPageContentStream cs, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, SIZE);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
