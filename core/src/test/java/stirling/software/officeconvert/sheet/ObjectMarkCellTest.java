package stirling.software.officeconvert.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.layout.Word;

class ObjectMarkCellTest {

    private static final String MARKS = "￼￼￼￼￼￼";

    @Test
    void aLineOfObjectReplacementCharactersLeavesNoCell() {
        // MMWR's masthead: letters the PDF's text maps to U+FFFC, in words of their own
        assertSame(CellText.EMPTY, cell(MARKS + " " + MARKS + MARKS));
    }

    @Test
    void marksAmongWordsLeaveOneSpace() {
        assertEquals("Morbidity and Mortality Weekly Report",
                cell(MARKS + " Morbidity and Mortality " + MARKS + " Weekly Report").text());
        assertEquals("Weekly / Vol. 73", cell("Weekly / Vol. 73 " + MARKS).text());
    }

    @Test
    void textWithoutMarksComesOutAsBefore() {
        assertEquals("Loan Amount $162,000 NO", cell("Loan Amount $162,000 NO").text());
    }

    private static CellText cell(String line) {
        ParaDraft d = new ParaDraft(0, 400);
        List<Word> words = new ArrayList<>();
        float x = 0;
        for (String w : line.split(" ", -1)) {
            if (w.isEmpty()) {
                x += 2.5f;
                continue;
            }
            words.add(new Word(List.of(new Glyph(w, x, 5 * w.length(), 700, 10, 8, 2, FontInfo.DEFAULT, 0, 0, 2.5f,
                    false, false))));
            x += 5 * w.length() + 2.5f;
        }
        d.lines.add(new Line(words, new byte[words.size()]));
        return CellText.of(List.of(d), false);
    }
}
