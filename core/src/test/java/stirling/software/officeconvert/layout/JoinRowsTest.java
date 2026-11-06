package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

/**
 * How LineGroups.joinRows() puts a column's segments back into rows. A phone photo of a form had its header, one tilted
 * OCR line, cut into three pieces at its wide gaps; each piece stood on its own baseline, 0.8 points from the next, and
 * the first, 1.6 points from the last, came out as a text box beside the rest.
 */
class JoinRowsTest {

    private static final float SIZE = 7.97f;

    /** The form photo's header pieces, where its OCR layer puts them once each piece stands on one baseline. */
    private static List<Line> header() {
        return new ArrayList<>(List.of(line("Northgate Water Authorty", 81.65f, 166.336f),
                line("Complete every section and ete de ko", 195.19f, 165.503f), line("to de odfice", 366.70f, 164.725f)));
    }

    @Test
    void theThreePiecesOfATiltedLineMakeOneRow() {
        List<Line> rows = LineGroups.joinRows(header());

        assertEquals(1, rows.size(), "one row");
        List<String> words = rows.getFirst().words.stream().map(w -> w.text).toList();
        assertEquals("Northgate", words.getFirst());
        assertEquals("odfice", words.getLast());
        assertEquals(13, words.size(), "every piece's words, in order");
    }

    @Test
    void theNextLineOfAParagraphStaysItsOwnRow() {
        List<Line> segs = header();
        segs.add(line("Section 1", 81.65f, 166.336f + 1.2f * SIZE));
        assertEquals(2, LineGroups.joinRows(segs).size());
    }

    @Test
    void piecesTwiceTheToleranceApartStayApart() {
        // A fifth of the size is 1.6 points: 3.5 points apart is no one row, whatever the order
        List<Line> segs = new ArrayList<>(List.of(line("first", 100, 200), line("second", 200, 203.5f)));
        assertEquals(2, LineGroups.joinRows(segs).size());
    }

    /** A segment of OCR text at {@code baseline}: glyphs half an em wide with OCRmyPDF's NotoSans metrics. */
    private static Line line(String text, float x, float baseline) {
        List<Glyph> glyphs = new ArrayList<>();
        float advance = SIZE * 0.5f;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, advance, baseline, SIZE, SIZE * 1.069f, SIZE * 0.293f,
                    FontInfo.DEFAULT, 0, glyphs.size(), SIZE * 0.25f, false, false));
            x += advance;
        }
        return LineBuilder.build(glyphs).getFirst();
    }
}
