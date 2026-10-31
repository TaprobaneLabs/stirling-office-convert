package stirling.software.officeconvert.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import stirling.software.officeconvert.extract.PageGraphics.Rule;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.PageLayout;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph.Align;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Table;
import stirling.software.officeconvert.table.TableDetection;

final class TableBuilder {

    private static final float FIT_SPARE = 1f;

    private static final int MIN_BODY_ROWS = 3;

    // Room a one-line cell may run past its edge, as a share of its text's width (at least OVERRUN_MIN points)
    private static final float OVERRUN = 0.5f;

    private static final float OVERRUN_MIN = 6f;

    private final ParagraphFactory paragraphs;

    TableBuilder(ParagraphFactory paragraphs) {
        this.paragraphs = paragraphs;
    }

    Table build(PageLayout.TableItem ti, PageLayout layout, float colLeft, float colRight) {
        TableDetection.Found f = ti.table();
        Table t = new Table();
        float width = 0;
        for (int c = 0; c < f.cols(); c++) {
            t.columnWidths.add(Math.max(MIN_COLUMN, f.colEdges()[c + 1] - f.colEdges()[c]));
            width += t.columnWidths.getLast();
        }
        t.indent = f.left() - colLeft;
        t.indentEnd = colRight - f.left() - width;
        float pad = ti.pad();
        float sideBorder = f.ruled() ? 0.5f : 0f;
        t.cellMarginLeft = Math.max(0, pad - sideBorder);
        t.cellMarginRight = Math.max(0, pad - sideBorder);
        float[] rowEdges = ti.rowEdges();
        TableDetection.FoundCell[][] anchor = new TableDetection.FoundCell[f.rows()][f.cols()];
        int[][] index = new int[f.rows()][f.cols()];
        boolean[][] covered = new boolean[f.rows()][f.cols()];
        for (int i = 0; i < f.cells().size(); i++) {
            TableDetection.FoundCell c = f.cells().get(i);
            if (c.row() >= f.rows() || c.col() >= f.cols()) {
                continue;
            }
            anchor[c.row()][c.col()] = c;
            index[c.row()][c.col()] = i;
            for (int r = c.row(); r < Math.min(f.rows(), c.row() + c.rowSpan()); r++) {
                for (int k = c.col(); k < Math.min(f.cols(), c.col() + c.colSpan()); k++) {
                    covered[r][k] = true;
                }
            }
        }
        List<Rule> rules = layout.page().graphics().rules();
        for (int r = 0; r < f.rows(); r++) {
            Table.Row row = new Table.Row();
            row.height = Math.max(4f, rowEdges[r + 1] - rowEdges[r]);
            row.header = r < f.headerRows() && f.rows() - f.headerRows() >= MIN_BODY_ROWS;
            int k = 0;
            while (k < f.cols()) {
                TableDetection.FoundCell c = anchor[r][k];
                Table.Cell cell = new Table.Cell();
                if (c != null) {
                    cell.gridSpan = Math.max(1, Math.min(c.colSpan(), f.cols() - k));
                    cell.vMerge = c.rowSpan() > 1 ? 1 : 0;
                    borders(cell, c, rules, f.ruled());
                    cell.shading = c.fill() == null ? -1 : c.fill();
                    cell.vAlign = switch (c.vAlign()) {
                        case CENTER -> Table.VAlign.CENTER;
                        case BOTTOM -> Table.VAlign.BOTTOM;
                        default -> Table.VAlign.TOP;
                    };
                    float cellTop = rowEdges[r] + cell.top.width();
                    float prev = cellTop;
                    for (ParaDraft d : ti.cellParas().get(index[r][k])) {
                        float innerLeft = d.colLeft;
                        float innerRight = d.colRight;
                        float sb = cell.vAlign == Table.VAlign.TOP ? Math.max(0, paragraphs.wordTop(d) - prev) : 0;
                        if (prev != cellTop) {
                            sb = Math.max(0, paragraphs.wordTop(d) - prev);
                        }
                        if (stacked(d)) {
                            stack(d);
                        }
                        Paragraph p = paragraphs.detached(d, innerLeft, innerRight, sb);
                        cell.paragraphs.add(p);
                        prev = paragraphs.wordBottom(d);
                    }
                    k += cell.gridSpan;
                } else {
                    TableDetection.FoundCell above = null;
                    for (int rr = r - 1; rr >= 0 && above == null; rr--) {
                        TableDetection.FoundCell a = anchor[rr][k];
                        if (a != null && a.row() + a.rowSpan() > r) {
                            above = a;
                        }
                    }
                    if (above != null) {
                        cell.vMerge = 2;
                        cell.gridSpan = Math.max(1, Math.min(above.colSpan(), f.cols() - k));
                        borders(cell, above, rules, f.ruled());
                        cell.shading = above.fill() == null ? -1 : above.fill();
                    }
                    k += cell.gridSpan;
                }
                row.cells.add(cell);
            }
            float topBorder = 0;
            float bottomBorder = 0;
            for (Table.Cell cell : row.cells) {
                topBorder = Math.max(topBorder, cell.top.width());
                bottomBorder = Math.max(bottomBorder, cell.bottom.width());
            }
            row.height = Math.max(1f, row.height - topBorder - (r == f.rows() - 1 ? bottomBorder : 0));
            t.rows.add(row);
        }
        spanOverEmptyCells(t);
        widenForText(t, layout.page().width() - PAGE_EDGE - (f.left() + width));
        fitCells(t);
        allowOverrun(t);
        keepColumnHeaders(t);
        if (rightToLeft(t)) {
            t.mirror();
        }
        return t;
    }

