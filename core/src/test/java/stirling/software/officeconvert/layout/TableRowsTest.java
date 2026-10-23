package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.table.TableDetection;

/**
 * A table's rows count as rows of text for the column finder, with the characters their cells hold. A row that
 * {@code TableShaper.splitStacked} made out of one with several lines keeps its text only in its cells' paragraphs.
 */
class TableRowsTest {

    private static final String[][] TEXT = {{"Purchases", "+$529.57"}, {"Fees Charged", "+$69.45"}, {"New Balance", "$1,784.53"}};

    @Test
    void rowsSplitFromOneCountTheTextOfTheirParagraphs() {
        List<TableDetection.FoundCell> cells = new ArrayList<>();
        List<List<ParaDraft>> paras = new ArrayList<>();
        float[] edges = {100, 116, 132, 148};
        for (int r = 0; r < TEXT.length; r++) {
            for (int c = 0; c < 2; c++) {
                float left = c == 0 ? 72 : 200;
                float right = c == 0 ? 200 : 290;
                // As splitStacked leaves them: no text of their own.
                cells.add(new TableDetection.FoundCell(r, c, 1, 1, left, edges[r], right, edges[r + 1], false, false,
                        false, false, null, TableDetection.HAlign.GENERAL, TableDetection.VAlign.TOP, ""));
                ParaDraft p = new ParaDraft(left, right);
                p.lines.add(line(TEXT[r][c], left + 4, edges[r + 1] - 4));
                paras.add(List.of(p));
            }
        }
        TableDetection.Found t = new TableDetection.Found(TEXT.length, 2, new float[] {72, 200, 290}, true, 0, 0f, cells,
                72, 100, 290, 148);
        List<ColumnFinder.Item> rows = PageAnalyzer.tableRows(new PageLayout.TableItem(t, paras, edges, 0f));
        assertEquals(TEXT.length, rows.size(), "a row of text for each of the table's rows");
        for (int r = 0; r < TEXT.length; r++) {
            ColumnFinder.Item row = rows.get(r);
            assertTrue(row.text(), "a row of text");
            int letters = (TEXT[r][0] + TEXT[r][1]).replace(" ", "").length();
            assertTrue(row.chars() >= letters, "the characters of its cells: " + row.chars());
            assertEquals(72f, row.x(), 0.01f);
            assertEquals(290f, row.right(), 0.01f);
        }
    }

    private static Line line(String text, float x, float baseline) {
        List<Glyph> glyphs = new ArrayList<>();
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, 4.5f, baseline, 9, 6.75f, 2.25f, FontInfo.DEFAULT, 0, glyphs.size(),
                    2.25f, false, false));
            x += 4.5f;
        }
        return LineBuilder.build(glyphs).getFirst();
    }
}
