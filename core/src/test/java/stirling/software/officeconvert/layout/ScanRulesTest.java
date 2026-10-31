package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.PageGraphics.Rule;

class ScanRulesTest {

    @Test
    void piecesOfOneRuleAreJoinedAndParallelOrDistantRulesAreNot() {
        List<Rule> joined = ScanRules.joined(List.of(
                h(300f, 72f, 190f), h(300.8f, 196f, 330f), h(299.6f, 336f, 512f),
                h(306f, 72f, 512f),
                h(300f, 560f, 600f),
                new Rule(false, 72f, 100f, 300f, 0.5f, 0)));
        assertEquals(4, joined.size(), joined.toString());
        Rule row = joined.stream().filter(r -> r.horizontal() && r.start() == 72f && r.pos() < 303f).findFirst().orElseThrow();
        assertEquals(512f, row.end(), 0.01f, row.toString());
        assertEquals(300.1f, row.pos(), 0.2f, "the joined rule lies where its pieces do, weighted by length");
    }

    @Test
    void aFaintEdgeIsAddedBetweenTwoCrossingRulesOnlyWhereItsInkIs() {
        // 100 px = 100 pt; a faint line along y = 50 from x = 10 to 60, nothing from 60 to 90
        boolean[] faint = new boolean[100 * 100];
        for (int x = 10; x < 60; x++) {
            faint[50 * 100 + x] = true;
        }
        ScanRules.Ink ink = new ScanRules.Ink(faint, 100, 100, 0, 0, 1f, 1f);
        List<Rule> frame = List.of(h(20f, 10f, 90f), h(50f, 70f, 90f), h(80f, 10f, 90f),
                v(10f, 20f, 80f), v(60f, 20f, 80f), v(90f, 20f, 80f));
        List<Rule> done = ScanRules.completed(frame, ink);
        assertTrue(done.contains(new Rule(true, 50f, 10f, 60f, 0.5f, 0)), done.toString());
        assertEquals(frame.size() + 1, done.size(), "nothing added where there is no ink: " + done);
    }

    @Test
    void aLetterCrossingAnEdgeIsNoRule() {
        // A broad blot across y = 50, as a letter's stroke would be, is ink on both sides of the line
        boolean[] faint = new boolean[100 * 100];
        for (int x = 10; x < 60; x++) {
            for (int y = 40; y < 60; y++) {
                faint[y * 100 + x] = true;
            }
        }
        ScanRules.Ink ink = new ScanRules.Ink(faint, 100, 100, 0, 0, 1f, 1f);
        assertEquals(0f, ink.share(true, 50f, 12f, 58f), 0.001f);
    }

    @Test
    void shadingAsGreyAsTheInkLimitMakesNoRule() {
        // Banded rows shaded at luma 211 with a run of noise at 200 through them, against a rule at 120 on white paper
        byte[] luma = new byte[100 * 100];
        java.util.Arrays.fill(luma, (byte) 245);
        for (int y = 30; y < 70; y++) {
            for (int x = 0; x < 100; x++) {
                luma[y * 100 + x] = (byte) (y == 50 ? 200 : 211);
            }
        }
        for (int x = 0; x < 100; x++) {
            luma[85 * 100 + x] = (byte) 120;
        }
        assertFalse(ScanRules.contrasted(luma, 100, 100, true, new int[] {5, 94, 50, 50}, 4));
        assertTrue(ScanRules.contrasted(luma, 100, 100, true, new int[] {5, 94, 85, 85}, 4));
    }

    @Test
    void aCellSideRunsFromRuleToRuleAndAFiguresStrokeDoesNot() {
        // Rows ruled at y = 20, 40 and 60; a cell's side at x = 30 in both rows, and a figure's stroke at x = 60
        // that fills most of each row but stops four pixels short of its rules
        boolean[] faint = new boolean[100 * 100];
        for (int x = 0; x < 100; x++) {
            faint[20 * 100 + x] = faint[40 * 100 + x] = faint[60 * 100 + x] = true;
        }
        for (int y = 20; y <= 60; y++) {
            faint[y * 100 + 30] = true;
        }
        for (int top : new int[] {20, 40}) {
            for (int y = top + 5; y <= top + 15; y++) {
                faint[y * 100 + 60] = true;
            }
        }
        ScanRules.Ink ink = new ScanRules.Ink(faint, 100, 100, 0, 0, 1f, 1f);
        List<Rule> sides = ScanRules.cellSides(List.of(h(20f, 0f, 99f), h(40f, 0f, 99f), h(60f, 0f, 99f)), ink);
        assertEquals(2, sides.size(), sides.toString());
        assertTrue(sides.stream().allMatch(s -> !s.horizontal() && Math.abs(s.pos() - 30.5f) < 1f), sides.toString());
    }

    @Test
    void aSideInOneRowOnlyIsNoColumns() {
        boolean[] faint = new boolean[100 * 100];
        for (int x = 0; x < 100; x++) {
            faint[20 * 100 + x] = faint[40 * 100 + x] = faint[60 * 100 + x] = true;
        }
        for (int y = 20; y <= 40; y++) {
            faint[y * 100 + 30] = true;
        }
        ScanRules.Ink ink = new ScanRules.Ink(faint, 100, 100, 0, 0, 1f, 1f);
        assertTrue(ScanRules.cellSides(List.of(h(20f, 0f, 99f), h(40f, 0f, 99f), h(60f, 0f, 99f)), ink).isEmpty());
    }

    private static Rule h(float pos, float start, float end) {
        return new Rule(true, pos, start, end, 0.5f, 0);
    }

    private static Rule v(float pos, float start, float end) {
        return new Rule(false, pos, start, end, 0.5f, 0);
    }
}
