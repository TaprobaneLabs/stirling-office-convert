package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.model.Paragraph.Align;

/** {@code ParagraphMeasure} sets a heading stacked right-aligned a word a line as a right-aligned paragraph. */
class RightStackTest {

    private static final FontInfo ARIAL = new FontInfo("Arial", true, false, false, false, false, "Arial-BoldMT", true);
    private static final float SIZE = 6;

    /**
     * CMS's claims tables head each column with its name stacked right-aligned a word a line, "Benefit" over "Days" over
     * "Used". The last two lines start together, being as wide, which read as a paragraph with a hanging first line: it
     * came out justified, indented to the narrow lines' start, and Word broke "Days" and "Used" in two.
     */
    @Test
    void aHeadingStackedRightAlignedIsRightAligned() {
        List<Glyph> glyphs = new ArrayList<>();
        rightAligned(glyphs, "Benefit", 374, 312);
        rightAligned(glyphs, "Days", 374, 319);
        rightAligned(glyphs, "Used", 374, 326);
        List<ParaDraft> paras = new ParagraphBuilder(new DocStats()).build(LineBuilder.build(glyphs), 345, 380);
        assertEquals(1, paras.size(), "one heading");
        assertEquals(Align.RIGHT, paras.getFirst().align);
        assertEquals(6, paras.getFirst().right, 0.01f);
        assertEquals(2, paras.getFirst().hardBreaks.cardinality(), "each line its own: nothing narrows it in a column");
    }

    /** A paragraph's lines start at its room's edge, its first line indented: the same lines aren't right-aligned. */
    @Test
    void aParagraphWithItsFirstLineIndentedStaysLeft() {
        List<Glyph> glyphs = new ArrayList<>();
        rightAligned(glyphs, "Benefit", 374, 312);
        rightAligned(glyphs, "Days", 374, 319);
        rightAligned(glyphs, "Used", 374, 326);
        List<ParaDraft> paras = new ParagraphBuilder(new DocStats()).build(LineBuilder.build(glyphs), 362, 380);
        assertEquals(1, paras.size());
        assertEquals(false, paras.getFirst().align == Align.RIGHT, "starting at the room's edge, not a stack");
    }

    private static void rightAligned(List<Glyph> glyphs, String text, float right, float baseline) {
        float x = right - text.length() * SIZE * 0.5f;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, SIZE * 0.5f, baseline, SIZE, SIZE * 0.7f, SIZE * 0.2f, ARIAL, 0,
                    glyphs.size(), SIZE * 0.25f, true, false));
            x += SIZE * 0.5f;
        }
    }
}
