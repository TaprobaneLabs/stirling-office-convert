package stirling.software.officeconvert.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.table.PageContent.PdfWord;

class RowGrouperTest {

    /**
     * A table without figures has no header to find by them, and its rows' lines can sit closer than a header's
     * staggered lines: here each row's 6 pt description runs to a second line 7 pt below its 10 pt label, 7 pt above
     * the next row's label. Joined as a staggered header, the four rows became one and the table was lost.
     */
    @Test
    void aSmallSecondLineCloseToTheNextLabelKeepsItsRow() {
        String[] labels = {"Hydrology", "Ecology", "Geology", "Mapping"};
        String[][] notes = {
            {"Streamflow, groundwater levels", "and water quality"},
            {"Habitats, species and reefs", "that shelter the coast"},
            {"Minerals, faults and hazards", "they bring to towns"},
            {"Elevation and land cover", "maps planners use"}
        };
        List<PdfWord> words = new ArrayList<>();
        for (int r = 0; r < labels.length; r++) {
            float baseline = 100 + 14 * r;
            phrase(words, labels[r], 72, baseline, 10);
            phrase(words, notes[r][0], 200, baseline, 6);
            phrase(words, notes[r][1], 200, baseline + 7, 6);
        }

        List<DraftTable> tables = TextTableFinder.find(0, words, List.of(), List.of());

        assertEquals(1, tables.size(), "one table");
        DraftTable table = tables.getFirst();
        assertEquals(labels.length, table.rows(), "a row for each label");
        for (int r = 0; r < labels.length; r++) {
            assertEquals(labels[r], textOf(table, r, 0), "row " + r + "'s label");
        }
    }

    /**
     * Form 8822's mailing table rules off each group of states with its service centre's address. The states run on
     * from line to line; the address breaks after each part, so its lines read as items of their own. Grouped line by
     * line, a scan of the form came out a spreadsheet row per line; the rules say where each group ends.
     */
    @Test
    void aRuledTablesLongGroupsAreItsRows() {
        String[][][] groups = {
            {{"IF your old home mailing", "address was in . . ."}, {"THEN use this", "address . . ."}},
            {
                {"Alabama, Arkansas,", "Delaware, Georgia, Illinois,", "Indiana, Iowa, Kentucky,", "Maine, Massachusetts,",
                    "Minnesota, Missouri, New", "Hampshire, New Jersey,", "New York, North Carolina,",
                    "Oklahoma, South Carolina,", "Tennessee, Vermont,", "Virginia, Wisconsin"},
                {"Department of the", "Treasury", "Internal Revenue", "Service", "Kansas City, MO", "64999-0023"}
            },
            {
                {"Florida, Louisiana,", "Mississippi, Texas"},
                {"Department of the", "Treasury", "Internal Revenue", "Service", "Austin, TX", "73301-0023"}
            },
            {
                {"Guam:", "bona fide residents"},
                {"Department of", "Revenue and", "Taxation", "Government of", "Guam", "P.O. Box 23607", "GMF, GU 96921"}
            }
        };
        List<CellLine> lines = new ArrayList<>();
        List<Float> rules = new ArrayList<>();
        List<LineRange> expected = new ArrayList<>();
        float baseline = 100;
        rules.add(baseline - 12);
        for (String[][] group : groups) {
            int first = lines.size();
            for (int k = 0; k < Math.max(group[0].length, group[1].length); k++) {
                lines.add(cellLine(k < group[0].length ? group[0][k] : "", k < group[1].length ? group[1][k] : "",
                        baseline));
                baseline += 8;
            }
            expected.add(new LineRange(first, lines.size() - 1));
            rules.add(baseline - 4);
            baseline += 4;
        }

        List<LineRange> rows = RowGrouper.group(lines, ColumnProfile.of(lines, 2), separators(rules));

        assertEquals(expected, rows, "a row for each group between the rules");
    }

    /** Rows of figures under section rules keep a row each: a section is several rows, not one. */
    @Test
    void sectionsOfFiguresUnderRulesKeepTheirRows() {
        String[] labels = {"North", "South", "Inland", "Cold store"};
        String[] figures = {"1,234.50", "2,416.30", "4,361.70", "2,375.20"};
        assertRowPerLine(labels, figures);
    }

    /** Items under section rules keep a row each: each line starts an item of its own in every column. */
    @Test
    void itemsUnderSectionRulesKeepTheirRows() {
        String[] names = {"Alice Smith", "Bob Jones", "Carol White", "Dan Brown"};
        String[] roles = {"Engineer", "Designer", "Manager", "Analyst"};
        assertRowPerLine(names, roles);
    }

    /** Three sections of four lines under rules: each line must stay its own row. */
    private static void assertRowPerLine(String[] a, String[] b) {
        List<CellLine> lines = new ArrayList<>();
        List<Float> rules = new ArrayList<>();
        float baseline = 100;
        rules.add(baseline - 12);
        for (int section = 0; section < 3; section++) {
            for (int k = 0; k < a.length; k++) {
                lines.add(cellLine(a[k], b[k], baseline));
                baseline += 8;
            }
            rules.add(baseline - 4);
            baseline += 4;
        }

        List<LineRange> rows = RowGrouper.group(lines, ColumnProfile.of(lines, 2), separators(rules));

        assertEquals(lines.size(), rows.size(), "a row for each line");
    }

    /** A line of a two-column table: {@code a} at x 226 and {@code b} at x 340, in 7 pt type. */
    private static CellLine cellLine(String a, String b, float baseline) {
        List<PdfWord> left = new ArrayList<>();
        List<PdfWord> right = new ArrayList<>();
        if (!a.isEmpty()) {
            phrase(left, a, 226, baseline, 7);
        }
        if (!b.isEmpty()) {
            phrase(right, b, 340, baseline, 7);
        }
        List<PdfWord> all = new ArrayList<>(left);
        all.addAll(right);
        return new CellLine(TextLine.of(all), List.of(left, right));
    }

    private static Separators separators(List<Float> ys) {
        float[] out = new float[ys.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ys.get(i);
        }
        return new Separators(out);
    }

    private static String textOf(DraftTable table, int row, int col) {
        for (DraftTable.Cell cell : table.cells()) {
            if (cell.row() == row && cell.col() == col) {
                return TextLine.joinWords(cell.words());
            }
        }
        return "";
    }

    /** Adds {@code text}'s words at {@code x}, a space apart, half their size wide per character. */
    private static void phrase(List<PdfWord> words, String text, float x, float baseline, float size) {
        for (String w : text.split(" ")) {
            float right = x + w.length() * size * 0.5f;
            words.add(word(w, x, right, baseline, size));
            x = right + size * 0.28f;
        }
    }

    private static PdfWord word(String text, float x, float right, float baseline, float size) {
        int n = text.length();
        float[] left = new float[n];
        float[] rightEdges = new float[n];
        String[] glyphs = new String[n];
        for (int i = 0; i < n; i++) {
            left[i] = x + (right - x) * i / n;
            rightEdges[i] = x + (right - x) * (i + 1) / n;
            glyphs[i] = String.valueOf(text.charAt(i));
        }
        return new PdfWord(text, x, right, baseline - 0.8f * size, baseline + 0.2f * size, baseline, size, false,
                false, 0, size * 0.28f, left, rightEdges, glyphs);
    }
}
