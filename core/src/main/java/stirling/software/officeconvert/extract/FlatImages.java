package stirling.software.officeconvert.extract;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;

/**
 * Whether an image paints one colour, as a panel or a banner drawn as an image does, rather than a picture. Such an
 * image compresses to almost nothing, so one that doesn't is a picture without being decoded. An image of a kind this
 * can't measure counts as flat, so it's never taken for a picture.
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
        PDImage image = draw.image();
        long coded;
        if (image instanceof PDImageXObject x) {
            coded = x.getCOSObject().getLength();
        } else if (image instanceof PDInlineImage inline) {
            coded = inline.getData().length;
        } else {
            return true;
        }
        if (!ImageBudget.affordable(image)) {
            return false;
        }
        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels <= 0 || coded > FLAT_BYTES * pixels + ALLOWANCE) {
            return false;
        }
        try {
            BufferedImage img = image.getImage(null, Math.max(1, Math.max(image.getWidth(), image.getHeight()) / SAMPLES));
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
