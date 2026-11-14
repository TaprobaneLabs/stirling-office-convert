package stirling.software.officeconvert.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import stirling.software.officeconvert.table.DraftTable.Borders;
import stirling.software.officeconvert.table.PageContent.PdfWord;
import stirling.software.officeconvert.table.PageContent.Ruling;

final class RuledGrid {

    static final float LINE_TOLERANCE = 2f;

    private static final float EDGE_COVERAGE = 0.6f;

    private static final float TOLERANCE = LINE_TOLERANCE + 0.5f;

    /**
     * How much of a word standing alone may reach past a text column's gutter, on its lesser side, before it spans the
     * columns: a heading set right-aligned over two lines reaches left past its column's figures ("Approved?" on CMS's
     * Part B notice, a quarter of it), and stays its own column's. A word in a phrase spans the gutter as before: BEA's
     * "Seasonally adjusted at annual rates", centred over several columns.
     */
    private static final float REACH = 0.3f;

    final float[] xs;
    final float[] ys;
    private final boolean[][] vertical;
    private final boolean[][] horizontal;
    /** Lines between rows: the drawn ones, plus filled bands' edges, which keep rows apart but aren't drawn. */
    private final boolean[][] rowBreaks;
    /** The column lines found in the text, not drawn: words aren't cut at them. */
    private final boolean[][] textLines;

    private RuledGrid(
            float[] xs, float[] ys, boolean[][] vertical, boolean[][] horizontal, boolean[][] rowBreaks) {
        this.xs = xs;
        this.ys = ys;
        this.vertical = vertical;
        this.horizontal = horizontal;
        this.rowBreaks = rowBreaks;
        this.textLines = new boolean[vertical.length][vertical.length == 0 ? 0 : vertical[0].length];
    }

    static RuledGrid measure(float[] xs, float[] ys, List<Ruling> hs, List<Ruling> vs) {
        return measure(xs, ys, hs, vs, List.of());
    }

    /**
     * {@code bandEdges} keep the rows on either side apart without counting as drawn borders, which Word and Excel
     * would draw as black lines.
     */
    static RuledGrid measure(
            float[] xs, float[] ys, List<Ruling> hs, List<Ruling> vs, List<Ruling> bandEdges) {
        int rows = ys.length - 1;
        int cols = xs.length - 1;
        boolean[][] vertical = new boolean[rows][cols + 1];
        for (int c = 0; c <= cols; c++) {
            List<Ruling> near = near(vs, xs[c], TOLERANCE);
            for (int r = 0; r < rows; r++) {
                vertical[r][c] =
                        Rulings.coverage(near, xs[c], ys[r], ys[r + 1], TOLERANCE) >= EDGE_COVERAGE;
            }
        }
        boolean[][] horizontal = horizontals(xs, ys, hs);
        boolean[][] rowBreaks = horizontal;
        if (!bandEdges.isEmpty()) {
            boolean[][] edges = horizontals(xs, ys, bandEdges);
            rowBreaks = new boolean[rows + 1][cols];
            for (int r = 0; r <= rows; r++) {
                for (int c = 0; c < cols; c++) {
                    rowBreaks[r][c] = horizontal[r][c] || edges[r][c];
                }
            }
        }
        return new RuledGrid(xs, ys, vertical, horizontal, rowBreaks);
    }

    private static boolean[][] horizontals(float[] xs, float[] ys, List<Ruling> hs) {
        int cols = xs.length - 1;
        boolean[][] horizontal = new boolean[ys.length][cols];
        for (int r = 0; r < ys.length; r++) {
            List<Ruling> near = near(hs, ys[r], TOLERANCE);
            for (int c = 0; c < cols; c++) {
                horizontal[r][c] =
                        Rulings.coverage(near, ys[r], xs[c], xs[c + 1], TOLERANCE) >= EDGE_COVERAGE;
            }
        }
        return horizontal;
    }

    private static List<Ruling> near(List<Ruling> rulings, float pos, float tolerance) {
        List<Ruling> near = new ArrayList<>();
        for (Ruling r : rulings) {
            if (Math.abs(r.pos() - pos) <= tolerance) {
                near.add(r);
            }
        }
        return near;
    }

    int rows() {
        return ys.length - 1;
    }

    int cols() {
        return xs.length - 1;
    }

    int rowAt(float y) {
        return band(ys, y);
    }

    int colAt(float x) {
        return band(xs, x);
    }

    static int band(float[] edges, float v) {
        int idx = Arrays.binarySearch(edges, v);
        if (idx < 0) {
            idx = -idx - 2;
        }
        return Math.clamp(idx, 0, edges.length - 2);
    }

