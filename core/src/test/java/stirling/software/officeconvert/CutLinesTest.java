package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Lines the page cuts off keep their ends in Word. The left page of a spread (USGS Fact Sheet 2024-3033) sets an
 * introduction whose lines run on past the page's right edge onto the next page. Word filled each line from the next one
 * and wrapped what didn't fit, so the paragraph took a line more, and the page's captions ran onto a page of their own.
 */
class CutLinesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float H = PDRectangle.LETTER.getHeight();
    /** The first four lines run on past the page's right edge, where their last words are never seen; the others don't. */
    private static final String[] LINES = {
        "Native to the large rivers of the middle of the continent, the fish were first brought east as a sportfish, and anglers "
                + "OFFPAGEONE carried them further",
        "Program, 2020). The map below shows the records of where the fish have been found over the years, and how far up the "
                + "OFFPAGETWO rivers they reach",
        "the Bay are likely passing through, and their presence there shows how much salt water the fish can stand, which "
                + "OFFPAGETHREE surprised many",
        "Bay tributaries. Because of the need to balance these interests, any plan for the whole bay will take work from "
                + "OFFPAGEFOUR every state",
        "Below the spread the introduction carries on inside the page, so this line ends a little short of the page's edge, and",
        "notwithstanding the last one, which is short.",
    };
    /** A lone line that starts right of the middle and runs on past the page's edge. */
    private static final String LONE = "Location data on the map come from state agencies and the river commissions "
            + "OFFPAGEFIVE and more";

    @TempDir Path dir;

    @Test
    void eachLineEndsWhereThePageCutsIt() throws Exception {
        Element para = paragraph(convert(), "Native to the large rivers");
        assertEquals(LINES.length - 1, para.getElementsByTagName("w:br").getLength(),
                "a break after every line, so the room past the edge pulls no words up: " + para.getTextContent());
        assertTrue(para.getTextContent().contains("notwithstanding the last one"), "one paragraph: " + para.getTextContent());
        assertFalse(para.getTextContent().contains("OFFPAGE"), "words past the page's edge stay out");
        Element ind = (Element) para.getElementsByTagName("w:ind").item(0);
        int right = ind == null || ind.getAttribute("w:right").isEmpty() ? 0 : Integer.parseInt(ind.getAttribute("w:right"));
        assertTrue(right <= -20 * 36, "Word's room reaches well past the page's edge, so wider widths don't wrap a line: "
                + right + " twips");
    }

    @Test
    void aCutLineStartingMidPageStaysWhereItStarts() throws Exception {
        Element para = paragraph(convert(), "Location data on the map");
        Element pPr = (Element) para.getElementsByTagName("w:pPr").item(0);
        Element jc = (Element) pPr.getElementsByTagName("w:jc").item(0);
        String align = jc == null ? "left" : jc.getAttribute("w:val");
        assertTrue(align.equals("left") || align.equals("start"), "set flush left, not moved within the room: " + align);
        assertEquals(0, pPr.getElementsByTagName("w:bidi").getLength(), "not taken for right-to-left text");
        Element ind = (Element) pPr.getElementsByTagName("w:ind").item(0);
        int left = ind == null || ind.getAttribute("w:left").isEmpty() ? 0 : Integer.parseInt(ind.getAttribute("w:left"));
        assertTrue(left >= 20 * 200, "starts where the PDF's line does: " + left + " twips");
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("spread.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 36, 50, 18, "Since Their Introduction, the Fish Have Spread");
                for (int i = 0; i < LINES.length; i++) {
                    text(cs, 36, 80 + 14.4f * i, 12, LINES[i]);
                }
                for (int i = 0; i < 6; i++) {
                    text(cs, 72, 200 + 14 * i, 10, "A note under the introduction, line " + (i + 1) + ", well inside the page.");
                }
                text(cs, 330, 340, 9, LONE);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("spread.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The paragraph whose text holds {@code words}. */
    private static Element paragraph(String xml, String words) throws Exception {
        var body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:p");
        for (int i = 0; i < body.getLength(); i++) {
            Element p = (Element) body.item(i);
            if (p.getTextContent().contains(words)) {
                return p;
            }
        }
        throw new AssertionError("no paragraph holds " + words);
    }

    /** Text with its baseline at {@code baseline} from the page's top edge. */
    private static void text(PDPageContentStream cs, float x, float baseline, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, size);
        cs.newLineAtOffset(x, H - baseline);
        cs.showText(s);
        cs.endText();
    }
}
