package stirling.software.officeconvert.layout;

import java.awt.image.BufferedImage;

import stirling.software.officeconvert.extract.PageGraphics.ImageDraw;

/**
 * A scan's pixels the way the page shows them. A page turned a quarter (/Rotate 90: a landscape table printed sideways on
 * portrait paper, a portrait page photographed sideways and turned upright) shows its scan image turned too, while
 * {@link ScanRegions} and {@link ScanRules} map the image's columns and rows onto the page as it is shown.
 */
final class ScanImage {

    private ScanImage() {}

    /** The image's width as the page shows it: its height when it's drawn a quarter turned. */
    static int shownWidth(ImageDraw scan) {
        return scan.quarterTurns() % 2 == 1 ? scan.image().getHeight() : scan.image().getWidth();
    }

    /**
     * {@code raw} turned as {@code scan} draws it: flipped left to right first when it's drawn mirrored, then turned
     * {@link ImageDraw#quarterTurns()} quarters clockwise, the turns that bring the image's top to where the page shows it.
     */
    static BufferedImage shown(BufferedImage raw, ImageDraw scan) {
        int turns = Math.floorMod(scan.quarterTurns(), 4);
        if (turns == 0 && !scan.flipH() && !scan.flipV()) {
            return raw;
        }
        int w = raw.getWidth();
        int h = raw.getHeight();
        int[] src = raw.getRGB(0, 0, w, h, null, 0, w);
        int ow = turns % 2 == 1 ? h : w;
        int oh = turns % 2 == 1 ? w : h;
        int[] dst = new int[ow * oh];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = scan.flipH() ? w - 1 - x : x;
                int sy = scan.flipV() ? h - 1 - y : y;
                int px = src[sy * w + sx];
                int dx;
                int dy;
                switch (turns) {
                    case 1 -> {
                        dx = h - 1 - y;
                        dy = x;
                    }
                    case 2 -> {
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 3 -> {
                        dx = y;
                        dy = w - 1 - x;
                    }
                    default -> {
                        dx = x;
                        dy = y;
                    }
                }
                dst[dy * ow + dx] = px;
            }
        }
        BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, ow, oh, dst, 0, ow);
        return out;
    }
}
