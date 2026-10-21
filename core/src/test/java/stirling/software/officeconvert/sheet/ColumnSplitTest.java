package stirling.software.officeconvert.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnSplitTest {

    private static ColumnSplit.Piece piece(Object... wordsAndX) {
        List<ColumnSplit.Piece> words = new ArrayList<>();
        for (int i = 0; i < wordsAndX.length; i += 2) {
            String text = (String) wordsAndX[i];
            float x = ((Number) wordsAndX[i + 1]).floatValue();
            words.add(new ColumnSplit.Piece(text, x, x + 5 * text.length(), List.of()));
        }
        StringBuilder sb = new StringBuilder();
        words.forEach(w -> sb.append(sb.isEmpty() ? "" : " ").append(w.text()));
        return new ColumnSplit.Piece(sb.toString(), words.getFirst().x(), words.getLast().right(), words);
    }

    @Test
    void figuresAcrossACutAreSplitThere() {
        List<ColumnSplit.Piece> parts = ColumnSplit.atCuts(piece("3.3", 100, "7.0", 130), new float[] {122});
        assertEquals(List.of("3.3", "7.0"), parts.stream().map(ColumnSplit.Piece::text).toList());
        assertEquals(130f, parts.get(1).x());
        List<ColumnSplit.Piece> three = ColumnSplit.atCuts(piece("-1.3", 100, "(2.5)", 130, "1,032.3", 160),
                new float[] {122, 152});
        assertEquals(List.of("-1.3", "(2.5)", "1,032.3"), three.stream().map(ColumnSplit.Piece::text).toList());
    }

    @Test
    void labelsAndPiecesInsideOnePartStayWhole() {
        assertEquals(1, ColumnSplit.atCuts(piece("Durable", 100, "goods", 140), new float[] {122}).size());
        assertEquals(1, ColumnSplit.atCuts(piece("3.3", 100, "7.0", 108), new float[] {122}).size());
        assertEquals(1, ColumnSplit.atCuts(piece("Q3", 100, "2.5", 130), new float[] {122}).size(), "not every word a number");
    }
}
