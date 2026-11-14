package stirling.software.officeconvert.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import stirling.software.officeconvert.table.PageContent.FillBox;
import stirling.software.officeconvert.table.PageContent.PdfWord;
import stirling.software.officeconvert.table.PageContent.Ruling;

final class LatticeFinder {

    private static final float JOIN_TOLERANCE = 2.5f;

    private static final float MIN_BAND = 4.5f;

    private static final int MAX_RULINGS = 4000;

    private static final float MIN_BORDER_LENGTH = 6f;

    private static final int MAX_GRID_CELLS = 20_000;

    private static final int PARAGRAPH_WORDS = 40;

    private static final float LABEL_COLUMN = 0.3f;
    private static final int LABEL_WORDS = 6;

    private LatticeFinder() {}

    record Found(DraftTable table, Box region) {}

    static List<Found> find(
            int page, List<Ruling> horizontals, List<Ruling> verticals, List<PdfWord> words) {
        return find(page, horizontals, verticals, words, List.of());
    }

    /**
     * The ruled tables on a page. {@code fills} are its filled bands: their top and bottom edges count as
     * drawn where they lie on the grid's row lines, so a shaded row isn't joined to the row under it.
     */
    static List<Found> find(
            int page, List<Ruling> horizontals, List<Ruling> verticals, List<PdfWord> words, List<FillBox> fills) {
        List<Ruling> hs = longEnough(horizontals);
        List<Ruling> vs = longEnough(verticals);
        if (hs.size() < 2 || vs.size() < 2 || hs.size() > MAX_RULINGS || vs.size() > MAX_RULINGS) {
            return List.of();
        }
        List<Found> found = new ArrayList<>();
        for (List<List<Ruling>> component : connected(hs, vs)) {
            List<Ruling> componentHs = component.get(0);
            List<Ruling> componentVs = component.get(1);
            if (componentHs.size() >= 2 && componentVs.size() >= 2) {
                Found f = build(page, componentHs, componentVs, words, fills, hs);
                if (f != null) {
                    found.add(f);
                }
            }
        }
        found.sort((a, b) -> Float.compare(a.region().top(), b.region().top()));
        return found;
    }

    private static List<Ruling> longEnough(List<Ruling> rulings) {
        List<Ruling> kept = new ArrayList<>(rulings.size());
        for (Ruling r : rulings) {
            if (r.length() >= MIN_BORDER_LENGTH) {
                kept.add(r);
            }
        }
        return kept;
    }

    private static List<List<List<Ruling>>> connected(List<Ruling> hs, List<Ruling> vs) {
        UnionFind groups = new UnionFind(hs.size() + vs.size());
        for (int i = 0; i < hs.size(); i++) {
            Ruling h = hs.get(i);
            for (int j = 0; j < vs.size(); j++) {
                Ruling v = vs.get(j);
                if (v.pos() >= h.start() - JOIN_TOLERANCE
                        && v.pos() <= h.end() + JOIN_TOLERANCE
                        && h.pos() >= v.start() - JOIN_TOLERANCE
                        && h.pos() <= v.end() + JOIN_TOLERANCE) {
                    groups.union(i, hs.size() + j);
                }
            }
        }
        Map<Integer, List<List<Ruling>>> byRoot = new TreeMap<>();
        for (int i = 0; i < hs.size() + vs.size(); i++) {
            boolean horizontal = i < hs.size();
            Ruling r = horizontal ? hs.get(i) : vs.get(i - hs.size());
            byRoot.computeIfAbsent(
                            groups.find(i), k -> List.of(new ArrayList<>(), new ArrayList<>()))
                    .get(horizontal ? 0 : 1)
                    .add(r);
        }
        return new ArrayList<>(byRoot.values());
    }

