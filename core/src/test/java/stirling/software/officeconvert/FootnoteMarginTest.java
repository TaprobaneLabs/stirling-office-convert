package stirling.software.officeconvert;

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

/**
 * Word sets footnotes on the bottom margin, so the margin decides where they land: it should leave them where the PDF's
 * end, not lower, while keeping room for body text that runs lower on another page.
 */
class FootnoteMarginTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float SIZE = 12;
    private static final float PITCH = 13.8f;
    private static final float NOTE_SIZE = 10;
    private static final float NOTE_PITCH = 11.5f;
    private static final float HEIGHT = PDRectangle.LETTER.getHeight();
    private static final String LINE = "The agencies expect institutions to have taken all necessary steps to prepare";
    private static final String NOTE = "The federal agencies are the Board, the Bureau and the Office of the Comptroller.";

    @TempDir Path dir;

    @Test
    void footnotesEndWhereThePdfsDo() throws Exception {
        float noteBaseline = 680;
        String xml = convert(makePdf(noteBaseline, 0));
        float wordBottom = HEIGHT - bottomMargin(xml);
        float pdfBottom = noteBaseline + 0.2f * NOTE_PITCH;
        assertTrue(xml.contains("w:footnoteReference"), "the notes are Word footnotes");
        assertTrue(Math.abs(wordBottom - pdfBottom) <= 8,
                "Word's notes end at " + wordBottom + ", the PDF's at " + pdfBottom);
    }

    @Test
    void bodyTextBelowTheNotesKeepsItsRoom() throws Exception {
        float lastBody = 90 + 47 * PITCH;
        String xml = convert(makePdf(680, lastBody));
        float wordBottom = HEIGHT - bottomMargin(xml);
        assertTrue(wordBottom >= lastBody + 0.2f * PITCH,
                "page 2's last line, at " + lastBody + ", still fits above the margin at " + wordBottom);
    }

    private static float bottomMargin(String xml) {
        Matcher m = Pattern.compile("<w:pgMar [^>]*w:bottom=\"(\\d+)\"").matcher(xml);
        assertTrue(m.find(), "section has page margins");
        return Integer.parseInt(m.group(1)) / 20f;
    }

    private String convert(Path pdf) throws IOException {
        Path docx = dir.resolve("notes.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Page 1: body text, a footnote reference, a separator rule and a two-line note whose last baseline is
     * {@code noteBaseline} (from the top). Page 2, when {@code lastBody} is set: body text down to that baseline, no
     * notes.
     */
    private Path makePdf(float noteBaseline, float lastBody) throws IOException {
        Path pdf = dir.resolve("notes.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 20; i++) {
                    float y = 90 + i * PITCH;
                    text(cs, 72, y, LINE, SIZE, 0);
                    if (i == 2) {
                        text(cs, 72 + FONT.getStringWidth(LINE) / 1000f * SIZE, y, "1", 7, 4);
                    }
                }
                float firstNote = noteBaseline - NOTE_PITCH;
                cs.setLineWidth(0.5f);
                cs.moveTo(72, HEIGHT - (firstNote - NOTE_PITCH));
                cs.lineTo(216, HEIGHT - (firstNote - NOTE_PITCH));
                cs.stroke();
                text(cs, 72, firstNote, "1", 6.5f, 3.5f);
                text(cs, 76, firstNote, NOTE, NOTE_SIZE, 0);
                text(cs, 72, noteBaseline, "It runs to a second line.", NOTE_SIZE, 0);
            }
            if (lastBody > 0) {
                PDPage second = new PDPage(PDRectangle.LETTER);
                doc.addPage(second);
                try (PDPageContentStream cs = new PDPageContentStream(doc, second)) {
                    for (int i = 0; 90 + i * PITCH <= lastBody + 0.1f; i++) {
                        text(cs, 72, 90 + i * PITCH, LINE, SIZE, 0);
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** Text with its baseline {@code baseline} points from the top, raised by {@code rise}. */
    private static void text(PDPageContentStream cs, float x, float baseline, String s, float size, float rise)
            throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.setTextRise(rise);
        cs.newLineAtOffset(x, HEIGHT - baseline);
        cs.showText(s);
        cs.endText();
    }
}
