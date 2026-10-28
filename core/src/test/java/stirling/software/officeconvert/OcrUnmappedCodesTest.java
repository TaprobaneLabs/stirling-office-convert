package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OCRmyPDF 17 shapes non-Latin OCR text in its glyphless Occulta, a composite font whose ToUnicode CMap maps a
 * cluster's text to its first glyph, with no advance, and leaves out the glyph after it that carries the advance. For a
 * code the CMap leaves out, PDFBox reads the embedded font's cmap, whose first character for Occulta's shared empty
 * glyph came out inside the words: "$" all through a Tamil scan's text and "&" through a Sinhala one. Invisible text in
 * such a font is read through its CMap alone, and a glyph it leaves out widens the one before it, so the word stays
 * whole.
 */
class OcrUnmappedCodesTest {

    private static final String FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    @TempDir Path dir;

    @Test
    void aCodeTheOcrLayersMapLeavesOutIsPartOfTheLetterBefore() throws Exception {
        String text = convert(RenderingMode.NEITHER);
        assertTrue(text.contains("abcd efgh"), text);
        assertFalse(text.contains("$"), text);
    }

    /**
     * "ab$cd efgh" in LiberationSans as a composite font, with a ToUnicode CMap that maps every code but the dollar
     * sign's, as OCRmyPDF leaves out its separators' codes.
     */
    private String convert(RenderingMode mode) throws Exception {
        String line = "ab$cd efgh";
        Path pdf = dir.resolve("unmapped-" + mode + ".pdf");
        try (PDDocument doc = new PDDocument(); InputStream ttf = PDTrueTypeFont.class.getResourceAsStream(FONT)) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font = PDType0Font.load(doc, ttf, false);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setRenderingMode(mode);
                cs.setFont(font, 14);
                cs.newLineAtOffset(72, 720);
                cs.showText(line);
                cs.endText();
            }
            StringBuilder chars = new StringBuilder();
            int mapped = 0;
            for (char c : line.toCharArray()) {
                String code = HexFormat.of().withUpperCase().formatHex(font.encode(String.valueOf(c)));
                if (c != '$' && chars.indexOf("<" + code + ">") < 0) {
                    chars.append('<').append(code).append("> <").append(String.format("%04X", (int) c)).append(">\n");
                    mapped++;
                }
            }
            String cmap = "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CMapName /Adobe-Identity-UCS def\n"
                    + "/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n" + mapped
                    + " beginbfchar\n" + chars + "endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n";
            COSStream map = doc.getDocument().createCOSStream();
            try (OutputStream out = map.createOutputStream()) {
                out.write(cmap.getBytes(StandardCharsets.US_ASCII));
            }
            font.getCOSObject().setItem(COSName.TO_UNICODE, map);
            doc.save(pdf.toFile());
        }
        Path txt = dir.resolve("unmapped-" + mode + ".txt");
        PdfToText.convert(pdf, txt, PdfToDocx.Options.defaults());
        return Files.readString(txt, StandardCharsets.UTF_8);
    }
}