    void addTextColumnLines(List<PdfWord> words) {
        int rows = rows();
        List<List<PdfWord>> byRow = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            byRow.add(new ArrayList<>());
        }
        for (PdfWord w : words) {
            byRow.get(rowAt(w.centreY())).add(w);
        }
        Set<PdfWord> alone = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<PdfWord> row : byRow) {
            for (TextLine line : TextLine.group(row)) {
                for (List<PdfWord> chunk : ChunkedLine.of(line).chunks()) {
                    if (chunk.size() == 1) {
                        alone.add(chunk.getFirst());
                    }
                }
            }
        }
        for (int c = 1; c < xs.length - 1; c++) {
            float x = xs[c];
            boolean[] crossed = new boolean[rows];
            int crossing = 0;
            int splitting = 0;
            for (int r = 0; r < rows; r++) {
                if (vertical[r][c]) {
                    continue;
                }
                boolean left = false;
                boolean right = false;
                for (PdfWord w : byRow.get(r)) {
                    crossed[r] |= w.x() < x - 1f && w.right() > x + 1f
                            && (!alone.contains(w) || Math.min(x - w.x(), w.right() - x) > REACH * w.width());
                    left |= w.right() <= x + 1f && w.x() >= xs[c - 1] - 1f;
                    right |= w.x() >= x - 1f && w.right() <= xs[c + 1] + 1f;
                }
                if (crossed[r]) {
                    crossing++;
                } else if (left && right) {
                    splitting++;
                }
            }
            if (splitting > 0 && splitting > crossing) {
                for (int r = 0; r < rows; r++) {
                    if (!vertical[r][c] && !crossed[r]) {
                        vertical[r][c] = true;
                        textLines[r][c] = true;
                    }
                }
            }
        }
    }

    int[][] anchors() {
        int rows = rows();
        int cols = cols();
        UnionFind groups = new UnionFind(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols && !vertical[r][c + 1]) {
                    groups.union(r * cols + c, r * cols + c + 1);
                }
                if (r + 1 < rows && !rowBreaks[r + 1][c]) {
                    groups.union(r * cols + c, (r + 1) * cols + c);
                }
            }
        }
        Map<Integer, int[]> bounds = new HashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int g = groups.find(r * cols + c);
                int[] b = bounds.computeIfAbsent(g, k -> new int[] {rows, cols, -1, -1});
                b[0] = Math.min(b[0], r);
                b[1] = Math.min(b[1], c);
                b[2] = Math.max(b[2], r);
                b[3] = Math.max(b[3], c);
                sizes.merge(g, 1, Integer::sum);
            }
        }
        int[][] anchor = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int[] b = bounds.get(groups.find(r * cols + c));
                int area = (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
                boolean rectangle = area == sizes.get(groups.find(r * cols + c));
                anchor[r][c] = rectangle ? b[0] * cols + b[1] : r * cols + c;
            }
        }
        return anchor;
    }

    List<int[]> openOutline() {
        int rows = rows();
        int cols = cols();
        List<int[]> out = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            boolean[] edge = horizontal[side == 0 ? 0 : rows];
            if (anyDrawn(edge)) {
                for (int c = 0; c < cols; c++) {
                    if (!edge[c]) {
                        out.add(new int[] {side == 0 ? 0 : rows - 1, c});
                    }
                }
            }
        }
        for (int side = 0; side < 2; side++) {
            int c = side == 0 ? 0 : cols;
            boolean drawn = false;
            for (int r = 0; r < rows; r++) {
                drawn |= vertical[r][c];
            }
            for (int r = 0; drawn && r < rows; r++) {
                if (!vertical[r][c]) {
                    out.add(new int[] {r, side == 0 ? 0 : cols - 1});
                }
            }
        }
        return out;
    }

    private static boolean anyDrawn(boolean[] edge) {
        for (boolean e : edge) {
            if (e) {
                return true;
            }
        }
        return false;
    }

    Borders borders(int r0, int c0, int r1, int c1) {
        return new Borders(
                mostlyDrawn(horizontal[r0], c0, c1),
                mostlyDrawn(horizontal[r1 + 1], c0, c1),
                mostlyDrawnDown(c0, r0, r1),
                mostlyDrawnDown(c1 + 1, r0, r1));
    }

    boolean[] verticalsInRow(int r) {
        return vertical[r];
    }

    /** The drawn lines in row {@code r} that cut a word running over them: rules, not the text's column lines. */
    boolean[] rulesInRow(int r) {
        boolean[] out = vertical[r].clone();
        for (int c = 0; c < out.length; c++) {
            out[c] &= !textLines[r][c];
        }
        return out;
    }

    private static boolean mostlyDrawn(boolean[] edges, int from, int to) {
        int n = 0;
        for (int i = from; i <= to; i++) {
            if (edges[i]) {
                n++;
            }
        }
        return n * 2 >= to - from + 1;
    }

    private boolean mostlyDrawnDown(int col, int fromRow, int toRow) {
        int n = 0;
        for (int r = fromRow; r <= toRow; r++) {
            if (vertical[r][col]) {
                n++;
            }
        }
        return n * 2 >= toRow - fromRow + 1;
    }
}
