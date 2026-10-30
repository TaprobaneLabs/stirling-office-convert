package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NumberedHeadingPinTest {

    @TempDir Path dir;

    /** The left column's centre: its lines run from 54 pt to about 288. */
    private static final float CENTRE = 171f;

    private static final String[] WORDS = ("the dataset holds short instructions for home appliances and robots each labelled "
            + "with the intent it carries so that a model can learn which command a user meant").split(" ");

    @Test
    void aColumnWithANumberedHeadingIsStillPinned() throws Exception {
        // An IEEE paper's two columns: the left one holds numbered section headings, centred ("I. INTRODUCTION"), which
        // are list items; the right one starts a paragraph of its own at the top
        String xml = convert((cs, times, h) -> {
            float y = 80;
            y = heading(cs, times, "I. INTRODUCTION", CENTRE, y, h);
            y = paragraph(cs, times, 54, 246, y, 20, 0, h);
            y = heading(cs, times, "II. PROPOSED SYSTEM", CENTRE, y + 6, h);
            y = paragraph(cs, times, 54, 246, y, 21, 7, h);
            y = heading(cs, times, "III. DATASETS", CENTRE, y + 6, h);
            paragraph(cs, times, 54, 246, y, 7, 3, h);
            paragraph(cs, times, 318, 246, 80, 52, 5, h);
        });
        int datasets = xml.indexOf("DATASETS");
        assertTrue(datasets >= 0);
        assertTrue(xml.indexOf("<w:br w:type=\"column\"/>", datasets) >= 0,
                "a column break ends the left column after its last heading, so Word can't pull the right column's lines into it");
    }

    private interface Drawing {
        void draw(PDPageContentStream cs, PDType1Font times, float h) throws IOException;
    }

    private String convert(Drawing drawing) throws Exception {
        Path pdf = dir.resolve("paper.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawing.draw(cs, new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), PDRectangle.LETTER.getHeight());
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("paper.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A numbered heading, its number half an em before its text as IEEE sets them, which makes it a list item. */
    private static float heading(PDPageContentStream cs, PDType1Font font, String text, float centre, float y, float h)
            throws IOException {
        int space = text.indexOf(' ');
        String number = text.substring(0, space);
        String title = text.substring(space + 1);
        float numberWidth = font.getStringWidth(number) / 1000 * 10;
        float w = numberWidth + 5 + font.getStringWidth(title) / 1000 * 10;
        text(cs, font, centre - w / 2, h - y, number);
        text(cs, font, centre - w / 2 + numberWidth + 5, h - y, title);
        return y + 18;
    }

    /** Ragged left-aligned lines of about the column's width, from word {@code seed} of the list on. */
    private static float paragraph(PDPageContentStream cs, PDType1Font font, float x, float width, float y, int lines, int seed,
            float h) throws IOException {
        int at = seed;
        for (int i = 0; i < lines; i++) {
            StringBuilder line = new StringBuilder();
            while (true) {
                String next = WORDS[at % WORDS.length];
                String tried = line.isEmpty() ? next : line + " " + next;
                if (font.getStringWidth(tried) / 1000 * 10 > width - 12) {
                    break;
                }
                line.setLength(0);
                line.append(tried);
                at++;
            }
            text(cs, font, x + (i == 0 ? 10 : 0), h - y, line.toString());
            y += 12;
        }
        return y + 6;
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
