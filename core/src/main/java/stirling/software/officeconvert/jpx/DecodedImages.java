package stirling.software.officeconvert.jpx;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JPEG 2000 images decoded last, by their bytes and the part asked for. PDFBox decodes a JPX image in full to read
 * its size and depth, decodes it again for its pixels, and does both again wherever it's drawn: a page of photos had each
 * decoded six times or more. Callers get a copy, so what's kept stays as decoded.
 */
final class DecodedImages {

    /** Bytes kept at most: the decoded pixels and the codestreams they're found by. */
    static final long MOST_BYTES = 64L << 20;

    interface Decoding {
        BufferedImage run() throws IOException;
    }

    private static final class Key {
        private final byte[] data;
        private final String part;
        private final int hash;

        Key(byte[] data, String part) {
            this.data = data;
            this.part = part;
            this.hash = 31 * Arrays.hashCode(data) + part.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.hash == hash && k.part.equals(part) && Arrays.equals(k.data, data);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private record Kept(SoftReference<BufferedImage> image, long bytes) {}

    private static final Map<Key, Kept> KEPT = new LinkedHashMap<>(16, 0.75f, true);

    private static long keptBytes;

    private static long decodes;

    private DecodedImages() {}

    /** {@code part} of the image in {@code data}, decoded by {@code decoding} unless it was decoded lately. */
    static BufferedImage decode(byte[] data, String part, Decoding decoding) throws IOException {
        Key key = new Key(data, part);
        BufferedImage kept = find(key);
        if (kept != null) {
            return copy(kept);
        }
        counted();
        BufferedImage image = decoding.run();
        keep(key, image);
        return copy(image);
    }

    /** How many images were decoded rather than found (for tests). */
    static synchronized long decodes() {
        return decodes;
    }

    static synchronized void clear() {
        KEPT.clear();
        keptBytes = 0;
    }

    private static synchronized void counted() {
        decodes++;
    }

    private static synchronized BufferedImage find(Key key) {
        Kept kept = KEPT.get(key);
        BufferedImage image = kept == null ? null : kept.image().get();
        if (kept != null && image == null) {
            KEPT.remove(key);
            keptBytes -= kept.bytes();
        }
        return image;
    }

    private static synchronized void keep(Key key, BufferedImage image) {
        long bytes = key.data.length + pixelBytes(image);
        if (bytes > MOST_BYTES / 4) {
            return;
        }
        Kept old = KEPT.put(key, new Kept(new SoftReference<>(image), bytes));
        keptBytes += bytes - (old == null ? 0 : old.bytes());
        for (Iterator<Kept> it = KEPT.values().iterator(); keptBytes > MOST_BYTES && it.hasNext();) {
            keptBytes -= it.next().bytes();
            it.remove();
        }
    }

    private static long pixelBytes(BufferedImage image) {
        DataBuffer buffer = image.getRaster().getDataBuffer();
        return (long) buffer.getSize() * buffer.getNumBanks() * DataBuffer.getDataTypeSize(buffer.getDataType()) / 8;
    }

    private static BufferedImage copy(BufferedImage image) {
        WritableRaster raster = image.copyData(image.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(image.getColorModel(), raster, image.isAlphaPremultiplied(), null);
    }
}
