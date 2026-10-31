package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageNumberStartTest {

    private static final String TITLE = "Federal Register / Vol. 90, No. 57 / Rules and Regulations";

    @TempDir Path dir;

    @Test
    void aPrintersFrameCountDoesNotNumberThePages() throws Exception {
        // A Federal Register rule from page 13687: the number at the header's outer edge (right on odd pages, left on even
        // ones), and at each page's foot the printer's line, which counts its frames from 2 with leading zeros
        Path pdf = dir.resolve("rule.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDType1Font plain = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font small = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float h = PDRectangle.LETTER.getHeight();
            for (int i = 0; i < 8; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                int number = 13687 + i;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    if (number % 2 == 1) {
                        text(cs, small, 9, 160, h - 40, TITLE);
                        text(cs, bold, 11, 540, h - 40, Integer.toString(number));
                    } else {
                        text(cs, bold, 11, 40, h - 40, Integer.toString(number));
                        text(cs, small, 9, 160, h - 40, TITLE);
                    }
                    for (int l = 0; l < 40; l++) {
                        text(cs, plain, 10, 40, h - 80 - 15 * l, l % 2 == 0 ? "the amendment revises the safety zone"
                                : "for the boat race held on the river");
                    }
                    text(cs, small, 6, 60, 20, "Jkt 265001 PO 00000 Frm %05d Fmt 4700 Sfmt 4700".formatted(i + 2));
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("rule.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            String body = entry(zip, "word/document.xml");
            Matcher start = Pattern.compile("<w:pgNumType w:start=\"(\\d+)\"").matcher(body);
            assertTrue(start.find(), "the pages are numbered from the header's number");
            assertEquals("13687", start.group(1));
            // Word picks a page's header by its number: page 13687 is odd, so the default header has its number on the right
            String odd = header(zip, body, "default");
            String even = header(zip, body, "even");
            assertTrue(odd.indexOf("PAGE") > odd.indexOf("Federal Register"), "odd pages' number on the right");
            assertTrue(even.indexOf("PAGE") < even.indexOf("Federal Register"), "even pages' number on the left");
            String footers = String.join("", zip.stream().filter(e -> e.getName().startsWith("word/footer"))
                    .map(e -> entry(zip, e.getName())).toList());
            assertTrue(Pattern.compile("Frm 0000\\d").matcher(footers).find(), "the frame count stays the PDF's text");
            assertFalse(footers.contains("PAGE"), "the frame count is no page number");
        }
    }

    private static String header(ZipFile zip, String body, String type) {
        Matcher ref = Pattern.compile("<w:headerReference w:type=\"" + type + "\" r:id=\"([^\"]+)\"").matcher(body);
        assertTrue(ref.find(), type + " header");
        String rels = entry(zip, "word/_rels/document.xml.rels");
        Matcher target = Pattern.compile("Id=\"" + ref.group(1) + "\"[^>]*Target=\"([^\"]+)\"").matcher(rels);
        assertTrue(target.find(), type + " header's part");
        return entry(zip, "word/" + target.group(1).replaceFirst("^/?word/", ""));
    }

    private static String entry(ZipFile zip, String name) {
        try {
            return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
