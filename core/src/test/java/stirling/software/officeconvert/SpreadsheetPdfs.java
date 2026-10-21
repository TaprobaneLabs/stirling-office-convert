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

    /** A statement whose ruled table lacks the rule between paid out and paid in on every other row. */
    static Path statement(Path dir) throws IOException {
        Path pdf = dir.resolve("statement.pdf");
        String[][] rows = {
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
                        if (c == 3 && r % 2 == 0 && r > 0) {
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
                        PDType1Font font = r == 0 ? BOLD : REGULAR;
                        float x = c >= 2 ? cols[c + 1] - 4 - font.getStringWidth(s) / 1000f * 10 : cols[c] + 4;
                        text(cs, font, 10, x, top - r * rowH - 14, s);
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
