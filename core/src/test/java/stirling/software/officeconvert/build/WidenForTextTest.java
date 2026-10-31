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
    void aLineHangingFromItsMarkerAtTheCellsEdgeStays() {
        // "• Store #4" with its bullet at the cell's edge and its text 10 pt in (from Codex's review)
        Paragraph item = paragraph(Align.LEFT, 10f, 70f);
        item.indentFirst = -10f;
        Table t = table(64f, 60f, item, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(10f, item.indentLeft, 0.01f, "the marker stays in its cell");
        assertEquals(82f, t.columnWidths.get(0), 0.01f, "the column grows instead");
    }

    @Test
    void aHangingLineMovesNoFurtherThanItsMarker() {
        // The bullet 6 pt in from the cell's edge, its text 16 pt in: the line can move 6 pt
        Paragraph item = paragraph(Align.LEFT, 16f, 60f);
        item.indentFirst = -10f;
        Table t = table(64f, 60f, item, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(10f, item.indentLeft, 0.01f, "the bullet reaches the cell's edge");
        assertEquals(72f, t.columnWidths.get(0), 0.01f, "and the column grows for the rest");
    }

    @Test
    void aLineThatFitsTheWidthTheColumnNeedsAnywayStays() {
        // A centred line of 100 pt makes the column 102 pt wide whatever moves; the other line fits that where it is
        // (from Codex's review)
        Paragraph set = paragraph(Align.LEFT, 20f, 50f);
        Paragraph wide = paragraph(Align.CENTER, 0f, 100f);
        Table t = table(64f, 60f, set, wide, paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(20f, set.indentLeft, 0.01f, "the set-in line keeps its place");
        assertEquals(102f, t.columnWidths.get(0), 0.01f, "the column grows for the centred line");
    }

    @Test
    void aLineMovesOnlyAsFarAsTheWidthTheColumnNeeds() {
        // A line that can move only 10 pt makes the column 72 pt wide; the other moves only to fit that
        Paragraph set = paragraph(Align.LEFT, 40f, 50f);
        Paragraph item = paragraph(Align.LEFT, 10f, 70f);
        Table t = table(64f, 60f, set, item, paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(20f, set.indentLeft, 0.01f, "the set-in line moves 20 pt, to end at 72 pt, not 40");
        assertEquals(0f, item.indentLeft, 0.01f, "the other moves as far as it can");
        assertEquals(72f, t.columnWidths.get(0), 0.01f);
    }

    @Test
    void aLineSetAtATabStopMovesWithItsTabs() {
        Paragraph tabbed = paragraph(Align.LEFT, 20f, 50f);
        tabbed.tabs.add(new Paragraph.TabStop(40f, Paragraph.TabStop.Kind.RIGHT, '.'));
        Table t = table(64f, 60f, tabbed, paragraph(Align.CENTER, 0f, 30f), paragraph(Align.CENTER, 0f, 30f));
        TableBuilder.widenForText(t, 0f);
        assertEquals(12f, tabbed.indentLeft, 0.01f, "the line moves 8 pt left");
        assertEquals(32f, tabbed.tabs.getFirst().pos(), 0.01f, "and its tab stop with it");
        assertEquals(Paragraph.TabStop.Kind.RIGHT, tabbed.tabs.getFirst().kind());
        assertEquals(64f, t.columnWidths.get(0), 0.01f, "the column keeps its width");
    }

    @Test
    void aColumnTakesAllTheRoomTheNextColumnsLinesLeave() {
        // MMWR's Table 1 (cont.): a label wider than its column, and figures set in from the next column's edge. The
        // figures move left by what their column gives, so they keep their place on the page and need that much less
        Paragraph label = paragraph(Align.LEFT, 0f, 80f);
        Paragraph figure = paragraph(Align.LEFT, 30f, 20f);
        Paragraph total = paragraph(Align.LEFT, 25f, 30f);
        total.tabs.add(new Paragraph.TabStop(50f, Paragraph.TabStop.Kind.RIGHT, '.'));
        Table t = grid(new float[] {64f, 60f}, row(cell(label), cell(figure)),
                row(cell(paragraph(Align.LEFT, 0f, 0f)), cell(total)));
        TableBuilder.widenForText(t, 0f);
        assertEquals(82f, t.columnWidths.get(0), 0.01f, "the label's column takes the 18 pt it needs");
        assertEquals(42f, t.columnWidths.get(1), 0.01f, "from the next column, which doesn't grow back");
        assertEquals(12f, figure.indentLeft, 0.01f, "the figures move left by as much");
        assertEquals(7f, total.indentLeft, 0.01f);
        assertEquals(32f, total.tabs.getFirst().pos(), 0.01f, "their tab stops with them");
    }

    @Test
    void aBoundaryDrawnBetweenTheColumnsMovesNoFurtherThanBefore() {
        // A rule between the columns: the next column gives only the room its widest line leaves it
        Paragraph label = paragraph(Align.LEFT, 0f, 80f);
        Paragraph figure = paragraph(Align.LEFT, 30f, 20f);
        Paragraph total = paragraph(Align.LEFT, 25f, 30f);
        Table.Cell ruled = cell(label);
        ruled.right = new Table.Border(0.5f, 0);
        Table t = grid(new float[] {64f, 60f}, row(ruled, cell(figure)),
                row(cell(paragraph(Align.LEFT, 0f, 0f)), cell(total)));
        TableBuilder.widenForText(t, 0f);
        assertEquals(57f, t.columnWidths.get(1), 0.01f, "the next column gives 3 pt, as its widest line needs 57");
        assertEquals(82f, t.columnWidths.get(0), 0.01f, "and the label's column grows for the rest");
    }

    @Test
    void aCellSpanningOnFromTheNextColumnMovesWithIt() {
        Paragraph label = paragraph(Align.LEFT, 0f, 80f);
        Paragraph figure = paragraph(Align.LEFT, 30f, 20f);
        Paragraph heading = paragraph(Align.LEFT, 12f, 60f);
        Table.Cell across = cell(heading);
        across.gridSpan = 2;
        Table t = grid(new float[] {64f, 60f, 50f},
                row(cell(label), cell(figure), cell(paragraph(Align.CENTER, 0f, 20f))),
                row(cell(paragraph(Align.LEFT, 0f, 0f)), across));
        TableBuilder.widenForText(t, 0f);
        assertEquals(48f, t.columnWidths.get(1), 0.01f, "the spanning heading leaves only 12 pt to take");
        assertEquals(0f, heading.indentLeft, 0.01f, "and moves left with the column it starts in");
        assertEquals(18f, figure.indentLeft, 0.01f);
    }

    private static Table grid(float[] widths, Table.Row... rows) {
        Table t = new Table();
        t.cellMarginLeft = 0;
        t.cellMarginRight = 0;
        for (float w : widths) {
            t.columnWidths.add(w);
        }
        t.rows.addAll(java.util.List.of(rows));
        return t;
    }

    private static Table.Row row(Table.Cell... cells) {
        Table.Row row = new Table.Row();
        row.cells.addAll(java.util.List.of(cells));
        return row;
    }

    private static Table.Cell cell(Paragraph p) {
        Table.Cell cell = new Table.Cell();
        cell.paragraphs.add(p);
        return cell;
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
