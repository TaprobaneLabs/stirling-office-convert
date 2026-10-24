package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.pdfbox.cos.COSName;
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
 * A paragraph fitted from the PDF's own widths keeps its lines in Word though Word sets it at another size: its font size
 * rounded to a half point. In a face Word has whose widths the fit knows only from the PDF (Calibri here, PDFBox's
 * Liberation Sans under that name), lines measured at the PDF's 10.24 pt looked 2% wider than Word sets them at 10 pt, and
 * the room the fit gave them took the next line's first word as well.
 */
class FitAtWordsSizeTest {

    private static final float SIZE = 10.24f;
    /** Word's size for it, rounded to a half point. */
    private static final float WORD_SIZE = 10f;
    private static final float LINE = 460;
    private static final String[] POOL = ("The survey keeps watch over every volcano on the islands with seismometers, "
            + "tiltmeters and cameras that send their readings to the observatory each minute through the day and night "
            + "while staff review the records for signs that magma is rising toward the surface below the summit craters "
            + "where small swarms of earthquakes often come before an eruption begins").split(" ");

    @TempDir Path dir;

    @Test
    void wordKeepsThePdfsLineBreaks() throws Exception {
        List<String> lines = new ArrayList<>();
        String xml = convert(lines);
        Element para = paragraph(xml, "Each The survey");
        float[] room = room(xml, para);
        PDFont metrics = arial(new PDDocument());
        float space = metrics.getStringWidth(" ") / 1000 * WORD_SIZE;
        for (int i = 0; i + 1 < lines.size(); i++) {
            float line = metrics.getStringWidth(lines.get(i)) / 1000 * WORD_SIZE;
            String next = lines.get(i + 1).split(" ")[0];
            float withNext = line + space + metrics.getStringWidth(next) / 1000 * WORD_SIZE;
            float r = i == 0 ? room[0] : room[1];
            assertTrue(line <= r + 0.5f, "line " + (i + 1) + " fits its room in Word: " + line + " pt in " + r);
            assertTrue(withNext > r, "line " + (i + 1) + " doesn't take the next line's \"" + next + "\" too: " + withNext
                    + " pt would fit in " + r);
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

    private String convert(List<String> lines) throws IOException {
        Path pdf = dir.resolve("notice.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            PDType0Font calibri = (PDType0Font) arial(doc);
            // Named Calibri, a face Word has whose widths the fit has no table of: only the PDF's widths tell them
            calibri.getCOSObject().setName(COSName.BASE_FONT, "Calibri");
            calibri.getDescendantFont().getFontDescriptor().setFontFamily("Calibri");
            lines.addAll(lines(calibri));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 8; i++) {
                    text(cs, calibri, 36, h - 72 - 14 * i, "Body line " + (i + 1) + " above the paragraph.");
                }
                for (int i = 0; i < lines.size(); i++) {
                    text(cs, calibri, 36, h - 220 - 14 * i, lines.get(i));
                }
                text(cs, calibri, 36, h - 220 - 14 * (lines.size() + 2), "A closing line under the paragraph.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("notice.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Lines as a PDF sets them at 10.24 pt, left-aligned. The first and third are filled to within {@link #FULL} of
     * {@link #LINE}, so the next line's first word, a short one, doesn't fit on them at that size, though at Word's 10 pt
     * it would, by less than the fit may narrow a paragraph's room. The second ends well short, before a word too long for
     * the room left at either size, so the paragraph reads as ragged, not justified.
     */
    private static List<String> lines(PDFont font) throws IOException {
        String[] starters = {"Each", "of", "seismometers,", "to"};
        List<String> out = new ArrayList<>();
        int w = 0;
        for (int i = 0; i < starters.length; i++) {
            StringBuilder line = new StringBuilder(starters[i]);
            boolean last = i + 1 == starters.length;
            float stop = i == 1 ? LINE - 45 : last ? LINE - 150 : LINE - 70;
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
    private static PDFont arial(PDDocument doc) throws IOException {
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
