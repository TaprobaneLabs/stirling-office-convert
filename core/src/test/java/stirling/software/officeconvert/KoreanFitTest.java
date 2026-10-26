package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Korean text in a face Word has keeps its lines in Word, as Latin text does. The IRS's Korean Publication 1 embeds Malgun
 * Gothic, whose widths Word shares, but no paragraph with Hangul was fitted to its lines, so Word broke them where its own
 * room ended and its columns ran a line or more past the PDF's. Word also widens the space between Hangul and Latin
 * letters or digits ("IRS는") unless told not to, which the PDF doesn't.
 */
class KoreanFitTest {

    private static final float SIZE = 10.24f;
    /** Word's size for it, rounded to a half point. */
    private static final float WORD_SIZE = 10f;
    private static final float LINE = 460;
    /** Hangul syllables, each drawn with a Latin letter's glyph that the PDF maps to it. */
    private static final String SYLLABLES = "가나다라마바사아자차카타파"
            + "하거너더러머버서어저처커터";
    private static final String LETTERS = "ABCDEGHKMNOPQRSUVWXYZbdghk";
    private static final String[] POOL = ("BAC DKE GHM NOPQ RS UVW XYZ bd ghk AB CDE GHK MN OPQR SUV WX YZb dgh kA BCD EGH "
            + "KMNO PQ RSU VWX Yb dghk AC EG KN PR UW Yd hk BD GM OQ SV XZ bg").split(" ");

    @TempDir Path dir;

    @Test
    void wordKeepsThePdfsLineBreaks() throws Exception {
        List<String> lines = new ArrayList<>();
        String[] parts = convert(lines);
        String xml = parts[0];
        Element para = paragraph(xml, korean("BAC BAC"));
        float[] room = room(xml, para);
        PDFont metrics = font(new PDDocument());
        float space = metrics.getStringWidth(" ") / 1000 * WORD_SIZE;
        for (int i = 0; i + 1 < lines.size(); i++) {
            float line = metrics.getStringWidth(lines.get(i)) / 1000 * WORD_SIZE;
            String next = lines.get(i + 1).split(" ")[0];
            float withNext = line + space + metrics.getStringWidth(next) / 1000 * WORD_SIZE;
            float r = i == 0 ? room[0] : room[1];
            assertTrue(line <= r + 0.5f, "line " + (i + 1) + " fits its room in Word: " + line + " pt in " + r);
            assertTrue(withNext > r, "line " + (i + 1) + " doesn't take the next line's first word too: " + withNext
                    + " pt would fit in " + r);
        }
    }

    @Test
    void wordAddsNoSpaceBetweenHangulAndLatinText() throws Exception {
        String styles = convert(new ArrayList<>())[1];
        assertTrue(styles.contains("<w:autoSpaceDE w:val=\"0\"/><w:autoSpaceDN w:val=\"0\"/>"),
                "no automatic space between East Asian and Latin text or digits");
    }

    /** The Hangul the PDF maps {@code letters} to. */
    private static String korean(String letters) {
        StringBuilder sb = new StringBuilder();
        for (char c : letters.toCharArray()) {
            sb.append(c == ' ' ? ' ' : SYLLABLES.charAt(LETTERS.indexOf(c)));
        }
        return sb.toString();
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

    /** The room Word gives the paragraph's first line and its others, in points. */
    private static float[] room(String xml, Element para) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element size = (Element) doc.getElementsByTagName("w:pgSz").item(0);
        Element margin = (Element) doc.getElementsByTagName("w:pgMar").item(0);
        float text = (twips(size, "w:w") - twips(margin, "w:left") - twips(margin, "w:right"));
        Element ind = (Element) para.getElementsByTagName("w:ind").item(0);
        float left = ind == null ? 0 : twips(ind, "w:left");
        float right = ind == null ? 0 : twips(ind, "w:right");
        float first = ind == null ? 0 : twips(ind, "w:firstLine") - twips(ind, "w:hanging");
        return new float[] {text - left - right - first, text - left - right};
    }

    private static float twips(Element e, String attr) {
        String v = e.getAttribute(attr);
        return v.isEmpty() ? 0 : Integer.parseInt(v) / 20f;
    }

