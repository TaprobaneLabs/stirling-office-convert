package stirling.software.officeconvert;

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

/** A report cover whose title is set tight, with character spacing, as Word's "condensed" exports it. */
class TrackingTest {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDType1Font ROMAN = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    @TempDir Path dir;

    @Test
    void condensedTitleKeepsItsCharacterSpacing() throws Exception {
        String xml = convert();
        Matcher run = Pattern.compile("<w:r><w:rPr>((?:(?!</w:rPr>).)*)</w:rPr><w:t[^>]*>Guide to Enterprise").matcher(xml);
        assertTrue(run.find(), "the title's run has its own properties");
        Matcher spacing = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(run.group(1));
        assertTrue(spacing.find(), "the title keeps its character spacing: " + run.group(1));
        int twips = Integer.parseInt(spacing.group(1));
        assertTrue(twips <= -9 && twips >= -12, "about half a point tighter, as in the PDF: " + twips);
    }

    @Test
    void textWithoutCharacterSpacingGetsNone() throws Exception {
        String xml = convert();
        Matcher body = Pattern.compile("<w:p>(?:(?!</w:p>).)*This publication is available(?:(?!</w:p>).)*</w:p>").matcher(xml);
        assertTrue(body.find(), "the body paragraph");
        assertFalse(body.group().contains("<w:spacing w:val="), "no character spacing: " + body.group());
    }

    @Test
    void justifiedTextLeavesItsCharacterSpacingToWord() throws Exception {
        String xml = convert();
        Matcher body = Pattern.compile("<w:p>(?:(?!</w:p>).)*Patches the(?:(?!</w:p>).)*</w:p>").matcher(xml);
        assertTrue(body.find(), "the justified paragraph");
        assertTrue(body.group().contains("<w:jc w:val=\"both\"/>"), "it is justified: " + body.group());
        assertFalse(body.group().contains("<w:spacing w:val="), "Word justifies it with its spaces: " + body.group());
    }

    @Test
    void letterspacedCapitalsAreLeftAsTheyAre() throws Exception {
        String xml = convert();
        Matcher head = Pattern.compile("<w:p>(?:(?!</w:p>).)*NIST Special Publication(?:(?!</w:p>).)*</w:p>").matcher(xml);
        assertTrue(head.find(), "the letterspaced line");
        assertFalse(head.group().contains("<w:spacing w:val="), "expanded spacing isn't carried: " + head.group());
    }

    private String convert() throws Exception {
        Path pdf = dir.resolve("cover.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                right(cs, BOLD, 20, 2f, 703, "NIST Special Publication");
                right(cs, BOLD, 28, -0.5f, 624, "Guide to Enterprise Patch");
                right(cs, BOLD, 28, -0.5f, 592, "Management Planning:");
                justified(cs, 380);
                float y = 480;
                for (String line : new String[] {
                    "This publication is available free of charge from the National Institute of Standards and",
                    "Technology, which publishes guidance on managing the patches that keep technology secure."}) {
                    cs.beginText();
                    cs.setFont(ROMAN, 12);
                    cs.setCharacterSpacing(0);
                    cs.newLineAtOffset(72, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("cover.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A justified paragraph set with condensed character spacing and wide word spaces: its lines have room to spare in
     * Word's widths, and each runover line starts with a word too long to come up, so Word needs no spacing to keep them.
     */
    private static void justified(PDPageContentStream cs, float y) throws IOException {
        String[] pool = {"the", "flaws", "in", "software", "are", "patched", "by", "vendors", "and", "users"};
        java.util.List<String> lines = new java.util.ArrayList<>();
        int w = 0;
        for (int i = 0; i < 4; i++) {
            StringBuilder line = new StringBuilder(i == 0 ? "Patches" : "Telecommunications");
            while (ROMAN.getStringWidth(line + " " + pool[w % pool.length]) / 1000f * 12 < (i == 3 ? 200 : 450)) {
                line.append(' ').append(pool[w++ % pool.length]);
            }
            lines.add(line.toString());
        }
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            float spacing = -0.3f;
            float natural = ROMAN.getStringWidth(s) / 1000f * 12 + spacing * s.length();
            int spaces = s.split(" ").length - 1;
            cs.beginText();
            cs.setFont(ROMAN, 12);
            cs.setCharacterSpacing(spacing);
            cs.setWordSpacing(i + 1 < lines.size() ? (468 - natural) / spaces : 0);
            cs.newLineAtOffset(72, y - i * 14);
            cs.showText(s);
            cs.endText();
        }
        cs.setWordSpacing(0);
    }

    /** Sets a line flush right at 540 with the given character spacing, as a producer would. */
    private static void right(PDPageContentStream cs, PDType1Font font, float size, float spacing, float y, String s)
            throws IOException {
        float width = font.getStringWidth(s) / 1000f * size + spacing * s.length();
        cs.beginText();
        cs.setFont(font, size);
        cs.setCharacterSpacing(spacing);
        cs.newLineAtOffset(540 - width, y);
        cs.showText(s);
        cs.endText();
    }
}
