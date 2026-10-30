package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.table.TableDetection;
import stirling.software.officeconvert.table.TableDetection.FoundCell;

class FrameHeaderTest {

    private static final float[] EDGES = {45f, 200f, 400f, 567f};

    private static Line line(String text, float x, float baseline) {
        List<Glyph> glyphs = new ArrayList<>();
        float advance = 5;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, advance, baseline, 10, 7.5f, 2.5f, FontInfo.DEFAULT, 0,
                    glyphs.size(), 2.5f, false, false));
            x += advance;
        }
        return LineBuilder.build(glyphs).getFirst();
    }

    /** A table of two rows under a header the detection left out, as the Federal Register's cultural property table. */
    private static TableDetection.Found table(float top) {
        List<FoundCell> cells = new ArrayList<>();
        String[][] text = {{"Ecuador", "Archaeological material", "CBP Dec. 20-03"}, {"Peru", "Ethnological material", "CBP Dec. 21-01"}};
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                cells.add(new FoundCell(r, c, 1, 1, EDGES[c], top + 15 * r, EDGES[c + 1], top + 15 * (r + 1), false, false,
                        false, false, null, TableDetection.HAlign.LEFT, TableDetection.VAlign.TOP, text[r][c]));
            }
        }
        return new TableDetection.Found(2, 3, EDGES, false, 0, 0f, cells, 45f, top, 567f, top + 30);
    }

    private static List<Line> header(float baseline) {
        return List.of(line("State party", 50, baseline), line("Cultural property", 210, baseline),
                line("Decision No.", 450, baseline));
    }

    @Test
    void proseBetweenAFramesTopAndItsTableStaysOut() {
        // The running header's rule (47 pt) and the table's rules share their ends, so they make one frame; three columns
        // of prose fill it down to the table's header row.
        Box frame = new Box(43f, 46.5f, 569f, 431f);
        List<Line> segments = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            float baseline = 70f + 14f * i;
            segments.add(line("rules of the department", 50, baseline));
            segments.add(line("to the import of property", 210, baseline));
            segments.add(line("from the state named below", 410, baseline));
        }
        List<Line> head = header(390f);
        segments.addAll(head);

        TableDetection.Found kept = TableShaper.addFrameHeader(table(400f), frame, segments, false);

        assertEquals(3, kept.rows());
        assertEquals(head.getFirst().top - 1, kept.top(), 0.01f);
    }

    @Test
    void aHeaderRightUnderTheFramesTopStartsTheTableThere() {
        Box frame = new Box(43f, 370f, 569f, 431f);
        List<Line> segments = new ArrayList<>(header(390f));

        TableDetection.Found kept = TableShaper.addFrameHeader(table(400f), frame, segments, false);

        assertEquals(3, kept.rows());
        assertEquals(370.5f, kept.top(), 0.01f);
    }

    @Test
    void anotherTablesRowsOfAmountsAboveAreNoHeader() {
        // A scanned bank statement found as two tables: the lower one, in a frame of the scan's rules, has the upper one's
        // rows just above it, which fit its columns as header rows would
        Box frame = new Box(43f, 300f, 569f, 431f);
        List<Line> segments = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            float baseline = 390f - 12f * i;
            segments.add(line((10 - i) + " Mar Interest", 50, baseline));
            segments.add(line("135.90", 260, baseline));
            segments.add(line("11,450.68", 450, baseline));
        }

        TableDetection.Found kept = TableShaper.addFrameHeader(table(400f), frame, segments, false);

        assertEquals(2, kept.rows(), "the other table's rows stay out of this one's header");
        assertEquals(400f, kept.top(), 0.01f);
    }

    @Test
    void aRowOfAmountsJustAboveIsStillTheTablesFirst() {
        // The CFPB loan estimate's "Total Closing Costs (J) $8,791", a row the detection left out of its table
        Box frame = new Box(43f, 370f, 569f, 431f);
        List<Line> segments = List.of(line("Total Closing Costs", 50, 390f), line("$8,791", 450, 390f));

        TableDetection.Found kept = TableShaper.addFrameHeader(table(400f), frame, segments, false);

        assertEquals(3, kept.rows());
    }
}
