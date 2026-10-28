package stirling.software.officeconvert.layout;

import java.awt.image.BufferedImage;

/**
 * How much darker than white a scan's paper is. The thresholds that tell ink from paper in a scan ({@link ScanRegions},
 * {@link ScanRules}) were set on copier and flatbed scans, whose paper is near white. A phone's document scanner keeps
 * the room's light, so its paper is grey (luma 175 to 210 in FormatJet's phone photos): most of the page read as ink,
 * the page became one picture, and its OCR text went into the picture with it.
 */
final class ScanPaper {

    /** Paper at least this light counts as white, so the thresholds don't move for scanners' pages. */
    static final int WHITE = 240;

    /** Anything darker than this is not paper: a dark photo filling most of a page doesn't move the thresholds. */
    private static final int DARKEST_PAPER = 150;

    /** A pixel this far from grey is colour, never paper (the same limit as {@link ScanRegions}' colour test). */
    private static final int GREY = 40;

    /** Luma levels on each side of a level that count for it, so the paper's noise makes one peak. */
    private static final int SPREAD = 3;

    /** The share of the light grey pixels at or below the paper's level, at least: the paper is lighter than most of them. */
    private static final float PAPER_SHARE = 0.6f;

    /** At most about this many pixels are read: every n-th pixel of every n-th row. */
    private static final int SAMPLES = 250_000;

    private ScanPaper() {}

    /**
     * How far the scan's paper is below {@link #WHITE}, or 0 when it is at least that light or can't be told. The
     * paper is the most common luma among grey pixels at least {@link #DARKEST_PAPER} light (text and pictures cover
     * less of a page than its paper does), or the level {@link #PAPER_SHARE} of them reach when that is lighter: a
     * neutral-grey graphic can out-count paper whose light varies, spread over many levels, and the graphic would then
     * no longer count as ink.
     */
    static int shift(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            return 0;
        }
        int step = Math.max(1, (int) Math.ceil(Math.sqrt((double) w * h / SAMPLES)));
        int[] counts = new int[256];
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                int luma = (red * 299 + green * 587 + blue * 114) / 1000;
                if (max - min <= GREY && luma >= DARKEST_PAPER) {
                    counts[luma]++;
                }
            }
        }
        int paper = -1;
        int best = 0;
        for (int level = DARKEST_PAPER; level < 256; level++) {
            int near = 0;
            for (int k = Math.max(0, level - SPREAD); k <= Math.min(255, level + SPREAD); k++) {
                near += counts[k];
            }
            if (near > 0 && near >= best) {
                paper = level;
                best = near;
            }
        }
        if (paper < 0) {
            return 0;
        }
        int total = 0;
        for (int level = DARKEST_PAPER; level < 256; level++) {
            total += counts[level];
        }
        int seen = 0;
        for (int level = DARKEST_PAPER; level < 256; level++) {
            seen += counts[level];
            if (seen >= PAPER_SHARE * total) {
                paper = Math.max(paper, level);
                break;
            }
        }
        return Math.max(0, WHITE - paper);
    }
}
