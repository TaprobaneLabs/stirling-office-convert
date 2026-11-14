package stirling.software.officeconvert.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import stirling.software.officeconvert.extract.PageGraphics;
import stirling.software.officeconvert.table.TableDetection;

public final class LayoutDump {

    private LayoutDump() {}

    public static String dump(PageLayout layout) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "=== page %d (%.0fx%.0f dir %d)%n", layout.page().index() + 1,
                layout.page().width(), layout.page().height(), layout.page().direction()));
        for (PageLayout.Band band : layout.bands()) {
            sb.append(String.format(Locale.ROOT, " band %.1f..%.1f cols=%d%n", band.top(), band.bottom(), band.columns().size()));
            for (PageLayout.Column col : band.columns()) {
                sb.append(String.format(Locale.ROOT, "  col %.1f..%.1f%n", col.left(), col.right()));
                for (PageLayout.Item it : col.items()) {
                    sb.append(String.format(Locale.ROOT, "   [%6.1f %6.1f %6.1f %6.1f] ", it.x(), it.top(), it.right(), it.bottom()));
                    switch (it) {
                        case PageLayout.ParaItem p -> sb.append(String.format(Locale.ROOT, "PARA %s %s l=%.1f f=%.1f r=%.1f n=%d | %s",
                                p.para().role, p.para().align, p.para().left, p.para().first, p.para().right,
                                p.para().lines.size(), clip(p.para().text())));
                        case PageLayout.TableItem t -> {
                            TableDetection.Found f = t.table();
                            sb.append(String.format(Locale.ROOT, "TABLE %dx%d ruled=%s", f.rows(), f.cols(), f.ruled()));
                            for (TableDetection.FoundCell c : f.cells()) {
                                sb.append(String.format(Locale.ROOT, "%n      (%d,%d %dx%d) %s", c.row(), c.col(), c.rowSpan(), c.colSpan(), clip(c.text())));
                            }
                        }
                        case PageLayout.ImageItem i -> sb.append("IMAGE bg=" + i.background());
                        case PageLayout.FigureItem f -> sb.append("FIGURE");
                        case PageLayout.FloatItem f -> sb.append("FLOAT gap=" + f.gap());
                        case PageLayout.PictureRow r -> sb.append("PICTURE ROW x" + r.pictures().size());
                        case PageLayout.TextBoxItem t -> sb.append("TEXTBOX paras=" + t.paras().size());
                    }
                    sb.append('\n');
                }
            }
        }
        for (PageLayout.Veil v : layout.veils()) {
            veil(sb, v, layout);
        }
        return sb.toString();
    }

    /**
     * One line per veil: its box, how many of its marks are curved, round, boxy or pattern-filled, the page's text lines
     * its box holds whole and those whose middle it holds, and the first of them; a veil of one mark also gets the mark.
     */
    private static void veil(StringBuilder sb, PageLayout.Veil v, PageLayout layout) {
        Box b = v.box();
        int whole = 0;
        int centred = 0;
        String first = "";
        for (Line l : lines(layout)) {
            float cx = (l.x + l.right) / 2f;
            float cy = (l.top + l.bottom) / 2f;
            if (!b.contains(cx, cy)) {
                continue;
            }
            centred++;
            if (l.x >= b.x() && l.right <= b.right() && l.top >= b.top() && l.bottom <= b.bottom()) {
                whole++;
            }
            if (first.isEmpty()) {
                first = l.text();
            }
        }
        long curved = v.marks().stream().filter(PageGraphics.VectorMark::curved).count();
        long round = v.marks().stream().filter(PageGraphics.VectorMark::round).count();
        long boxy = v.marks().stream().filter(PageGraphics.VectorMark::boxy).count();
        long pattern = v.marks().stream().filter(PageGraphics.VectorMark::shading).count();
        sb.append(String.format(Locale.ROOT, " VEIL [%6.1f %6.1f %6.1f %6.1f] marks=%d curved=%d round=%d boxy=%d pattern=%d"
                + " lines=%d/%d", b.x(), b.top(), b.right(), b.bottom(), v.marks().size(), curved, round, boxy, pattern, whole,
                centred));
        if (v.marks().size() == 1) {
            PageGraphics.VectorMark m = v.marks().getFirst();
            sb.append(String.format(Locale.ROOT, " | seg=%d fill=%s/%06x stroke=%s/%06x lw=%.2f diag=%s sparse=%s",
                    m.segments(), m.filled(), m.rgb() & 0xffffff, m.stroked(), m.strokeRgb() & 0xffffff, m.lineWidth(),
                    m.diagonal(), m.sparse()));
        }
        sb.append(" | ").append(clip(first)).append('\n');
    }

    private static List<Line> lines(PageLayout layout) {
        List<Line> out = new ArrayList<>();
        for (PageLayout.Band band : layout.bands()) {
            for (PageLayout.Column col : band.columns()) {
                for (PageLayout.Item it : col.items()) {
                    switch (it) {
                        case PageLayout.ParaItem p -> out.addAll(p.para().lines);
                        case PageLayout.TextBoxItem t -> t.paras().forEach(d -> out.addAll(d.lines));
                        default -> {}
                    }
                }
            }
        }
        for (PageLayout.TextBoxItem t : layout.furniture()) {
            t.paras().forEach(d -> out.addAll(d.lines));
        }
        return out;
    }

    private static String clip(String s) {
        s = s.replace('\n', '/');
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }
}
