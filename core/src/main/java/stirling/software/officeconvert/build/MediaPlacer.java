package stirling.software.officeconvert.build;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import stirling.software.officeconvert.Pictures;
import stirling.software.officeconvert.extract.PageData;
import stirling.software.officeconvert.extract.PageGraphics.ImageDraw;
import stirling.software.officeconvert.extract.PageGraphics;
import stirling.software.officeconvert.layout.Box;
import stirling.software.officeconvert.layout.PageLayout;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture;
import stirling.software.officeconvert.model.Stacking;

final class MediaPlacer {

    private final PDDocument document;
    private final MediaEncoder media;
    private final DocSink sink;
    private final ParagraphFactory paragraphs;

    MediaPlacer(PDDocument document, float figureDpi, Pictures pictures, DocSink sink, ParagraphFactory paragraphs) {
        this.document = document;
        this.media = new MediaEncoder(document, figureDpi, pictures);
        this.sink = sink;
        this.paragraphs = paragraphs;
    }

    Inline.TextBox textBox(PageLayout.TextBoxItem tb) {
        List<Paragraph> paras = new ArrayList<>();
        float prev = tb.box().top();
        // Where the output sets the box's text: at its text edges, unless a rounded box's own rounding reaches further in
        Box b = tb.box();
        float rounding = tb.rounded() ? Stacking.roundedTextInset(b.width(), b.height()) : 0f;
        float textLeft = Math.max(tb.textLeft(), b.x() + rounding);
        float textRight = Math.min(tb.textRight(), b.right() - rounding);
        for (ParaDraft d : tb.paras()) {
            float sb = Math.max(0, paragraphs.wordTop(d) - prev);
            paras.add(tb.turn() == null ? paragraphs.boxed(d, textLeft, textRight, sb)
                    : paragraphs.detached(d, tb.textLeft(), tb.textRight(), sb));
            prev = paragraphs.wordBottom(d);
        }
        if (tb.turn() != null) {
            Box on = tb.turn().onPage();
            return new Inline.TextBox(on.x(), on.top(), on.width(), on.height(), tb.fillRgb(), 0, 0, tb.gap(), paras,
                    tb.lineRgb(), tb.lineWidth(), tb.rounded(), tb.overlay(), tb.turn().direction(), tb.groundRgb(),
                    tb.turn().upright());
        }
        return new Inline.TextBox(b.x(), b.top(), b.width(), b.height(), tb.fillRgb(),
                tb.textLeft() - b.x(), Math.max(0, b.right() - tb.textRight()), tb.gap(), paras,
                tb.lineRgb(), tb.lineWidth(), tb.rounded(), tb.overlay(), 0, tb.groundRgb(), false);
    }

    void endPage() {
        media.endPage();
    }

    private static Object mediaKey(ImageDraw d) {
        if (d.key() == null) {
            return new Object();
        }
        int opacity = Math.round(d.alpha() * 255);
        return d.stencilRgb() >= 0 || opacity < 255 ? List.of(d.key(), d.stencilRgb(), opacity) : d.key();
    }

    Picture picture(PageLayout.Item item, PageLayout layout, java.awt.geom.AffineTransform toDisplay)
            throws IOException {
        PageData page = layout.page();
        if (item instanceof PageLayout.ImageItem im) {
            ImageDraw d = im.draw();
            if (d.skewed()) {
                return figurePicture(new Box(d.clipX(), d.clipTop(), d.clipRight(), d.clipBottom()), page, toDisplay, false);
            }
            Object key = mediaKey(d);
            Picture.MediaRef ref = sink.media(key);
            if (ref == null) {
                MediaEncoder.Encoded enc = media.image(d);
                if (enc == null) {
                    return null;
                }
                ref = sink.media(enc.bytes(), enc.ext(), enc.width(), enc.height(), key);
            }
            boolean quarter = d.quarterTurns() % 2 == 1;
            float fullW = d.right() - d.x();
            float fullH = d.bottom() - d.top();
            Picture pic = new Picture(ref, quarter ? fullH : fullW, quarter ? fullW : fullH);
            int order = page.graphics().order(d);
            pic.paintOrder = order == Integer.MAX_VALUE ? -1 : order;
            pic.rotation = d.quarterTurns() * 90;
            pic.flipH = d.flipH();
            if (d.quarterTurns() == 0 && !d.flipH()) {
                pic.cropLeft = clamp((d.clipX() - d.x()) / fullW);
                pic.cropRight = clamp((d.right() - d.clipRight()) / fullW);
                pic.cropTop = clamp((d.clipTop() - d.top()) / fullH);
                pic.cropBottom = clamp((d.bottom() - d.clipBottom()) / fullH);
                pic.width = d.clipRight() - d.clipX();
                pic.height = d.clipBottom() - d.clipTop();
            }
            return pic;
        }
        if (item instanceof PageLayout.FigureItem fi && fi.paper()) {
            MediaEncoder.Encoded enc = media.paper(document.getPage(page.index()), page.index(), toDisplay, fi.box());
            Picture pic = enc == null ? null : stored(enc, fi.box());
            if (pic != null && fi.order() != Integer.MAX_VALUE) {
                pic.paintOrder = fi.order();
            }
            return pic;
        }
        if (item instanceof PageLayout.FigureItem fi && fi.ground()) {
            MediaEncoder.Encoded enc = media.ground(document.getPage(page.index()), page.index(), toDisplay, fi.box(),
                    placedAbove(fi, page, toDisplay));
            Picture pic = enc == null ? null : stored(enc, fi.box());
            if (pic != null) {
                pic.paintOrder = fi.order();
            }
            return pic;
        }
        if (item instanceof PageLayout.FigureItem fi) {
            Picture pic = figurePicture(fi.box(), page, toDisplay, !fi.backdrop());
            if (pic != null && !fi.backdrop()) {
                pic.description = FigureWords.text(page, fi.box());
            }
            return pic;
        }
        return null;
    }

