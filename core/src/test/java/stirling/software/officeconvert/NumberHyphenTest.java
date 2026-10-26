package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A phone number the PDF keeps whole within a line stays whole in Word. Word breaks after any hyphen where its room runs out,
 * and a number split at a line's end reads as one run of digits ("1-800-232-" and "4636" read as 2324636): CDC's HPV sheet
 * and the IRS's Spanish Publication 1 lost their phone numbers where Word's lines came out longer than the PDF's.
 */
class NumberHyphenTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();

    @TempDir Path dir;

    @Test
    void hyphensInsideANumberDontBreakInWord() throws Exception {
        String xml = docx();
        assertTrue(xml.contains("232</w:t><w:noBreakHyphen/>"), "the hyphens inside the number don't break: " + around(xml, "232"));
        assertFalse(xml.contains("Center 1-800"), "nor do the ones before 800");
    }

    @Test
    void aHyphenWhereThePdfBreaksTheNumberStillBreaks() throws Exception {
        String xml = docx();
        assertTrue(xml.contains("829-3676"), "the PDF breaks the line after 829-, so Word may too: " + around(xml, "3676"));
    }

    @Test
    void textAndRtfKeepTheirHyphens() throws Exception {
        Path pdf = pdf();
        Path txt = dir.resolve("sheet.txt");
        PdfToText.convert(pdf, txt, PdfToDocx.Options.defaults());
        assertTrue(Files.readString(txt, StandardCharsets.UTF_8).contains("1-800-232-4636"), "plain hyphens in text");
        Path rtf = dir.resolve("sheet.rtf");
        PdfToRtf.convert(pdf, rtf, PdfToDocx.Options.defaults());
        assertTrue(Files.readString(rtf, StandardCharsets.US_ASCII).contains("232\\_4636"), "RTF's non-breaking hyphen");
    }

    private static String around(String xml, String s) {
        int i = xml.indexOf(s);
        return i < 0 ? "(missing)" : xml.substring(Math.max(0, i - 120), Math.min(xml.length(), i + 60));
    }

    private String docx() throws IOException {
        Path docx = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf(), docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path pdf() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        if (Files.exists(pdf)) {
            return pdf;
        }
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            String[] lines = {
                "For more information about the vaccine, talk with your doctor or nurse, or call the",
                "CDC-INFO Contact Center 1-800-232-4636 any day of the week, at any hour, for free",
                "advice in English and Spanish. Forms and publications can be ordered at 1-800-829-",
                "3676 or downloaded from the agency's website, where they are kept up to date.",
            };
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.length; i++) {
                    cs.beginText();
                    cs.setFont(FONT, 10);
                    cs.newLineAtOffset(72, H - 100 - 13 * i);
                    cs.showText(lines[i]);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }
}
