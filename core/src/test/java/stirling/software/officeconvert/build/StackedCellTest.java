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
    void numberLinesAreStacked() {
        assertTrue(TableBuilder.stacked(draft("9", "10", "11")));
        assertTrue(TableBuilder.stacked(draft("4.1", "4.2", "12a")));
    }

    @Test
    void proseWrappedAWordToALineIsNot() {
        assertFalse(TableBuilder.stacked(draft("Personal", "income")));
    }

    @Test
    void aWordHyphenatedAcrossLinesIsNot() {
        // Kept as one wrapped paragraph, so the line-end hyphen is dropped and the word joined again
        assertFalse(TableBuilder.stacked(draft("trans-", "duction")));
        assertFalse(TableBuilder.stacked(draft("trans\u00AD", "duction")), "a soft hyphen");
        assertFalse(TableBuilder.stacked(draft("trans\u2010", "duction")), "a Unicode hyphen");
        assertFalse(TableBuilder.stacked(draft("one", "two words")));
    }

    @Test
    void wrappedTextWithoutSpacesIsNot() {
        // Chinese wraps without spaces, so each line comes out as one word
        assertFalse(TableBuilder.stacked(draft("\u7d93\u6fdf\u767c\u5c55\u8a08\u756b", "\u7684\u7b2c\u4e00\u968e\u6bb5")));
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
