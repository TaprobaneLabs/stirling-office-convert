package stirling.software.officeconvert.layout;

import static stirling.software.officeconvert.layout.LineTraits.EDGE;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class LineRoom {

    /** {@code leftWraps}: the output wraps text around the obstacle that sets the left limit. */
    private record Limits(float left, float right, boolean narrowed, boolean leftWraps) {}

    final float colLeft;
    final float colRight;
    final float measure;
    private final Map<Line, Limits> limits = new IdentityHashMap<>();

    LineRoom(List<Line> lines, float colLeft, float colRight, List<ParagraphBuilder.Obstacle> obstacles) {
        this.colLeft = colLeft;
        this.colRight = colRight;
        for (Line l : lines) {
            limits.put(l, limitsOf(l, colLeft, colRight, obstacles));
        }
        this.measure = measure(lines, colLeft, colRight);
    }

    private static Limits limitsOf(Line l, float colLeft, float colRight, List<ParagraphBuilder.Obstacle> obstacles) {
        float left = colLeft;
        float right = colRight;
        boolean narrowed = false;
        boolean leftWraps = false;
        for (ParagraphBuilder.Obstacle o : obstacles) {
            Box b = o.box();
            if (l.top >= b.bottom() - 1 || l.bottom <= b.top() + 1) {
                continue;
            }
            if (b.x() >= l.right - 1) {
                right = Math.min(right, b.x() - o.gapLeft());
                narrowed = true;
            } else if (b.right() <= l.x + 1) {
                if (b.right() + o.gapRight() > left) {
                    left = b.right() + o.gapRight();
                    leftWraps = o.wraps();
                }
                narrowed = true;
            }
        }
        return new Limits(left, right, narrowed, leftWraps);
    }

    float left(Line l) {
        return limits.get(l).left();
    }

    float right(Line l) {
        return limits.get(l).right();
    }

    float wrap(Line l) {
        Limits lim = limits.get(l);
        return lim.narrowed() ? Math.min(lim.right(), measure) : measure;
    }

    /** Whether the output wraps text around the obstacle that sets a line's left limit. */
    boolean leftWraps(Line l) {
        return limits.get(l).leftWraps();
    }

    boolean narrowed(Line l) {
        return limits.get(l).narrowed();
    }

    float rel(Line l) {
        return l.x - left(l);
    }

    boolean centred(Line l) {
        return LineTraits.isCentred(l, colLeft, colRight);
    }

    private static float measure(List<Line> lines, float colLeft, float colRight) {
        float[] rights = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            rights[i] = lines.get(i).right;
        }
        Arrays.sort(rights);
        float max = rights[rights.length - 1];
        int best = 0;
        float edge = max;
        for (int i = rights.length - 1; i >= 0; i--) {
            int n = 0;
            for (float r : rights) {
                if (Math.abs(r - rights[i]) <= EDGE) {
                    n++;
                }
            }
            if (n > best && rights[i] >= colLeft + (colRight - colLeft) * 0.5f) {
                best = n;
                edge = rights[i];
            }
        }
        return best >= 3 ? Math.max(edge, Math.min(bodyRight(lines, edge, max), colRight)) : Math.max(max, colRight);
    }

    /**
     * The furthest right a line in the body's size reaches: the size of the lines that end at the column's common edge.
     * Lines in another size, such as a title's pieces set into the column, don't widen the measure: USGS Fact Sheet
     * 2024-3036's right column ends its ragged lines by 576 pt, and its title's pieces reaching 591 pt made every line that
     * ended 15 pt short look like a paragraph's last, so its paragraphs broke where the next word would have fitted.
     */
    private static float bodyRight(List<Line> lines, float edge, float max) {
        for (Line l : lines) {
            // An OCR layer's sizes come from the scan's ink and vary line to line, and its paragraphs aren't fitted to
            // their lines, so one joined to the next reflows in Word and PowerPoint: IRS Form 8822's scan lost 8 points
            // of its words in place when its notice's first line joined the rest
            if (OcrWords.ocrText(l.words.getFirst().first())) {
                return max;
            }
        }
        float[] sizes = new float[lines.size()];
        int n = 0;
        for (Line l : lines) {
            if (Math.abs(l.right - edge) <= EDGE) {
                sizes[n++] = l.size;
            }
        }
        Arrays.sort(sizes, 0, n);
        float size = sizes[n / 2];
        float right = edge;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (Line l : lines) {
            if (Math.abs(l.size - size) <= 0.12f * size) {
                right = Math.max(right, l.right);
                top = Math.min(top, l.top);
                bottom = Math.max(bottom, l.bottom);
            }
        }
        for (Line l : lines) {
            // A wider line among the body's lines, not above or below them, is part of what the column sets: IRS Form
            // 4506-T's lines that run a table's cells on from the text beside it broke as its own lines, and joined
            // Word reflowed them (10 points of its words in place lost)
            if (l.right > right && l.bottom > top && l.top < bottom) {
                return max;
            }
        }
        return Math.min(right, max);
    }
}
