package stirling.software.officeconvert;

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
 * A list of labelled rows read as a table ends before the text boxes set beside it, and keeps text out only of the room up to
 * them. Its last column had run on to the text's right edge, under the boxes; Word can't set a table under a box text wraps
 * around, so it moved the boxes' paragraph and everything after it to the next page. USGS Fact Sheet 2023-3022's alert
 * levels, beside its aviation colour codes, took two pages for one.
 */
class KeyValueBesideBoxTest {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final String[] LABELS = {"Normal", "Advisory", "Watch", "Warning"};
    private static final String[][] BOXES = {
        {"Green", "Volcano is in", "typical state", "or after a", "change from", "a higher level", "activity has", "ceased."},
        {"Yellow", "Volcano shows", "signs of unrest", "above its", "background", "level and is", "closely", "watched."},
    };

    @TempDir Path dir;

    @Test
    void theTableEndsBeforeTheBoxesBesideIt() throws Exception {
        String xml = convert();
        assertTrue(xml.contains("<w:tbl>"), "the labelled rows are a table");
        assertTrue(xml.contains("Volcano is in"), "the boxes' text is there");
        Matcher anchor = firstBox(xml);
        float wrapLeft = (Long.parseLong(anchor.group(2)) - Long.parseLong(anchor.group(1))) / 12700f;
        float right = tableRight(xml);
        assertTrue(right <= wrapLeft + 1, "the table ends at " + right + " pt, before the room the box keeps clear from " + wrapLeft);
    }

    @Test
    void theTableKeepsTextOutOfTheRoomUpToTheBoxesOnly() throws Exception {
        // Set on the page beside the boxes, the table keeps the room to its right as far as the first box, which keeps its
        // own room. Kept to the column's edge, it moved text above the boxes, the heading over them, below the table.
        String xml = convert();
        String table = xml.substring(xml.indexOf("<w:tbl>"), xml.indexOf("</w:tbl>"));
        Matcher room = Pattern.compile("<w:tblpPr [^>]*w:rightFromText=\"(\\d+)\"").matcher(table);
        assertTrue(room.find(), "the table is set on the page, beside the boxes");
        Matcher anchor = firstBox(xml);
        float boxLeft = Long.parseLong(anchor.group(2)) / 12700f;
        float clearTo = tableRight(xml) + Integer.parseInt(room.group(1)) / 20f;
        assertTrue(clearTo <= boxLeft + 3, "the table keeps text out up to " + clearTo + " pt, the box starts at " + boxLeft);
    }

    /** The first box's anchor: its wrapping gap (group 1) and its place across the page (group 2), in EMUs. */
    private static Matcher firstBox(String xml) {
        Matcher anchor = Pattern.compile("<wp:anchor [^>]*distL=\"(\\d+)\"[^>]*>(?:(?!</wp:anchor>).)*?<wp:positionH relativeFrom=\"page\">"
                + "<wp:posOffset>(\\d+)</wp:posOffset>(?:(?!</wp:anchor>).)*?Volcano is in", Pattern.DOTALL).matcher(xml);
        assertTrue(anchor.find(), "the first box is a text box set on the page");
        return anchor;
    }

    /** Where the first table ends across the page, in points. */
    private static float tableRight(String xml) {
        String table = xml.substring(xml.indexOf("<w:tbl>"), xml.indexOf("</w:tbl>"));
        // A table set on the page starts where its position says; one in the text, at the margin plus its indent.
        Matcher onPage = Pattern.compile("<w:tblpPr [^>]*w:horzAnchor=\"page\"[^>]*w:tblpX=\"(-?\\d+)\"").matcher(table);
        Matcher margin = Pattern.compile("<w:pgMar [^>]*w:left=\"(\\d+)\"").matcher(xml);
        Matcher indent = Pattern.compile("<w:tblInd w:w=\"(-?\\d+)\"").matcher(table);
        assertTrue(margin.find() && indent.find(), "a left margin and the table's indent");
        float right = onPage.find() ? Integer.parseInt(onPage.group(1)) / 20f
                : Integer.parseInt(margin.group(1)) / 20f + Integer.parseInt(indent.group(1)) / 20f;
        Matcher col = Pattern.compile("<w:gridCol w:w=\"(\\d+)\"").matcher(table);
        while (col.find()) {
            right += Integer.parseInt(col.group(1)) / 20f;
        }
        return right;
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("levels.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, BOLD, 40, 740, 16, "Volcano Alert Levels and Aviation Color Codes");
                String[] intro = {
                    "The survey issues alert levels for the volcanoes of the islands and colour codes for aircraft. Alert",
                    "levels tell people on the ground about a volcano's status, and colour codes tell the aviation sector",
                    "about hazards in the air. Notices go out when the level rises or falls and say what has changed.",
                };
                for (int i = 0; i < intro.length; i++) {
                    text(cs, PLAIN, 40, 715 - 13 * i, 10, intro[i]);
                }
                String[] value = {"Volcano activity is described here", "over a few short lines of text", "that sit beside their label in", "the list of alert levels."};
                for (int i = 0; i < LABELS.length; i++) {
                    float top = 650 - 70 * i;
                    text(cs, BOLD, 40, top, 10, LABELS[i]);
                    for (int j = 0; j < value.length; j++) {
                        text(cs, PLAIN, 120, top - 12 * j, 9, value[j]);
                    }
                }
                Color[] fills = {new Color(168, 214, 168), new Color(242, 238, 168)};
                for (int b = 0; b < BOXES.length; b++) {
                    float x = 330 + 100 * b;
                    cs.setNonStrokingColor(fills[b]);
                    cs.addRect(x, 420, 90, 245);
                    cs.fill();
                    cs.setNonStrokingColor(Color.BLACK);
                    for (int j = 0; j < BOXES[b].length; j++) {
                        text(cs, j == 0 ? BOLD : PLAIN, x + 8, 650 - 13 * j, 9, BOXES[b][j]);
                    }
                }
                text(cs, PLAIN, 40, 360, 10, "Notices are sent by email and posted on the observatory's website as soon as they are issued.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("levels.docx");
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
