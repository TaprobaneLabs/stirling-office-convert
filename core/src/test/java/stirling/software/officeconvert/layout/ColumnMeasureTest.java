package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

class ColumnMeasureTest {

    private static final FontInfo TIMES = new FontInfo("Times New Roman", false, false, true, false, false, "NimbusRomNo9L-Regu", true);
    private static final float COL_LEFT = 315f;
    private static final float COL_RIGHT = 591.4f;

    private static void row(List<Glyph> glyphs, String text, float x, float baseline, float size) {
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, size * 0.5f, baseline, size, size * 0.7f, size * 0.2f, TIMES, 0,
                    glyphs.size(), size * 0.25f, false, false));
            x += size * 0.5f;
        }
    }

    /**
     * USGS Fact Sheet 2024-3036's right column: ragged lines ending by 576 pt, and pieces of the title, in another size,
     * reaching the column's edge at 591 pt. A line ending 10 pt short, whose next line's first word fits only past 576,
     * carries on: the paragraph broke there ("… into the power grid. They" | "are not …").
     */
    @Test
    void aTitlePieceInAnotherSizeDoesntWidenTheMeasure() {
        List<Glyph> glyphs = new ArrayList<>();
        String full = "aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaa"; // 52 glyphs, to 575
        row(glyphs, "Geoelectric Hazards", COL_RIGHT - 19 * 8f, 60, 16);
        row(glyphs, full, COL_LEFT, 100, 10);
        row(glyphs, full, COL_LEFT, 112, 10);
        row(glyphs, "aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aa", COL_LEFT, 124, 10); // to 565
        row(glyphs, "are aa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaa", COL_LEFT, 136, 10);
        row(glyphs, full, COL_LEFT, 148, 10);
        row(glyphs, "aaaaa aaaaa aaaaa aaaaa", COL_LEFT, 160, 10);
        List<Line> lines = LineBuilder.build(glyphs);
        assertEquals(575f, lines.get(1).right, 0.01f);
        assertEquals(565f, lines.get(3).right, 0.01f);

        List<ParaDraft> paras = new ParagraphBuilder(new DocStats()).build(lines, COL_LEFT, COL_RIGHT);
        assertEquals(2, paras.size(), "the title and one body paragraph");
        assertEquals(6, paras.getLast().lines.size());
    }

    /**
     * A wider line in another size among the body's lines is part of what the column sets, as IRS Form 4506-T's lines
     * running a table's cells on from the text beside them: the measure keeps it, and the short line still ends there.
     */
    @Test
    void aWiderLineAmongTheBodysKeepsTheMeasure() {
        List<Glyph> glyphs = new ArrayList<>();
        String full = "aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaa";
        row(glyphs, full, COL_LEFT, 100, 10);
        row(glyphs, "aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaa aaaaa aaaaa aaaaa aaaaa aaaaa", COL_LEFT, 110, 8); // to 591
        row(glyphs, full, COL_LEFT, 122, 10);
        row(glyphs, "aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aa", COL_LEFT, 134, 10);
        row(glyphs, "are aa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaaa aaaa", COL_LEFT, 146, 10);
        row(glyphs, full, COL_LEFT, 158, 10);
        List<Line> lines = LineBuilder.build(glyphs);
        assertEquals(591f, lines.get(1).right, 0.01f);

        List<ParaDraft> paras = new ParagraphBuilder(new DocStats()).build(lines, COL_LEFT, COL_RIGHT);
        boolean breaksBeforeAre = false;
        for (ParaDraft p : paras) {
            breaksBeforeAre |= p.lines.getFirst().words.getFirst().text.equals("are");
        }
        assertEquals(true, breaksBeforeAre, "the widest line still sets the measure");
    }
}
