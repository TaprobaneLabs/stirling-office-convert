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
    /** Where each column's header lines and figures start, as in 3052's Table 1: 10 pt or so between columns. */
    private static final float[] HEADER_X = {41, 84, 122, 165, 255};
    private static final float[] DATA_X = {51, 93, 120, 198, 268};
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

    /**
     * A bank statement's OCR layer, nine rows of it as read from a scan turned a little: each row's words fall about 4.5 pt
     * from its balance on the right to its date on the left, so the date's half of a row is a line 3 pt below the
     * other half, and was taken for the start of the row below. Joined to its own row with the lines grouped with it,
     * it took that row along, and the rows ran together three to five to a cell.
     */
    private static final List<Word> STATEMENT = List.of(
        new Word("Description", 150.8f, 175.7f, 9.0f),
        new Word("Paid", 253.1f, 174.6f, 9.0f),
        new Word("out", 275.8f, 174.4f, 9.0f),
        new Word("Balance", 457.9f, 172.5f, 9.0f),
        new Word("01", 48.6f, 188.6f, 9.0f),
        new Word("Jan", 61.6f, 188.5f, 9.0f),
        new Word("Refund", 151.2f, 187.6f, 9.0f),
        new Word("85.53", 525.3f, 183.9f, 9.0f),
        new Word("01", 48.6f, 200.5f, 9.0f),
        new Word("Jan", 61.5f, 200.4f, 9.0f),
        new Word("Direct", 151.2f, 199.5f, 9.0f),
        new Word("debit,", 178.2f, 199.2f, 9.0f),
        new Word("utilities", 204.5f, 199.0f, 9.0f),
        new Word("516.62", 418.0f, 196.9f, 9.0f),
        new Word("602.15", 520.2f, 195.9f, 9.0f),
        new Word("02", 48.9f, 212.8f, 11.0f),
        new Word("Jan", 61.9f, 212.6f, 11.0f),
        new Word("|", 145.1f, 211.7f, 11.0f),
        new Word("ATM", 150.8f, 211.7f, 11.0f),
        new Word("withdrawal", 173.2f, 211.4f, 11.0f),
        new Word("|", 247.0f, 210.7f, 11.0f),
        new Word("325.75", 315.7f, 209.9f, 11.0f),
        new Word("|", 349.2f, 209.6f, 11.0f),
        new Word("|", 451.4f, 208.5f, 11.0f),
        new Word("276.40", 520.2f, 207.8f, 11.0f),
        new Word("02", 48.9f, 224.6f, 9.0f),
        new Word("Jan", 61.9f, 224.5f, 9.0f),
        new Word("Card", 151.2f, 223.6f, 9.0f),
        new Word("payment,", 174.6f, 223.4f, 9.0f),
        new Word("213.18", 316.1f, 222.0f, 9.0f),
        new Word("63.22", 525.6f, 219.9f, 9.0f),
        new Word("_grocery", 147.2f, 234.0f, 10.0f),
        new Word("03", 49.3f, 247.3f, 11.0f),
        new Word("Jan", 62.3f, 247.2f, 11.0f),
        new Word("|", 145.1f, 246.4f, 11.0f),
        new Word("Salary", 151.5f, 246.3f, 11.0f),
        new Word("|", 247.3f, 245.4f, 11.0f),
        new Word("|", 349.6f, 244.4f, 11.0f),
        new Word("494.17", 418.0f, 243.7f, 11.0f),
        new Word("|", 451.8f, 243.3f, 11.0f),
        new Word("557.39", 520.6f, 242.7f, 11.0f),
        new Word("03", 49.3f, 259.2f, 11.0f),
        new Word("Jan", 62.3f, 259.1f, 11.0f),
        new Word("Refund", 151.9f, 258.2f, 11.0f),
        new Word("280.19", 316.4f, 256.6f, 11.0f),
        new Word("277.20", 520.9f, 254.5f, 11.0f),
        new Word("04", 49.3f, 271.1f, 11.0f),
        new Word("Jan", 62.3f, 270.9f, 11.0f),
        new Word("|", 145.1f, 270.1f, 11.0f),
        new Word("ATM", 151.5f, 270.0f, 11.0f),
        new Word("withdrawal", 173.9f, 269.8f, 11.0f),
        new Word("|", 247.7f, 269.0f, 11.0f),
        new Word("|", 349.9f, 267.9f, 11.0f),
        new Word("822.11", 418.7f, 267.2f, 11.0f),
        new Word("|", 452.2f, 266.8f, 11.0f),
        new Word("1,099.31", 513.4f, 266.2f, 11.0f),
        new Word("04", 49.3f, 283.0f, 11.0f),
        new Word("Jan", 62.6f, 282.8f, 11.0f),
        new Word("Refund", 151.9f, 281.9f, 11.0f),
        new Word("2,064.50", 410.8f, 279.4f, 11.0f),
        new Word("3,163.81", 513.0f, 278.4f, 11.0f),
        new Word("05", 49.7f, 294.8f, 11.0f),
        new Word("Jan", 62.6f, 294.7f, 11.0f),
        new Word("|", 145.8f, 293.8f, 11.0f),
        new Word("Transfer", 151.5f, 293.8f, 11.0f),
        new Word("in", 190.1f, 293.4f, 11.0f),
        new Word("|", 248.0f, 292.7f, 11.0f),
        new Word("|", 350.3f, 291.7f, 11.0f),
        new Word("1,130.49", 411.1f, 291.0f, 11.0f),
        new Word("|", 452.5f, 290.6f, 11.0f),
        new Word("4,294.30", 513.0f, 289.9f, 11.0f)
    );

    private record Word(String text, float x, float baseline, float size) {}

    @Test
    void aTiltedScansRowsKeepTheirOwnDates() throws Exception {
        String xml = convert(cs -> {
            for (Word w : STATEMENT) {
                text(cs, PLAIN, w.size(), w.x(), H - w.baseline(), w.text());
            }
        });
        List<String> found = rows(xml, "Description");
        long entries = found.stream().filter(r -> r.contains("Jan")).count();
        assertEquals(9, entries, "a row for each entry: " + found);
        for (String row : found) {
            assertTrue(row.split("Jan", -1).length <= 2, "one date to a row: " + row);
        }
    }

    @Test
    void aHeadersSecondLineOverFiguresStaysInTheHeader() throws Exception {
        String[][] rows = {
            {"Widget", "Small blue widget", "12.50"}, {"Gadget", "Folding gadget", "8.75"},
            {"Bracket", "Steel wall bracket", "4.20"}, {"Hinge", "Brass door hinge", "3.10"},
            {"Clamp", "Bench clamp", "15.00"},
        };
        String xml = convert(cs -> {
            // "Product" and "Price" in 12 pt, "Description" 3.5 pt below them in 8 pt, "(USD)" under "Price" 13.5 pt
            // below them: "(USD)" sits in the column of prices but holds no figure, so it stays with the header
            text(cs, BOLD, 12, 72, H - 100, "Product");
            text(cs, BOLD, 12, 400, H - 100, "Price");
            text(cs, BOLD, 8, 180, H - 103.5f, "Description");
            text(cs, BOLD, 8, 400, H - 113.5f, "(USD)");
            for (int r = 0; r < rows.length; r++) {
                text(cs, PLAIN, 10, 72, H - 135 - 15 * r, rows[r][0]);
                text(cs, PLAIN, 10, 180, H - 135 - 15 * r, rows[r][1]);
                text(cs, PLAIN, 10, 400, H - 135 - 15 * r, rows[r][2]);
            }
        });
        List<String> found = rows(xml, "Product");
        String header = found.getFirst();
        assertTrue(header.contains("Product") && header.contains("Description") && header.contains("Price")
                && header.contains("(USD)"), "the header's lines share its row: " + found);
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
                        text(cs, BOLD, 8, HEADER_X[c], H - top - 8 - STARTS[c] - 11 * i, HEADER[c][i]);
                    }
                }
                rule(cs, 36, H - 136, 290);
                for (int r = 0; r < ROWS.length; r++) {
                    for (int c = 0; c < ROWS[r].length; c++) {
                        text(cs, PLAIN, 8, DATA_X[c], H - 148 - 13.8f * r, ROWS[r][c]);
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
