package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * A reference set with a hanging indent stays left-aligned with its indent, even where its lines' centres line up. Its
 * first line starts at the column's edge and the rest 10 pt in, so a first line that ends about 10 pt past the others puts
 * every centre within a point or two: USGS Fact Sheets 2024-3021 and 2024-3052 had references set centred, and Word moved
 * each of their lines' starts.
 */
class HangingReferenceTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final float SIZE = 10;
    private static final float LEFT = 72;
    private static final float INDENT = 10;
    private static final String[] BODY = {
        "The concentration of lithium in brines was predicted across the",
        "southern part of the state by using a model that incorporated the",
        "lithium concentration data and geologic information from the wells.",
    };
    /**
     * Lines of the first reference, and the width a line is stretched to (0: as set), which puts all four lines' centres
     * within 2.5 pt.
     */
    private static final String[] REFERENCE = {
        "Bradley, D.C., Stillings, L.L., Jaskula, B.W., Munk, L., and McCauley,",
        "A.D., 2017, Lithium, chap. K of Schulz, K.J., DeYoung, J.H., Jr.,",
        "Seal, R.R., II, and Bradley, D.C., eds., Critical mineral resources",
        "of the United States: U.S. Geological Survey Professional Paper.",
    };
    private static final float[] WIDTHS = {0, 0, 261f, 260.5f};
    private static final String[] NEXT = {
        "Knierim, K.J., Blondes, M.S., Masterson, A.L., Freeman, P.A., and",
        "McDevitt, B., 2024, Lithium concentrations in brines of the Smackover",
        "Formation: U.S. Geological Survey data release.",
    };

    @TempDir Path dir;

    @Test
    void aHangingReferenceIsNotCentred() throws Exception {
        String para = paragraph(convert(), "Bradley, D.C.");
        assertFalse(para.contains("<w:jc w:val=\"center\"/>"), "the reference isn't centred: " + para);
        Matcher ind = Pattern.compile("<w:ind w:left=\"(-?\\d+)\"[^>]*w:hanging=\"(\\d+)\"").matcher(para);
        assertTrue(ind.find(), "the reference hangs its first line: " + para);
        assertEquals(INDENT, Integer.parseInt(ind.group(2)) / 20f, 1f, "by the PDF's indent");
    }

    private static String paragraph(String xml, String start) {
        Matcher m = Pattern.compile("<w:p>.*?</w:p>|<w:p .*?</w:p>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            if (m.group().replaceAll("<[^>]+>", "").startsWith(start)) {
                return m.group();
            }
        }
        throw new AssertionError("no paragraph starts with " + start);
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("refs.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (String line : BODY) {
                    text(cs, LEFT, y, line, 0);
                    y -= 11;
                }
                y -= 20;
                for (int i = 0; i < REFERENCE.length; i++) {
                    float natural = width(REFERENCE[i]);
                    float tracking = WIDTHS[i] == 0 ? 0 : (WIDTHS[i] - natural) / (REFERENCE[i].length() - 1);
                    text(cs, i == 0 ? LEFT : LEFT + INDENT, y, REFERENCE[i], tracking);
                    y -= 11;
                }
                y -= 6;
                for (int i = 0; i < NEXT.length; i++) {
                    text(cs, i == 0 ? LEFT : LEFT + INDENT, y, NEXT[i], 0);
                    y -= 11;
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

    private static void text(PDPageContentStream cs, float x, float y, String s, float tracking) throws IOException {
        cs.beginText();
        cs.setFont(FONT, SIZE);
        cs.setCharacterSpacing(tracking);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
