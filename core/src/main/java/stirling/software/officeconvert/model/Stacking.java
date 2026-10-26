package stirling.software.officeconvert.model;

public final class Stacking {

    public static final int LIMIT = 0x1E000000;
    public static final int BACKDROP = 252_000_000;
    public static final int STACKED = 260_000_000;
    public static final int PICTURES = 460_000_000;
    public static final int BOXES = 480_000_000;
    public static final float CORNER = 6f;
    /** Where DrawingML's {@code roundRect} sets its text rectangle: this share of its corner radius in from each side. */
    private static final float ROUND_RECT_TEXT = 0.29289f;

    private Stacking() {}

    /** The {@code roundRect} adjustment that gives a box of this size {@link #CORNER} corners, at most half its shorter side. */
    public static long cornerAdjust(float width, float height) {
        return Math.min(50000, Math.round(CORNER / Math.max(1f, Math.min(width, height)) * 100000));
    }

    /**
     * How much further in than a square box's a rounded box of this size sets its text on each side, inside its insets: Word
     * and LibreOffice both set a {@code roundRect}'s text in the rectangle its geometry gives, 1.8 pt in with 6 pt corners.
     */
    public static float roundedTextInset(float width, float height) {
        return ROUND_RECT_TEXT * cornerAdjust(width, height) / 100000f * Math.min(width, height);
    }

    public static int stacked(int step) {
        return STACKED + Math.min(Math.max(0, step), PICTURES - STACKED - 1);
    }

    public static int shape(Inline.Shape s, int seq) {
        return s.z() > 0 ? s.z() : stacked(seq);
    }

    public static int picture(Picture p, int seq) {
        if (p.z > 0) {
            return p.z;
        }
        return p.backdrop ? Math.min(BACKDROP + seq, STACKED - 1) : Math.min(PICTURES + seq, BOXES - 1);
    }

    public static int box(int seq) {
        return Math.min(BOXES + seq, LIMIT);
    }
}
