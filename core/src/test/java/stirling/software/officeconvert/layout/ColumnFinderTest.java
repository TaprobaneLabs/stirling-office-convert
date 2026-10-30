package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnFinderTest {

    private static final float LEFT = 50.5f;
    private static final float RIGHT = 561.3f;
    private static final float HEIGHT = 10.6f;

    /** A column's lines: ragged ends cycling through four widths, as set text has. */
    private static void column(List<ColumnFinder.Item> items, float x, float firstBaseline, int lines) {
        float[] widths = {150f, 146f, 141f, 148f};
        for (int i = 0; i < lines; i++) {
            float baseline = firstBaseline + 12.5f * i;
            items.add(new ColumnFinder.Item(x, baseline - 8.1f, x + widths[i % widths.length], baseline - 8.1f + HEIGHT,
                    baseline, true, 36));
        }
    }

    private static ColumnFinder.Band bandAt(List<ColumnFinder.Band> bands, float y) {
        for (ColumnFinder.Band b : bands) {
            if (b.top() <= y && b.bottom() >= y) {
                return b;
            }
        }
        throw new AssertionError("no band at " + y);
    }

    @Test
    void aMiddleColumnOffTheOthersBaselinesKeepsItsGutter() {
        // Three columns, as on a newsletter's page: the first and third on one baseline grid, the middle one 6 pt off it
        // (a heading above moved its lines) and shorter. Left of the second gutter, the rows nearest the gutter then
        // alternate between the first and middle columns, and the first, with more lines, set the side's start.
        List<ColumnFinder.Item> items = new ArrayList<>();
        column(items, LEFT, 140f, 24);
        column(items, 229.9f, 146.25f, 16);
        column(items, 409.3f, 140f, 24);

        List<ColumnFinder.Band> bands = ColumnFinder.find(items, LEFT, RIGHT, 792f, 9f);

        ColumnFinder.Band band = bandAt(bands, 200f);
        assertEquals(3, band.columns().size(), "columns at y=200");
        assertEquals(1, band.columnAt(300f));
        assertEquals(2, band.columnAt(480f));
        assertEquals(3, bandAt(bands, 400f).columns().size(), "columns below the middle column's end");
    }

    @Test
    void aColumnsShortLinesPastItsProseKeepItsGutter() {
        // A ragged left column whose three short lines are set further right than its prose (Codex's case): they alone end
        // at the gutter, and judged alone they're no column
        List<ColumnFinder.Item> items = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            float baseline = 100f + 12f * i;
            items.add(new ColumnFinder.Item(50f, baseline - 8f, 200f, baseline + 2f, baseline, true, 36));
            items.add(new ColumnFinder.Item(300f, baseline - 8f, 500f, baseline + 2f, baseline, true, 40));
            if (i >= 6 && i < 9) {
                items.add(new ColumnFinder.Item(210f, baseline - 8f, 260f, baseline + 2f, baseline, true, 8));
            }
        }

        List<ColumnFinder.Band> bands = ColumnFinder.find(items, 50f, 500f, 792f, 10f);

        for (float y : new float[] {120f, 180f, 250f}) {
            assertEquals(2, bandAt(bands, y).columns().size(), "columns at y=" + y);
        }
    }

    @Test
    void twoColumnsOnTheirOwnBaselinesStayTwo() {
        List<ColumnFinder.Item> items = new ArrayList<>();
        column(items, LEFT, 140f, 30);
        column(items, 310f, 146.25f, 30);

        List<ColumnFinder.Band> bands = ColumnFinder.find(items, LEFT, RIGHT, 792f, 9f);

        assertEquals(2, bandAt(bands, 300f).columns().size());
    }
}