    private static boolean rightToLeft(Table t) {
        int rtl = 0;
        int ltr = 0;
        for (Table.Row row : t.rows) {
            for (Table.Cell cell : row.cells) {
                for (Paragraph p : cell.paragraphs) {
                    for (Inline in : p.inlines) {
                        if (in instanceof Inline.Text text) {
                            for (int i = 0; i < text.text().length(); i++) {
                                byte d = Character.getDirectionality(text.text().charAt(i));
                                rtl += d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ? 1 : 0;
                                ltr += d == Character.DIRECTIONALITY_LEFT_TO_RIGHT ? 1 : 0;
                            }
                        }
                    }
                }
            }
        }
        return rtl > ltr;
    }

    private static void keepColumnHeaders(Table t) {
        boolean header = true;
        for (Table.Row row : t.rows) {
            long filled = row.cells.stream().filter(c -> c.paragraphs.stream().anyMatch(p -> !p.inlines.isEmpty())).count();
            header &= row.header && row.cells.size() >= 2 && filled * 2 > row.cells.size();
            row.header = header;
        }
    }

    private static void fitCells(Table t) {
        for (int r = 0; r < t.rows.size(); r++) {
            Table.Row row = t.rows.get(r);
            for (Table.Cell cell : row.cells) {
                if (cell.vMerge != 0 || cell.paragraphs.isEmpty()) {
                    continue;
                }
                float avail = row.height;
                float content = 0;
                int lines = 0;
                for (Paragraph p : cell.paragraphs) {
                    int n = Math.max(1, lineCount(p));
                    content += p.spaceBefore + p.spaceAfter + n * wordLine(p);
                    lines += n;
                }
                float excess = content - avail;
                float spare = excess + FIT_SPARE;
                if (spare <= 0.05f || lines == 0) {
                    continue;
                }
                for (Paragraph p : cell.paragraphs) {
                    float cut = Math.min(p.spaceBefore, spare);
                    p.spaceBefore -= cut;
                    spare -= cut;
                    excess -= cut;
                }
                if (excess <= 0.05f) {
                    continue;
                }
                float perLine = excess / lines;
                for (Paragraph p : cell.paragraphs) {
                    float floor = p.markStyle != null ? p.markStyle.size() * 0.9f : p.lineHeight * 0.85f;
                    p.lineHeight = Math.max(floor, p.lineHeight - perLine);
                }
            }
        }
    }

    private static float wordLine(Paragraph p) {
        if (p.lineRule != Paragraph.LineRule.AT_LEAST) {
            return p.lineHeight;
        }
        float size = 0;
        for (Inline in : p.inlines) {
            if (in instanceof Inline.Text t) {
                size = Math.max(size, t.style().size());
            }
        }
        return Math.max(p.lineHeight, SINGLE * size);
    }

