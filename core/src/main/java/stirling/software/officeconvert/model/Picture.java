package stirling.software.officeconvert.model;

public final class Picture {

    public enum Wrap {
        INLINE,
        SQUARE,
        TOP_BOTTOM,
        NONE
    }

    /** The sides of a square-wrapped picture text runs on. */
    public enum Side {
        BOTH,
        LEFT,
        RIGHT
    }

    public final MediaRef media;
    public float width;
    public float height;
    public Wrap wrap = Wrap.INLINE;
    public float x;
    public float y;
    public boolean behind;
    public boolean backdrop;
    public int paintOrder = -1;
    public int z;
    public boolean fromParagraph;
    public float wrapGap;
    public Side wrapSide = Side.BOTH;
    public float cropLeft;
    public float cropTop;
    public float cropRight;
    public float cropBottom;
    public int rotation;
    public boolean flipH;
    public String description = "";
    public float baselineShift;

    public Picture(MediaRef media, float width, float height) {
        this.media = media;
        this.width = width;
        this.height = height;
    }

    public record MediaRef(String name, String contentType, int pixelWidth, int pixelHeight) {}
}
