package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * A text box keeps the text that ends beside it, on its left, out of the room up to that text, with room to spare for the
 * lines a fit widens. Its wrapping gap was the PDF's gap to the nearest line beside it, so Word's text could reach exactly
 * as far as the PDF's widest line there and no further: on USGS Fact Sheet 2024-3021's last page, the box in the right column kept the left
 * column's references from the point where its widest line ended, and a reference whose first line Word sets 6 pt wider
 * than the PDF, fitted to that width, lost its last word to the next line.
 */
class BoxWrapGapTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float SIZE = 10;
    private static final float LEFT = 36;
    private static final float MEASURE = 260;
    private static final float BOX_X = 308;
    private static final float BOX_TOP = 500;
    private static final float BOX_BOTTOM = 330;
    private static final String TEXT = "Schuurman and others described a framework for the natural resource manager that "
            + "sets out how a park can resist, accept or direct the changes that rising water brings to its habitats, its "
            + "historic and cultural resources and the infrastructure that serves its visitors. The maps of projected "
            + "flooding in this report can be used with that framework to weigh the choices open to managers, and the same "
            + "approach can be used to show how tidal wetlands may move inland as the sea rises over the coming decades. "
            + "Sweet and others set out patterns and projections of high tide flooding along the coast of the country "
            + "using a common threshold for minor, moderate and major flooding, which this report applies to the park. "
            + "The National Park Service keeps the boundaries of its units in a national dataset, which was used to clip "
            + "the maps to the park and to report the areas of land and water in each class of flooding probability.";

    @TempDir Path dir;

    @Test
    void theBoxLeavesRoomPastTheWidestLineBesideIt() throws Exception {
        List<Float> rights = new ArrayList<>();
        String xml = convert(rights);
        Matcher anchor = Pattern.compile("<wp:anchor [^>]*distL=\"(\\d+)\"[^>]*>(?:(?!</wp:anchor>).)*?"
                + "<wp:positionH relativeFrom=\"page\"><wp:posOffset>(\\d+)</wp:posOffset>(?:(?!</wp:anchor>).)*?"
                + "For more information", Pattern.DOTALL).matcher(xml);
        assertTrue(anchor.find(), "the box is a text box set on the page");
        float wrapLeft = (Long.parseLong(anchor.group(2)) - Long.parseLong(anchor.group(1))) / 12700f;
        float widest = rights.stream().max(Float::compare).orElseThrow();
        assertTrue(wrapLeft >= widest + 5, "the box keeps text out from " + wrapLeft + " pt, and the widest line beside it "
                + "ends at " + widest + " pt");
    }

    /** Two columns of ragged text, the right one holding a grey box with a heading and four lines. */
    private String convert(List<Float> besideRights) throws IOException {
        Path pdf = dir.resolve("refs.pdf");
        String[] words = TEXT.split(" ");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                int w = 0;
                for (int col = 0; col < 2; col++) {
                    float x = col == 0 ? LEFT : 316;
                    for (float y = 730; y >= 120; y -= 12) {
                        if (col == 1 && y <= BOX_TOP + 12 && y >= BOX_BOTTOM - 12) {
                            continue;
                        }
                        StringBuilder line = new StringBuilder(words[w++ % words.length]);
                        while (width(line + " " + words[w % words.length]) <= MEASURE) {
                            line.append(' ').append(words[w++ % words.length]);
                        }
                        text(cs, FONT, SIZE, x, y, line.toString());
                        if (col == 0 && y + 2 >= BOX_BOTTOM && y - 2 <= BOX_TOP) {
                            besideRights.add(x + width(line.toString()));
                        }
                    }
                }
                cs.setNonStrokingColor(new Color(209, 211, 212));
                cs.addRect(BOX_X, BOX_BOTTOM, 264, BOX_TOP - BOX_BOTTOM);
                cs.fill();
                cs.setNonStrokingColor(Color.BLACK);
                text(cs, BOLD, 9, BOX_X + 10, BOX_TOP - 18, "For more information about this publication, contact");
                String[] box = {"Director, Wetland and Aquatic Research Center", "U.S. Geological Survey",
                    "700 Cajundome Blvd.", "Lafayette, LA 70506"};
                for (int i = 0; i < box.length; i++) {
                    text(cs, FONT, SIZE, BOX_X + 10, BOX_TOP - 34 - 14 * i, box[i]);
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("refs.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float width(String s) {
        try {
            return FONT.getStringWidth(s) / 1000f * SIZE;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
