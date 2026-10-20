package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** A report's contents and list of figures: unnumbered entries in one style, each led by dots to its page number. */
class ContentsEntriesTest {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final String[][] ENTRIES = {
        {"Appendix A. CSF Core", "15"},
        {"Appendix B. CSF Tiers", "24"},
        {"Appendix C. Glossary", "26"},
    };
    private static final String[][] FIGURES = {
        {"Fig. 1. CSF Core structure", "3"},
        {"Fig. 2. CSF Functions", "5"},
        {"Fig. 3. Steps for creating and using a CSF Organizational Profile", "6"},
        {"Fig. 4. CSF Tiers for cybersecurity risk governance and management", "8"},
    };

    @TempDir Path dir;

    @Test
    void eachEntryIsItsOwnParagraph() throws Exception {
        String xml = convert();
        for (String[][] list : new String[][][] {ENTRIES, FIGURES}) {
            for (String[] entry : list) {
                Matcher p = Pattern.compile("<w:p>(?:(?!</w:p>).)*>" + Pattern.quote(entry[0]) + "<(?:(?!</w:p>).)*</w:p>")
                        .matcher(xml);
                assertTrue(p.find(), "a paragraph for " + entry[0]);
                String text = p.group().replaceAll("<w:tab/>", "\t").replaceAll("<[^>]+>", "");
                assertEquals(entry[0] + "\t" + entry[1], text, "the entry and its page number alone");
                assertTrue(p.group().contains("w:leader=\"dot\""), "led by dots to its page number: " + p.group());
            }
        }
    }

    private String convert() throws Exception {
        Path pdf = dir.resolve("contents.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 72, 700, 13, "Table of Contents");
                float y = 676;
                for (String[] e : ENTRIES) {
                    entry(cs, y, e[0], e[1]);
                    y -= 18.5f;
                }
                text(cs, 72, y - 40, 13, "List of Figures");
                y -= 64;
                for (String[] e : FIGURES) {
                    entry(cs, y, e[0], e[1]);
                    y -= 18.5f;
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("contents.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The entry at the left margin, dots to just before its page number, and the number flush right at 540. */
    private static void entry(PDPageContentStream cs, float y, String title, String number) throws IOException {
        float size = 10;
        text(cs, 72, y, size, title);
        float numberX = 540 - BOLD.getStringWidth(number) / 1000f * size;
        float dot = BOLD.getStringWidth(".") / 1000f * size;
        float x = 72 + BOLD.getStringWidth(title) / 1000f * size + 2;
        StringBuilder dots = new StringBuilder();
        while (x + (dots.length() + 1) * dot < numberX - 2) {
            dots.append('.');
        }
        text(cs, x, y, size, dots.toString());
        text(cs, numberX, y, size, number);
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(BOLD, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
