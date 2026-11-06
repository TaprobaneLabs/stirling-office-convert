package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A rule under rows Word sets lower than the PDF rides a short line under them. DOE OIG's 2025 assessment draws its budget
 * figures as labels and amounts at staggered heights, which Word sets in taller rows, and a rule 13 pt under the last row.
 * The line carrying the rule took those 13 pt on top of the rows' extra height, so the heading under it went 10 pt lower in
 * Word, onto its own rule fixed to the page.
 */
class RuleUnderLoweredRowsTest {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();

    @TempDir Path dir;

    @Test
    void theRuleRidesAShortLineAndTheHeadingKeepsItsPlace() throws Exception {
        List<float[]> rows = ridingLines(convert());
        assertEquals(1, rows.size(), "one line carries the rule");
        float[] row = rows.getFirst();
        assertTrue(row[0] <= 6f, "the line under the rows is short, as Word already sets them lower: " + row[0] + " pt");
        assertTrue(row[1] >= 2f, "the rule stays under the rows' text: " + row[1] + " pt into its line");
    }

    /** Each empty line of exact height holding a shape placed from it: {its height, the shape's offset}, in points. */
    private static List<float[]> ridingLines(String xml) throws Exception {
        List<float[]> found = new ArrayList<>();
        NodeList ps = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("w:p");
        for (int i = 0; i < ps.getLength(); i++) {
            Element p = (Element) ps.item(i);
            if (p.getElementsByTagName("w:t").getLength() > 0) {
                continue;
            }
            Element spacing = (Element) p.getElementsByTagName("w:spacing").item(0);
            NodeList pos = p.getElementsByTagName("wp:positionV");
            if (spacing == null || !"exact".equals(spacing.getAttribute("w:lineRule")) || pos.getLength() == 0) {
                continue;
            }
            Element v = (Element) pos.item(0);
            if (!"paragraph".equals(v.getAttribute("relativeFrom"))) {
                continue;
            }
            float offset = Long.parseLong(v.getElementsByTagName("wp:posOffset").item(0).getTextContent()) / 12700f;
            found.add(new float[] {Integer.parseInt(spacing.getAttribute("w:line")) / 20f, offset});
        }
        return found;
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("budget.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, PLAIN, 11, 72, 89.7f, "The Office of Inspector General has a mandate to oversee the Department's work");
                text(cs, PLAIN, 11, 72, 104.1f, "across many program, staff, and operational offices of the Department.");
                // The budget figures, at the heights the assessment sets them (tops, in points from the page's top)
                text(cs, BOLD, 11, 414.8f, 209.1f, "FULL-TIME");
                text(cs, BOLD, 11, 216.8f, 216.3f, "BASE APPROPRIATION");
                text(cs, BOLD, 11, 414.8f, 223.5f, "EMPLOYEES");
                text(cs, BOLD, 11, 45.8f, 239.2f, "Department of Energy");
                text(cs, BOLD, 11, 242.0f, 240.3f, "$53,000,000,000");
                text(cs, BOLD, 11, 441.2f, 240.5f, "15,705");
                text(cs, BOLD, 11, 45.8f, 254.9f, "Office of Inspector");
                text(cs, BOLD, 11, 242.8f, 263.2f, "$86,000,000");
                text(cs, BOLD, 11, 440.8f, 263.2f, "247");
                text(cs, BOLD, 11, 45.8f, 269.3f, "General");
                // The rule under them, level, and the next heading with its own rule under it
                rule(cs, 37.2f, 501.1f, 574.8f, 1.5f);
                text(cs, BOLD, 21, 36, 304.1f, "The Department of Energy");
                rule(cs, 36, 459.8f, 610.2f, 3f);
                text(cs, PLAIN, 11, 72, 343.0f, "The Department's mission is to ensure America's security and prosperity by");
                text(cs, PLAIN, 11, 72, 357.4f, "addressing its energy, environmental, and nuclear challenges.");
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("budget.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void rule(PDPageContentStream cs, float x0, float y, float x1, float width) throws IOException {
        cs.setLineWidth(width);
        cs.moveTo(x0, y);
        cs.lineTo(x1, y);
        cs.stroke();
    }

    /** Text whose line box starts {@code top} points from the page's top. */
    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float top, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, H - top - size);
        cs.showText(s);
        cs.endText();
    }
}