    /** {@code pageHs}: every horizontal rule on the page, which bounds how far up the table's heading may reach. */
    private static Found build(int page, List<Ruling> hs, List<Ruling> vs, List<PdfWord> words, List<FillBox> fills,
            List<Ruling> pageHs) {
        float[][] edges = gridEdges(hs, vs);
        float[] xs = edges[0];
        float[] ys = edges[1];
        if ((long) xs.length * ys.length > MAX_GRID_CELLS * 4L) {
            return null;
        }
        List<PdfWord> inside = new ArrayList<>();
        Box outline = new Box(xs[0], ys[0], xs[xs.length - 1], ys[ys.length - 1]);
        for (PdfWord w : words) {
            float cx = w.centreX();
            float cy = w.centreY();
            if (cx >= outline.left()
                    && cx <= outline.right()
                    && cy >= outline.top()
                    && cy <= outline.bottom()) {
                inside.add(w);
            }
        }
        List<PdfWord> header = amountsFirst(inside, ys) ? headerAbove(words, outline, fills, pageHs) : List.of();
        if (!header.isEmpty()) {
            float top = outline.top();
            for (PdfWord w : header) {
                top = Math.min(top, w.top() - 1f);
            }
            float[] grown = new float[ys.length + 1];
            grown[0] = top;
            System.arraycopy(ys, 0, grown, 1, ys.length);
            ys = grown;
            inside.addAll(header);
        }
        xs = dropThinBands(xs, inside, true);
        ys = dropThinBands(ys, inside, false);
        if (xs.length >= 3) {
            xs = addTextGutters(xs, inside, ys.length >= 3 ? ys[1] : Float.NaN);
        }
        if (inside.isEmpty()) {
            return null;
        }
        Box region = new Box(xs[0], ys[0], xs[xs.length - 1], ys[ys.length - 1]);
        if (xs.length < 3) {
            return new Found(null, region);
        }
        if ((long) (xs.length - 1) * (ys.length - 1) > MAX_GRID_CELLS) {
            return null;
        }
        RuledGrid grid = RuledGrid.measure(xs, ys, hs, vs, bandEdges(fills));
        grid.addTextColumnLines(inside);
        DraftTable table = cells(page, grid, inside);
        if (!looksLikeTable(table) || proseOutside(table, grid)) {
            return null;
        }
        return new Found(RuledBandSplitter.split(table), region);
    }

    private static final int MAX_HEADER_LINES = 4;

    /**
     * How far above the first rule a table's heading may end, in its own size: CMS's ends 0.4 of it above, while a
     * list or bulleted lines set over a table stood 0.87 and 1.1 above it.
     */
    private static final float HEADER_REACH = 0.8f;

    /**
     * The widest a heading's chunk may be, as a share of the table: a column's heading is a few words. A title or caption
     * set on the table's first rule ("Layers on Hand and Eggs Produced by Type and Molt – United States", NASS; "Table
     * NTR-1 - Summary of changes between …", NIST) runs across most of it.
     */
    private static final float HEADING_CHUNK = 0.4f;

    /** How far apart a heading's lines may stand, in their size: a heading stacks its words a line at a time. */
    private static final float HEADER_LEADING = 0.6f;

