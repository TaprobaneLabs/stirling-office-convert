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
 * The shapes of a drawn box under a paragraph ride a line no taller than the room down to the box. That line had reached
 * the box's foot; the box's text is a text box Word wraps text around, so Word moved the line below the box and the box's
 * height came twice: USGS Fact Sheet 2024-3021's contact box and all after it went to a page of its own.
 */
class RoundedBoxUnderTextTest {

    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    /** 3021's box starts 12 pt under the paragraph above it, close enough to ride it. */
    private static final float BOX_TOP = 526;
    private static final float BOX_BOTTOM = 691;

    @TempDir Path dir;

    @Test
    void theBoxsShapesRideALineEndingAtTheBox() throws Exception {
        String xml = convert();
        assertTrue(xml.contains("<w:txbxContent>") && xml.contains("For more information"), "the box's text is a text box");
        assertTrue(xml.contains("Publishing support"), "the text under the box is there");
        float tallest = 0;
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        for (int i = 0; i < body.getLength(); i++) {
            if (body.item(i) instanceof Element p && p.getTagName().equals("w:p") && ownText(p).isBlank()) {
                Element spacing = (Element) p.getElementsByTagName("w:spacing").item(0);
                if (spacing != null && "exact".equals(spacing.getAttribute("w:lineRule"))) {
                    tallest = Math.max(tallest, Integer.parseInt(spacing.getAttribute("w:line")) / 20f);
                }
            }
        }
        assertTrue(tallest < (BOX_BOTTOM - BOX_TOP) / 2,
                "no empty line reaches over the box's " + (BOX_BOTTOM - BOX_TOP) + " pt: the tallest is " + tallest + " pt");
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

    private String convert() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 52; i++) {
                    text(cs, 36, h - 100 - 12 * i, 9, "Reference line " + (i + 1) + " of the cited works in column one");
                }
                for (int i = 0; i < 30; i++) {
                    text(cs, 315, h - 100 - 12 * i, 9, "Second column reference " + (i + 1) + " with its long title");
                }
                text(cs, 315, h - 480, 9, "By Hana R. Thurman, Nicholas M. Enwright, Michael J. Osland,");
                text(cs, 323, h - 492, 9, "Davina L. Passeri, Richard H. Day, and Bethanie M. Simons");
                text(cs, 323, h - 504, 7, "Cherokee Nation System Solutions, under contract to the USGS");
                text(cs, 323, h - 514, 7, "U.S. Geological Survey");
                roundedBox(cs, 308, h - BOX_BOTTOM, 275, BOX_BOTTOM - BOX_TOP, 8);
                String[] inside = {"For more information about this publication, contact", "Director, Wetland and Aquatic Research Center",
                    "U.S. Geological Survey", "700 Cajundome Blvd.", "Lafayette, LA 70506-3152", "",
                    "For additional information, visit the center's website", "", "Any use of trade, firm, or product names is",
                    "for descriptive purposes only and does not imply", "endorsement by the U.S. Government."};
                for (int i = 0; i < inside.length; i++) {
                    if (!inside[i].isEmpty()) {
                        text(cs, 315, h - BOX_TOP - 14 - 12 * i, 8, inside[i]);
                    }
                }
                text(cs, 315, h - BOX_BOTTOM - 20, 8, "Publishing support provided by");
                text(cs, 315, h - BOX_BOTTOM - 32, 8, "Lafayette Publishing Service Center");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A grey box with rounded corners and a dark outline, filled and then stroked, as the USGS sheets draw theirs. */
    private static void roundedBox(PDPageContentStream cs, float x, float y, float w, float hgt, float r) throws IOException {
        for (boolean stroke : new boolean[] {false, true}) {
            float k = 0.5523f * r;
            cs.moveTo(x + r, y);
            cs.lineTo(x + w - r, y);
            cs.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
            cs.lineTo(x + w, y + hgt - r);
            cs.curveTo(x + w, y + hgt - r + k, x + w - r + k, y + hgt, x + w - r, y + hgt);
            cs.lineTo(x + r, y + hgt);
            cs.curveTo(x + r - k, y + hgt, x, y + hgt - r + k, x, y + hgt - r);
            cs.lineTo(x, y + r);
            cs.curveTo(x, y + r - k, x + r - k, y, x + r, y);
            cs.closePath();
            if (stroke) {
                cs.setStrokingColor(new Color(35, 31, 32));
                cs.setLineWidth(1f);
                cs.stroke();
            } else {
                cs.setNonStrokingColor(new Color(209, 211, 212));
                cs.fill();
            }
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static void text(PDPageContentStream cs, float x, float y, float size, String s) throws IOException {
        cs.beginText();
        cs.setFont(PLAIN, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