    /**
     * The images a ground's picture leaves out, by their bounds on the PDF page: those painted after its gradient that
     * cross its edge. They are placed on their own above it ({@code PageAnalyzer.addImages} keeps only images inside a
     * drawn region in its picture), so drawn in it too, a translucent one would be painted twice where they overlap.
     * Images painted before it stay in: the picture is opaque and covers their own placing.
     */
    private static List<java.awt.geom.Rectangle2D> placedAbove(PageLayout.FigureItem ground, PageData page,
            java.awt.geom.AffineTransform toDisplay) throws IOException {
        Box g = ground.box();
        List<java.awt.geom.Rectangle2D> above = new ArrayList<>();
        try {
            java.awt.geom.AffineTransform toPdf = toDisplay.createInverse();
            for (ImageDraw d : page.graphics().images()) {
                Box shown = new Box(d.clipX(), d.clipTop(), d.clipRight(), d.clipBottom());
                boolean inside = shown.x() >= g.x() - 2 && shown.top() >= g.top() - 2 && shown.right() <= g.right() + 2
                        && shown.bottom() <= g.bottom() + 2;
                int order = page.graphics().order(d);
                if (!inside && shown.overlapArea(g) > 0 && order != Integer.MAX_VALUE && order > ground.order()) {
                    above.add(toPdf.createTransformedShape(new java.awt.geom.Rectangle2D.Float(d.x(), d.top(),
                            d.right() - d.x(), d.bottom() - d.top())).getBounds2D());
                }
            }
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            throw new IOException("The page's transform can't be inverted", e);
        }
        return above;
    }

    Picture veil(PageLayout.Veil veil, PageData page, List<Box> shown) throws IOException {
        List<PageGraphics.Outline> outlines = new ArrayList<>();
        for (PageGraphics.VectorMark m : veil.marks()) {
            PageGraphics.Outline o = page.graphics().outline(m);
            if (o != null) {
                outlines.add(o);
            }
        }
        MediaEncoder.Encoded enc = outlines.isEmpty() ? null : media.veil(outlines, veil.box(), shown);
        if (enc == null) {
            return null;
        }
        String key = "veil:" + digest(enc.bytes());
        Picture.MediaRef ref = sink.media(key);
        if (ref == null) {
            ref = sink.media(enc.bytes(), enc.ext(), enc.width(), enc.height(), key);
        }
        Picture pic = new Picture(ref, veil.box().width(), veil.box().height());
        pic.paintOrder = veil.order() == Integer.MAX_VALUE ? -1 : veil.order();
        return pic;
    }

    /**
     * A figure drawn without its text, for its labels to be set over it as text. Turned text in it isn't among its labels,
     * so the picture draws it.
     */
    Picture unlabelled(PageLayout.FigureItem fi, PageData page, java.awt.geom.AffineTransform toDisplay) throws IOException {
        Box area = fi.box().grow(1f);
        boolean turned = page.rotated().stream().anyMatch(g -> area.contains(g.x, g.baseline));
        PDPage pdPage = document.getPage(page.index());
        MediaEncoder.Encoded enc = media.figure(pdPage, page.index(), toDisplay, fi.box(), false, turned ? page.direction() : -1);
        Picture pic = enc == null ? null : stored(enc, fi.box());
        if (pic != null) {
            pic.description = FigureWords.text(page, fi.box());
        }
        return pic;
    }

    Picture figurePicture(Box box, PageData page, java.awt.geom.AffineTransform toDisplay, boolean withText)
            throws IOException {
        PDPage pdPage = document.getPage(page.index());
        MediaEncoder.Encoded enc = media.figure(pdPage, page.index(), toDisplay, box, withText);
        return enc == null ? null : stored(enc, box);
    }

    private Picture stored(MediaEncoder.Encoded enc, Box box) throws IOException {
        String key = "figure:" + digest(enc.bytes());
        Picture.MediaRef ref = sink.media(key);
        if (ref == null) {
            ref = sink.media(enc.bytes(), enc.ext(), enc.width(), enc.height(), key);
        }
        return new Picture(ref, box.width(), box.height());
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static float clamp(float v) {
        return v < 0.002f ? 0 : Math.min(v, 0.95f);
    }
}
