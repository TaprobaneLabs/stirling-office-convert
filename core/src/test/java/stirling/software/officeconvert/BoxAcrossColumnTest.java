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
 * Text under a box that fills its column starts where the PDF has it. Word moves the lines a box leaves no room beside, a
 * spacer's included, below the box, so a spacer standing for all the room down to the text, the box's height with it, set
 * the text a box's height too low: USGS Fact Sheet 2023-3022's cover sent its introduction to a page of its own.
 */
class BoxAcrossColumnTest {

    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final float PANEL_TOP = 100;
    private static final float PANEL_BOTTOM = 300;

    @TempDir Path dir;

    @Test
    void noSpacerStandsForTheBoxsHeight() throws Exception {
        String xml = convert();
        assertTrue(xml.contains("<w:txbxContent>") && xml.contains("Islands of the eastern chain"), "the panel is a text box");
        assertTrue(xml.contains("Introduction"), "the text under it is there");
        float tallest = 0;
        NodeList body = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:body").item(0).getChildNodes();
        for (int i = 0; i < body.getLength(); i++) {
            if (body.item(i) instanceof Element p && p.getTagName().equals("w:p") && ownText(p).isBlank()) {
                NodeList spacing = p.getElementsByTagName("w:spacing");
                if (spacing.getLength() > 0 && "exact".equals(((Element) spacing.item(0)).getAttribute("w:lineRule"))) {
                    tallest = Math.max(tallest, Integer.parseInt(((Element) spacing.item(0)).getAttribute("w:line")) / 20f);
                }
            }
        }
        assertTrue(tallest < (PANEL_BOTTOM - PANEL_TOP) / 2,
                "no empty paragraph stands for the panel's " + (PANEL_BOTTOM - PANEL_TOP) + " pt: the tallest is " + tallest + " pt");
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
        Path pdf = dir.resolve("cover.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float h = PDRectangle.LETTER.getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < 40; i++) {
                    text(cs, PLAIN, 36, h - PANEL_TOP - 10 - 12 * i, 10, "Left column line " + (i + 1) + " of the islands");
                }
                cs.setNonStrokingColor(new Color(60, 100, 160));
                cs.addRect(210, h - PANEL_BOTTOM, 380, PANEL_BOTTOM - PANEL_TOP);
                cs.fill();
                cs.setNonStrokingColor(Color.WHITE);
                String[] panel = {"Islands of the eastern chain", "Tutuila and Aunu'u to the west", "Ofu, Olosega and Ta'u to the east"};
                for (int i = 0; i < panel.length; i++) {
                    text(cs, PLAIN, 230, h - PANEL_TOP - 30 - 14 * i, 11, panel[i]);
                }
                cs.setNonStrokingColor(Color.BLACK);
                text(cs, BOLD, 214, h - PANEL_BOTTOM - 20, 13, "Introduction");
                for (int i = 0; i < 20; i++) {
                    text(cs, PLAIN, 214, h - PANEL_BOTTOM - 38 - 12 * i, 10,
                            "Right column line " + (i + 1) + " tells how the islands formed and how they are watched");
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("cover.docx");
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
