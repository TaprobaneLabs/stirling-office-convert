package stirling.software.officeconvert.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.table.PageContent.FillBox;
import stirling.software.officeconvert.table.PageContent.PdfWord;
import stirling.software.officeconvert.table.PageContent.Ruling;

class LatticeHeadingTest {

    private static final float SIZE = 7;

    /** A letter's advance, in ems: CMS sets its notices in a narrow sans. */
    private static final float LETTER = 0.42f;

    /**
     * CMS's Part A notice: the claims table's heading stands in three right-aligned lines over its first dotted rule,
     * and only the "Maximum You May Be Billed" column is boxed. The heading joins the table as its first row, and with
     * it the table has the lines to find its columns by: each heading over its own column, "4 days" and "Yes" apart from
     * the claim's label. The caption over the heading stays out.
     */
    @Test
    void aHeadingOverTheFirstRuleJoinsTheTable() {
        List<Ruling> hs = new ArrayList<>();
        for (float y : new float[] {330.5f, 343.5f, 355}) {
            hs.add(new Ruling(y, 245, 548, 0.5f, 0));
        }
        List<Ruling> vs = List.of(new Ruling(486, 330.5f, 355, 0.5f, 0), new Ruling(527, 330.5f, 355, 0.5f, 0));

        List<PdfWord> words = new ArrayList<>();
        phrase(words, "Your Inpatient Claims", 250, 290);
        String[][] heading = {
            {"Benefit", "Days", "Used"}, {"", "Claim", "Approved?"}, {"Non-", "Covered", "Charges"},
            {"Amount", "Medicare", "Paid"}, {"Maximum", "You May", "Be Billed"}, {"See", "Notes", "Below"}};
        float[] rightEdges = {374, 413, 448, 483, 524, 547};
        for (int c = 0; c < heading.length; c++) {
            for (int line = 0; line < 3; line++) {
                if (!heading[c][line].isEmpty()) {
                    rightAligned(words, heading[c][line], rightEdges[c], 312 + 7 * line);
                }
            }
        }
        phrase(words, "Benefit Period May 27", 250, 340);
        rightAligned(words, "4 days", 374, 340);
        rightAligned(words, "Yes", 413, 340);
        rightAligned(words, "$0.00", 448, 340);
        rightAligned(words, "$4,886.98", 483, 340);
        rightAligned(words, "$0.00", 524, 340);
        phrase(words, "Total for Claim #20905", 250, 351);
        rightAligned(words, "$0.00", 448, 351);
        rightAligned(words, "$4,886.98", 483, 351);
        rightAligned(words, "$0.00", 524, 351);
        phrase(words, "A,B", 529, 351);

        DraftTable table = only(LatticeFinder.find(0, hs, vs, words));
        assertTrue(table.top() > 290, "the caption stays out");
        assertEquals("Benefit Days Used", text(table, "Benefit Days"));
        assertEquals("Claim Approved?", text(table, "Claim"));
        assertEquals("Maximum You May Be Billed", text(table, "Maximum"));
        assertEquals("4 days", text(table, "4 days"));
        assertEquals("Yes", text(table, "Yes"));
        assertEquals("Benefit Period May 27", text(table, "Benefit Period"));
        assertEquals(column(table, "Benefit Days"), column(table, "4 days"), "the heading over its days");
        assertEquals(column(table, "Claim"), column(table, "Yes"), "the heading over its answer");
    }

