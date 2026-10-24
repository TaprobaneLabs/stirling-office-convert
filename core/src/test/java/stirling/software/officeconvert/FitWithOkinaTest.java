package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSName;
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
 * A paragraph set tighter than Word would set it is fitted to its PDF lines though a few of its characters are missing from
 * the widths the fit knows, when the PDF embeds a face Word has: the PDF's widths are Word's. Samoan's ʻokina (U+02BB) in
 * Times New Roman kept USGS Fact Sheet 2023-3022's photo captions unfitted, and Word ran each a line longer. Here the face
 * is Arial (PDFBox's Liberation Sans, metric-compatible, named as Arial), whose ʻokina the widths lack too.
 */
class FitWithOkinaTest {

    private static final float SIZE = 9.5f;
    /** The PDF's word space, tighter than Arial's 0.278 em that Word sets. */
    private static final float SPACE = 1.8f;
    /** How wide the caption's lines run: the widest text on the page, so its column ends with them. */
    private static final float LINE = 470;
    private static final String[] WORDS = ("O lo\u02BBo galulue fa\u02BBatasi le ofisa o le Geological Survey le ofisa o le National Oceanic "
            + "and Atmospheric Administration Ofisa Va\u02BBai Tau le ofisa o Puipuiga o le Saogalemu Fa\u02BBalotoifale "
            + "a Amerika Samoa ma tagata-nu\u02BBu e fa\u02BBapipi\u02BBi masini e mata\u02BBituina gaioiga o mauga i le "
            + "motu o Ta\u02BBu ia Aukuso 2022 Ata pu\u02BBeina e Elinor Lutu-McMoore.").split(" ");

    @TempDir Path dir;

    @Test
    void aTightParagraphWithOkinasIsFitted() throws Exception {
        String xml = convert();
        int at = xml.indexOf("galulue");
        assertTrue(at > 0, "the caption is there");
        String para = xml.substring(xml.lastIndexOf("<w:p>", at), xml.indexOf("</w:p>", at));
        assertTrue(para.contains("ʻ"), "the okina reads as U+02BB: " + para);
        Matcher condensed = Pattern.compile("<w:spacing w:val=\"-\\d+\"/>").matcher(para);
        assertTrue(condensed.find(), "the caption is fitted to its lines, its spacing condensed: " + para);
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("caption.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            PDType1Font times = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDFont caption = arial(doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 20; i++) {
                    cs.beginText();
                    cs.setFont(times, 10);
                    cs.newLineAtOffset(36, h - 72 - 12 * i);
                    cs.showText("Body text line " + (i + 1) + " above the caption");
                    cs.endText();
                }
                // The caption's lines, each word placed on its own with the PDF's tight space before the next.
                int w = 0;
                for (int i = 0; w < WORDS.length; i++) {
                    float x = 36;
                    while (w < WORDS.length) {
                        float width = caption.getStringWidth(WORDS[w]) / 1000 * SIZE;
                        if (x > 36 && x + width > 36 + LINE) {
                            break;
                        }
                        cs.beginText();
                        cs.setFont(caption, SIZE);
                        cs.newLineAtOffset(x, h - 340 - 12 * i);
                        cs.showText(WORDS[w++]);
                        cs.endText();
                        x += width + SPACE;
                    }
                }
                cs.beginText();
                cs.setFont(times, 10);
                cs.newLineAtOffset(36, h - 440);
                cs.showText("A closing line of body text under the caption.");
                cs.endText();
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("caption.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** PDFBox's own Liberation Sans, embedded and named as Arial is, so it reads as the face Word has. */
    private static PDFont arial(PDDocument doc) throws IOException {
        try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(doc, in, false);
            font.getCOSObject().setName(COSName.BASE_FONT, "ArialMT");
            return font;
        }
    }
}
