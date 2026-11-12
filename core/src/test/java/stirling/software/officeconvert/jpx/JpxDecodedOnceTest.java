package stirling.software.officeconvert.jpx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JpxDecodedOnceTest {

    @BeforeEach
    void forget() {
        JpxImageIO.install();
        DecodedImages.clear();
    }

    /** PDFBox decodes a JPX image in full to read its size, then again for its pixels: one decode does both. */
    @Test
    void anImageReadForItsSizeAndThenItsPixelsIsDecodedOnce() throws IOException {
        byte[] jpx = SyntheticCodestream.samples(9, 7, 3, 8, 0, true, false, false, false, false);
        BufferedImage expected = JpxDecoder.decode(jpx).toBufferedImage();
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject made = new PDImageXObject(doc, new ByteArrayInputStream(jpx), COSName.JPX_DECODE, 9, 7, 8,
                    PDDeviceRGB.INSTANCE);
            // As a page's image is read: PDFBox marks it JPX only when it's made from its stream
            PDImageXObject image = new PDImageXObject(made.getStream(), null);
            long before = DecodedImages.decodes();
            assertEquals(9, image.getWidth());
            BufferedImage pixels = image.getImage();
            assertEquals(1, DecodedImages.decodes() - before);
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 9; x++) {
                    assertEquals(expected.getRGB(x, y), pixels.getRGB(x, y), x + "," + y);
                }
            }
        }
    }

    @Test
    void eachReaderGetsItsOwnCopy() throws IOException {
        byte[] jpx = SyntheticCodestream.samples(7, 5, 1, 8, 0, true, false, false, false, false);
        long before = DecodedImages.decodes();
        BufferedImage first = read(jpx);
        int sample = first.getRaster().getSample(2, 3, 0);
        first.getRaster().setSample(2, 3, 0, sample ^ 0xFF);
        BufferedImage second = read(jpx.clone());
        assertNotSame(first, second);
        assertEquals(sample, second.getRaster().getSample(2, 3, 0));
        assertEquals(1, DecodedImages.decodes() - before);
    }

    @Test
    void aReducedReadIsKeptApartFromTheFullOne() throws IOException {
        byte[] jpx = SyntheticCodestream.samples(8, 6, 1, 8, 0, true, false, false, false, false);
        long before = DecodedImages.decodes();
        BufferedImage full = read(jpx);
        JpxImageReader reader = new JpxImageReader(new JpxImageReaderSpi());
        try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpx))) {
            reader.setInput(in);
            javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(2, 2, 0, 0);
            BufferedImage half = reader.read(0, param);
            assertEquals(4, half.getWidth());
            assertEquals(3, half.getHeight());
        } finally {
            reader.dispose();
        }
        assertEquals(8, full.getWidth());
        assertEquals(2, DecodedImages.decodes() - before);
    }

    private static BufferedImage read(byte[] jpx) throws IOException {
        JpxImageReader reader = new JpxImageReader(new JpxImageReaderSpi());
        try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpx))) {
            reader.setInput(in);
            return reader.read(0, null);
        } finally {
            reader.dispose();
        }
    }
}
