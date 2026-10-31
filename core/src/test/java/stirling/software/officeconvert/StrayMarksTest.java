package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CMS's Medicare Summary Notices number their callouts with blue discs whose digits are drawn as shapes, not text, and
 * point from each callout to the sample page with thin lines. The discs became Word ellipses without their digits, and the
 * digits and pointers were dropped: nothing draws a shape outside figures unless it's a round dot.
 */
class StrayMarksTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font MONO = new PDType1Font(Standard14Fonts.FontName.COURIER);
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float K = 0.5523f;

    @TempDir Path dir;

    @Test
    void aPointerAndADiscWithADrawnDigitAreOnePictureBehindTheText() throws Exception {
        Path pdf = makePdf();
        Path docx = dir.resolve("callout.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        String xml;
        BufferedImage picture = null;
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.getName().startsWith("word/media/") && entry.getName().endsWith(".png")) {
                    picture = ImageIO.read(zip.getInputStream(entry));
                }
            }
        }
        assertEquals(1, count(xml, "<pic:pic>"), "the disc and its digit are one picture; the underline and boxes make none");
        assertTrue(xml.contains("behindDoc=\"1\""), "it's behind the text");
        assertTrue(picture != null && has(picture, 0x005BAA) && has(picture, 0xFFFFFF), "it draws the blue disc and the white digit");
        assertTrue(has(picture, 0x8CB3D9), "and the pointer to it");
        assertTrue(has(picture, 0x990000), "and the disc's border, beside its see-through fill (from Codex's review)");
        assertTrue(xml.contains("Your Claims for Part B"), "the heading stays text");
    }

    private static boolean has(BufferedImage img, int rgb) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if ((p >>> 24) > 200 && close(p & 0xFFFFFF, rgb)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean close(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) < 24 && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) < 24
                && Math.abs((a & 0xFF) - (b & 0xFF)) < 24;
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }

    private Path makePdf() throws IOException {
        Path pdf = dir.resolve("callout.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // The sample page: its frame and its prose
                cs.setStrokingColor(0.3f);
                cs.setLineWidth(1f);
                cs.addRect(140, H - 260, 330, 160);
                cs.stroke();
                text(cs, FONT, 165, 130, 14, "Your Claims for Part B");
                for (int i = 0; i < 7; i++) {
                    text(cs, FONT, 165, 155 + 14 * i, 10, "Part B Medical Insurance helps pay for doctors' services.");
                }
                // A callout on a light panel right of the sample page
                cs.setNonStrokingColor(0.86f, 0.92f, 0.97f);
                cs.addRect(480, H - 270, 120, 60);
                cs.fill();
                cs.setNonStrokingColor(0f);
                text(cs, FONT, 486, 228, 11, "Type of Claim");
                text(cs, FONT, 486, 245, 9, "Claims can be");
                text(cs, FONT, 486, 257, 9, "assigned.");
                // The pointer from the callout, across the sample page's prose, to the disc
                cs.setStrokingColor(0.55f, 0.7f, 0.85f);
                cs.setLineWidth(0.75f);
                cs.moveTo(480, H - 240);
                cs.lineTo(157, H - 128);
                cs.stroke();
                // The callout's disc, slightly see-through and bordered, and its digit drawn as a white shape
                cs.saveGraphicsState();
                org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState faint =
                        new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
                faint.setNonStrokingAlphaConstant(0.9f);
                cs.setGraphicsStateParameters(faint);
                cs.setNonStrokingColor(0f, 0x5B / 255f, 0xAA / 255f);
                cs.setStrokingColor(0.6f, 0f, 0f);
                cs.setLineWidth(1.5f);
                circle(cs, 150, 125, 7);
                cs.fillAndStroke();
                cs.restoreGraphicsState();
                cs.setNonStrokingColor(1f);
                cs.moveTo(148, H - 122);
                cs.lineTo(150.5f, H - 119.5f);
                cs.lineTo(152, H - 119.5f);
                cs.lineTo(152, H - 130.5f);
                cs.lineTo(150, H - 130.5f);
                cs.lineTo(150, H - 122.5f);
                cs.lineTo(148.5f, H - 123.5f);
                cs.closePath();
                cs.fill();

                // Guards: an underlined word, a boxed word and a rounded box of code make no picture
                cs.setNonStrokingColor(0f);
                text(cs, FONT, 72, 320, 11, "An underlined word ends this line of the notice.");
                cs.addRect(72, H - 323.5f, 70, 0.6f);
                cs.fill();
                cs.setStrokingColor(0f);
                cs.setLineWidth(0.8f);
                cs.addRect(72, H - 358, 60, 18);
                cs.stroke();
                text(cs, FONT, 76, 352, 11, "BOXED");
                cs.setNonStrokingColor(0.93f);
                roundRect(cs, 72, 370, 250, 392, 4);
                cs.fill();
                cs.setNonStrokingColor(0f);
                text(cs, MONO, 78, 385, 10, "convert --to docx in.pdf");
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static void circle(PDPageContentStream cs, float cx, float cyTop, float r) throws IOException {
        float cy = H - cyTop;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + K * r, cx + K * r, cy + r, cx, cy + r);
        cs.curveTo(cx - K * r, cy + r, cx - r, cy + K * r, cx - r, cy);
        cs.curveTo(cx - r, cy - K * r, cx - K * r, cy - r, cx, cy - r);
        cs.curveTo(cx + K * r, cy - r, cx + r, cy - K * r, cx + r, cy);
        cs.closePath();
    }

    private static void roundRect(PDPageContentStream cs, float x, float top, float right, float bottom, float r)
            throws IOException {
        float t = H - top;
        float b = H - bottom;
        cs.moveTo(x + r, b);
        cs.lineTo(right - r, b);
        cs.curveTo(right - r + K * r, b, right, b + r - K * r, right, b + r);
        cs.lineTo(right, t - r);
        cs.curveTo(right, t - r + K * r, right - r + K * r, t, right - r, t);
        cs.lineTo(x + r, t);
        cs.curveTo(x + r - K * r, t, x, t - r + K * r, x, t - r);
        cs.lineTo(x, b + r);
        cs.curveTo(x, b + r - K * r, x + r - K * r, b, x + r, b);
        cs.closePath();
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float x, float top, float size, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, H - top);
        cs.showText(s);
        cs.endText();
    }
}
