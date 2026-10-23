package stirling.software.officeconvert.extract;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;

/**
 * Whether an image paints one colour where the page shows it, as a panel or a banner drawn as an image does, rather
 * than a picture, judged from a sample of its visible pixels however it's stored. An image whose pixels can't be read
 * (of another kind, too large to decode, broken, or clipped while turned or flipped) counts as flat, so it's never taken
 * for a picture.
 */
public final class FlatImages {

    /** Samples along the image's longer side. */
    private static final int SAMPLES = 64;

    private static final int ALIKE = 8;

    /** Points a clip may fall inside an image's edge before the image counts as clipped. */
    private static final float CLIP_SLACK = 1f;

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
        Rectangle region = null;
        if (clipped(draw)) {
            region = visible(draw, image.getWidth(), image.getHeight());
            if (region == null) {
                return true;
            }
        }
        try {
            int longer = region == null ? Math.max(image.getWidth(), image.getHeight()) : Math.max(region.width, region.height);
            BufferedImage img = image.getImage(region, Math.max(1, longer / SAMPLES));
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

    private static boolean clipped(PageGraphics.ImageDraw d) {
        return d.clipX() > d.x() + CLIP_SLACK || d.clipRight() < d.right() - CLIP_SLACK || d.clipTop() > d.top() + CLIP_SLACK
                || d.clipBottom() < d.bottom() - CLIP_SLACK;
    }

    /** The source pixels a clip shows, for an image drawn upright and unflipped, as its picture is cropped; else none. */
    private static Rectangle visible(PageGraphics.ImageDraw d, int width, int height) {
        float w = d.right() - d.x();
        float h = d.bottom() - d.top();
        if (d.quarterTurns() % 4 != 0 || d.flipH() || d.flipV() || w <= 0 || h <= 0) {
            return null;
        }
        int x0 = Math.clamp((long) Math.floor((d.clipX() - d.x()) / w * width), 0, width - 1);
        int x1 = Math.clamp((long) Math.ceil((d.clipRight() - d.x()) / w * width), x0 + 1, width);
        int y0 = Math.clamp((long) Math.floor((d.clipTop() - d.top()) / h * height), 0, height - 1);
        int y1 = Math.clamp((long) Math.ceil((d.clipBottom() - d.top()) / h * height), y0 + 1, height);
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    private static boolean alike(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= ALIKE && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= ALIKE
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= ALIKE;
    }
}
