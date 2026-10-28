package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * A table header whose columns set their lines half a line apart is one row. Each line took a row of its own, and Word
 * sets no row shorter than its text, so the header grew by two lines and everything under the table moved down: USGS
 * Fact Sheet 2024-3052's Table 1, whose columns' header lines are 11 pt apart and 5.5 pt from the next column's. A row
 * whose cells sit a point or two off one baseline is one row too, while a header's lines that hold figures keep their
 * rows: the Federal Reserve's H.8 sets a heading over four date columns 4 pt above them, and joined, the heading took a
 * column of its own and every figure under it moved.
 */
class StaggeredHeaderTest {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font PLAIN = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float[] COLS = {51, 92, 120, 175, 255};
    /** Each column's header lines and the height (from the top of the header) its first line starts at. */
    private static final String[][] HEADER = {
        {"Porosity", "(percent)"},
        {"Brine", "volume", "(trillion", "liters)"},
        {"Machine-", "learning", "model"},
        {"Average predicted", "lithium concentration", "from all cells in", "the model", "(milligrams per liter)"},
        {"Calculated", "lithium", "(million", "metric", "tons)"},
    };
    private static final float[] STARTS = {16.5f, 5.5f, 11f, 0f, 0f};
    private static final String[][] ROWS = {
        {"10", "65", "Low", "96", "5.1"}, {"", "65", "Median", "107", "5.7"}, {"", "65", "High", "119", "6.3"},
        {"20", "130", "Low", "96", "10"}, {"", "130", "Median", "107", "11"}, {"", "130", "High", "119", "13"},
        {"30", "200", "Low", "96", "15"}, {"", "200", "Median", "107", "17"}, {"", "200", "High", "119", "19"},
    };

    @TempDir Path dir;

    @Test
    void theStaggeredHeaderIsOneRow() throws Exception {
        String xml = convert();
        int at = xml.indexOf("Porosity");
        assertTrue(at >= 0 && xml.lastIndexOf("<w:tbl>", at) >= 0, "the header is in a table");
        String table = xml.substring(xml.lastIndexOf("<w:tbl>", at), xml.indexOf("</w:tbl>", at));
        List<String> rows = new ArrayList<>();
        Matcher row = Pattern.compile("<w:tr>.*?</w:tr>", Pattern.DOTALL).matcher(table);
        while (row.find()) {
            rows.add(row.group().replaceAll("<w:br/>", " ").replaceAll("<[^>]+>", ""));
        }
        assertEquals(1 + ROWS.length, rows.size(), "a header row and a row for each line of numbers: " + rows);
        for (String[] col : HEADER) {
            for (String line : col) {
                assertTrue(rows.getFirst().contains(line), "the header row holds \"" + line + "\": " + rows.getFirst());
            }
        }
    }

