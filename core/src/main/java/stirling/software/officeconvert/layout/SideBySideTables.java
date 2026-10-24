package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Two tables side by side in a one-column band, as a form's header sets its key-and-value blocks, become a band of two
 * columns over their rows. In one column they were emitted one after the other, and Word, which can't set two tables
 * beside each other in the flow, put the second under the first: the CFPB loan estimate's applicant block went under its
 * loan terms, 70 pt lower, and its first page ran over onto a second. The ColumnFinder doesn't see them as columns: their
 * rows share baselines, as one table's rows would.
 */
final class SideBySideTables {

    /** The least share of the shorter table's height two tables must share to stand side by side. */
    private static final float LEVEL = 0.5f;

    private SideBySideTables() {}

    /** The bands with each one-column band holding two tables side by side split around them. */
    static List<ColumnFinder.Band> split(List<ColumnFinder.Band> bands, List<PageLayout.Item> blocks, List<Line> lines,
            float minGutter) {
        List<ColumnFinder.Band> out = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            ColumnFinder.Band band = bands.get(i);
            List<ColumnFinder.Band> pieces = band.multiColumn() ? null
                    : split(band, i == bands.size() - 1, blocks, lines, minGutter);
            if (pieces == null) {
                out.add(band);
            } else {
                out.addAll(pieces);
            }
        }
        return out;
    }

    private static List<ColumnFinder.Band> split(ColumnFinder.Band band, boolean last, List<PageLayout.Item> blocks,
            List<Line> lines, float minGutter) {
        List<PageLayout.Item> tables = new ArrayList<>();
        List<float[]> boxes = new ArrayList<>();
        for (PageLayout.Item b : blocks) {
            if (in(band, (b.top() + b.bottom()) / 2f, last) && !b.behindText()) {
                boxes.add(new float[] {b.x(), b.top(), b.right(), b.bottom()});
                if (b instanceof PageLayout.TableItem) {
                    tables.add(b);
                }
            }
        }
        for (Line l : lines) {
            if (in(band, l.baseline, last)) {
                boxes.add(new float[] {l.x, l.top, l.right, l.bottom});
            }
        }
        for (PageLayout.Item a : tables) {
            for (PageLayout.Item b : tables) {
                if (a == b || a.right() + minGutter > b.x()) {
                    continue;
                }
                float shared = Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top());
                if (shared < LEVEL * Math.min(a.bottom() - a.top(), b.bottom() - b.top())) {
                    continue;
                }
                List<ColumnFinder.Band> pieces = around(band, a, b, boxes);
                if (pieces != null) {
                    return pieces;
                }
            }
        }
        return null;
    }

    /**
     * The band split above and below the rows of {@code left} and {@code right}, at the nearest things above and below them
     * that cross the gap between them, or null when something crosses it beside them.
     */
    private static List<ColumnFinder.Band> around(ColumnFinder.Band band, PageLayout.Item left, PageLayout.Item right,
            List<float[]> boxes) {
        float gl = left.right();
        float gr = right.x();
        float rowsTop = Math.min(left.top(), right.top());
        float rowsBottom = Math.max(left.bottom(), right.bottom());
        float top = band.top();
        float bottom = band.bottom();
        for (float[] r : boxes) {
            if (!(r[0] < gr && r[2] > gl)) {
                continue;
            }
            if (r[3] <= rowsTop + 0.5f) {
                top = Math.max(top, r[3]);
            } else if (r[1] >= rowsBottom - 0.5f) {
                bottom = Math.min(bottom, r[1]);
            } else {
                return null;
            }
        }
        float[] c = band.columns().getFirst();
        List<ColumnFinder.Band> out = new ArrayList<>();
        if (top > band.top()) {
            out.add(new ColumnFinder.Band(band.top(), top, band.columns()));
        }
        out.add(new ColumnFinder.Band(top, bottom, List.of(new float[] {c[0], gl}, new float[] {gr, c[1]})));
        if (bottom < band.bottom()) {
            out.add(new ColumnFinder.Band(bottom, band.bottom(), band.columns()));
        }
        return out;
    }

    private static boolean in(ColumnFinder.Band band, float y, boolean last) {
        return y >= band.top() && y < band.bottom() || last && y >= band.bottom();
    }
}
