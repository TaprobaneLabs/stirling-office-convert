package stirling.software.officeconvert.extract;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Whether an image paints one colour, as a panel or a banner drawn as an image does, rather than a picture. Such an
 * image compresses to almost nothing, so one that doesn't is a picture without being decoded.
 */
public final class FlatImages {

    /** Compressed bytes per pixel above which an image holds detail. */
    private static final double FLAT_BYTES = 0.02;

    private static final int ALLOWANCE = 1024;

    /** Samples along the image's longer side. */
    private static final int SAMPLES = 64;

    private static final int ALIKE = 8;

    private FlatImages() {}

    public static boolean flat(PageGraphics.ImageDraw draw) {
        if (draw.stencilRgb() >= 0) {
            return true;
        }
        if (!(draw.image() instanceof PDImageXObject x) || !ImageBudget.affordable(x)) {
            return false;
        }
        long pixels = (long) x.getWidth() * x.getHeight();
        if (pixels <= 0 || x.getCOSObject().getLength() > FLAT_BYTES * pixels + ALLOWANCE) {
            return false;
        }
        try {
            BufferedImage img = x.getImage(null, Math.max(1, Math.max(x.getWidth(), x.getHeight()) / SAMPLES));
            int first = -1;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int i = 0; i < img.getWidth(); i++) {
                    int argb = img.getRGB(i, y);
                    if ((argb >>> 24) < 16) {
                        continue;
                    }
                    int rgb = argb & 0xFFFFFF;
                    if (first < 0) {
                        first = rgb;
                    } else if (!alike(first, rgb)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean alike(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= ALIKE && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= ALIKE
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= ALIKE;
    }
}
