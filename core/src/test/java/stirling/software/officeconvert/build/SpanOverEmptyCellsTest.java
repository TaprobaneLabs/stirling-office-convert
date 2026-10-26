package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Paragraph.Align;
import stirling.software.officeconvert.model.Table;

/**
 * A cell spans the empty cells after it only when all its text can take their room. A paragraph set right or centred in
 * the same cell would move into the cells joined, and one of several lines would rewrap (from Codex's review).
 */
class SpanOverEmptyCellsTest {

    @Test
    void aLabelRunningOnSpansTheEmptyCellAfterIt() {
        Table t = table(paragraph(Align.LEFT, 1, 49f));
        TableBuilder.spanOverEmptyCells(t);
        assertEquals(2, t.rows.getFirst().cells.getFirst().gridSpan, "the label's cell spans the empty one");
    }

    @Test
    void aCellAlsoHoldingRightAlignedTextKeepsToItsColumn() {
        Table t = table(paragraph(Align.LEFT, 1, 49f), paragraph(Align.RIGHT, 1, 20f));
        TableBuilder.spanOverEmptyCells(t);
        assertEquals(1, t.rows.getFirst().cells.getFirst().gridSpan, "the right-aligned amount stays where it is");
    }

    @Test
    void aCellAlsoHoldingSeveralLinesKeepsToItsColumn() {
        Table t = table(paragraph(Align.LEFT, 1, 49f), paragraph(Align.LEFT, 3, 45f));
        TableBuilder.spanOverEmptyCells(t);
        assertEquals(1, t.rows.getFirst().cells.getFirst().gridSpan, "the paragraph of three lines keeps its breaks");
    }

    /** A row of a 50 pt column holding {@code paras} and an empty 150 pt one, no cell margins. */
    private static Table table(Paragraph... paras) {
        Table t = new Table();
        t.columnWidths.add(50f);
        t.columnWidths.add(150f);
        Table.Row row = new Table.Row();
        Table.Cell label = new Table.Cell();
        for (Paragraph p : paras) {
            label.paragraphs.add(p);
        }
        Table.Cell empty = new Table.Cell();
        row.cells.add(label);
        row.cells.add(empty);
        t.rows.add(row);
        return t;
    }

    private static Paragraph paragraph(Align align, int lines, float width) {
        Paragraph p = new Paragraph();
        p.align = align;
        p.sourceLines = lines;
        p.textWidth = width;
        return p;
    }
}
