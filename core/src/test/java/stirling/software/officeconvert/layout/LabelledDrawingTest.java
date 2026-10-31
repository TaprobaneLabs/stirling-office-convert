package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageGraphics.Fill;
import stirling.software.officeconvert.extract.PageGraphics.VectorMark;

/**
 * Which drawings are labelled drawings (a map whose text is its place names) and how far they grow: USGS Fact Sheet
 * 2024-3029's map of Africa was taken for prose once it had grown into the grey panel of prose beside it, and dropped.
 */
class LabelledDrawingTest {

    private static final Box MAP = new Box(200, 200, 520, 520);

    @Test
    void aMapNamedInShortLabelsIsALabelledDrawing() {
        assertTrue(FigureFinder.labelledDrawing(MAP, labels(), countries(64)));
    }

    @Test
    void aFewMarksAreNot() {
        assertFalse(FigureFinder.labelledDrawing(MAP, labels(), countries(30)), "30 marks are a few icons, not a map");
    }

    @Test
    void aDrawingWithLinesOfProseIsNot() {
        List<Line> lines = new ArrayList<>(labels());
        lines.add(line("the volcano erupted in the spring of", 220, 480, 10));
        lines.add(line("that year and again in the autumn", 220, 495, 10));
        assertFalse(FigureFinder.labelledDrawing(MAP, lines, countries(64)), "two lines of six words are prose");
    }

    @Test
    void aDrawingWhoseLinesStandOneUnderAnotherIsNot() {
        // An infographic's captions: short lines, but in stacks at a paragraph's pitch
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            lines.add(line("Spawning", 220, 240 + 12 * i, 10));
            lines.add(line("Juveniles", 380, 380 + 12 * i, 10));
        }
        lines.add(line("Lake", 300, 300, 10));
        assertFalse(FigureFinder.labelledDrawing(MAP, lines, countries(64)));
    }

    @Test
    void stackedShareCountsRunsOfThreeLines() {
        List<Line> paragraph = List.of(line("first", 100, 100, 10), line("second", 100, 112, 10), line("third", 100, 124, 10));
        assertEquals(1f, FigureFinder.stackedShare(paragraph), 0.01f);
        assertEquals(0f, FigureFinder.stackedShare(labels()), 0.01f, "labels apart from each other");
        List<Line> two = List.of(line("first", 100, 100, 10), line("second", 100, 112, 10));
        assertEquals(0f, FigureFinder.stackedShare(two), 0.01f, "two lines make no run");
    }

    @Test
    void aMapGrowsThroughItsOwnPanelButNotThePanelOfProseBesideIt() {
        List<VectorMark> marks = countries(64);
        List<Line> lines = new ArrayList<>(labels());
        lines.add(line("Base from Natural Earth", 210, 540, 6));
        for (int i = 0; i < 13; i++) {
            lines.add(line("the long column of prose that runs beside the map", 40, 210 + 12.5f * i, 9));
        }
        Fill panel = new Fill(190, 195, 530, 560, 0xF0F0F0);
        Fill prose = new Fill(30, 195, 260, 380, 0xC8C8C8);
        List<Box> strong = FigureFinder.strongRegions(marks, 612, 792);
        List<Box> labelled = FigureFinder.labelledDrawings(marks, lines, 612, 792);
        assertEquals(1, labelled.size());
        List<Box> figures = FigureFinder.figures(strong, List.of(panel, prose), List.of(), lines, marks, labelled, 9f, 612, 792);
        assertEquals(1, figures.size(), "the map is one figure");
        Box map = figures.getFirst();
        assertEquals(190f, map.x(), 0.5f, "it takes in its own panel, whose text is small print");
        assertEquals(560f, map.bottom(), 0.5f);

        List<Box> unguarded = FigureFinder.figures(strong, List.of(panel, prose), List.of(), lines, marks, List.of(), 9f, 612, 792);
        assertTrue(unguarded.isEmpty(), "taken for an unlabelled drawing, it grows into the prose and is dropped");
    }

    @Test
    void ownTextIsBodyTextOrALongLineOutsideTheRegion() {
        Box fill = new Box(30, 195, 260, 380);
        List<Line> small = List.of(line("Base from Natural Earth", 40, 220, 6), line("Web Mercator", 40, 230, 6));
        assertFalse(FigureFinder.holdsOwnText(fill, MAP, small, 9f), "small print isn't text of its own");
        List<Line> body = List.of(line("of the column", 40, 220, 9), line("of prose", 40, 232, 9));
        assertTrue(FigureFinder.holdsOwnText(fill, MAP, body, 9f));
        List<Line> inside = List.of(line("of the column", 210, 220, 9), line("of prose", 210, 232, 9));
        assertFalse(FigureFinder.holdsOwnText(fill, MAP, inside, 9f), "lines inside the region are its labels");
    }

    /** Short place names spread over the map, none under another. */
    private static List<Line> labels() {
        return List.of(line("MALI", 230, 240, 7), line("NIGERIA", 330, 300, 7), line("GHANA", 250, 360, 7),
                line("KENYA", 460, 330, 7), line("ANGOLA", 300, 430, 7), line("ZAMBIA", 400, 470, 7));
    }

    /** Touching outlines with slanted sides, eight to a row, over the map's box. */
    private static List<VectorMark> countries(int n) {
        List<VectorMark> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float x = 210 + 38 * (i % 8);
            float top = 210 + 38 * (i / 8);
            out.add(new VectorMark(x, top, x + 37, top + 37, 5, false, true, true, false, 0xD9BF99, true, 0x666666, 0.5f,
                    false));
        }
        return out;
    }

    private static Line line(String text, float x, float baseline, float size) {
        List<Glyph> glyphs = new ArrayList<>();
        float advance = size * 0.5f;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, advance, baseline, size, size * 0.75f, size * 0.25f,
                    FontInfo.DEFAULT, 0, glyphs.size(), size * 0.25f, false, false));
            x += advance;
        }
        return LineBuilder.build(glyphs).getFirst();
    }
}
