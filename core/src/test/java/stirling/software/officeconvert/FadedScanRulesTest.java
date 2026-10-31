package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.junit.jupiter.api.Test;

/**
 * A copier's scan of a light table: its rules come out broken where the ink fades, and some of a row rule or a
 * cell's side not at all. The rows still come out one to a spreadsheet row, a wrapped cell's lines in its cell
 * (step G's scans to Excel: a meeting's action table and a sideways table ran rows together).
 */
class FadedScanRulesTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    private static final float SCALE = 150f / 72f;

    private static final float LEFT = 72;

    private static final float TOP = 120;

    private static final float[] COLS = {0, 200, 300, 440};

    private static final float HEAD = 20;

    /** Three lines a row, twelve points apart, and the next row's first line twelve points on: only the rules tell the rows apart. */
    private static final float ROW = 36;

    private static final float LINE = 12;

    private static final String[][] ROWS = {
        {"Rail connections to the inland", "depot carried more of the", "import boxes this year", "KR", "2026-11-25"},
        {"Harbour and Finch Logistics", "moved more containers", "through its terminals", "KR", "2026-11-26"},
        {"The board approved a second", "cold store building, due", "to open next year", "MdM", "2026-12-18"},
    };

    @Test
    void rowRulesBrokenIntoPiecesStillSplitTheRows() throws Exception {
        String sheet = convert(g -> {
            for (int r = 0; r <= ROWS.length + 1; r++) {
                float y = rowTop(r);
                // Each row rule in three pieces, six points apart, none ending at a column's side
                line(g, Color.BLACK, LEFT, y, LEFT + 120, y);
                line(g, Color.BLACK, LEFT + 126, y, LEFT + 260, y);
                line(g, Color.BLACK, LEFT + 266, y, LEFT + COLS[3], y);
            }
            for (float c : COLS) {
                line(g, Color.BLACK, LEFT + c, TOP, LEFT + c, rowTop(ROWS.length + 1));
            }
        });
        assertRowsApart(sheet);
    }

    @Test
    void aRowRuleDrawnOnlyFaintlyIsFoundAcrossTheRow() throws Exception {
        String sheet = convert(g -> {
            for (int r = 0; r <= ROWS.length + 1; r++) {
                float y = rowTop(r);
                if (r == 3) {
                    // The rule under the second row: dark over its last column only, faint grey over the rest
                    line(g, new Color(215, 215, 215), LEFT, y, LEFT + COLS[2], y);
                    line(g, Color.BLACK, LEFT + COLS[2], y, LEFT + COLS[3], y);
                } else {
                    line(g, Color.BLACK, LEFT, y, LEFT + COLS[3], y);
                }
            }
            line(g, Color.BLACK, LEFT, TOP, LEFT, rowTop(ROWS.length + 1));
            line(g, Color.BLACK, LEFT + COLS[3], TOP, LEFT + COLS[3], rowTop(ROWS.length + 1));
            // The columns' sides are gone in the second row, as a copier dropped them
            for (int c = 1; c < COLS.length - 1; c++) {
                line(g, Color.BLACK, LEFT + COLS[c], TOP, LEFT + COLS[c], rowTop(2));
                line(g, Color.BLACK, LEFT + COLS[c], rowTop(3), LEFT + COLS[c], rowTop(ROWS.length + 1));
            }
        });
        assertRowsApart(sheet);
    }

    private static float rowTop(int r) {
        return r == 0 ? TOP : TOP + HEAD + (r - 1) * ROW;
    }

    private void assertRowsApart(String sheet) {
        List<List<String>> rows = rows(sheet);
        for (String[] item : ROWS) {
            List<String> row = rows.stream().filter(cells -> cells.stream().anyMatch(c -> c.startsWith(item[0])))
                    .findFirst().orElseThrow(() -> new AssertionError(item[0] + " not found in " + rows));
            assertTrue(row.stream().anyMatch(c -> c.equals(item[0] + "\n" + item[1] + "\n" + item[2])),
                    "the wrapped cell keeps its lines, and no other row's: " + row);
            assertTrue(row.contains(item[3]), "the owner is in the action's row: " + row);
        }
        assertFalse(rows.stream().anyMatch(cells -> cells.contains(ROWS[0][1])), "no wrapped line on a row of its own");
    }

    private interface Drawing {
        void draw(Graphics2D g);
    }

    private static void line(Graphics2D g, Color c, float x0, float y0, float x1, float y1) {
        g.setColor(c);
        g.drawLine(px(x0), px(y0), px(x1), px(y1));
    }

    private static int px(float pt) {
        return Math.round(pt * SCALE);
    }

    /** A scanned Letter page with {@code rules} on white paper, the table's text in an invisible OCR layer. */
    private static String convert(Drawing rules) throws IOException {
        BufferedImage img = new BufferedImage(px(612), px(792), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setStroke(new BasicStroke(2));
        rules.draw(g);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDImageXObject x = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(x, 0, 0, 612, 792);
                hidden(cs, 72, 90, "Actions agreed at the meeting");
                hidden(cs, LEFT + 4, TOP + 14, "Action");
                hidden(cs, LEFT + COLS[1] + 4, TOP + 14, "Owner");
                hidden(cs, LEFT + COLS[2] + 4, TOP + 14, "Due");
                for (int r = 0; r < ROWS.length; r++) {
                    float base = rowTop(r + 1) + 9;
                    for (int l = 0; l < 3; l++) {
                        hidden(cs, LEFT + 4, base + l * LINE, ROWS[r][l]);
                    }
                    hidden(cs, LEFT + COLS[1] + 4, base, ROWS[r][3]);
                    hidden(cs, LEFT + COLS[2] + 4, base, ROWS[r][4]);
                }
                hidden(cs, 72, rowTop(ROWS.length + 1) + 30, "The next meeting is in November.");
            }
            PdfToXlsx.convert(doc, out, PdfToXlsx.Options.defaults());
        }
        Map<String, byte[]> parts = SpreadsheetPdfs.parts(out.toByteArray());
        return resolve(SpreadsheetPdfs.part(parts, "xl/worksheets/sheet1.xml"), SpreadsheetPdfs.part(parts, "xl/sharedStrings.xml"));
    }

    /** Invisible text whose baseline starts at ({@code x}, {@code baseline}), from the page's top left. */
    private static void hidden(PDPageContentStream cs, float x, float baseline, String s) throws IOException {
        cs.beginText();
        cs.setRenderingMode(RenderingMode.NEITHER);
        cs.setFont(FONT, 10);
        cs.newLineAtOffset(x, 792 - baseline);
        cs.showText(s);
        cs.endText();
    }

    /** The sheet with each shared string's index replaced by its text, as {@code <row>…<c>text</c>…</row>}. */
    private static String resolve(String sheet, String strings) {
        List<String> shared = new ArrayList<>();
        Matcher si = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL).matcher(strings);
        while (si.find()) {
            StringBuilder text = new StringBuilder();
            Matcher t = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(si.group(1));
            while (t.find()) {
                text.append(t.group(1));
            }
            shared.add(text.toString().replace("&amp;", "&"));
        }
        StringBuilder out = new StringBuilder();
        Matcher row = Pattern.compile("<row[^>]*>(.*?)</row>", Pattern.DOTALL).matcher(sheet);
        while (row.find()) {
            out.append("<row>");
            Matcher c = Pattern.compile("<c ([^>]*)>(?:<v>([^<]*)</v>|<is><t[^>]*>([^<]*)</t></is>)</c>").matcher(row.group(1));
            while (c.find()) {
                String value = c.group(1).contains("t=\"s\"") ? shared.get(Integer.parseInt(c.group(2)))
                        : c.group(2) != null ? c.group(2) : c.group(3);
                out.append("<c>").append(value).append("</c>");
            }
            out.append("</row>");
        }
        return out.toString();
    }

    private static List<List<String>> rows(String resolved) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = Pattern.compile("<row>(.*?)</row>", Pattern.DOTALL).matcher(resolved);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher c = Pattern.compile("<c>(.*?)</c>", Pattern.DOTALL).matcher(row.group(1));
            while (c.find()) {
                cells.add(c.group(1));
            }
            rows.add(cells);
        }
        return rows;
    }
}
