package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A paragraph just under a photo with text beside it keeps the room above it in a spacer, not as its own space above. Word
 * fits a paragraph's first line, space above and all, beside a float that space reaches, so the line kept only the words
 * that fit beside the photo: USGS Fact Sheet 2023-3022's caption under its photo set two words to the photo's right and
 * ran a line longer.
 */
class SpaceBesideFloatTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float PHOTO_TOP = 450;
    private static final float PHOTO_BOTTOM = 650;

    @TempDir Path dir;

    @Test
    void theCaptionsRoomAboveIsASpacerBesideThePhoto() throws Exception {
        assertSpacerAbove(convert(false), "Workers install");
    }

    /** A caption that runs on to the next page joins its rest there without taking its room above back. */
    @Test
    void aCaptionCarriedToTheNextPageKeepsItsRoomInTheSpacer() throws Exception {
        String xml = convert(true);
        assertTrue(xml.contains("emergency managers"), "the caption's rest is there");
        assertSpacerAbove(xml, "Workers install");
        Element joined = paragraph(xml, "Workers install");
        assertTrue(ownText(joined).contains("emergency managers"), "the caption joins its rest from the next page");
    }

    /**
     * Floats lying wholly inside a caption's room above count too. USGS Fact Sheet 2024-3033 sets panels of text beside a
     * map between its introduction and the map's caption; the caption's space above held them all, and Word set its first
     * line right of the panels, the rest under them.
     */
    @Test
    void aCaptionUnderPanelsBesideAMapKeepsItsRoomInASpacer() throws Exception {
        assertSpacerAbove(convertPanels(), "Map showing", false);
    }

    private static Element paragraph(String xml, String words) throws Exception {
        NodeList ps = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:p");
        for (int i = 0; i < ps.getLength(); i++) {
            if (ownText((Element) ps.item(i)).contains(words)) {
                return (Element) ps.item(i);
            }
        }
        throw new AssertionError("no paragraph holds " + words);
    }

    private static void assertSpacerAbove(String xml, String words) throws Exception {
        assertSpacerAbove(xml, words, true);
    }

    /** {@code picture}: the float is a picture, not a text box. */
    private static void assertSpacerAbove(String xml, String words, boolean picture) throws Exception {
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        Element before = null;
        Element caption = null;
        for (int i = 0; i < body.getLength(); i++) {
            if (body.item(i) instanceof Element p && p.getTagName().equals("w:p")) {
                if (ownText(p).contains(words)) {
                    caption = p;
                    break;
                }
                before = p;
            }
        }
        assertTrue(caption != null, "the caption is there");
        assertTrue((!picture || xml.contains("<pic:pic")) && xml.contains("wrapSquare"), "the float is one text wraps around");
        Element spacing = (Element) caption.getElementsByTagName("w:spacing").item(0);
        float space = spacing == null || spacing.getAttribute("w:before").isEmpty() ? 0
                : Integer.parseInt(spacing.getAttribute("w:before")) / 20f;
        assertTrue(space < 10, "the caption's own space above doesn't reach up beside the photo: " + space + " pt");
        assertTrue(before != null && ownText(before).isBlank(), "a spacer keeps the room above the caption");
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

    /**
     * A sheet with a photo, text beside it and a caption under it. {@code continued}: the caption is one unfinished line,
     * the page's last, and goes on at the top of a second page.
     */
    private String convert(boolean continued) throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            PDImageXObject photo = LosslessFactory.createFromImage(doc, photo(330, 250));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 28; i++) {
                    text(cs, 36, h - 72 - 13 * i, "Body text line " + (i + 1) + " runs from the left margin across the page to the right");
                }
                cs.drawImage(photo, 36, h - PHOTO_BOTTOM, 330, PHOTO_BOTTOM - PHOTO_TOP);
                String[] beside = {"By Natalia I. Deligne,", "Drew T. Downs and", "Wendy K. Stovall", "", "Edited by Phil Frederick",
                    "Layout by Kimber Petersen"};
                for (int i = 0; i < beside.length; i++) {
                    if (!beside[i].isEmpty()) {
                        text(cs, 400, h - PHOTO_TOP - 12 - 13 * i, beside[i]);
                    }
                }
                String[] caption = continued
                    ? new String[] {"Workers install monitoring equipment on the island in August 2022, working with the weather service and the"}
                    : new String[] {
                        "Workers install monitoring equipment on the island in August 2022, working with the weather service",
                        "and the territory's homeland security office to watch the volcano for signs of new activity. Photograph",
                        "by Elinor Lutu-McMoore, National Oceanic and Atmospheric Administration.",
                    };
                for (int i = 0; i < caption.length; i++) {
                    text(cs, 36, h - PHOTO_BOTTOM - 15 - 13 * i, caption[i]);
                }
            }
            if (continued) {
                PDPage next = new PDPage(PDRectangle.LETTER);
                doc.addPage(next);
                try (PDPageContentStream cs = new PDPageContentStream(doc, next)) {
                    text(cs, 36, h - 72, "territory's emergency managers, who keep the sirens and radios on every island working.");
                    for (int i = 0; i < 6; i++) {
                        text(cs, 36, h - 100 - 13 * i, "Body text line " + (i + 1) + " on the second page runs across to the right");
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** An introduction, a map with labels, a stack of panels left of it, and the map's caption under them. */
    private String convertPanels() throws IOException {
        Path pdf = dir.resolve("panels.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            PDImageXObject map = LosslessFactory.createFromImage(doc, photo(276, 300));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 6; i++) {
                    text(cs, 36, h - 72 - 13 * i, "Introduction line " + (i + 1) + " runs from the left margin across the page to the right edge");
                }
                // A map on the right: a base picture, rivers drawn over it, and labels on them
                cs.drawImage(map, 300, h - 460, 276, 300);
                cs.setStrokingColor(new Color(200, 40, 30));
                cs.setLineWidth(2f);
                for (int r = 0; r < 24; r++) {
                    float x = 310 + 11 * r;
                    cs.moveTo(x, h - 170);
                    cs.curveTo(x + 20, h - 250, x - 15, h - 330, x + 10, h - 450);
                    cs.stroke();
                }
                String[] legend = {"EXPLANATION", "Blue catfish population", "range through time", "", "", "", "pre-1996", "", "",
                    "Locations of transient", "blue catfish captured", "", "", "", "", "James"};
                for (int i = 0; i < legend.length; i++) {
                    if (!legend[i].isEmpty()) {
                        text(cs, 430, h - 190 - 13 * i, legend[i]);
                    }
                }
                for (int k = 0; k < 3; k++) {
                    float top = 170 + 95 * k;
                    cs.setNonStrokingColor(new Color(215, 240, 225));
                    rounded(cs, 52, h - top - 80, 314, 80, 10);
                    cs.fill();
                    cs.setNonStrokingColor(Color.BLACK);
                    for (int j = 0; j < 4; j++) {
                        text(cs, 60, h - top - 18 - 14 * j, "Panel " + (k + 1) + " line " + (j + 1) + " tells of the fish in this part of the bay");
                    }
                }
                String[] caption = {
                    "Map showing the spatial and temporal invasion of blue catfish in the Chesapeake Bay, 1970-2023, from state",
                    "records and the surveys of the river commissions, which keep count of where the fish are caught each year.",
                };
                for (int i = 0; i < caption.length; i++) {
                    text(cs, 36, h - 490 - 13 * i, caption[i]);
                }
                for (int i = 0; i < 6; i++) {
                    text(cs, 36, h - 540 - 13 * i, "Body text line " + (i + 1) + " under the panels runs from the left margin across to the right");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("panels.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A rectangle with rounded corners, as the fact sheet draws its panels. */
    private static void rounded(PDPageContentStream cs, float x, float y, float w, float h, float r) throws IOException {
        float k = 0.552f * r;
        cs.moveTo(x + r, y);
        cs.lineTo(x + w - r, y);
        cs.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        cs.lineTo(x + w, y + h - r);
        cs.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        cs.lineTo(x + r, y + h);
        cs.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        cs.lineTo(x, y + r);
        cs.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        cs.closePath();
    }

    private static BufferedImage photo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y += 10) {
            g.setColor(new Color(60 + y % 120, 120, 200 - y % 90));
            g.fillRect(0, y, w, 10);
        }
        g.setColor(new Color(30, 120, 40));
        g.fillOval(40, 60, 120, 140);
        g.dispose();
        return img;
    }

    private static void text(PDPageContentStream cs, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
