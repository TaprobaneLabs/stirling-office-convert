package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A paragraph beside a box on its left keeps its indent from the column's start. Its indent was measured from the room
 * beside the box but written as if from the column's start, so Word set an indented paragraph at the box's edge: CDC's
 * sheet on violence against American Indian and Alaska Native people lost the indent of a list item's runover lines
 * beside its blue panel. LibreOffice measures every indent of a paragraph from a float's edge when one of its lines starts
 * inside the float (on a text box's edge too), so the lines of a paragraph written from the column start just past it.
 */
class IndentBesideBoxTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float TEXT_X = 284.4f;
    private static final float INDENTED_X = 302.4f;

    @TempDir Path dir;

    @Test
    void theIndentedParagraphStartsWhereThePdfsDoes() throws Exception {
        Docx docx = convert(false);
        Para p = docx.para("of the homicides");
        assertEquals(INDENTED_X, docx.margin + p.left, 3f, "it starts where the PDF's does, not at the box's edge");
        assertTrue(docx.margin + p.left > docx.edge, "past the box's edge, where LibreOffice measures it from the column");
    }

    @Test
    void aParagraphAtThePanelsEdgeKeepsItsIndentsFromTheRoom() throws Exception {
        Docx docx = convert(false);
        Para p = docx.para("Homicide was the fourth");
        // From the column it would start on the box's edge, and LibreOffice would add its indent to the edge
        assertTrue(docx.margin + p.left < docx.edge - 1f, "it starts inside the box, so Word and LibreOffice set it at the edge");
    }

    @Test
    void aHangingFirstLineAtThePanelsEdgeStartsJustPastIt() throws Exception {
        Docx docx = convert(true);
        Para p = docx.para("Homicide was the fourth");
        float first = docx.margin + p.left - p.hanging;
        assertTrue(first > docx.edge + 0.1f && first < docx.edge + 1f, "the first line starts just past the box's edge: " + first);
        assertEquals(INDENTED_X, docx.margin + p.left, 1f, "the runover lines keep their indent");
    }

    private record Para(float left, float hanging) {}

    private record Docx(String xml, float margin, float edge) {

        Para para(String start) {
            Matcher m = Pattern.compile("<w:p>.*?</w:p>|<w:p .*?</w:p>", Pattern.DOTALL).matcher(xml);
            while (m.find()) {
                String para = m.group();
                if (para.replaceAll("<[^>]+>", "").startsWith(start)) {
                    return new Para(twips(para, "w:left"), twips(para, "w:hanging") - twips(para, "w:firstLine"));
                }
            }
            throw new AssertionError("no paragraph starts with " + start);
        }

        private static float twips(String para, String attr) {
            Matcher ind = Pattern.compile("<w:ind [^>]*" + attr + "=\"(-?\\d+)\"").matcher(para);
            return ind.find() ? Integer.parseInt(ind.group(1)) / 20f : 0f;
        }
    }

    private Docx convert(boolean hanging) throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // A blue panel of text down the left of the page
                cs.setNonStrokingColor(new Color(215, 230, 245));
                rounded(cs, 27, H - 600, 244, 400, 10);
                cs.fill();
                cs.setNonStrokingColor(Color.BLACK);
                for (int i = 0; i < 24; i++) {
                    text(cs, FONT, 40, 220 + 15 * i, 9, "Panel line " + (i + 1) + " says what the program does");
                }
                text(cs, BOLD, TEXT_X, 215, 11, "People experience high rates of homicide");
                String[] first = hanging ? new String[] {
                    "Homicide was the fourth leading cause of death among the young in",
                    "the years the report covers, and the sixth among women of every",
                    "age, and intimate partner violence was a factor in 38% of the cases",
                } : new String[] {
                    "Homicide was the fourth leading cause of death among the young in",
                    "the years the report covers, and the sixth among women of every age.",
                    "Intimate partner violence was a factor in 38% of the cases counted",
                };
                for (int i = 0; i < first.length; i++) {
                    text(cs, FONT, hanging && i > 0 ? INDENTED_X : TEXT_X, 240 + 13 * i, 9, first[i]);
                }
                String[] indented = {
                    "of the homicides of women and 7% of those of men, as the states",
                    "reported them to the national system each year they took part,",
                    "though some states sent reports for only part of the period.",
                };
                for (int i = 0; i < indented.length; i++) {
                    text(cs, FONT, INDENTED_X, 300 + 13 * i, 9, indented[i]);
                }
                String[] after = {
                    "Not all violence happens in isolated areas; most cases were in cities",
                    "or towns, and many in the places where the people they hurt lived.",
                };
                for (int i = 0; i < after.length; i++) {
                    text(cs, FONT, TEXT_X, 360 + 13 * i, 9, after[i]);
                }
            }
            doc.save(pdf.toFile());
        }
        Path out = dir.resolve("sheet.docx");
        PdfToDocx.convert(pdf, out, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(out.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher mar = Pattern.compile("<w:pgMar [^>]*w:left=\"(\\d+)\"").matcher(xml);
        assertTrue(mar.find(), "the section has margins");
        // The panel's wrap edge: where its anchor puts its right side, plus the gap text keeps from it
        Matcher box = Pattern.compile("<wp:anchor [^>]*distR=\"(\\d+)\"[^>]*>(?:(?!</wp:anchor>).)*?<wp:positionH relativeFrom=\"page\">"
                + "<wp:posOffset>(-?\\d+)</wp:posOffset>(?:(?!</wp:anchor>).)*?<wp:extent cx=\"(\\d+)\"(?:(?!</wp:anchor>).)*?"
                + "<wps:cNvSpPr txBox=\"1\"/>", Pattern.DOTALL).matcher(xml);
        assertTrue(box.find(), "the panel is a text box");
        float edge = (Long.parseLong(box.group(2)) + Long.parseLong(box.group(3)) + Long.parseLong(box.group(1))) / 12700f;
        return new Docx(xml, Integer.parseInt(mar.group(1)) / 20f, edge);
    }

    /** A rectangle with rounded corners, as the sheet draws its panel. */
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

    /** Text with its baseline at {@code baseline} from the page's top edge. */
    private static void text(PDPageContentStream cs, PDType1Font font, float x, float baseline, float size, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, H - baseline);
        cs.showText(s);
        cs.endText();
    }
}
