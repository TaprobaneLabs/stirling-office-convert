package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A report whose text pages are portrait and whose wide tables are on landscape pages, under the same running header. */
class RunningDuplicatesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String LEFT = "NIST SP 800-218A";
    private static final String RIGHT = "Secure Software Development Practices";

    @TempDir Path dir;

    @Test
    void headerPiecesAppearOnce() throws Exception {
        String header = headers(convert(LEFT));
        assertTrue(header.contains(LEFT), "the running header is kept: " + header);
        assertEquals(1, Pattern.compile(Pattern.quote(LEFT)).matcher(header).results().count(), header);
        assertEquals(1, Pattern.compile(Pattern.quote(RIGHT)).matcher(header).results().count(), header);
    }

    @Test
    void headersThatDifferInAFixedNumberAreBothKept() throws Exception {
        String header = headers(convert("NIST SP 800-219A"));
        assertTrue(header.contains(LEFT) && header.contains("NIST SP 800-219A"), "neither header is dropped: " + header);
    }

    /** Six pages, the last three landscape, whose left header piece reads {@code landscapeLeft}. */
    private Path convert(String landscapeLeft) throws Exception {
        Path pdf = dir.resolve("report.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int p = 0; p < 6; p++) {
                boolean landscape = p >= 3;
                PDRectangle size = landscape ? new PDRectangle(792, 612) : PDRectangle.LETTER;
                PDPage page = new PDPage(size);
                doc.addPage(page);
                float w = size.getWidth();
                float h = size.getHeight();
                float margin = landscape ? 36 : 72;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    text(cs, margin, h - 32, 9, landscape ? landscapeLeft : LEFT);
                    text(cs, w - margin - FONT.getStringWidth(RIGHT) / 1000f * 9, h - 32, 9, RIGHT);
                    float y = h - 90;
                    for (int i = 0; i < 20; i++) {
                        text(cs, margin, y, 10, "Practice " + (p * 20 + i + 1) + " defines criteria for software security checks"
                                + " and tracks them throughout the development life cycle.");
                        y -= 14;
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("report.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        return docx;
    }

    private static String headers(Path docx) throws IOException {
        StringBuilder all = new StringBuilder();
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().matches("word/header\\d*\\.xml")) {
                    String xml = new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                    all.append(xml.replaceAll("<[^>]+>", " ")).append('\n');
                }
            }
        }
        return all.toString();
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
