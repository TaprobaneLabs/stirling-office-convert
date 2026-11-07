package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.table.PageContent.PdfWord;
import stirling.software.officeconvert.table.TableDetection;
import stirling.software.officeconvert.table.TableDetection.FoundCell;
import stirling.software.officeconvert.table.TableDetection.HAlign;
import stirling.software.officeconvert.table.TableDetection.VAlign;

class TopRuleTest {

    private static final float SIZE = 9.96f;
    private static final float[] EDGES = {319.3f, 362, 405, 445, 485, 511.3f};
    /** arXiv 2403.01461, page 4, Table 3: its rows' tops and bottoms. */
    private static final float[][] ROWS = {{75.12f, 101.64f}, {109.07f, 118.7f}, {126.06f, 135.69f}, {138.02f, 147.65f}};
    private static final String[][] TEXT = {
        {"", "Correct", "Other", "Hallu.", "Abstain"},
        {"", "", "T5-XL", "", ""},
        {"Factual", "70.7", "2.4", "26.5", "0.4"},
        {"C-factual", "60.2", "3.2", "35.8", "0.8"}};

    /**
     * Its top rule is 3.96 pt above the header's words and its bottom rule 3.63 pt below the last row's. Past a fixed
     * 3 pt both were left out, the rules under the header and under "T5-XL" framed the table instead, and its header
     * was trimmed off: two rows, not taken for a table.
     */
    @Test
    void aTopRuleFourPointsAboveA10PointHeaderStillFramesTheTable() {
        List<PdfWord> words = new ArrayList<>();
        TableDetection.Found table = table(words);

        assertEquals(4.98f, TableShaper.ruleReach(table, words), 0.01f, "half the type size");
        TableDetection.Found trimmed = TableShaper.trimToRules(table, rules(71.25f, 105.19f, 122.18f, 151.28f), words);
        assertEquals(4, trimmed.rows(), "the header and every row stay");
        assertEquals(1, trimmed.headerRows());
    }

    /**
     * A rule just above a table with none under it is no frame: the last table's bottom rule, say. Taken in with the
     * rule under the header, it would trim the table to its header (BLS's JOLTS lost a table that way).
     */
    @Test
    void aLoneRuleJustAboveATableDoesntTrimIt() {
        List<PdfWord> words = new ArrayList<>();
        TableDetection.Found table = table(words);
        TableDetection.Found trimmed = TableShaper.trimToRules(table, rules(71.25f, 105.19f), words);
        assertEquals(4, trimmed.rows());
    }

    private static List<Rule> rules(float... ys) {
        List<Rule> rules = new ArrayList<>();
        for (float y : ys) {
            rules.add(new Rule(true, y, 314.0f, 516.5f, 0.4f, 0));
        }
        return rules;
    }

    private static TableDetection.Found table(List<PdfWord> words) {
        List<FoundCell> cells = new ArrayList<>();
        for (int r = 0; r < ROWS.length; r++) {
            for (int c = 0; c < EDGES.length - 1; c++) {
                String text = TEXT[r][c];
                cells.add(new FoundCell(r, c, 1, 1, EDGES[c], ROWS[r][0], EDGES[c + 1], ROWS[r][1], false, false, false,
                        false, null, HAlign.GENERAL, VAlign.TOP, text));
                if (!text.isEmpty()) {
                    words.add(word(text, EDGES[c] + 2, ROWS[r][1] - 2.5f));
                }
            }
        }
        return new TableDetection.Found(ROWS.length, EDGES.length - 1, EDGES, false, 1, 0f, cells,
                EDGES[0], ROWS[0][0], EDGES[EDGES.length - 1], ROWS[ROWS.length - 1][1]);
    }

    private static PdfWord word(String text, float x, float baseline) {
        int n = text.length();
        float right = x + n * SIZE * 0.5f;
        float[] left = new float[n];
        float[] rights = new float[n];
        String[] glyphs = new String[n];
        for (int i = 0; i < n; i++) {
            left[i] = x + (right - x) * i / n;
            rights[i] = x + (right - x) * (i + 1) / n;
            glyphs[i] = String.valueOf(text.charAt(i));
        }
        return new PdfWord(text, x, right, baseline - SIZE * 0.75f, baseline + SIZE * 0.22f, baseline, SIZE, false, false,
                0, SIZE * 0.25f, left, rights, glyphs);
    }
}
