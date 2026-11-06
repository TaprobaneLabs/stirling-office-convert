package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.table.TableDetection;

/**
 * {@code TableShaper.splitStacked} cuts a ruled row into the rows stacked in it, where its columns' lines share
 * baselines, and leaves a row whose lines are its cells' wrapped text whole.
 */
class StackedRowsTest {

    /**
     * Form 8822's mailing table rules off each group of states with its service centre's address. Both columns are set
     * on one grid of baselines, so every line of a group shares its baseline with the other column, and the group was
     * cut into a row per line. The states run on from line to line.
     */
    @Test
    void aGroupsWrappedLinesStayOneRow() {
        String[] states = {"Alabama, Arkansas,", "Delaware, Georgia, Illinois,", "Indiana, Iowa, Kentucky,",
            "Maine, Massachusetts,", "Minnesota, Missouri, New", "Hampshire, New Jersey,", "New York, North Carolina,",
            "Oklahoma, South Carolina,", "Tennessee, Vermont,", "Virginia, Wisconsin"};
        String[] address = {"Department of the", "Treasury", "Internal Revenue", "Service", "Kansas City, MO",
            "64999-0023"};

        assertEquals(1, rowsAfterSplit(states, address), "one row for the group");
    }

    /** A group's two-line header ("IF your old home mailing / address was in . . .") is one row too. */
    @Test
    void aTwoLineHeaderStaysOneRow() {
        assertEquals(1, rowsAfterSplit(new String[] {"IF your old home mailing", "address was in . . ."},
                new String[] {"THEN use this", "address . . ."}), "one row for the header");
    }

    /** Rows stacked in one ruled row each start afresh: a statement's summary, a label and its amount a line. */
    @Test
    void rowsStackedInARuledRowAreCutApart() {
        assertEquals(3, rowsAfterSplit(new String[] {"Purchases", "Fees Charged", "New Balance"},
                new String[] {"+$529.57", "+$69.45", "$1,784.53"}), "a row for each label and amount");
    }

    /** The rows {@code splitStacked} makes of one ruled row holding {@code a} and {@code b}, a line 8 pt apart. */
    private static int rowsAfterSplit(String[] a, String[] b) {
        float top = 100;
        float bottom = top + 8 * Math.max(a.length, b.length) + 4;
        List<TableDetection.FoundCell> cells = List.of(
                cell(0, 223, 315, top, bottom),
                cell(1, 315, 389, top, bottom));
        List<List<Word>> words = List.of(lines(a, 226, top), lines(b, 318, top));
        TableDetection.Found t = new TableDetection.Found(1, 2, new float[] {223, 315, 389}, true, 0, 0f, cells, 223,
                top, 389, bottom);
        return TableShaper.splitStacked(t, words).rows();
    }

    private static TableDetection.FoundCell cell(int col, float left, float right, float top, float bottom) {
        return new TableDetection.FoundCell(0, col, 1, 1, left, top, right, bottom, true, true, true, true, null,
                TableDetection.HAlign.GENERAL, TableDetection.VAlign.TOP, "");
    }

    /** {@code lines}' words at {@code x}, a line every 8 pt from {@code top}, in 7 pt type. */
    private static List<Word> lines(String[] lines, float x, float top) {
        List<Word> out = new ArrayList<>();
        for (int k = 0; k < lines.length; k++) {
            float baseline = top + 8 + 8 * k;
            float at = x;
            for (String text : lines[k].split(" ")) {
                List<Glyph> glyphs = new ArrayList<>();
                for (char ch : text.toCharArray()) {
                    glyphs.add(new Glyph(String.valueOf(ch), at, 3.5f, baseline, 7, 5.25f, 1.75f, FontInfo.DEFAULT, 0,
                            glyphs.size(), 1.75f, false, false));
                    at += 3.5f;
                }
                out.add(new Word(glyphs));
                at += 1.75f;
            }
        }
        return out;
    }
}
