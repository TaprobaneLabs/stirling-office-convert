package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

class LineBuilderTest {

    private static final float ADVANCE = 5f;
    private static final float SPACE = 2.5f;

    private static void place(List<Glyph> out, String text, float x, float baseline, float size) {
        float scale = size / 10f;
        for (char c : text.toCharArray()) {
            float w = (c == ' ' ? SPACE : ADVANCE) * scale;
            out.add(new Glyph(String.valueOf(c), x, w, baseline, size, size * 0.75f, size * 0.25f,
                    FontInfo.DEFAULT, 0, out.size(), SPACE * scale, false, false));
            x += w;
        }
    }

    @Test
    void groupsGlyphsIntoWordsAndLines() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "second line", 72, 114, 10);
        place(glyphs, "Hello world", 72, 100, 10);

        List<Line> lines = LineBuilder.build(glyphs);

        assertEquals(2, lines.size());
        assertEquals("Hello world", lines.get(0).text());
        assertEquals("second line", lines.get(1).text());
        assertEquals(SPACE, lines.get(0).drawnSpace, 0.01f);
    }

    @Test
    void splitsAtAGutter() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "Left column", 72, 100, 10);
        place(glyphs, "Right column", 320, 100, 10);

        List<Line> lines = LineBuilder.build(glyphs);

        assertEquals(2, lines.size());
        assertEquals("Left column", lines.get(0).text());
        assertEquals(320, lines.get(1).x, 0.01f);
    }

    @Test
    void keepsDotLeadersInOneLine() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "Introduction", 72, 100, 10);
        place(glyphs, "..........", 160, 100, 10);
        place(glyphs, "7", 260, 100, 10);

        List<Line> lines = LineBuilder.build(glyphs);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).text().startsWith("Introduction"));
        assertTrue(lines.get(0).text().endsWith("7"));
    }

    @Test
    void splitsLeaderDotsGluedToTheirWords() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "Estimate..........8", 72, 100, 10);

        Line line = LineBuilder.join(LineBuilder.build(glyphs));

        assertEquals(List.of("Estimate", "8"), line.words.stream().map(w -> w.text).toList());
        assertEquals(Line.LEADER, line.gaps[1]);
        assertEquals('.', line.leaders[1]);
    }

    @Test
    void keepsDotsGluedInsideProseAsProse() {
        for (String prose : List.of("she paused....then went on", "and so.....it ends here", "Wait.....what 12 more")) {
            List<Glyph> glyphs = new ArrayList<>();
            place(glyphs, prose, 72, 100, 10);

            Line line = LineBuilder.join(LineBuilder.build(glyphs));

            assertEquals(prose, line.text(), prose);
            for (byte gap : line.gaps) {
                assertTrue(gap != Line.LEADER && gap != Line.TAB, prose);
            }
        }
    }

    @Test
    void splitsGluedLeadersThatRunToAPageNumber() {
        for (String entry : List.of("Scope....12", "Scope.... 12", "Annex B....iv", "Long fill-in.............. here")) {
            List<Glyph> glyphs = new ArrayList<>();
            place(glyphs, entry, 72, 100, 10);

            Line line = LineBuilder.join(LineBuilder.build(glyphs));

            assertEquals(Line.LEADER, line.gaps[line.words.size() - 1], entry);
        }
    }

    @Test
    void keepsTrailingDotsOnTheirWord() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "Loading....", 72, 100, 10);

        Line line = LineBuilder.join(LineBuilder.build(glyphs));

        assertEquals(List.of("Loading...."), line.words.stream().map(w -> w.text).toList());
    }

    private static void placeTracked(List<Glyph> out, String text, float x, float baseline, float tracking) {
        for (char c : text.toCharArray()) {
            float w = c == ' ' ? SPACE : ADVANCE;
            out.add(new Glyph(String.valueOf(c), x, w, baseline, 10, 7.5f, 2.5f, FontInfo.DEFAULT, 0, out.size(), SPACE,
                    false, false));
            x += w + tracking;
        }
    }

    @Test
    void keepsWordsWholeInLetterSpacedText() {
        List<Glyph> glyphs = new ArrayList<>();
        placeTracked(glyphs, "Our wedding will be outdoors", 72, 100, 2f);

        List<Line> lines = LineBuilder.build(glyphs);

        assertEquals(1, lines.size());
        assertEquals("Our wedding will be outdoors", lines.get(0).text());
    }

    @Test
    void keepsEvenlySpacedAxisLabelsApart() {
        List<Glyph> glyphs = new ArrayList<>();
        String[] labels = {"0", "5", "10", "15", "20", "25"};
        for (int i = 0; i < labels.length; i++) {
            place(glyphs, labels[i], 320 + 68 * i, 300, 12);
        }

        Line line = LineBuilder.join(LineBuilder.build(glyphs));

        assertEquals(List.of(labels), line.words.stream().map(w -> w.text).toList());

        List<Glyph> tight = new ArrayList<>();
        String[] hours = {"1", "5", "9", "13", "17", "21"};
        for (int i = 0; i < hours.length; i++) {
            place(tight, hours[i], 260 + 32.7f * i, 300, 12);
        }
        Line hourLine = LineBuilder.join(LineBuilder.build(tight));
        assertEquals(List.of(hours), hourLine.words.stream().map(w -> w.text).toList(), "labels 1.7 em apart");
    }

    @Test
    void keepsALetterSpacedWordWholeAfterAPlainOne() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "Tech", 72, 100, 10);
        placeTracked(glyphs, "SPACING", 100, 100, 3f);

        Line line = LineBuilder.join(LineBuilder.build(glyphs));

        assertEquals(List.of("Tech", "SPACING"), line.words.stream().map(w -> w.text).toList());
    }

    @Test
    void keepsAWidelySpacedHeadingWholeAfterASmallMarker() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "1", 60, 100, 4);
        placeTracked(glyphs, "HEADINGS", 72, 100, 9f);

        Line line = LineBuilder.join(LineBuilder.build(glyphs));

        assertEquals("HEADINGS", line.words.getLast().text);
    }

    @Test
    void marksRaisedSmallGlyphsAsSuperscript() {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, "E=mc", 72, 100, 10);
        place(glyphs, "2", 92, 96, 6);
        place(glyphs, " is famous", 95, 100, 10);

        List<Line> lines = LineBuilder.build(glyphs);

        assertEquals(1, lines.size());
        Glyph two = glyphs.stream().filter(g -> g.text.equals("2")).findFirst().orElseThrow();
        assertEquals(1, two.vertAlign);
    }
}
