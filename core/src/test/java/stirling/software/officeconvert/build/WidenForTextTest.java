package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Paragraph.Align;
import stirling.software.officeconvert.model.Table;

/**
 * A column grows for its one-line text as far as the page's edge allows, then its lines set in from their cells' edges
 * move left. Growing past the edge had Word cut the table's last columns off there: the Federal Reserve's H.4.1 release
 * and OPM's SF 15 lost figures that way.
 */
class WidenForTextTest {

    @Test
    void aLineSetInMovesLeftWhenTheTableHasNoRoom() {
        // A 64 pt column: one line 62 pt in, then counts below it; the next column's counts
        Paragraph spill = paragraph(Align.LEFT, 62f, 40f);
        Paragraph count = paragraph(Align.CENTER, 0f, 30f);
        Table t = table(64f, 60f, spill, count, paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(64f, t.columnWidths.get(0), 0.01f, "the column keeps its width");
        assertEquals(60f, t.columnWidths.get(1), 0.01f, "and so does the next");
        assertEquals(22f, spill.indentLeft, 0.01f, "the line moves 40 pt left, to end at the column's edge");
    }

    @Test
    void aColumnStillGrowsWhenItsLineHasNoRoomLeft() {
        Paragraph wide = paragraph(Align.LEFT, 10f, 80f);
        Table t = table(64f, 60f, wide, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(0f, wide.indentLeft, 0.01f, "the line moves to the cell's edge");
        assertEquals(82f, t.columnWidths.get(0), 0.01f, "and the column grows for the rest");
    }

    @Test
    void aTableWithRoomBeforeThePagesEdgeGrowsAndItsLinesStay() {
        // A CV's table, full column width, the page's margin beside it: growing keeps every line where the PDF sets it
        Paragraph spill = paragraph(Align.LEFT, 62f, 40f);
        Table t = table(64f, 60f, spill, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 100f);
        assertEquals(62f, spill.indentLeft, 0.01f, "the line keeps its place");
        assertEquals(104f, t.columnWidths.get(0), 0.01f, "the column grows into the room");
    }

    @Test
    void aTableGrowsIntoTheRoomItHasThenItsLinesMove() {
        Paragraph spill = paragraph(Align.LEFT, 62f, 40f);
        Table t = table(64f, 60f, spill, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 15f);
        assertEquals(79f, t.columnWidths.get(0), 0.01f, "the column takes the 15 pt there is");
        assertEquals(37f, spill.indentLeft, 0.01f, "and the line moves left by the other 25");
    }

    @Test
    void aLineHangingFromItsMarkerStays() {
        // "• Store #4" with its bullet at the cell's edge and its text 10 pt in (from Codex's review)
        Paragraph item = paragraph(Align.LEFT, 10f, 70f);
        item.indentFirst = -10f;
        Table t = table(64f, 60f, item, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(10f, item.indentLeft, 0.01f, "the marker stays in its cell");
        assertEquals(82f, t.columnWidths.get(0), 0.01f, "the column grows instead");
    }

    @Test
    void aLineSetAtATabStopStays() {
        Paragraph tabbed = paragraph(Align.LEFT, 20f, 50f);
        tabbed.tabs.add(new Paragraph.TabStop(40f, Paragraph.TabStop.Kind.LEFT, (char) 0));
        Table t = table(64f, 60f, tabbed, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(20f, tabbed.indentLeft, 0.01f, "the text after its tab wouldn't move with it");
        assertEquals(72f, t.columnWidths.get(0), 0.01f, "the column grows instead");
    }

    /** Two rows: {@code first} then {@code second} in the first column, {@code next} in the second, no cell margins. */
    private static Table table(float width, float nextWidth, Paragraph first, Paragraph second, Paragraph next) {
        Table t = new Table();
        t.cellMarginLeft = 0;
        t.cellMarginRight = 0;
        t.columnWidths.add(width);
        t.columnWidths.add(nextWidth);
        for (Paragraph p : new Paragraph[] {first, second}) {
            Table.Row row = new Table.Row();
            Table.Cell cell = new Table.Cell();
            cell.paragraphs.add(p);
            Table.Cell other = new Table.Cell();
            other.paragraphs.add(p == first ? paragraph(Align.CENTER, 0f, 0f) : next);
            row.cells.add(cell);
            row.cells.add(other);
            t.rows.add(row);
        }
        return t;
    }

    private static Paragraph paragraph(Align align, float indent, float width) {
        Paragraph p = new Paragraph();
        p.align = align;
        p.sourceLines = width > 0 ? 1 : 0;
        p.indentLeft = indent;
        p.textWidth = width;
        return p;
    }
}
