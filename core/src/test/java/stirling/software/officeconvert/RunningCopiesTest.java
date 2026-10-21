package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
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
 * A report whose contents page carries its running header and footer at other heights, after a cover with bands to the
 * page's edges (as CRS reports are laid out). The header and footer can't be Word's, but they still bound the body:
 * the bands mustn't stretch the page frame to the page's edges.
 */
class RunningCopiesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float WIDTH = PDRectangle.LETTER.getWidth();
    private static final String HEADER = "Indian Health Service FY2022 Budget Request and Funding History: In Brief";
    private static final String OTHER_YEAR = "Indian Health Service FY2021 Budget Request and Funding History: In Brief";
    private static final String FOOTER = "Congressional Research Service";
    private static final String LINE = "The agency has three major sources of funding, described here in order of size";
    private static final float BODY_TOP = 100;
    private static final float PITCH = 13.8f;

    @TempDir Path dir;

    @Test
    void theBodyStartsBelowTheRunningHeader() throws Exception {
        String xml = convert(makePdf(HEADER));
        float top = margin(xml, "top");
        float bottom = margin(xml, "bottom");
        assertTrue(top > 50 && top < BODY_TOP, "the body starts below the header, near its PDF top: top margin " + top);
        assertTrue(bottom > 12, "the cover's band doesn't set the bottom margin either: " + bottom);
    }

    @Test
    void aCopyInCapitalsIsStillACopy() throws Exception {
        float top = margin(convert(makePdf(HEADER.toUpperCase(Locale.ROOT))), "top");
        assertTrue(top > 50 && top < BODY_TOP, "case doesn't make the copy body text: top margin " + top);
    }

    @Test
    void aLineDifferingInItsFixedDigitsStaysBodyText() throws Exception {
        String xml = convert(makePdf(OTHER_YEAR));
        int at = xml.indexOf("FY2021");
        assertTrue(at >= 0, "the contents page's line is converted");
        assertTrue(xml.lastIndexOf("<w:txbxContent", at) <= xml.lastIndexOf("</w:txbxContent>", at),
                "a line that differs from the running header in its year isn't a copy of it, so it isn't set apart");
    }

    private static float margin(String xml, String side) {
        Matcher m = Pattern.compile("<w:pgMar [^>]*w:" + side + "=\"(\\d+)\"").matcher(xml);
        assertTrue(m.find(), "section has page margins");
        return Integer.parseInt(m.group(1)) / 20f;
    }

    private String convert(Path pdf) throws IOException {
        Path docx = dir.resolve("report.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A cover with bands at the top and bottom edges, a contents page whose header line is {@code contentsHeader}, then
     * six pages of running text.
     */
    private Path makePdf(String contentsHeader) throws IOException {
        Path pdf = dir.resolve("report.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage cover = new PDPage(PDRectangle.LETTER);
            doc.addPage(cover);
            try (PDPageContentStream cs = new PDPageContentStream(doc, cover)) {
                cs.setNonStrokingColor(new Color(0x1f, 0x4e, 0x79));
                cs.addRect(0, HEIGHT - 140, WIDTH, 140);
                cs.addRect(0, 0, WIDTH, 70);
                cs.fill();
                cs.setNonStrokingColor(Color.BLACK);
                text(cs, 72, 200, HEADER, 20);
                text(cs, 72, 240, "January 6, 2022", 12);
            }
            PDPage contents = new PDPage(PDRectangle.LETTER);
            doc.addPage(contents);
            try (PDPageContentStream cs = new PDPageContentStream(doc, contents)) {
                text(cs, 190, 30, contentsHeader, 9);
                text(cs, 90, BODY_TOP, "Contents", 16);
                for (int i = 1; i <= 6; i++) {
                    text(cs, 90, BODY_TOP + 10 + i * 16, "Section " + i + " of the report", 12);
                }
                text(cs, 90, 776, FOOTER, 9);
            }
            for (int p = 1; p <= 6; p++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    text(cs, 190, 44, HEADER, 9);
                    for (int i = 0; BODY_TOP + i * PITCH <= 700; i++) {
                        text(cs, 90, BODY_TOP + i * PITCH, LINE, 12);
                    }
                    text(cs, 90, 755, FOOTER, 9);
                    text(cs, 518, 755, String.valueOf(p), 9);
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** Text with its baseline {@code baseline} points from the top. */
    private static void text(PDPageContentStream cs, float x, float baseline, String s, float size) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, HEIGHT - baseline);
        cs.showText(s);
        cs.endText();
    }
}
