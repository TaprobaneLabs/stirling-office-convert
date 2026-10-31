package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.table.TableDetection;
import stirling.software.officeconvert.table.TableDetection.FoundCell;

class ChartAxesTest {

    private static List<Word> words(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<Glyph> glyphs = new ArrayList<>();
        float x = 0;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, 5, 100, 10, 7.5f, 2.5f, FontInfo.DEFAULT, 0, glyphs.size(), 2.5f,
                    false, false));
            x += 5;
        }
        return LineBuilder.build(glyphs).getFirst().words;
    }

    /** A table of {@code text}'s rows and columns, each cell holding its text. */
    private static TableDetection.Found table(String[][] text, List<List<Word>> cellWords) {
        int rows = text.length;
        int cols = text[0].length;
        float[] edges = new float[cols + 1];
        for (int c = 0; c <= cols; c++) {
            edges[c] = 40 + 60 * c;
        }
        List<FoundCell> cells = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells.add(new FoundCell(r, c, 1, 1, edges[c], 100 + 12 * r, edges[c + 1], 112 + 12 * r, false, false,
                        false, false, null, TableDetection.HAlign.LEFT, TableDetection.VAlign.TOP, text[r][c]));
                cellWords.add(words(text[r][c]));
            }
        }
        return new TableDetection.Found(rows, cols, edges, false, 0, 0f, cells, 40f, 100f, edges[cols], 100 + 12 * rows);
    }

    @Test
    void aBarChartsAxesAreNoTable() {
        // MMWR's figure: two panels side by side, their ticks down the left of each, their years along the foot
        String[][] text = new String[12][7];
        String[] ticks = {"” 100", "7", "6", "5", "4", "3", "2", "1", "0", "-1", "−2"};
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 7; c++) {
                text[r][c] = "";
            }
        }
        for (int r = 0; r < ticks.length; r++) {
            text[r][0] = ticks[r];
            text[r][3] = ticks[r];
        }
        text[11] = new String[] {"", "2008 2010", "2012 2014 2016", "", "2008", "2010", "2012 2014 2016"};
        List<List<Word>> cellWords = new ArrayList<>();
        TableDetection.Found chart = table(text, cellWords);
        assertTrue(TableShaper.looksLikeChart(chart, cellWords), "its ticks and years are a chart's axes");
    }

    @Test
    void aTableOfAmountsIsStillATable() {
        String[][] text = new String[8][6];
        for (int r = 0; r < 8; r++) {
            text[r] = new String[] {"20" + (10 + r), "19.2", "14.4", "9.0", "4.7", "5.1"};
        }
        List<List<Word>> cellWords = new ArrayList<>();
        assertFalse(TableShaper.looksLikeChart(table(text, cellWords), cellWords), "every row holds its figures");
    }

    @Test
    void aSparseTableOfWordsIsStillATable() {
        // A schedule's sections: a heading row now and then, most rows a label and a note
        String[][] text = new String[8][5];
        for (int r = 0; r < 8; r++) {
            text[r] = new String[] {"Item " + (char) ('A' + r), "", "", "", "see note"};
        }
        List<List<Word>> cellWords = new ArrayList<>();
        assertFalse(TableShaper.looksLikeChart(table(text, cellWords), cellWords), "its text is words, not ticks");
    }
}
