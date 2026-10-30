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
    void twoColumnsOnTheirOwnBaselinesStayTwo() {
        List<ColumnFinder.Item> items = new ArrayList<>();
        column(items, LEFT, 140f, 30);
        column(items, 310f, 146.25f, 30);

        List<ColumnFinder.Band> bands = ColumnFinder.find(items, LEFT, RIGHT, 792f, 9f);

        assertEquals(2, bandAt(bands, 300f).columns().size());
    }
}
