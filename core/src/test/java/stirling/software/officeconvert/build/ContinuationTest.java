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

/**
 * A paragraph that ends a page runs on into the next page's first paragraph only when both start where the output sets
 * them: beside a float, a paragraph's indent may be measured from the column ({@link ParaDraft#roomShift}), and the joined
 * paragraph's lines on the next page take that indent too.
 */
class ContinuationTest {

    private static final float COL_LEFT = 36;
    private static final float COL_RIGHT = 576;

    @Test
    void aParagraphIndentedBesideAFloatDoesntRunOnIntoOneIndentedAsMuchFromTheColumn() {
        // 18 pt past a float whose room starts 200 pt into the column; the next page's paragraph 18 pt into the column
        ParaDraft prev = draft(COL_LEFT + 218, 200, 18);
        ParaDraft next = draft(COL_LEFT + 18, 0, 18);
        assertFalse(Continuation.continues(prev, next));
    }

    @Test
    void itRunsOnIntoOneStartingWhereItsBodyDoes() {
        assertTrue(Continuation.continues(draft(COL_LEFT + 218, 200, 18), draft(COL_LEFT + 218, 0, 218)));
    }

    /** A paragraph of one line from {@code x} to the column's end, mid-sentence, indented {@code left} from its room. */
    private static ParaDraft draft(float x, float roomShift, float left) {
        ParaDraft d = new ParaDraft(COL_LEFT, COL_RIGHT);
        d.roomShift = roomShift;
        d.left = left;
        List<Word> words = new ArrayList<>();
        for (float at = x; at + 20 <= COL_RIGHT - 6; at += 22.5f) {
            words.add(new Word(List.of(new Glyph("word", at, 20, 700, 10, 8, 2, FontInfo.DEFAULT, 0, 0, 2.5f, false, false))));
        }
        d.lines.add(new Line(words, new byte[words.size()]));
        return d;
    }
}