    private static final float SINGLE = 1.15f;

    private static int lineCount(Paragraph p) {
        return p.sourceLines;
    }

    /**
     * A cell paragraph with one number to a line, such as a column of line numbers set as one paragraph that relies on
     * wrapping a number to a line in a cell too narrow for two. Words of prose, hyphenated words and text without spaces
     * that happen to wrap one to a line stay a wrapping paragraph, which keeps reflowing when edited. Word wraps it by its own measure and splits a number wider
     * than the cell digit by digit, so its lines are kept with line breaks and may run past the edge like one-line cells.
     */
    static boolean stacked(ParaDraft d) {
        if (d.lines.size() < 2) {
            return false;
        }
        for (Line l : d.lines) {
            if (l.words.size() != 1 || !NUMBER_LIKE.matcher(l.words.getFirst().text).matches()) {
                return false;
            }
        }
        return true;
    }

    // A line number or item number: 9, 10, 4.1, 12a
    private static final Pattern NUMBER_LIKE = Pattern.compile("\\p{Nd}[\\p{Nd}.,:/]*\\p{L}?");

    /** Breaks every line. Words flush right, measured as justified with one word a line, are aligned right. */
    private static void stack(ParaDraft d) {
        d.hardBreaks.set(1, d.lines.size());
        if (d.align == Align.JUSTIFY && !d.rtl) {
            d.align = Align.RIGHT;
            d.left = 0;
            d.first = 0;
            d.justifySlack = Float.NaN;
        }
    }

    private static boolean everyLineBroken(Paragraph p) {
        long breaks = p.inlines.stream().filter(in -> in instanceof Inline.Break).count();
        return p.sourceLines > 1 && breaks == p.sourceLines - 1;
    }

    /**
     * Lets a cell that is one line in the PDF run past its edge rather than wrap. Word's font or its spaces can come out a
     * little wider than the PDF's, and a wrapped label or figure doubles its row's height, which pushes the rest of the
     * table down and often onto another page. The indent opens on the side the text grows towards.
     */
    private static void allowOverrun(Table t) {
        for (Table.Row row : t.rows) {
            for (Table.Cell cell : row.cells) {
                for (Paragraph p : cell.paragraphs) {
                    if (p.sourceLines != 1 && !everyLineBroken(p) || p.bidi || p.list != null || p.textWidth <= 0) {
                        continue;
                    }
                    float room = Math.max(OVERRUN_MIN, OVERRUN * p.textWidth);
                    switch (p.align) {
                        case RIGHT -> p.indentLeft -= room;
                        case CENTER -> {
                            p.indentLeft -= room / 2f;
                            p.indentRight -= room / 2f;
                        }
                        default -> p.indentRight -= room;
                    }
                }
            }
        }
    }

    /** The narrowest a column is set. */
    private static final float MIN_COLUMN = 4f;

    /**
     * Joins a cell to the empty cells after it in its row while its one line runs on over them, as a section's title does
     * ("Interest Charged" in Regulation Z's model statement, on a bar across the table, starting in the post-date column):
     * widened for the title instead, that column moved every column after it right, and the amounts over the table's rule.
     */
    static void spanOverEmptyCells(Table t) {
        for (Table.Row row : t.rows) {
            int col = 0;
            for (int i = 0; i < row.cells.size(); i++) {
                Table.Cell cell = row.cells.get(i);
                float need = spannable(cell) ? lineNeed(t, cell) : 0f;
                float width = spanWidth(t, col, cell.gridSpan);
                while (need > width && i + 1 < row.cells.size() && cell.vMerge == 0 && joins(cell, row.cells.get(i + 1))) {
                    Table.Cell next = row.cells.remove(i + 1);
                    width += spanWidth(t, col + cell.gridSpan, next.gridSpan);
                    cell.gridSpan += next.gridSpan;
                    cell.right = next.right;
                }
                col += cell.gridSpan;
            }
        }
    }