    /**
     * CMS's Part B notice: the heading is inside the rules, and "Approved?", set right-aligned under "Service", reaches
     * left past the gutter its column's answers keep from the services' names, by a quarter of its width. The body's
     * rows place the gutter, and the heading stays its own column's, not spanning the names' column too.
     */
    @Test
    void aRightAlignedHeadingReachingPastItsGutterStaysItsColumns() {
        List<Ruling> hs = new ArrayList<>();
        for (float y : new float[] {300, 322, 347, 358, 369}) {
            hs.add(new Ruling(y, 245, 548, 0.5f, 0));
        }
        List<Ruling> vs = List.of(new Ruling(486, 300, 369, 0.5f, 0), new Ruling(527, 300, 369, 0.5f, 0));

        List<PdfWord> words = new ArrayList<>();
        rightAligned(words, "Service", 373, 310);
        phrase(words, "Service Provided & Billing Code", 250, 319);
        rightAligned(words, "Approved?", 373, 319);
        rightAligned(words, "Charged", 410, 319);
        rightAligned(words, "Amount", 448, 319);
        rightAligned(words, "Paid", 483, 319);
        rightAligned(words, "Billed", 524, 319);
        phrase(words, "Eye and medical examination", 250, 332);
        rightAligned(words, "Yes", 373, 332);
        rightAligned(words, "$143.00", 410, 332);
        rightAligned(words, "$107.97", 448, 332);
        rightAligned(words, "$86.38", 483, 332);
        rightAligned(words, "$21.59", 524, 332);
        phrase(words, "for diagnosis", 250, 341);
        phrase(words, "Destruction of skin (17000)", 250, 355);
        rightAligned(words, "NO", 373, 355);
        rightAligned(words, "68.56", 410, 355);
        rightAligned(words, "0.00", 448, 355);
        rightAligned(words, "0.00", 483, 355);
        rightAligned(words, "68.56", 524, 355);
        phrase(words, "Total for Claim #02-10195", 250, 366);
        rightAligned(words, "$211.56", 410, 366);
        rightAligned(words, "$107.97", 448, 366);
        rightAligned(words, "$86.38", 483, 366);
        rightAligned(words, "$90.15", 524, 366);

        DraftTable table = only(LatticeFinder.find(0, hs, vs, words));
        assertEquals("Service Provided & Billing Code", text(table, "Service Provided"));
        assertEquals("Service Approved?", text(table, "Service Approved?"));
        assertEquals(column(table, "Service Approved?"), column(table, "Yes"), "the heading over its answers");
        assertEquals(column(table, "Service Approved?"), column(table, "NO"));
        assertEquals("Destruction of skin (17000)", text(table, "Destruction"));
    }

    /**
     * A ledger's header row drawn as a band, white on dark blue, just over its first rule: the band joins the table
     * later with its fill (TableShaper.addBandHeader), so the lattice leaves its line out. The same line on white paper
     * is the table's heading.
     */
    @Test
    void aHeadingOnABandAcrossTheTableStaysOut() {
        List<Ruling> hs = new ArrayList<>();
        for (float y : new float[] {330.5f, 343.5f, 356.5f}) {
            hs.add(new Ruling(y, 245, 548, 0.5f, 0));
        }
        List<Ruling> vs = new ArrayList<>();
        for (float x : new float[] {245, 330, 470, 548}) {
            vs.add(new Ruling(x, 330.5f, 356.5f, 0.5f, 0));
        }
        List<PdfWord> words = new ArrayList<>();
        phrase(words, "Date", 250, 327);
        phrase(words, "Description", 335, 327);
        rightAligned(words, "Amount", 543, 327);
        phrase(words, "2026-01-02", 250, 340);
        phrase(words, "Coffee beans", 335, 340);
        rightAligned(words, "$12.50", 543, 340);
        phrase(words, "2026-01-05", 250, 353);
        phrase(words, "Printer paper", 335, 353);
        rightAligned(words, "$48.00", 543, 353);

        assertTrue(only(LatticeFinder.find(0, hs, vs, words)).top() < 321, "on white paper the heading joins");
        List<FillBox> band = List.of(new FillBox(245, 318, 548, 330.5f, 0x1F3864));
        assertTrue(only(LatticeFinder.find(0, hs, vs, words, band)).top() > 330, "on a band it stays out");
    }

