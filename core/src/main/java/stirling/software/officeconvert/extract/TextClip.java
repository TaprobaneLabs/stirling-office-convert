package stirling.software.officeconvert.extract;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorProcessor;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.util.Matrix;

/**
 * Clipping for the text pass: the path operators a {@link PDFStreamEngine} reading only text ignores, kept just far enough
 * to intersect the graphics state's clipping path, and a test for glyphs the clip hides whole. USGS Fact Sheet 2023-3022
 * places a figure whose file carries a label, "AAXXXX_fig 01", below the rectangle the page clips the figure to: never
 * shown, it was read as the last words of the paragraph it lies over and cost Word a line.
 */
final class TextClip {

    /** How far outside a clip's bounds a glyph's box may reach and still count as shown, in points. */
    private static final float SLACK = 1f;
    /** A glyph's box, in ems from its baseline: below for descenders, above for ascenders and accents. */
    private static final float BELOW = 0.3f;
    private static final float ABOVE = 1.1f;

    private final PDFStreamEngine engine;
    private final ClipPath path = new ClipPath();
    private List<Path2D> lastClip;
    private int lastClipCount;
    private Rectangle2D lastBounds;

    private TextClip(PDFStreamEngine engine) {
        this.engine = engine;
    }

    /** Registers the path and clipping operators on an engine that reads text, and returns the clip to ask. */
    static TextClip install(PDFStreamEngine engine, Registrar registrar) {
        TextClip clip = new TextClip(engine);
        registrar.add(clip.op("re", 4, (s, v) -> {
            Point2D p0 = point(s, v[0], v[1]);
            Point2D p1 = point(s, v[0] + v[2], v[1]);
            Point2D p2 = point(s, v[0] + v[2], v[1] + v[3]);
            Point2D p3 = point(s, v[0], v[1] + v[3]);
            clip.path.rectangle((float) p0.getX(), (float) p0.getY(), (float) p1.getX(), (float) p1.getY(),
                    (float) p2.getX(), (float) p2.getY(), (float) p3.getX(), (float) p3.getY());
        }));
        registrar.add(clip.op("m", 2, (s, v) -> {
            Point2D p = point(s, v[0], v[1]);
            clip.path.moveTo((float) p.getX(), (float) p.getY());
        }));
        registrar.add(clip.op("l", 2, (s, v) -> {
            Point2D p = point(s, v[0], v[1]);
            clip.path.lineTo((float) p.getX(), (float) p.getY());
        }));
        registrar.add(clip.op("c", 6, (s, v) -> clip.curve(s, v[0], v[1], v[2], v[3], v[4], v[5])));
        // The first control point is the current one: within the curve's hull either way, which is all the bounds need
        registrar.add(clip.op("v", 4, (s, v) -> clip.curve(s, v[0], v[1], v[0], v[1], v[2], v[3])));
        registrar.add(clip.op("y", 4, (s, v) -> clip.curve(s, v[0], v[1], v[2], v[3], v[2], v[3])));
        registrar.add(clip.op("h", 0, (s, v) -> clip.path.close()));
        registrar.add(clip.op("W", 0, (s, v) -> clip.path.clipOnEnd(Path2D.WIND_NON_ZERO)));
        registrar.add(clip.op("W*", 0, (s, v) -> clip.path.clipOnEnd(Path2D.WIND_EVEN_ODD)));
        for (String end : new String[] {"n", "S", "s", "f", "F", "f*", "B", "B*", "b", "b*"}) {
            registrar.add(clip.op(end, 0, (s, v) -> clip.path.end(s)));
        }
        return clip;
    }

    /**
     * Whether the clip hides a glyph whole: its box, {@code advance} ems along its baseline from the origin of
     * {@code rendering} (the text rendering matrix, in the page's user space, one unit an em), lies outside the bounds of
     * the current clipping path.
     */
    boolean hides(Matrix rendering, float advance) {
        Rectangle2D clip = bounds(engine.getGraphicsState());
        if (clip == null || !Float.isFinite(advance)) {
            return false;
        }
        float u = Math.max(advance, 0);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (float[] corner : new float[][] {{0, -BELOW}, {u, -BELOW}, {0, ABOVE}, {u, ABOVE}}) {
            Point2D.Float p = rendering.transformPoint(corner[0], corner[1]);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return maxX < clip.getMinX() - SLACK || minX > clip.getMaxX() + SLACK
                || maxY < clip.getMinY() - SLACK || minY > clip.getMaxY() + SLACK;
    }

    /** The bounds of the current clipping path (the intersection of its paths' bounds), or null when there is none. */
    private Rectangle2D bounds(PDGraphicsState state) {
        List<Path2D> clips = state.getCurrentClippingPaths();
        if (clips != lastClip || clips.size() != lastClipCount) {
            lastClip = clips;
            lastClipCount = clips.size();
            Rectangle2D b = null;
            for (Path2D p : clips) {
                Rectangle2D pb = p.getBounds2D();
                b = b == null ? pb : b.createIntersection(pb);
            }
            lastBounds = b;
        }
        return lastBounds;
    }

    private void curve(PDGraphicsState s, float x1, float y1, float x2, float y2, float x3, float y3) {
        Point2D a = point(s, x1, y1);
        Point2D b = point(s, x2, y2);
        Point2D c = point(s, x3, y3);
        path.curveTo((float) a.getX(), (float) a.getY(), (float) b.getX(), (float) b.getY(), (float) c.getX(),
                (float) c.getY());
    }

    private static Point2D point(PDGraphicsState s, float x, float y) {
        return s.getCurrentTransformationMatrix().transformPoint(x, y);
    }

    private OperatorProcessor op(String name, int operands, PathStep step) {
        return new OperatorProcessor(engine) {
            @Override
            public void process(Operator operator, List<COSBase> args) throws IOException {
                if (args.size() < operands) {
                    return;
                }
                float[] v = new float[operands];
                for (int i = 0; i < operands; i++) {
                    if (!(args.get(args.size() - operands + i) instanceof COSNumber n) || !Float.isFinite(n.floatValue())) {
                        return;
                    }
                    v[i] = n.floatValue();
                }
                step.apply(engine.getGraphicsState(), v);
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    @FunctionalInterface
    private interface PathStep {
        void apply(PDGraphicsState state, float[] operands);
    }

    /** How the engine takes an operator: {@code PDFStreamEngine.addOperator} is protected. */
    @FunctionalInterface
    interface Registrar {
        void add(OperatorProcessor op);
    }
}