    /**
     * Column headings set above a table's first rule, as CMS's Medicare notices set "Benefit Days Used", "Claim
     * Approved?" and the rest in three right-aligned lines over a dotted rule: the lines directly above it, the nearest
     * within {@link #HEADER_REACH} and in two chunks or more, list marks left out, each further one close above the
     * last with each of its chunks over one of the last's, every one inside the table's width with nothing else on its
     * baseline. A caption or a line of prose above a table is one chunk, or a list item's text after its mark, or runs
     * past the table, or stands apart from the heading under it ("Referred by …" over the notice's headings), or spans
     * several of its columns ("Table 1: Visitors by month" over "Region" and "Q1").
     */
    private static List<PdfWord> headerAbove(List<PdfWord> words, Box outline, List<FillBox> fills,
            List<Ruling> pageHs) {
        float ceiling = ceiling(outline, pageHs);
        List<PdfWord> near = new ArrayList<>();
        for (PdfWord w : words) {
            if (w.centreY() < outline.top() && w.bottom() > outline.top() - 8 * w.fontSize() && w.top() > ceiling) {
                near.add(w);
            }
        }
        if (near.isEmpty()) {
            return List.of();
        }
        List<TextLine> lines = new ArrayList<>(TextLine.group(near));
        lines.sort((a, b) -> Float.compare(b.baseline(), a.baseline()));
        List<PdfWord> out = new ArrayList<>();
        float below = outline.top();
        ChunkedLine under = null;
        int taken = 0;
        for (TextLine l : lines) {
            List<PdfWord> text = new ArrayList<>();
            for (PdfWord w : l.words()) {
                boolean mark = ListMarkers.isBullet(w.text()) || text.isEmpty() && ListMarkers.isMarker(w.text());
                if (!mark) {
                    text.add(w);
                }
            }
            if (text.isEmpty()) {
                break;
            }
            ChunkedLine chunked = ChunkedLine.of(TextLine.of(text));
            float gap = below - l.bottom();
            boolean fits = l.x() >= outline.left() - 1 && l.right() <= outline.right() + 1;
            boolean close = gap <= (under == null ? HEADER_REACH : HEADER_LEADING) * l.fontSize();
            boolean narrow = true;
            for (List<PdfWord> chunk : chunked.chunks()) {
                narrow &= chunk.getLast().right() - chunk.getFirst().x() <= HEADING_CHUNK * (outline.right() - outline.left());
            }
            boolean shaped = narrow && (under == null ? chunked.tabular() : stacked(chunked, under));
            if (taken >= MAX_HEADER_LINES || !fits || !close || !shaped || banded(l, outline, fills)) {
                break;
            }
            out.addAll(l.words());
            below = l.top();
            under = chunked;
            taken++;
        }
        return out;
    }

    /** How much of a table's width a rule above it must span to end its heading's reach, as a share. */
    private static final float CEILING_SPAN = 0.5f;

    /**
     * The lowest rule above a table that spans half its width or more, or none: a heading never reaches past one, as the
     * rule closes what's over it. Two grids stacked 5 pt apart otherwise gave the second the first one's last row as its
     * heading (Codex's review); an underline under a heading's words spans less.
     */
    private static float ceiling(Box outline, List<Ruling> pageHs) {
        float ceiling = -Float.MAX_VALUE;
        float width = outline.right() - outline.left();
        for (Ruling r : pageHs) {
            float span = Math.min(r.end(), outline.right()) - Math.max(r.start(), outline.left());
            if (r.pos() < outline.top() - RuledGrid.LINE_TOLERANCE && span >= CEILING_SPAN * width) {
                ceiling = Math.max(ceiling, r.pos());
            }
        }
        return ceiling;
    }

    /** How much of a table's width the fills behind a heading's line cover when it's a band, as a share. */
    private static final float BAND_COVER = 0.8f;

    /**
     * Whether a heading's line stands on a filled band across the table: a header row drawn as a band (white on dark
     * blue, as in the generated ledgers) joins the table later with its fill (TableShaper.addBandHeader), which a row
     * taken here would lose. CMS's notices fill only one column's heading.
     */
    private static boolean banded(TextLine line, Box outline, List<FillBox> fills) {
        float mid = (line.top() + line.bottom()) / 2f;
        List<float[]> spans = new ArrayList<>();
        for (FillBox f : fills) {
            if (f.top() <= mid && f.bottom() >= mid && f.right() > outline.left() && f.x() < outline.right()) {
                spans.add(new float[] {Math.max(f.x(), outline.left()), Math.min(f.right(), outline.right())});
            }
        }
        spans.sort((a, b) -> Float.compare(a[0], b[0]));
        float covered = 0;
        float reach = -Float.MAX_VALUE;
        for (float[] sp : spans) {
            float from = Math.max(sp[0], reach);
            if (sp[1] > from) {
                covered += sp[1] - from;
                reach = sp[1];
            }
        }
        return covered >= BAND_COVER * (outline.right() - outline.left());
    }

