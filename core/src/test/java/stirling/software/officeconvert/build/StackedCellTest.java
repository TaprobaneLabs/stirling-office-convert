package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.layout.Word;

class StackedCellTest {

    @Test
    void oneWordLinesAreStacked() {
        assertTrue(TableBuilder.stacked(draft("9", "10", "11")));
    }

    @Test
    void aWordHyphenatedAcrossLinesIsNot() {
        // Kept as one wrapped paragraph, so the line-end hyphen is dropped and the word joined again
        assertFalse(TableBuilder.stacked(draft("trans-", "duction")));
        assertFalse(TableBuilder.stacked(draft("one", "two words")));
    }

    private static ParaDraft draft(String... lines) {
        ParaDraft d = new ParaDraft(0, 40);
        float baseline = 700;
        for (String text : lines) {
            List<Word> words = new ArrayList<>();
            float x = 0;
            for (String w : text.split(" ")) {
                Glyph g = new Glyph(w, x, 5 * w.length(), baseline, 10, 8, 2, FontInfo.DEFAULT, 0, 0, 2.5f, false, false);
                words.add(new Word(List.of(g)));
                x += 5 * w.length() + 2.5f;
            }
            d.lines.add(new Line(words, new byte[words.size()]));
            baseline -= 12;
        }
        return d;
    }
}
