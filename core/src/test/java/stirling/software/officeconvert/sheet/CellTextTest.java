package stirling.software.officeconvert.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.layout.Word;

class CellTextTest {

    @Test
    void koreanLinesKeepTheSpaceBetweenTheirWords() {
        // Korean writes spaces between words, so a wrapped line's last word stays apart from the
        // next line's first, Hangul syllables or compatibility jamo alike
        assertEquals("납세자는 귀하의 권리가 있습니다", text("납세자는 귀하의", "권리가 있습니다"));
        assertEquals("ㄱ ㄴ", text("ㄱ", "ㄴ"));
    }

    @Test
    void chineseAndJapaneseLinesStillJoinWithoutOne() {
        assertEquals("經濟發展計畫的第一階段", text("經濟發展計畫", "的第一階段"));
        assertEquals("日本語の文章はスペースなしで続きます", text("日本語の文章は", "スペースなしで続きます"));
    }

    @Test
    void otherLinesJoinWithASpace() {
        assertEquals("one two three", text("one two", "three"));
        // A line of Korean after one of Chinese, or the other way round, keeps a space too
        assertEquals("經濟 권리가", text("經濟", "권리가"));
    }

    private static String text(String... lines) {
        return CellText.of(List.of(draft(lines)), false).text();
    }

    private static ParaDraft draft(String... lines) {
        ParaDraft d = new ParaDraft(0, 200);
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