    /**
     * Two ruled grids stacked 5 pt apart (Codex's review): the first one's last row stands just over the second's first
     * rule, as a heading would, but under the first one's foot rule. It stays the first table's, and the second keeps
     * its own top.
     */
    @Test
    void theRowOverAnotherGridsFootIsNoHeading() {
        List<Ruling> hs = new ArrayList<>();
        List<Ruling> vs = new ArrayList<>();
        List<PdfWord> words = new ArrayList<>();
        String[][] rows = {{"Rent", "$1,200.00"}, {"Fees", "$35.00"}, {"Water", "$45.10"}, {"Power", "$88.20"}};
        float[] tops = {100, 145};
        for (int g = 0; g < 2; g++) {
            float top = tops[g];
            for (float y : new float[] {top, top + 20, top + 40}) {
                hs.add(new Ruling(y, 100, 400, 0.5f, 0));
            }
            for (float x : new float[] {100, 250, 400}) {
                vs.add(new Ruling(x, top, top + 40, 0.5f, 0));
            }
            for (int r = 0; r < 2; r++) {
                phrase(words, rows[g * 2 + r][0], 105, top + 16 + 20 * r, 10);
                phrase(words, rows[g * 2 + r][1], 255, top + 16 + 20 * r, 10);
            }
        }

        List<DraftTable> tables = new ArrayList<>();
        for (LatticeFinder.Found f : LatticeFinder.find(0, hs, vs, words)) {
            if (f.table() != null) {
                tables.add(f.table());
            }
        }
        assertEquals(2, tables.size(), "two tables");
        assertTrue(tables.get(1).top() > 144, "the second keeps its top: " + tables.get(1).top());
        int fees = 0;
        for (DraftTable t : tables) {
            for (DraftTable.Cell c : t.cells()) {
                fees += join(c.words()).contains("Fees") ? 1 : 0;
            }
        }
        assertEquals(1, fees, "the first table's last row once");
    }

    /** Adds {@code text}'s words so that they end at {@code right}. */
    private static void rightAligned(List<PdfWord> words, String text, float right, float baseline) {
        float width = 0;
        String[] parts = text.split(" ");
        for (String p : parts) {
            width += p.length() * SIZE * LETTER;
        }
        width += (parts.length - 1) * SIZE * 0.28f;
        phrase(words, text, right - width, baseline);
    }

    private static DraftTable only(List<LatticeFinder.Found> found) {
        List<DraftTable> tables = new ArrayList<>();
        for (LatticeFinder.Found f : found) {
            if (f.table() != null) {
                tables.add(f.table());
            }
        }
        assertEquals(1, tables.size(), "one table");
        return tables.getFirst();
    }

    private static DraftTable.Cell cell(DraftTable table, String start) {
        for (DraftTable.Cell cell : table.cells()) {
            if (join(cell.words()).startsWith(start)) {
                return cell;
            }
        }
        throw new AssertionError("no cell starts with " + start + " in " + texts(table));
    }

    private static String text(DraftTable table, String start) {
        return join(cell(table, start).words());
    }

    /** A cell's words in reading order: line by line, left to right. */
    private static String join(List<PdfWord> words) {
        List<PdfWord> sorted = new ArrayList<>(words);
        sorted.sort((a, b) -> Math.abs(a.baseline() - b.baseline()) > 1 ? Float.compare(a.baseline(), b.baseline())
                : Float.compare(a.x(), b.x()));
        StringBuilder sb = new StringBuilder();
        for (PdfWord w : sorted) {
            sb.append(sb.isEmpty() ? "" : " ").append(w.text());
        }
        return sb.toString();
    }

    private static int column(DraftTable table, String start) {
        DraftTable.Cell c = cell(table, start);
        assertNotNull(c);
        assertEquals(1, c.colSpan(), start + " in one column: " + texts(table));
        return c.col();
    }

    private static List<String> texts(DraftTable table) {
        List<String> out = new ArrayList<>();
        for (DraftTable.Cell c : table.cells()) {
            out.add(c.row() + "," + c.col() + "x" + c.colSpan() + "=" + join(c.words()));
        }
        return out;
    }

    /** Adds {@code text}'s words at {@code x}, a space apart. */
    private static void phrase(List<PdfWord> words, String text, float x, float baseline) {
        phrase(words, text, x, baseline, SIZE);
    }

    private static void phrase(List<PdfWord> words, String text, float x, float baseline, float size) {
        for (String w : text.split(" ")) {
            float right = x + w.length() * size * LETTER;
            int n = w.length();
            float[] left = new float[n];
            float[] rights = new float[n];
            String[] glyphs = new String[n];
            for (int i = 0; i < n; i++) {
                left[i] = x + (right - x) * i / n;
                rights[i] = x + (right - x) * (i + 1) / n;
                glyphs[i] = String.valueOf(w.charAt(i));
            }
            words.add(new PdfWord(w, x, right, baseline - size * 0.8f, baseline + size * 0.2f, baseline, size, false,
                    false, 0, size * 0.28f, left, rights, glyphs));
            x = right + size * 0.28f;
        }
    }
}
