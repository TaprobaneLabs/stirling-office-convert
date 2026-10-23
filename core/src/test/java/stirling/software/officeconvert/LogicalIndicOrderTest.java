package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

/**
 * Bengali text whose pre-base vowel signs (ি, ে) a PDF gives in logical order, after their consonant, comes back as it
 * was. Microsoft Word writes its PDFs so: CDC's Bengali polio statement's "বিবৃতি" had come out "ববিৃতি", each sign moved
 * past the next consonant as if the text were in visual order. Text in visual order, where a sign comes before its
 * consonant, is still put in logical order.
 */
class LogicalIndicOrderTest {

    private static final String TEXT = "টিকার বিবরণ নেবেন কি";

    /** The same words with each pre-base sign before its consonant, as a PDF that labels its glyphs in drawing order has them. */
    private static final String VISUAL = "িটকার িববরণ েনেবন িক";

    @Test
    void textInLogicalOrderKeepsIt() throws Exception {
        assertEquals(nfc(TEXT), nfc(convert(TEXT)));
    }

    @Test
    void textInVisualOrderIsPutInLogicalOrder() throws Exception {
        assertEquals(nfc(TEXT), nfc(convert(VISUAL)));
    }

    @Test
    void aFontWhoseSignsAllStandInsideWordsFollowsThePage() throws Exception {
        // The title's face has one sign, inside a word, which either order allows: the body's face shows the page's order.
        assertEquals(nfc("নেবেন কি\nবিবরণ"), nfc(convert("নেবেন কি", "বিবরণ")));
    }

    /**
     * A line of glyphs from left to right for each of {@code lines}, one per character, each glyph's ToUnicode entry that
     * character, and each line in a face of its own. The glyphs' shapes are Latin letters: only their text and places
     * matter here.
     */
    private static String convert(String... lines) throws IOException {
        byte[] bytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources res = new PDResources();
            StringBuilder cs = new StringBuilder("BT\n");
            for (int n = 0; n < lines.length; n++) {
                PDType0Font font;
                try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                    font = PDType0Font.load(doc, in, false);
                }
                font.getCOSObject().setName(COSName.BASE_FONT, "Face" + (char) ('A' + n));
                String name = "F" + (n + 1);
                res.put(COSName.getPDFName(name), font);
                cs.append("/").append(name).append(" 14 Tf\n");
                Map<Character, Integer> gids = new LinkedHashMap<>();
                float x = 72;
                float y = 700 - 30 * n;
                for (char c : lines[n].toCharArray()) {
                    if (c == ' ') {
                        x += 6;
                        continue;
                    }
                    int gid = gids.computeIfAbsent(c, k -> 36 + gids.size());
                    cs.append(String.format("1 0 0 1 %.2f %.2f Tm <%04X> Tj%n", x, y, gid));
                    x += font.getWidth(gid) / 1000f * 14;
                }
                StringBuilder cmap = new StringBuilder("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n"
                        + "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
                        + "/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\n"
                        + "endcodespacerange\n" + gids.size() + " beginbfchar\n");
                for (Map.Entry<Character, Integer> e : gids.entrySet()) {
                    cmap.append(String.format("<%04X> <%04X>%n", e.getValue(), (int) e.getKey()));
                }
                cmap.append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
                font.getCOSObject().setItem(COSName.TO_UNICODE, stream(doc, cmap.toString()));
            }
            cs.append("ET\n");
            page.setResources(res);
            page.setContents(stream(doc, cs.toString()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            bytes = out.toByteArray();
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return PdfToText.text(doc, PdfToDocx.Options.defaults()).strip().replaceAll("\\s*\\n\\s*", "\n");
        }
    }

    private static PDStream stream(PDDocument doc, String content) throws IOException {
        PDStream s = new PDStream(doc);
        try (OutputStream o = s.createOutputStream()) {
            o.write(content.getBytes(StandardCharsets.US_ASCII));
        }
        return s;
    }

    private static String nfc(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }
}