    @Test
    void aRowWhoseDateSitsOffItsBaselineIsOneRow() throws Exception {
        String[][] rows = {
            {"02 Jan", "ATM withdrawal", "325.75", "276.40"}, {"07 Jan", "Direct debit, utilities", "26.26", "5,770.99"},
            {"08 Jan", "Direct debit, utilities", "209.89", "5,561.10"}, {"10 Jan", "ATM withdrawal", "259.54", "6,954.19"},
            {"11 Jan", "ATM withdrawal", "332.04", "6,610.21"}, {"15 Jan", "Standing order, rent", "51.87", "13,449.06"},
        };
        String xml = convert(cs -> {
            String[] head = {"Date", "Description", "Amount", "Balance"};
            float[] x = {50, 150, 330, 480};
            for (int c = 0; c < head.length; c++) {
                text(cs, BOLD, 9, x[c], H - 100, head[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    // The date set 2.2 pt above its row's baseline, as a scan's OCR read it
                    text(cs, PLAIN, 9, x[c], H - 118 - 16 * r + (c == 0 ? 2.2f : 0), rows[r][c]);
                }
            }
        });
        List<String> found = rows(xml, "Description");
        assertEquals(1 + rows.length, found.size(), "a row for the header and each entry: " + found);
        for (int r = 0; r < rows.length; r++) {
            assertTrue(found.get(r + 1).contains(rows[r][0]) && found.get(r + 1).contains(rows[r][3]),
                    "the date shares its entry's row: " + found.get(r + 1));
        }
    }

    @Test
    void aHeadingOverDatesKeepsItsOwnRow() throws Exception {
        String[] dates = {"Aug 20", "Aug 27", "Sep 03", "Sep 10"};
        String[] months = {"Feb", "Mar", "Apr", "May"};
        String xml = convert(cs -> {
            // The Federal Reserve's H.8: a heading over the weekly columns, years and months over the monthly ones,
            // and the dates, each line 4 or 5 pt from the next
            text(cs, PLAIN, 8, 470, H - 100, "Week ending");
            for (int c = 0; c < months.length; c++) {
                text(cs, PLAIN, 8, 200 + 45 * c, H - 104, "2025");
            }
            text(cs, PLAIN, 8, 60, H - 109, "Account");
            for (int c = 0; c < months.length; c++) {
                text(cs, PLAIN, 8, 202 + 45 * c, H - 114, months[c]);
                text(cs, PLAIN, 8, 400 + 45 * c, H - 118, dates[c]);
            }
            String[] accounts = {"Deposits", "Large time deposits", "Other deposits", "Borrowings", "Total liabilities"};
            for (int r = 0; r < accounts.length; r++) {
                float y = H - 134 - 10 * r;
                text(cs, PLAIN, 8, 60, y, (r + 34) + " " + accounts[r]);
                for (int c = 0; c < 8; c++) {
                    text(cs, PLAIN, 8, (c < 4 ? 196 : 396) + 45 * (c % 4), y, String.valueOf(176585 + 1931 * c + 977 * r));
                }
            }
        });
        for (String row : rows(xml, "Deposits")) {
            if (row.contains("Week ending")) {
                for (String date : dates) {
                    assertTrue(!row.contains(date), "the heading over the dates keeps its own row: " + row);
                }
            }
        }
    }

    /** The rows of the table holding {@code text}, their cells' text joined, line breaks as spaces. */
    private static List<String> rows(String xml, String text) {
        int at = xml.indexOf(text);
        assertTrue(at >= 0 && xml.lastIndexOf("<w:tbl>", at) >= 0, "the text is in a table");
        String table = xml.substring(xml.lastIndexOf("<w:tbl>", at), xml.indexOf("</w:tbl>", at));
        List<String> rows = new ArrayList<>();
        Matcher row = Pattern.compile("<w:tr>.*?</w:tr>", Pattern.DOTALL).matcher(table);
        while (row.find()) {
            rows.add(row.group().replaceAll("<w:br/>", " ").replaceAll("</w:tc>", " | ").replaceAll("<[^>]+>", ""));
        }
        return rows;
    }

    private interface Page {
        void draw(PDPageContentStream cs) throws IOException;
    }

    private String convert(Page page) throws IOException {
        Path pdf = dir.resolve("rows.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p = new PDPage(PDRectangle.LETTER);
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                page.draw(cs);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("rows.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String convert() throws IOException {
        Path pdf = dir.resolve("table.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, BOLD, 9, 36, H - 40, "Table 1. Calculated amount of lithium in brines, based on porosity");
                text(cs, BOLD, 9, 36, H - 52, "ranging from 10 to 30 percent and three model predictions.");
                rule(cs, 36, H - 72, 290);
                float top = 82;
                for (int c = 0; c < HEADER.length; c++) {
                    for (int i = 0; i < HEADER[c].length; i++) {
                        text(cs, BOLD, 8, COLS[c], H - top - 8 - STARTS[c] - 11 * i, HEADER[c][i]);
                    }
                }
                rule(cs, 36, H - 136, 290);
                for (int r = 0; r < ROWS.length; r++) {
                    for (int c = 0; c < ROWS[r].length; c++) {
                        text(cs, PLAIN, 8, COLS[c] + (c == 2 ? 0 : 8), H - 148 - 13.8f * r, ROWS[r][c]);
                    }
                }
                rule(cs, 36, H - 148 - 13.8f * ROWS.length, 290);
                String[] after = {
                    "USGS scientists process brine samples at a mobile field station. The samples are sent",
                    "to the laboratory, where their lithium is measured with the other elements in the brine.",
                };
                for (int i = 0; i < after.length; i++) {
                    text(cs, PLAIN, 9, 36, H - 300 - 12 * i, after[i]);
                }
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("table.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void rule(PDPageContentStream cs, float x, float y, float width) throws IOException {
        cs.setLineWidth(0.5f);
        cs.moveTo(x, y);
        cs.lineTo(x + width, y);
        cs.stroke();
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
