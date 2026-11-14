package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormLinesTest {

    @TempDir static Path dir;

    /**
     * A page set as CFPB's Loan Estimate sets its cost lines: a label, and its amount far to its right. Each such line
     * becomes a label cell and a typed number beside it; prose, a chart's labels, a contents line ending in its page
     * number and a contact line stay one cell each.
     */
    @Test
    void aLabelAndItsFigureLandInCellsOfTheirOwn() throws IOException {
        Path pdf = dir.resolve("form.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.BOLD, 16, 72, 720, "Loan Estimate");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 690,
                        "Save this Loan Estimate to compare with your Closing Disclosure.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 660, "Monthly Principal & Interest");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 420, 660, "$703.33");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 630,
                        "Your payment may change each year after the first five years of the loan.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 600, "Loan Amount");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 300, 600, "$162,000");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 470, 600, "NO");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 570,
                        "The interest rate below is fixed for the first five years of the loan term.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 540, "Interest Rate");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 340, 540, "3.875%");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 500,
                        "Frozen Fruit and Frozen Juice Concentrate in Cold Storage");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 520, 500, "4");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 470,
                        "The figures below are monthly averages for the two years shown.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 150, 440, "2022");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 380, 440, "2024");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 410,
                        "Questions about this release go to the press office.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 380, "Media contact:");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 260, 380, "(202) 691-5902");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 350,
                        "Escrow covers property taxes and homeowner's insurance, estimated for the year.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 320, "Estimated Escrow");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 420, 320, "$206.40");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 6, 456.3f, 324, "1");
            }
            doc.save(pdf.toFile());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PdfToXlsx.convert(doc, out, PdfToXlsx.Options.defaults());
        }
        Map<String, byte[]> parts = SpreadsheetPdfs.parts(out.toByteArray());
        String sheet = SpreadsheetPdfs.part(parts, "xl/worksheets/sheet1.xml");
        String strings = SpreadsheetPdfs.part(parts, "xl/sharedStrings.xml");

        assertTrue(number(sheet, "B", "703.33"), "the payment as a number beside its label");
        assertTrue(number(sheet, "B", "162000"), "the loan amount as a number");
        assertTrue(number(sheet, "B", "0.03875"), "the rate as a percentage");
        assertTrue(strings.contains(">Monthly Principal &amp; Interest<"), "the label alone in its cell");
        assertTrue(strings.contains(">NO<"), "the answer in a cell of its own");
        assertFalse(strings.contains("$703.33"), "no amount left as text");
        String styles = SpreadsheetPdfs.part(parts, "xl/styles.xml");
        assertTrue(styles.contains("formatCode=\"&quot;$&quot;0.00\""), "dollars and cents");
        assertTrue(styles.contains("formatCode=\"&quot;$&quot;#,##0\""), "whole dollars, grouped");
        assertTrue(styles.contains("formatCode=\"0.000%\""), "a percentage to three places");

        assertTrue(Pattern.compile(">Frozen Fruit and Frozen Juice Concentrate in Cold Storage +4<").matcher(strings).find(),
                "a contents line keeps its page number");
        assertTrue(Pattern.compile(">2022 +2024<").matcher(strings).find(), "a chart's labels stay together");
        assertTrue(Pattern.compile(">Media contact: +\\(202\\) 691-5902<").matcher(strings).find(), "a contact line stays whole");
        assertFalse(number(sheet, "B", "206.401") || number(sheet, "B", "206.4"),
                "an amount with a note mark raised against it stays text, not 206.401");
        assertTrue(strings.contains("Estimated Escrow"), strings);
    }

    /**
     * A typed figure's column is sized as Excel shows a number, not as text: "0.908893877" in 7.5 pt bold got 52.67 pt
     * of the 62.25 pt Excel's own fit needs (TextWidthTest), and Excel showed hashes.
     */
    @Test
    void aSmallBoldFigureGetsTheWidthExcelShowsIt() throws IOException {
        Path pdf = dir.resolve("ratio.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.BOLD, 16, 72, 720, "Quarterly summary");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.REGULAR, 10, 72, 690,
                        "The ratio below compares this quarter's output with the last quarter's output.");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.BOLD, 7.5f, 72, 660, "Output ratio");
                SpreadsheetPdfs.text(cs, SpreadsheetPdfs.BOLD, 7.5f, 420, 660, "0.908893877");
            }
            doc.save(pdf.toFile());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PdfToXlsx.convert(doc, out, PdfToXlsx.Options.defaults());
        }
        String sheet = SpreadsheetPdfs.part(SpreadsheetPdfs.parts(out.toByteArray()), "xl/worksheets/sheet1.xml");
        assertTrue(number(sheet, "B", "0.908893877"), "the figure typed beside its label: " + sheet);
        Matcher col = Pattern.compile("<col min=\"2\" max=\"2\" width=\"([0-9.]+)\"").matcher(sheet);
        assertTrue(col.find(), "column B sized: " + sheet);
        // XlsxWriter writes widths in units of 5.25 pt
        float points = Float.parseFloat(col.group(1)) * 5.25f;
        assertTrue(points >= 62.25f - 0.03f, "wide enough for Excel to show the figure: " + points + " pt");
    }

    private static boolean number(String sheet, String column, String value) {
        return Pattern.compile("<c r=\"" + column + "[0-9]+\"( s=\"[0-9]+\")?><v>" + Pattern.quote(value) + "</v>")
                .matcher(sheet).find();
    }
}