    /**
     * Whether a cell's text can take the room of the cells it would span: every paragraph one line, left to right and set
     * from the left. A paragraph set right or centred would move into the cells joined, and one of several lines rewrap
     * (from Codex's review).
     */
    private static boolean spannable(Table.Cell cell) {
        for (Paragraph p : cell.paragraphs) {
            if (p.sourceLines > 0 && (p.sourceLines != 1 || p.bidi || p.align != Align.LEFT && p.align != Align.JUSTIFY)) {
                return false;
            }
        }
        return true;
    }

    /** The room a cell's widest one-line paragraph, set from the left, needs; 0 when it has none. */
    private static float lineNeed(Table t, Table.Cell cell) {
        float need = 0;
        for (Paragraph p : cell.paragraphs) {
            if (p.sourceLines == 1 && !p.bidi && (p.align == Align.LEFT || p.align == Align.JUSTIFY)) {
                need = Math.max(need, Math.max(0, p.indentLeft) + p.textWidth + t.cellMarginLeft + t.cellMarginRight + 2f);
            }
        }
        return need;
    }

    private static float spanWidth(Table t, int from, int span) {
        float w = 0;
        for (int c = from; c < Math.min(t.columnWidths.size(), from + span); c++) {
            w += t.columnWidths.get(c);
        }
        return w;
    }

    /**
     * Whether an empty cell can join the one before it: nothing in it, no rule drawn between them (a blank field of a
     * ruled form stays a field of its own), and drawn the same above and below.
     */
    private static boolean joins(Table.Cell cell, Table.Cell next) {
        for (Paragraph p : next.paragraphs) {
            if (p.sourceLines > 0) {
                return false;
            }
        }
        return next.vMerge == 0 && cell.right.equals(Table.Border.NONE) && next.left.equals(Table.Border.NONE)
                && next.shading == cell.shading && next.top.equals(cell.top) && next.bottom.equals(cell.bottom);
    }

    /** How near the page's edge a table may grow: Word draws one into the margin, and cuts it off at the edge. */
    private static final float PAGE_EDGE = 18f;

