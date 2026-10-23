package stirling.software.officeconvert.extract;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;

/**
 * Whether an image paints one colour, as a panel or a banner drawn as an image does, rather than a picture, judged from a
 * sample of its pixels however it's stored. An image whose pixels can't be read (of another kind, too large to decode,
 * or broken) counts as flat, so it's never taken for a picture.
 */
public final class FlatImages {

    /** Samples along the image's longer side. */
    private static final int SAMPLES = 64;

    private static final int ALIKE = 8;

    private FlatImages() {}

    public static boolean flat(PageGraphics.ImageDraw draw) {
        if (draw.stencilRgb() >= 0) {
            return true;
        }
        PDImage image = draw.image();
        if (!(image instanceof PDImageXObject) && !(image instanceof PDInlineImage)) {
            return true;
        }
        if (!ImageBudget.affordable(image) || (long) image.getWidth() * image.getHeight() <= 0) {
            return true;
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
            return true;
        }
    }

    private static boolean alike(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= ALIKE && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= ALIKE
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= ALIKE;
    }
}
