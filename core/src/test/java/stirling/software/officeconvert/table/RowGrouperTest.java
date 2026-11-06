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