    /** An amount: digits with a decimal point or thousands separators ("$4,886.98", "0.00"), not a year or a count. */
    private static final Pattern AMOUNT = Pattern.compile(
            "[-−(]?[$€£]?(?:\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+\\.\\d+)\\)?%?");

    /**
     * Whether a lattice's first band holds amounts, so it's a row of the table's body and the table's heading, if any,
     * stands above its first rule (CMS's claims). A first band of words is the table's own heading ("Item 2024 2025
     * percent" on NASS's tables), and what stands above the rule is a title.
     */
    private static boolean amountsFirst(List<PdfWord> inside, float[] ys) {
        if (ys.length < 2) {
            return false;
        }
        for (PdfWord w : inside) {
            if (w.centreY() > ys[0] && w.centreY() < ys[1] && AMOUNT.matcher(w.text()).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Whether each chunk of {@code line} stands over one chunk of the line {@code under} it, and only one. */
    private static boolean stacked(ChunkedLine line, ChunkedLine under) {
        for (List<PdfWord> chunk : line.chunks()) {
            float left = chunk.getFirst().x();
            float right = chunk.getLast().right();
            int over = 0;
            for (List<PdfWord> u : under.chunks()) {
                over += left < u.getLast().right() && right > u.getFirst().x() ? 1 : 0;
            }
            if (over != 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * The filled bands' top and bottom edges, which keep the rows on either side apart: neither finds a grid line
     * nor counts as a drawn border. A Closing Disclosure's section heading is a grey band with no rule under it,
     * so its cells were joined to the first item's: "A. Origination Charges" over "01 0.25 % of Loan Amount",
     * "$1,802.00" over "$405.00".
     */
    static List<Ruling> bandEdges(List<FillBox> fills) {
        List<Ruling> edges = new ArrayList<>(fills.size() * 2);
        for (FillBox f : fills) {
            edges.add(new Ruling(f.top(), f.x(), f.right(), 0f, f.rgb()));
            edges.add(new Ruling(f.bottom(), f.x(), f.right(), 0f, f.rgb()));
        }
        return edges;
    }

    private static float[][] gridEdges(List<Ruling> hs, List<Ruling> vs) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        List<Float> xPositions = new ArrayList<>();
        List<Float> yPositions = new ArrayList<>();
        for (Ruling v : vs) {
            xPositions.add(v.pos());
            top = Math.min(top, v.start());
            bottom = Math.max(bottom, v.end());
        }
        for (Ruling h : hs) {
            yPositions.add(h.pos());
            left = Math.min(left, h.start());
            right = Math.max(right, h.end());
        }
        xPositions.add(left);
        xPositions.add(right);
        yPositions.add(top);
        yPositions.add(bottom);
        return new float[][] {
            Rulings.cluster(xPositions, RuledGrid.LINE_TOLERANCE),
            Rulings.cluster(yPositions, RuledGrid.LINE_TOLERANCE)
        };
    }

    private static DraftTable cells(int page, RuledGrid grid, List<PdfWord> words) {
        int rows = grid.rows();
        int cols = grid.cols();
        int[][] anchor = grid.anchors();
        List<List<PdfWord>> wordsByAnchor = new ArrayList<>(rows * cols);
        for (int i = 0; i < rows * cols; i++) {
            wordsByAnchor.add(new ArrayList<>());
        }
        for (PdfWord w : words) {
            int r = grid.rowAt(w.centreY());
            for (PdfWord piece : splitAtBorders(w, grid.xs, grid.rulesInRow(r))) {
                wordsByAnchor.get(anchor[r][grid.colAt(piece.centreX())]).add(piece);
            }
        }
        List<DraftTable.Cell> cells = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int a = anchor[r][c];
                if (a != r * cols + c) {
                    continue;
                }
                int r1 = r;
                int c1 = c;
                while (r1 + 1 < rows && anchor[r1 + 1][c] == a) {
                    r1++;
                }
                while (c1 + 1 < cols && anchor[r][c1 + 1] == a) {
                    c1++;
                }
                Box box = new Box(grid.xs[c], grid.ys[r], grid.xs[c1 + 1], grid.ys[r1 + 1]);
                cells.add(
                        new DraftTable.Cell(
                                r,
                                c,
                                r1 - r + 1,
                                c1 - c + 1,
                                box,
                                box.width(),
                                grid.borders(r, c, r1, c1),
                                wordsByAnchor.get(a)));
            }
        }
        return new DraftTable(page, rows, cols, grid.xs, true, cells, 0);
    }

    private static List<PdfWord> splitAtBorders(PdfWord w, float[] xs, boolean[] drawn) {
        List<PdfWord> pieces = new ArrayList<>();
        int from = 0;
        int n = w.glyphLeft().length;
        for (int c = 1; c < xs.length - 1; c++) {
            if (!drawn[c] || xs[c] <= w.x() + 0.5f || xs[c] >= w.right() - 0.5f) {
                continue;
            }
            int cut = from;
            while (cut < n && (w.glyphLeft()[cut] + w.glyphRight()[cut]) / 2f < xs[c]) {
                cut++;
            }
            PdfWord piece = w.slice(from, cut);
            if (piece != null) {
                pieces.add(piece);
            }
            from = cut;
        }
        if (from == 0) {
            return List.of(w);
        }
        PdfWord rest = w.slice(from, n);
        if (rest != null) {
            pieces.add(rest);
        }
        return pieces;
    }

    private static boolean proseOutside(DraftTable table, RuledGrid grid) {
        for (int[] slot : grid.openOutline()) {
            for (DraftTable.Cell cell : table.cells()) {
                boolean covers = slot[0] >= cell.row() && slot[0] < cell.row() + cell.rowSpan()
                        && slot[1] >= cell.col() && slot[1] < cell.col() + cell.colSpan();
                if (covers && running(cell.words())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean running(List<PdfWord> words) {
        java.util.Set<Integer> lines = new java.util.HashSet<>();
        int real = 0;
        for (PdfWord w : words) {
            if (w.text().codePoints().filter(Character::isLetter).count() >= 2) {
                real++;
                lines.add(Math.round(w.centreY() / 2f));
            }
        }
        return real > PARAGRAPH_WORDS && real >= PROSE_LINE_WORDS * lines.size();
    }

    private static final int PROSE_LINE_WORDS = 6;

    private static boolean looksLikeTable(DraftTable table) {
        int filled = 0;
        int paragraphs = 0;
        for (DraftTable.Cell cell : table.cells()) {
            if (!cell.words().isEmpty()) {
                filled++;
                if (cell.words().size() > PARAGRAPH_WORDS) {
                    paragraphs++;
                }
            }
        }
        boolean layoutBoxes = filled > 0 && paragraphs * 2 > filled && !labelColumn(table);
        return filled >= 2
                && table.cells().size() >= 2
                && !layoutBoxes
                && !looksLikeChart(table, filled);
    }

    private static boolean labelColumn(DraftTable table) {
        float[] e = table.colEdges();
        float width = e[e.length - 1] - e[0];
        for (int c = 0; c + 1 < e.length; c++) {
            if (e[c + 1] - e[c] > LABEL_COLUMN * width) {
                continue;
            }
            int labels = 0;
            boolean shortOnly = true;
            for (DraftTable.Cell cell : table.cells()) {
                if (cell.col() == c && cell.colSpan() == 1 && !cell.words().isEmpty()) {
                    labels++;
                    shortOnly &= cell.words().size() <= LABEL_WORDS;
                }
            }
            if (labels > 0 && shortOnly) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeChart(DraftTable table, int filled) {
        int anchors = table.cells().size();
        if (anchors < 12 || filled * 100 >= anchors * 35) {
            return false;
        }
        boolean[] mergedRow = new boolean[table.rows()];
        for (DraftTable.Cell cell : table.cells()) {
            if (cell.colSpan() > 1 && cell.colSpan() < table.cols()) {
                mergedRow[cell.row()] = true;
            }
        }
        int merged = 0;
        for (boolean m : mergedRow) {
            if (m) {
                merged++;
            }
        }
        return filled * 5 < anchors || merged * 2 > table.rows();
    }

    private static float[] addTextGutters(float[] xs, List<PdfWord> words, float bodyTop) {
        List<List<PdfWord>> columns = new ArrayList<>();
        for (int c = 0; c + 1 < xs.length; c++) {
            columns.add(new ArrayList<>());
        }
        for (PdfWord w : words) {
            columns.get(RuledGrid.band(xs, w.centreX())).add(w);
        }
        List<Float> result = new ArrayList<>();
        for (int c = 0; c + 1 < xs.length; c++) {
            result.add(xs[c]);
            result.addAll(gutterPositions(columns.get(c), xs[c], xs[c + 1], bodyTop));
        }
        result.add(xs[xs.length - 1]);
        return toArray(result);
    }

    private static List<Float> gutterPositions(List<PdfWord> words, float left, float right, float bodyTop) {
        List<PdfWord> text = new ArrayList<>();
        for (PdfWord w : words) {
            if (!ListMarkers.isBullet(w.text())) {
                text.add(w);
            }
        }
        List<ChunkedLine> lines = new ArrayList<>();
        List<ChunkedLine> body = new ArrayList<>();
        int tabular = 0;
        int bodyTabular = 0;
        for (TextLine line : TextLine.group(text)) {
            ChunkedLine chunked = ChunkedLine.of(line);
            lines.add(chunked);
            tabular += chunked.tabular() ? 1 : 0;
            if ((line.top() + line.bottom()) / 2f > bodyTop) {
                body.add(chunked);
                bodyTabular += chunked.tabular() ? 1 : 0;
            }
        }
        if (tabular < 3 || tabular * 2 < lines.size()) {
            return List.of();
        }
        // The rows under the heading's band place the gutters when there are enough of them: a heading set right-aligned
        // over two lines reaches left past its column's figures, over the gutter ("Approved?" over "Destruction of
        // skin growth (17000)" on CMS's Part B notice), and its words then fall in by their centres
        boolean byBody = bodyTabular >= 3 && body.size() < lines.size() && bodyTabular * 2 >= body.size();
        TextColumns columns = TextColumns.find(byBody ? body : lines);
        float[] edges = columns.edges();
        List<Float> gutters = new ArrayList<>();
        for (int c = 1; c < columns.count(); c++) {
            if (edges[c] > left + 2f && edges[c] < right - 2f) {
                gutters.add(edges[c]);
            }
        }
        return gutters;
    }

    private static float[] dropThinBands(float[] edges, List<PdfWord> words, boolean vertical) {
        float[] centres = new float[words.size()];
        for (int i = 0; i < centres.length; i++) {
            centres[i] = vertical ? words.get(i).centreX() : words.get(i).centreY();
        }
        Arrays.sort(centres);
        List<Float> kept = new ArrayList<>();
        for (float e : edges) {
            kept.add(e);
        }
        int i = 0;
        while (i + 1 < kept.size() && kept.size() > 2) {
            float a = kept.get(i);
            float b = kept.get(i + 1);
            if (b - a >= MIN_BAND || hasCentreBetween(centres, a, b)) {
                i++;
                continue;
            }
            if (i == 0) {
                kept.remove(i + 1);
            } else if (i + 1 == kept.size() - 1) {
                kept.remove(i);
                i--;
            } else {
                kept.set(i, (a + b) / 2f);
                kept.remove(i + 1);
                i--;
            }
            i = Math.max(i, 0);
        }
        return toArray(kept);
    }

    private static boolean hasCentreBetween(float[] sortedCentres, float from, float to) {
        int idx = Arrays.binarySearch(sortedCentres, from);
        idx = idx < 0 ? -idx - 1 : idx;
        while (idx < sortedCentres.length && sortedCentres[idx] <= from) {
            idx++;
        }
        return idx < sortedCentres.length && sortedCentres[idx] < to;
    }

    private static float[] toArray(List<Float> values) {
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
