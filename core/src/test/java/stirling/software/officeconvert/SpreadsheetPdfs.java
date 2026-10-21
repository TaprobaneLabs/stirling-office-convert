package stirling.software.officeconvert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class SpreadsheetPdfs {

    static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    static final String[][] TABLE = {
        {"Region", "Units", "Revenue", "Share", "Updated", "Code"},
        {"North", "1,250", "$12,400.50", "12.5%", "2024-01-05", "02134"},
        {"South", "980", "$9,800.00", "(3.0%)", "2024-02-11", "90210"},
        {"East", "1,431", "$14,310.25", "20.1%", "2024-03-02", "01002"},
        {"West", "875", "($1,250.00)", "8.4%", "2024-03-30", "10001"},
    };

    private SpreadsheetPdfs() {}

    static Path report(Path dir, int variant) throws IOException {
        Path pdf = dir.resolve("report" + variant + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4);
            doc.addPage(first);
            try (PDPageContentStream cs = new PDPageContentStream(doc, first)) {
                text(cs, BOLD, 18, 60, 780, "Sales report " + variant);
                text(cs, REGULAR, 10, 60, 755, "Figures for the first quarter, all regions, as reported by the branches.");
                float[] cols = {60, 150, 220, 320, 390, 470, 540};
                float top = 720;
                float rowH = 20;
                for (int r = 0; r <= TABLE.length; r++) {
                    cs.moveTo(cols[0], top - r * rowH);
                    cs.lineTo(cols[cols.length - 1], top - r * rowH);
                }
                for (float x : cols) {
                    cs.moveTo(x, top);
                    cs.lineTo(x, top - TABLE.length * rowH);
                }
                cs.stroke();
                for (int r = 0; r < TABLE.length; r++) {
                    for (int c = 0; c < TABLE[r].length; c++) {
                        String s = r > 0 && c == 1 && variant > 0 ? TABLE[r][c].replace("1", String.valueOf(variant % 9 + 1))
                                : TABLE[r][c];
                        text(cs, r == 0 ? BOLD : REGULAR, 10, cols[c] + 4, top - r * rowH - 14, s);
                    }
                }
                text(cs, REGULAR, 10, 60, 590, "Notes: shares are of national revenue; brackets mark a fall.");
            }
            PDPage second = new PDPage(PDRectangle.A4);
            doc.addPage(second);
            try (PDPageContentStream cs = new PDPageContentStream(doc, second)) {
                text(cs, BOLD, 14, 60, 780, "Commentary");
                text(cs, REGULAR, 11, 60, 755, "Demand held up in every region and the new stores opened on time.");
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** A ledger whose ruled table runs from the first page onto the second, its header row repeated there. */
    static Path ledger(Path dir) throws IOException {
        return ledger(dir, 30);
    }

    /** The same ledger with {@code firstRows} rows at the foot of its first page. */
    static Path ledger(Path dir, int firstRows) throws IOException {
        Path pdf = dir.resolve("ledger" + firstRows + ".pdf");
        String[] header = {"Date", "Account", "Description", "Amount"};
        try (PDDocument doc = new PDDocument()) {
            int row = 0;
            for (int p = 0; p < 2; p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    int rows = p == 0 ? firstRows : 12;
                    float[] cols = {60, 150, 240, 420, 520};
                    float rowH = 20;
                    float top = 780;
                    if (p == 0) {
                        text(cs, BOLD, 16, 60, 790, "General ledger");
                        top = 60 + (rows + 1) * rowH;
                    }
                    for (int r = 0; r <= rows + 1; r++) {
                        cs.moveTo(cols[0], top - r * rowH);
                        cs.lineTo(cols[cols.length - 1], top - r * rowH);
                    }
                    for (float x : cols) {
                        cs.moveTo(x, top);
                        cs.lineTo(x, top - (rows + 1) * rowH);
                    }
                    cs.stroke();
                    for (int c = 0; c < header.length; c++) {
                        text(cs, BOLD, 10, cols[c] + 4, top - 14, header[c]);
                    }
                    for (int r = 1; r <= rows; r++, row++) {
                        String[] cells = {String.format("2024-%02d-%02d", 1 + row / 28, 1 + row % 28), "4" + (100 + row % 7),
                            "Entry " + (row + 1), String.format("%,.2f", 100 + row * 37.5)};
                        for (int c = 0; c < cells.length; c++) {
                            text(cs, REGULAR, 10, cols[c] + 4, top - r * rowH - 14, cells[c]);
                        }
                    }
                    if (p == 1) {
                        text(cs, REGULAR, 10, 60, top - (rows + 2) * rowH - 14, "Closing balance carried to the next year.");
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /**
     * A statement whose ruled table lacks the rule between paid out and paid in on every other row, under a year
     * label merged across both on purpose.
     */
    static Path statement(Path dir) throws IOException {
        Path pdf = dir.resolve("statement.pdf");
        String[][] rows = {
            {"", "", "2026", "", ""},
            {"Date", "Description", "Paid out", "Paid in", "Balance"},
            {"01 Jul", "Rent", "950.00", "", "4,050.00"},
            {"02 Jul", "Salary", "", "2,400.00", "6,450.00"},
            {"03 Jul", "Groceries", "84.10", "", "6,365.90"},
            {"04 Jul", "Refund", "", "1,081.64", "7,447.54"},
            {"05 Jul", "Utilities", "120.35", "", "7,327.19"},
            {"06 Jul", "Interest", "", "12.80", "7,339.99"},
        };
        float[] cols = {60, 130, 290, 380, 470, 560};
        float top = 760;
        float rowH = 20;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, BOLD, 16, 60, 790, "Account statement");
                for (int r = 0; r <= rows.length; r++) {
                    cs.moveTo(cols[0], top - r * rowH);
                    cs.lineTo(cols[cols.length - 1], top - r * rowH);
                }
                for (int r = 0; r < rows.length; r++) {
                    for (int c = 0; c < cols.length; c++) {
                        if (c == 3 && (r == 0 || r % 2 == 1 && r > 1)) {
                            continue;
                        }
                        cs.moveTo(cols[c], top - r * rowH);
                        cs.lineTo(cols[c], top - (r + 1) * rowH);
                    }
                }
                cs.stroke();
                for (int r = 0; r < rows.length; r++) {
                    for (int c = 0; c < rows[r].length; c++) {
                        String s = rows[r][c];
                        PDType1Font font = r <= 1 ? BOLD : REGULAR;
                        float x = c >= 2 && r > 0 ? cols[c + 1] - 4 - font.getStringWidth(s) / 1000f * 10 : cols[c] + 4;
                        text(cs, font, 10, x, top - r * rowH - 14, s);
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** A short table whose plain header and total row share one shade. */
    static Path shadedTotal(Path dir) throws IOException {
        return shadedRows(dir, "total.pdf", new String[][] {{"Item", "2025", "2026"}, {"Rent", "1,200.00", "1,250.00"},
            {"Power", "300.00", "320.00"}, {"Total", "1,500.00", "1,570.00"}}, new int[] {0, 3});
    }

    /** A plain header in the colour of the stripes under it, the first body row unshaded. */
    static Path stripedHeader(Path dir) throws IOException {
        return shadedRows(dir, "striped.pdf", new String[][] {{"Item", "2025", "2026"}, {"Rent", "1,200.00", "1,250.00"},
            {"Power", "300.00", "320.00"}, {"Water", "80.00", "85.00"}, {"Rates", "410.00", "430.00"},
            {"Phone", "60.00", "62.00"}}, new int[] {0, 2, 4});
    }

    private static Path shadedRows(Path dir, String name, String[][] rows, int[] shaded) throws IOException {
        Path pdf = dir.resolve(name);
        float[] cols = {60, 220, 320, 420};
        float top = 700;
        float rowH = 18;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(0.85f, 0.85f, 0.85f);
                for (int r : shaded) {
                    cs.addRect(cols[0], top - (r + 1) * rowH, cols[cols.length - 1] - cols[0], rowH);
                }
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                for (int r = 0; r <= rows.length; r++) {
                    cs.moveTo(cols[0], top - r * rowH);
                    cs.lineTo(cols[cols.length - 1], top - r * rowH);
                }
                for (float x : cols) {
                    cs.moveTo(x, top);
                    cs.lineTo(x, top - rows.length * rowH);
                }
                cs.stroke();
                for (int r = 0; r < rows.length; r++) {
                    for (int c = 0; c < rows[r].length; c++) {
                        text(cs, REGULAR, 10, cols[c] + 4, top - r * rowH - 13, rows[r][c]);
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** An invoice with a bold header row over banded rows, the first item row shaded. */
    static Path banded(Path dir) throws IOException {
        Path pdf = dir.resolve("banded.pdf");
        float[] cols = {60, 250, 330, 430, 530};
        float top = 700;
        float rowH = 18;
        int rows = 12;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(0.84f, 0.89f, 0.95f);
                for (int r = 1; r <= rows; r += 2) {
                    cs.addRect(cols[0], top - (r + 1) * rowH, cols[cols.length - 1] - cols[0], rowH);
                }
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                for (int r = 0; r <= rows + 1; r++) {
                    cs.moveTo(cols[0], top - r * rowH);
                    cs.lineTo(cols[cols.length - 1], top - r * rowH);
                }
                for (float x : cols) {
                    cs.moveTo(x, top);
                    cs.lineTo(x, top - (rows + 1) * rowH);
                }
                cs.stroke();
                String[] header = {"Description", "Qty", "Unit price", "Amount"};
                for (int c = 0; c < header.length; c++) {
                    text(cs, BOLD, 10, cols[c] + 4, top - 13, header[c]);
                }
                for (int r = 1; r <= rows; r++) {
                    int qty = 3 + r;
                    double price = 10.25 * r + 0.5;
                    String[] cells = {"Item " + r, String.valueOf(qty), String.format("%.2f", price),
                        String.format("%,.2f", qty * price)};
                    for (int c = 0; c < cells.length; c++) {
                        float x = c == 0 ? cols[c] + 4 : cols[c + 1] - 4 - REGULAR.getStringWidth(cells[c]) / 1000f * 10;
                        text(cs, REGULAR, 10, x, top - r * rowH - 13, cells[c]);
                    }
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    static Map<String, byte[]> parts(byte[] zip) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                out.put(e.getName(), z.readAllBytes());
            }
        }
        return out;
    }

    static String part(Map<String, byte[]> parts, String name) {
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }
}
