package stirling.software.officeconvert.build;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;

import stirling.software.officeconvert.Pictures;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageData;
import stirling.software.officeconvert.layout.Box;
import stirling.software.officeconvert.layout.DocStats;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.LogicalOrder;
import stirling.software.officeconvert.layout.PageLayout;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.model.Block;
import stirling.software.officeconvert.model.Numbering;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.Section;
import stirling.software.officeconvert.model.StyleSheet;
import stirling.software.officeconvert.model.Table;

public final class PlacedContent {

    public interface MediaStore {

        Picture.MediaRef media(Object key);

        Picture.MediaRef media(byte[] bytes, String ext, int pixelWidth, int pixelHeight, Object key) throws IOException;
    }

    private final RunBuilder runs;
    private final TableBuilder tables;
    private final MediaPlacer placer;
    private final DocStats stats;

    public PlacedContent(PDDocument document, DocStats stats, MediaStore store, float figureDpi, boolean dropHyphens) {
        this(document, stats, store, figureDpi, dropHyphens, Pictures.COMPACT);
    }

    public PlacedContent(PDDocument document, DocStats stats, MediaStore store, float figureDpi, boolean dropHyphens,
            Pictures pictures) {
        DocSink sink = new StoreSink(store);
        this.stats = stats;
        this.runs = new RunBuilder(dropHyphens, stats);
        runs.icons(new IconPictures(sink));
        RunStyle normal = new RunStyle(stats.bodyFont.family(), DocumentBuilder.round(stats.bodySize), false, false,
                false, false, 0, -1, 0, false);
        ParagraphFactory paragraphs = new ParagraphFactory(stats, runs, new StyleSheet(normal), new Numbering());
        this.tables = new TableBuilder(paragraphs);
        this.placer = new MediaPlacer(document, figureDpi, pictures, sink, paragraphs);
    }

    public Paragraph runs(ParaDraft d, int skipWords, float left, float right) {
        Paragraph p = new Paragraph();
        p.bidi = bidi(d);
        runs.fill(p, d.lines, skipWords, left, right, d.size(), d.hardBreaks, d.pageBreaks);
        return p;
    }

    /** Whether a paragraph reads right to left, as {@link #runs} sets it. */
    public boolean bidi(ParaDraft d) {
        int rtl = 0;
        boolean anyRtl = false;
        for (Line l : d.lines) {
            rtl += LogicalOrder.rtlBase(l) ? 1 : 0;
            anyRtl |= LogicalOrder.hasRtl(l);
        }
        return d.rtl || rtl * 2 > d.lines.size() || anyRtl && d.align == Paragraph.Align.RIGHT && stats.scripts.rightToLeft();
    }

    /**
     * Where a slide's text box wraps a paragraph's lines: at {@code wrap}, its lines starting at {@code firstStart} and
     * {@code restStart}, and never past {@code limit}, the box's text edge.
     */
    public record Wrap(float wrap, float firstStart, float restStart, float limit) {}

    /**
     * The paragraph's runs fitted to its PDF lines in a slide's text box, as the DOCX writer fits them to a page: the
     * wrap edge each line's break holds at, extra character spacing where a ragged line would take the next one's first
     * word, and a justified paragraph's lines narrowed one by one where no edge holds them all. {@link #fittedWrap()}
     * then gives the edge to wrap at, or NaN where it stays {@code at.wrap()}. A left-aligned or justified paragraph
     * written left to right only.
     */
    public Paragraph runs(ParaDraft d, int skipWords, float left, float right, Paragraph.Align align, Wrap at) {
        lastEdge = Float.NaN;
        Paragraph p = new Paragraph();
        p.bidi = bidi(d);
        boolean justified = align == Paragraph.Align.JUSTIFY && !Float.isNaN(d.justifySlack);
        RunBuilder.Fit fit = null;
        if (!p.bidi && skipWords == 0 && (justified || align == Paragraph.Align.LEFT)) {
            float edge = at.wrap() - SectionPlanner.RIGHT_SLACK;
            fit = new RunBuilder.Fit(edge, justified ? d.justifySlack : SectionPlanner.RIGHT_SLACK, justified, justified,
                    at.firstStart(), at.restStart(), 0, at.limit() - edge, true);
            lastEdge = edge;
        }
        runs.fill(p, d.lines, skipWords, left, right, d.size(), d.hardBreaks, d.pageBreaks, false, fit);
        return p;
    }

    /** The wrap edge the last fitted paragraph's lines hold at, or NaN. */
    public float fittedWrap() {
        return Float.isNaN(lastEdge) || Float.isNaN(runs.fitted()) ? Float.NaN : lastEdge + runs.fitted();
    }

    private float lastEdge = Float.NaN;

    public static RunStyle style(Glyph g, float hostSize) {
        return RunBuilder.styleOf(g, hostSize);
    }

    public Picture picture(PageLayout.Item item, PageLayout layout, AffineTransform toDisplay) throws IOException {
        return placer.picture(item, layout, toDisplay);
    }

    public Picture region(Box box, PageData page, AffineTransform toDisplay, boolean withText) throws IOException {
        return placer.figurePicture(box, page, toDisplay, withText);
    }

    public Table table(PageLayout.TableItem item, PageLayout layout) {
        return tables.build(item, layout, item.x(), item.right());
    }

    public void endPage() {
        placer.endPage();
    }

    private record StoreSink(MediaStore store) implements DocSink {

        @Override
        public Picture.MediaRef media(byte[] bytes, String ext, int pixelWidth, int pixelHeight, Object key)
                throws IOException {
            return store.media(bytes, ext, pixelWidth, pixelHeight, key);
        }

        @Override
        public Picture.MediaRef media(Object key) {
            return store.media(key);
        }

        @Override
        public void begin(StyleSheet styles, HeaderFooterSet running) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void block(Block block) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void footnote(int id, List<Paragraph> paragraphs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finish(Section last, HeaderFooterSet running, Numbering numbering, StyleSheet styles, String title,
                String author) {
            throw new UnsupportedOperationException();
        }
    }
}
