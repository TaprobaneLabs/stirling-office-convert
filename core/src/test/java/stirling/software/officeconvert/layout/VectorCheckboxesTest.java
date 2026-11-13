package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

class VectorCheckboxesTest {

    private static final FontInfo MYRIAD = new FontInfo("Arial", false, false, false, false, false, "MyriadPro-Regular", true);
    private static final float SIZE = 9f;
    private static final float BASELINE = 127f;

    /** A square's four sides, as a stroked rectangle comes out of the page. */
    private static void box(List<Rule> rules, float x, float side) {
        float top = BASELINE - side;
        rules.add(new Rule(true, top, x, x + side, 0.5f, 0));
        rules.add(new Rule(true, BASELINE, x, x + side, 0.5f, 0));
        rules.add(new Rule(false, x, top, BASELINE, 0.5f, 0));
        rules.add(new Rule(false, x + side, top, BASELINE, 0.5f, 0));
    }

    private static float word(List<Glyph> glyphs, String text, float x, float size, float baseline) {
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, 0.5f * size, baseline, size, 0.7f * size, 0.2f * size, MYRIAD, 0,
                    glyphs.size(), 0.25f * size, false, false));
            x += 0.5f * size;
        }
        return x;
    }

    /**
     * CFPB's Loan Estimate: "☒Conventional ☐FHA", each box a stroked 6.5-point square beside its label, the ticked one
     * holding an "x" set in the form's face. Both become ballot boxes, the cross goes and the sides are used.
     */
    @Test
    void aFormsDrawnBoxesBecomeBallotBoxes() {
        List<Rule> rules = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        box(rules, 354.4f, 6.5f);
        word(glyphs, "x", 355.6f, 8f, BASELINE - 1f);
        float end = word(glyphs, "Conventional", 362.6f, SIZE, BASELINE);
        box(rules, end + 4f, 6.5f);
        word(glyphs, "FHA", end + 12.2f, SIZE, BASELINE);
        rules.add(new Rule(true, 300f, 36f, 576f, 0.5f, 0));

        VectorCheckboxes.Found found = VectorCheckboxes.find(glyphs, rules, List.of(), List.of());
        assertNotNull(found);
        assertEquals(8, found.rules().size(), "the boxes' sides, not the long rule");
        List<String> boxes = new ArrayList<>();
        for (Glyph g : found.glyphs()) {
            assertFalse(g.text.equals("x"), "the cross is the box's now");
            if (g.font == VectorCheckboxes.SYMBOLS) {
                boxes.add(g.text);
                assertEquals(BASELINE, g.baseline, 0.01f);
                assertTrue(g.size > 6.5f && g.size <= 1.15f * SIZE, "sized to the drawn square: " + g.size);
            }
        }
        assertEquals(List.of(VectorCheckboxes.CROSSED, VectorCheckboxes.EMPTY), boxes);
    }

    /** Boxes side by side for a number's digits are a comb, and a box with no label near it isn't a checkbox. */
    @Test
    void combsAndUnlabelledBoxesStayDrawn() {
        List<Rule> rules = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        box(rules, 100f, 9f);
        box(rules, 109f, 9f);
        box(rules, 118f, 9f);
        word(glyphs, "SSN", 129f, SIZE, BASELINE);
        box(rules, 300f, 9f);
        word(glyphs, "far", 400f, SIZE, BASELINE);
        assertNull(VectorCheckboxes.find(glyphs, rules, List.of(), List.of()));
    }

    /**
     * The Closing Disclosure's summaries table draws its grid in a piece per cell: a row's line runs on from a small
     * cell's top, which makes a square beside the row's text that isn't a checkbox.
     */
    @Test
    void aTablesCellStaysDrawn() {
        List<Rule> rules = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        box(rules, 36f, 9f);
        rules.add(new Rule(true, BASELINE - 9f, 45f, 300f, 0.5f, 0));
        word(glyphs, "Deposit", 48f, SIZE, BASELINE);
        assertNull(VectorCheckboxes.find(glyphs, rules, List.of(), List.of()));
    }

    /** A chart's legend key, a coloured square with an outline stroked over it, beside its series' name. */
    @Test
    void aLegendKeyStaysDrawn() {
        List<Rule> rules = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        box(rules, 100f, 7f);
        word(glyphs, "This year", 109f, SIZE, BASELINE);
        Fill key = new Fill(100f, BASELINE - 7f, 107f, BASELINE, 0x2E75B6);
        assertNull(VectorCheckboxes.find(glyphs, rules, List.of(key), List.of()));
        Fill white = new Fill(100f, BASELINE - 7f, 107f, BASELINE, 0xFFFFFF);
        assertNotNull(VectorCheckboxes.find(glyphs, rules, List.of(white), List.of()), "a white box is a checkbox");
    }

    /** MMWR's outbreak map keys its white class between grey ones: in a column of keys, the white one is a key too. */
    @Test
    void aWhiteKeyAmongPaintedOnesStaysDrawn() {
        List<Rule> rules = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        box(rules, 173f, 8.8f);
        word(glyphs, "0", 186f, SIZE, BASELINE);
        float below = 10f;
        for (Rule r : List.copyOf(rules)) {
            rules.add(new Rule(r.horizontal(), r.horizontal() ? r.pos() + below : r.pos(), r.horizontal() ? r.start()
                    : r.start() + below, r.horizontal() ? r.end() : r.end() + below, r.thickness(), r.rgb()));
        }
        word(glyphs, "1–5", 186f, SIZE, BASELINE + below);
        Fill grey = new Fill(174f, BASELINE + below - 7.8f, 180.8f, BASELINE + below - 1f, 0xBFBFBF);
        assertNull(VectorCheckboxes.find(glyphs, rules, List.of(grey), List.of()));
        // Its grey classes are hatched: a pattern fill, which reads as white
        VectorMark hatched = new VectorMark(173f, BASELINE + below - 8.8f, 181.8f, BASELINE + below, 4, false, false, true,
                true, 0xFFFFFF, false, 0x231F20, 1f, false);
        assertNull(VectorCheckboxes.find(glyphs, rules, List.of(), List.of(hatched)));
    }
}
