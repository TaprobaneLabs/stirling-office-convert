package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A caption set just under a row of boxes, beside which it has no room, keeps no space above it reaching back over the
 * boxes. Word moves such a line below the boxes and then gives it that space again: CMS's Medicare Summary Notices sent
 * the captions under their sample pages ("Page 3", "Last Page") to a page of their own.
 */
class CaptionUnderBoxesTest {

    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font SANS = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    /** Where the lower row of boxes ends, from the page's top: the caption's words start 3 pt above it. */
    private static final float ROW_FOOT = 554;

    @TempDir Path dir;

    @Test
    void theCaptionKeepsNoSpaceReachingOverTheBoxes() throws Exception {
        String xml = convert(8.5f);
        assertTrue(xml.contains("Last Page"), "the caption is there");
        float above = 0;
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        for (int i = 0; i < body.getLength(); i++) {
            if (!(body.item(i) instanceof Element p) || !p.getTagName().equals("w:p")) {
                continue;
            }
            Element spacing = (Element) p.getElementsByTagName("w:spacing").item(0);
            String text = ownText(p);
            if (text.contains("Last Page")) {
                above += spacing == null || spacing.getAttribute("w:before").isEmpty() ? 0
                        : Integer.parseInt(spacing.getAttribute("w:before")) / 20f;
                break;
            }
            // Spacers between the text beside the boxes and the caption count too.
            above = text.isBlank() && spacing != null && "exact".equals(spacing.getAttribute("w:lineRule"))
                    ? above + Integer.parseInt(spacing.getAttribute("w:line")) / 20f : 0;
        }
        assertTrue(above < 20, "the caption's room above reaches back " + above + " pt, over the boxes");
    }

    @Test
    void theRoomUnderTheBoxesCantSitBesideThem() throws Exception {
        // Set lower, the caption keeps the room between it and the boxes in a spacer. Unindented, the spacer fitted beside
        // the boxes, where the column has room, and the caption went straight under them; set from the caption's indent,
        // the spacer has no room beside them either.
        String xml = convert(30);
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        Element before = null;
        for (int i = 0; i < body.getLength(); i++) {
            if (body.item(i) instanceof Element p && p.getTagName().equals("w:p")) {
                if (ownText(p).contains("Last Page")) {
                    break;
                }
                before = p;
            }
        }
        assertTrue(before != null && ownText(before).isBlank(), "a spacer keeps the room above the caption");
        Element spacing = (Element) before.getElementsByTagName("w:spacing").item(0);
        float room = Integer.parseInt(spacing.getAttribute("w:line")) / 20f;
        assertTrue(room > 10 && room < 30, "the spacer stands for the room under the boxes: " + room + " pt");
        Element ind = (Element) before.getElementsByTagName("w:ind").item(0);
        float indent = ind == null || ind.getAttribute("w:left").isEmpty() ? 0 : Integer.parseInt(ind.getAttribute("w:left")) / 20f;
        assertTrue(indent > 300, "the spacer starts where the caption does, under the boxes: indented " + indent + " pt");
    }

    @Test
    void aRightAlignedCaptionCanSitBesideTheBoxes() throws Exception {
        // Set flush right, a caption's paragraph starts at the column's edge, not at its words, and has room left of the
        // boxes: it keeps the space above it from the text before, as it had.
        String xml = convert(8.5f, true);
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        float above = 0;
        for (int i = 0; i < body.getLength(); i++) {
            if (body.item(i) instanceof Element p && p.getTagName().equals("w:p")) {
                Element spacing = (Element) p.getElementsByTagName("w:spacing").item(0);
                String text = ownText(p);
                if (text.contains("Sample pages")) {
                    above += spacing == null || spacing.getAttribute("w:before").isEmpty() ? 0
                            : Integer.parseInt(spacing.getAttribute("w:before")) / 20f;
                    break;
                }
                above = text.isBlank() && spacing != null && "exact".equals(spacing.getAttribute("w:lineRule"))
                        ? above + Integer.parseInt(spacing.getAttribute("w:line")) / 20f : 0;
            }
        }
        assertTrue(above > 50, "the right-aligned caption keeps the room above it: " + above + " pt");
    }

    /** A paragraph's text, leaving out the text boxes anchored in it. */
    private static String ownText(Element p) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = p.getElementsByTagName("w:t");
        for (int i = 0; i < ts.getLength(); i++) {
            Node n = ts.item(i);
            boolean inBox = false;
            for (Node up = n.getParentNode(); up != null && up != p; up = up.getParentNode()) {
                inBox |= up.getNodeName().equals("w:txbxContent");
            }
            if (!inBox) {
                sb.append(n.getTextContent());
            }
        }
        return sb.toString();
    }

    private String convert(float below) throws IOException {
        return convert(below, false);
    }

    /**
     * The page, with the captions' baseline {@code below} the lower row's foot; with {@code flushRight}, the right-hand
     * boxes only and one caption set flush with the column's right edge.
     */
    private String convert(float below, boolean flushRight) throws IOException {
        Path pdf = dir.resolve("notice.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDRectangle landscape = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
            PDPage page = new PDPage(landscape);
            doc.addPage(page);
            float h = landscape.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, SANS, 36, h - 70, 26, "Your New Notice: Overview");
                for (int i = 0; i < 21; i++) {
                    text(cs, PLAIN, 36, h - 112 - 16 * i, 12, "Line " + (i + 1) + " of the overview beside the sample pages");
                }
                float[][] rows = {{98, 318}, {335, ROW_FOOT}};
                for (float[] row : rows) {
                    for (float x : flushRight ? new float[] {562} : new float[] {382, 562}) {
                        cs.setNonStrokingColor(new Color(225, 225, 225));
                        cs.addRect(x, h - row[1], 172, row[1] - row[0]);
                        cs.fill();
                        cs.setNonStrokingColor(Color.BLACK);
                        for (int j = 0; j < 12; j++) {
                            text(cs, SANS, x + 8, h - row[0] - 16 - 15 * j, 7, "Sample page line " + (j + 1) + " in small print");
                        }
                    }
                }
                if (flushRight) {
                    String caption = "Sample pages";
                    text(cs, SANS, 734 - SANS.getStringWidth(caption) / 1000 * 12, h - ROW_FOOT - below, 12, caption);
                } else {
                    text(cs, SANS, 385, h - ROW_FOOT - below, 12, "Page 3");
                    text(cs, SANS, 566, h - ROW_FOOT - below, 12, "Last Page");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("notice.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
