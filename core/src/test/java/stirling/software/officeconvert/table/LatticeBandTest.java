package stirling.software.officeconvert.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.table.PageContent.FillBox;
import stirling.software.officeconvert.table.PageContent.PdfWord;
import stirling.software.officeconvert.table.PageContent.Ruling;

class LatticeBandTest {

    /**
     * A Closing Disclosure's Loan Costs: each section heading is a grey band across the borrower's columns with no
     * rule under it, while the seller's columns are ruled at that line. Measured from the rules alone, the heading's
     * cells were joined to the first item's: "A. Origination Charges" over "01 Points", "$1,802.00" over "$405.00".
     */
    @Test
    void aShadedHeadingRowIsntJoinedToTheRowUnderIt() {
        List<Ruling> hs = new ArrayList<>();
        List<Ruling> vs = new ArrayList<>();
        // Column lines: label | borrower at closing | before closing | seller | others
        float[] xs = {36, 300, 400, 470, 576};
        for (float y : new float[] {50, 70, 100, 115, 130, 145}) {
            hs.add(new Ruling(y, 36, 576, 0.5f, 0));
        }
        // Under the band only the seller's columns are ruled
        hs.add(new Ruling(85, 400, 576, 0.5f, 0));
        for (float x : xs) {
            vs.add(new Ruling(x, 50, 145, 0.5f, 0));
        }
        List<FillBox> band = List.of(new FillBox(36, 70, 400, 85, 0xE6E6E6));

        List<PdfWord> words = new ArrayList<>();
        phrase(words, "Loan Costs", 40, 64);
        phrase(words, "Borrower-Paid", 310, 64);
        phrase(words, "Seller-Paid", 405, 64);
        phrase(words, "Others", 480, 64);
        phrase(words, "A. Origination Charges", 40, 81);
        phrase(words, "$1,802.00", 340, 81);
        String[][] items = {{"01 Points", "$405.00"}, {"02 Application Fee", "$300.00"},
                {"03 Underwriting Fee", "$1,097.00"}, {"04 Survey", "$85.00"}};
        float baseline = 96;
        for (String[] item : items) {
            phrase(words, item[0], 40, baseline);
            phrase(words, item[1], 340, baseline);
            phrase(words, "$0.00", 480, baseline);
            baseline += 15;
        }

        DraftTable without = only(LatticeFinder.find(0, hs, vs, words));
        assertEquals(2, rowSpan(without, "A. Origination Charges"), "without the band, the heading takes item 01's row");

        DraftTable with = only(LatticeFinder.find(0, hs, vs, words, band));
        assertEquals(1, rowSpan(with, "A. Origination Charges"), "the heading's own row");
        assertEquals(1, rowSpan(with, "$1,802.00"));
        assertEquals(1, rowSpan(with, "01 Points"));
        assertEquals(1, rowSpan(with, "$405.00"));

        // The band's lower edge keeps the rows apart but isn't drawn, so Word and Excel draw no line there
        assertTrue(cell(with, "A. Origination Charges").borders().top(), "the rule over the band");
        assertFalse(cell(with, "A. Origination Charges").borders().bottom(), "no rule under the band");
        assertFalse(cell(with, "01 Points").borders().top());
    }

    private static DraftTable.Cell cell(DraftTable table, String text) {
        for (DraftTable.Cell cell : table.cells()) {
            if (TextLine.joinWords(cell.words()).startsWith(text)) {
                return cell;
            }
        }
        throw new AssertionError("no cell starts with " + text);
    }

    private static DraftTable only(List<LatticeFinder.Found> found) {
        assertEquals(1, found.size(), "one table");
        DraftTable table = found.getFirst().table();
        assertNotNull(table, "a table, not a frame");
        return table;
    }

    private static int rowSpan(DraftTable table, String text) {
        return cell(table, text).rowSpan();
    }

    /** Adds {@code text}'s words at {@code x}, 9 pt type, a space apart. */
    private static void phrase(List<PdfWord> words, String text, float x, float baseline) {
        float size = 9;
        for (String w : text.split(" ")) {
            float right = x + w.length() * size * 0.5f;
            int n = w.length();
            float[] left = new float[n];
            float[] rights = new float[n];
            String[] glyphs = new String[n];
            for (int i = 0; i < n; i++) {
                left[i] = x + (right - x) * i / n;
                rights[i] = x + (right - x) * (i + 1) / n;
                glyphs[i] = String.valueOf(w.charAt(i));
            }
            words.add(new PdfWord(w, x, right, baseline - size * 0.8f, baseline + size * 0.2f, baseline, size, false,
                    false, 0, size * 0.28f, left, rights, glyphs));
            x = right + size * 0.28f;
        }
    }
}
