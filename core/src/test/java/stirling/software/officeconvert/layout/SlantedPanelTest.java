package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

/** The shapes of USGS Fact Sheet 2024-3019's third page and of a map, as the page reader reports them. */
class SlantedPanelTest {

    private static VectorMark mark(float x, float top, float right, float bottom, int segments, boolean curved,
            boolean diagonal, boolean filled, int rgb, boolean round, boolean boxy, boolean sparse) {
        return new VectorMark(x, top, right, bottom, segments, curved, diagonal, filled, false, rgb, false, 0, 0, round,
                boxy, sparse);
    }

    @Test
    void aLightPentagonUnderTextIsAPanelToDraw() {
        // Its left edge slants out to a point: no box, so no filled rectangle draws it
        assertTrue(PageAnalyzer.slantedPanel(mark(183.6f, 69.6f, 593.4f, 744f, 5, false, true, true, 0x8DABD9, false,
                false, false)));
    }

    @Test
    void shapesOtherPathsDrawAreLeftToThem() {
        // The dark pentagon under it is near enough a box to be a filled rectangle
        assertFalse(PageAnalyzer.slantedPanel(mark(191.3f, 69.1f, 602.3f, 743.5f, 5, false, true, true, 0x4B4A53, false,
                true, false)));
        // A country on a map, hundreds of edges: a drawing's, for the figure finder
        assertFalse(PageAnalyzer.slantedPanel(mark(360.9f, 386.2f, 448.7f, 472.7f, 604, false, true, true, 0xE2E2E2, false,
                false, false)));
        // A dot, an outline, a gradient's mark
        assertFalse(PageAnalyzer.slantedPanel(mark(10f, 10f, 20f, 20f, 4, true, false, true, 0x000000, true, false, false)));
        assertFalse(PageAnalyzer.slantedPanel(mark(10f, 10f, 200f, 200f, 5, false, true, false, 0x000000, false, false,
                false)));
    }
}