    /** The document's body and its styles. */
    private String[] convert(List<String> lines) throws IOException {
        Path pdf = dir.resolve("publication.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            PDType0Font malgun = (PDType0Font) font(doc);
            // Named Malgun Gothic, a face Word has whose widths the fit has no table of: only the PDF's widths tell them
            malgun.getCOSObject().setName(COSName.BASE_FONT, "MalgunGothic");
            malgun.getDescendantFont().getFontDescriptor().setFontFamily("Malgun Gothic");
            lines.addAll(lines(malgun));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 8; i++) {
                    text(cs, malgun, 36, h - 72 - 14 * i, POOL[i] + " " + POOL[i + 1] + " " + POOL[i + 2] + " " + POOL[i + 3]);
                }
                for (int i = 0; i < lines.size(); i++) {
                    text(cs, malgun, 36, h - 220 - 14 * i, lines.get(i));
                }
                text(cs, malgun, 36, h - 220 - 14 * (lines.size() + 2), "XYZ bd ghk");
            }
            mapToHangul(doc, malgun);
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("publication.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String[] {
                new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8),
                new String(zip.getInputStream(zip.getEntry("word/styles.xml")).readAllBytes(), StandardCharsets.UTF_8)
            };
        }
    }

    /** A ToUnicode map that reads each letter's glyph as its Hangul syllable. */
    private static void mapToHangul(PDDocument doc, PDType0Font font) throws IOException {
        Map<Integer, String> map = new TreeMap<>();
        for (int i = 0; i < LETTERS.length(); i++) {
            map.put(code(font, LETTERS.charAt(i)), String.valueOf(SYLLABLES.charAt(i)));
        }
        map.put(code(font, ' '), " ");
        StringBuilder cmap = new StringBuilder("/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
                + "/CMapName /Hangul def /CMapType 2 def\n1 begincodespacerange <0000> <FFFF> endcodespacerange\n");
        cmap.append(map.size()).append(" beginbfchar\n");
        for (var e : map.entrySet()) {
            cmap.append(String.format("<%04X> <%04X>%n", e.getKey(), (int) e.getValue().charAt(0)));
        }
        cmap.append("endbfchar\nendcmap CMapName currentdict /CMap defineresource pop end end\n");
        COSStream stream = doc.getDocument().createCOSStream();
        try (OutputStream os = stream.createOutputStream(COSName.FLATE_DECODE)) {
            os.write(cmap.toString().getBytes(StandardCharsets.US_ASCII));
        }
        font.getCOSObject().setItem(COSName.TO_UNICODE, stream);
    }

    private static int code(PDType0Font font, char c) throws IOException {
        byte[] b = font.encode(String.valueOf(c));
        return (b[0] & 0xFF) << 8 | b[1] & 0xFF;
    }

    /**
     * Lines as a PDF sets them at 10.24 pt, left-aligned. The first and third are filled to within {@link #FULL} of
     * {@link #LINE}, so the next line's first word, a short one, doesn't fit on them at that size, though at Word's 10 pt
     * it would, by less than the fit may narrow a paragraph's room. The second ends well short, before a word too long for
     * the room left at either size, so the paragraph reads as ragged, not justified.
     */
    private static List<String> lines(PDFont font) throws IOException {
        String[] starters = {"BAC", "bd", "GHKMNOPQRSUV", "hk"};
        List<String> out = new ArrayList<>();
        int w = 0;
        for (int i = 0; i < starters.length; i++) {
            StringBuilder line = new StringBuilder(starters[i]);
            boolean last = i + 1 == starters.length;
            float stop = i == 1 ? LINE - 45 : last ? LINE - 150 : LINE - 40;
            while (width(font, line + " " + POOL[w % POOL.length]) <= stop) {
                line.append(' ').append(POOL[w++ % POOL.length]);
            }
            if (i == 1 || last) {
                out.add(line.toString());
                continue;
            }
            // Finish the line with one or two words that leave it less than FULL short of LINE
            String best = null;
            for (String a : POOL) {
                for (String b : POOL) {
                    for (String tail : new String[] {a, a + " " + b}) {
                        float slack = LINE - width(font, line + " " + tail);
                        if (slack >= 0 && slack < FULL && best == null) {
                            best = tail;
                        }
                    }
                }
            }
            if (best == null) {
                throw new IllegalStateException("no words fill line " + (i + 1));
            }
            out.add(line + " " + best);
        }
        return out;
    }

    /** How close to {@link #LINE} each line but the last comes: within the fit's narrowing, at Word's size. */
    private static final float FULL = 3.5f;

    private static float width(PDFont font, String s) throws IOException {
        return font.getStringWidth(s) / 1000 * SIZE;
    }

    /** PDFBox's own Liberation Sans, embedded. */
    private static PDFont font(PDDocument doc) throws IOException {
        try (InputStream in = PDFont.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            return PDType0Font.load(doc, in, false);
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, SIZE);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
