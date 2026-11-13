package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.table.TableDetection;
import stirling.software.officeconvert.table.TableDetection.FoundCell;

class TableSpanTest {

    private static final FontInfo FACE = new FontInfo("Arial", false, true, false, false, false, "MyriadPro-It", true);
    private static final float[] EDGES = {36f, 228f, 288f, 432f, 488f, 576f};

    private static Word word(String text, float x) {
        List<Glyph> glyphs = new ArrayList<>();
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, 4f, 50f, 8f, 5.6f, 1.6f, FACE, 0, glyphs.size(), 2f, false, true));
            x += 4f;
        }
        return new Word(glyphs);
    }

    /** One row of the header's five columns, empty, its cells' words to add to {@code perCell}. */
    private static TableDetection.Found row(List<List<Word>> perCell) {
        List<FoundCell> cells = new ArrayList<>();
        for (int c = 0; c < EDGES.length - 1; c++) {
            cells.add(new FoundCell(0, c, 1, 1, EDGES[c], 40f, EDGES[c + 1], 60f, false, false, false, false, null, null,
                    null, ""));
            perCell.add(new ArrayList<>());
        }
        return new TableDetection.Found(1, EDGES.length - 1, EDGES, false, 0, 0f, cells, EDGES[0], 40f, EDGES[5], 60f);
    }

    /**
     * CFPB's Closing Disclosure: the header's italic note starts in its third column and runs to the page's right edge,
     * over two columns that hold nothing on its row. Its cell spans them, or each writer wraps it in one.
     */
    @Test
    void aCellsTextRunningOverEmptyCellsSpansThem() {
        List<List<Word>> perCell = new ArrayList<>();
        TableDetection.Found t = row(perCell);
        perCell.get(0).add(word("Closing", 36f));
        perCell.get(2).add(word("This form is a statement of final", 290f));
        perCell.get(2).add(word("loan terms and closing", 430f));
        TableDetection.Found spanned = TableShaper.spanOverflow(t, perCell);
        assertEquals(3, spanned.cells().size());
        FoundCell note = spanned.cells().get(2);
        assertEquals(2, note.col());
        assertEquals(3, note.colSpan());
        assertEquals(576f, note.right(), 0.01f);
        assertEquals(3, perCell.size());
        assertEquals(2, perCell.get(2).size());
    }

    /** Text running into a cell that holds its own stays in its column. */
    @Test
    void aCellsTextStopsAtACellThatHoldsText() {
        List<List<Word>> perCell = new ArrayList<>();
        TableDetection.Found t = row(perCell);
        perCell.get(2).add(word("This form is a statement of final loan", 290f));
        perCell.get(3).add(word("FHA", 450f));
        assertSame(t, TableShaper.spanOverflow(t, perCell));
    }

    /** An empty cell shaded unlike the text's is a field of its own, which a span would give the text's shading. */
    @Test
    void aCellsTextStopsAtACellShadedOtherwise() {
        List<List<Word>> perCell = new ArrayList<>();
        TableDetection.Found row = row(perCell);
        List<FoundCell> cells = new ArrayList<>(row.cells());
        FoundCell field = cells.get(3);
        cells.set(3, new FoundCell(0, 3, 1, 1, field.left(), field.top(), field.right(), field.bottom(), false, false, false,
                false, 0xDDDDDD, null, null, ""));
        TableDetection.Found t = new TableDetection.Found(1, EDGES.length - 1, EDGES, false, 0, 0f, cells, EDGES[0], 40f,
                EDGES[5], 60f);
        perCell.get(2).add(word("This form is a statement of final loan", 290f));
        assertSame(t, TableShaper.spanOverflow(t, perCell));
    }
}