    /** Widens columns for their one-line text; {@code roomRight} is how far the table may grow on the right. */
    static void widenForText(Table t, float roomRight) {
        int n = t.columnWidths.size();
        float[] need = new float[n];
        float[] freeLeft = new float[n];
        Arrays.fill(freeLeft, Float.MAX_VALUE);
        // Whether any cell starting in a column holds text, spanning or not
        boolean[] holdsText = new boolean[n];
        List<List<Paragraph>> byColumn = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            byColumn.add(new ArrayList<>());
        }
        int cellIndex = 0;
        for (Table.Row row : t.rows) {
            int col = 0;
            for (Table.Cell cell : row.cells) {
                if (col < n) {
                    for (Paragraph p : cell.paragraphs) {
                        holdsText[col] |= p.sourceLines > 0;
                    }
                }
                if (cell.gridSpan == 1 && col < n) {
                    for (Paragraph p : cell.paragraphs) {
                        byColumn.get(col).add(p);
                        freeLeft[col] = Math.min(freeLeft[col], p.align == Align.LEFT || p.align == Align.JUSTIFY
                                ? Math.max(0, p.indentLeft) : 0);
                        if (p.sourceLines == 1) {
                            float w = Math.max(0, p.indentLeft) + p.textWidth + t.cellMarginLeft + t.cellMarginRight + 2f;
                            need[col] = Math.max(need[col], w);
                        }
                    }
                }
                col += cell.gridSpan;
                cellIndex++;
            }
        }
        float spare = Math.max(0, roomRight);
        for (int c = 0; c < n; c++) {
            float deficit = need[c] - t.columnWidths.get(c);
            if (deficit <= 0) {
                continue;
            }
            // A column no cell starts text in gives its room whole: Reg Z's model statement split its transactions'
            // description column at the gap most rows leave, and the description, all in the first part, widened the
            // table 94 pt past the page's edge, the amounts with it
            boolean empty = c + 1 < n && !holdsText[c + 1];
            if (c + 1 < n && (freeLeft[c + 1] != Float.MAX_VALUE || empty)) {
                float room = empty ? t.columnWidths.get(c + 1) - MIN_COLUMN
                        : Math.min(freeLeft[c + 1], t.columnWidths.get(c + 1) - need[c + 1]);
                float take = Math.min(deficit, room);
                if (take > 0) {
                    t.columnWidths.set(c, t.columnWidths.get(c) + take);
                    t.columnWidths.set(c + 1, t.columnWidths.get(c + 1) - take);
                    freeLeft[c + 1] -= take;
                    for (Paragraph p : byColumn.get(c + 1)) {
                        p.indentLeft = Math.max(0, p.indentLeft - take);
                    }
                    deficit -= take;
                }
            }
            if (deficit > 0) {
                // Short of the page's edge the table grows; past it, the lines move left in their cells first
                float grow = Math.min(deficit, spare);
                spare -= grow;
                float still = deficit > grow ? moveIn(byColumn.get(c), t, t.columnWidths.get(c) + grow) : 0;
                t.columnWidths.set(c, t.columnWidths.get(c) + grow + still);
            }
        }
    }

    /**
     * Moves a column's lines left in their cells, as far as their indents allow, until they fit {@code width}, and returns
     * how much wider than that the column must still grow. A table grown past the page's edge has its last columns cut
     * off there: the Federal Reserve's H.4.1 release and OPM's SF 15 lost figures that way. A line moves whole: no
     * further than where its first line starts, so a marker it hangs from (a negative first-line indent) stays in the
     * cell, and its tab stops with it, so the text after a tab keeps its place in the line (from Codex's review). Lines
     * move only to fit the width the column needs anyway: a line that can't move as far as the rest makes the column
     * that wide, and a line that already fits there stays (also from Codex's review).
     */
    private static float moveIn(List<Paragraph> column, Table t, float width) {
        float least = width;
        for (Paragraph p : column) {
            if (p.sourceLines == 1) {
                least = Math.max(least, taken(p, t) - movable(p));
            }
        }
        for (Paragraph p : column) {
            float w = taken(p, t);
            if (p.sourceLines == 1 && w > least && movable(p) > 0) {
                float move = Math.min(movable(p), w - least);
                p.indentLeft -= move;
                p.tabs.replaceAll(s -> new Paragraph.TabStop(Math.max(0, s.pos() - move), s.kind(), s.leader()));
            }
        }
        return least - width;
    }

    /** The room a one-line paragraph takes in its cell. */
    private static float taken(Paragraph p, Table t) {
        return Math.max(0, p.indentLeft) + p.textWidth + t.cellMarginLeft + t.cellMarginRight + 2f;
    }

    /** How far a left-aligned line can move left: to where its first line starts at the cell's edge. */
    private static float movable(Paragraph p) {
        return p.align == Align.LEFT || p.align == Align.JUSTIFY ? Math.max(0, p.indentLeft + Math.min(0, p.indentFirst))
                : 0;
    }

    private static void borders(Table.Cell cell, TableDetection.FoundCell c, List<Rule> rules, boolean ruled) {
        cell.top = c.borderTop() ? border(rules, true, c.top(), c.left(), c.right()) : Table.Border.NONE;
        cell.bottom = c.borderBottom() ? border(rules, true, c.bottom(), c.left(), c.right()) : Table.Border.NONE;
        cell.left = c.borderLeft() ? border(rules, false, c.left(), c.top(), c.bottom()) : Table.Border.NONE;
        cell.right = c.borderRight() ? border(rules, false, c.right(), c.top(), c.bottom()) : Table.Border.NONE;
    }

    private static Table.Border border(List<Rule> rules, boolean horizontal, float pos, float from, float to) {
        Rule best = null;
        float bestD = Table.BORDER_REACH;
        float mid = (from + to) / 2f;
        for (Rule r : rules) {
            if (r.horizontal() != horizontal || r.start() > mid || r.end() < mid) {
                continue;
            }
            float d = Math.abs(r.pos() - pos);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        if (best == null) {
            return new Table.Border(0.5f, 0);
        }
        return new Table.Border(Math.max(0.25f, Math.min(best.thickness(), 6f)), best.rgb());
    }
}
