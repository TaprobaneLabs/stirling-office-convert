package stirling.software.officeconvert.pptx;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A TrueType font wrapped as Embedded OpenType (EOT, version 2.2, W3C Member Submission 2008), the form PowerPoint
 * keeps embedded fonts in ({@code ppt/fonts/*.fntdata}). The font data stays as it is: no subsetting, compression or
 * XOR. PowerPoint drew such a font only once its root string checksum read as for no root strings, 0x50475342;
 * with 0 it fell back to Calibri.
 */
final class Eot {

    private static final int VERSION = 0x00020002;
    private static final short MAGIC = 0x504C;
    private static final int NO_ROOT_STRINGS = 0x50475342;
    private static final int WESTERN = 1252;

    private Eot() {}

    static byte[] wrap(byte[] ttf) {
        ByteBuffer font = ByteBuffer.wrap(ttf).order(ByteOrder.BIG_ENDIAN);
        int os2 = table(font, "OS/2");
        int head = table(font, "head");
        int name = table(font, "name");
        if (os2 < 0 || head < 0 || name < 0) {
            throw new IllegalArgumentException("Not a TrueType font with OS/2, head and name tables");
        }
        ByteBuffer out = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0).putInt(ttf.length).putInt(VERSION).putInt(0);
        out.put(ttf, os2 + 32, 10);
        boolean italic = (font.getShort(os2 + 62) & 1) != 0;
        out.put((byte) 0).put((byte) (italic ? 1 : 0));
        out.putInt(font.getShort(os2 + 4) & 0xFFFF);
        out.putShort(font.getShort(os2 + 8)).putShort(MAGIC);
        for (int i = 0; i < 4; i++) {
            out.putInt(font.getInt(os2 + 42 + 4 * i));
        }
        boolean codePages = font.getShort(os2) >= 1;
        out.putInt(codePages ? font.getInt(os2 + 78) : 0).putInt(codePages ? font.getInt(os2 + 82) : 0);
        out.putInt(font.getInt(head + 8));
        out.putInt(0).putInt(0).putInt(0).putInt(0);
        ByteArrayOutputStream names = new ByteArrayOutputStream();
        writeName(names, name(font, name, 1) + "\0");
        writeName(names, name(font, name, 2) + "\0");
        writeName(names, name(font, name, 5));
        writeName(names, name(font, name, 4));
        ByteBuffer tail = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        // Padding5, RootStringSize, RootStringCheckSum, EUDCCodePage, Padding6, SignatureSize, EUDCFlags, EUDCFontSize
        tail.putShort((short) 0).putShort((short) 0).putInt(NO_ROOT_STRINGS).putInt(WESTERN).putShort((short) 0)
                .putShort((short) 0).putInt(0).putInt(0);
        int headerSize = out.position() + names.size() + tail.position();
        byte[] eot = new byte[headerSize + ttf.length];
        ByteBuffer result = ByteBuffer.wrap(eot).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0, eot.length);
        result.put(out.array(), 0, out.position()).put(names.toByteArray()).put(tail.array(), 0, tail.position()).put(ttf);
        return eot;
    }

    private static void writeName(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_16LE);
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) bytes.length);
        out.writeBytes(b.array());
        out.writeBytes(bytes);
    }

    /** The offset of a table, or -1. */
    private static int table(ByteBuffer font, String tag) {
        int count = font.getShort(4) & 0xFFFF;
        byte[] want = tag.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < count; i++) {
            int at = 12 + 16 * i;
            boolean match = true;
            for (int k = 0; k < 4; k++) {
                match &= font.get(at + k) == want[k];
            }
            if (match) {
                return font.getInt(at + 8);
            }
        }
        return -1;
    }

    /** A Windows Unicode name (platform 3, encoding 1), US English first, else empty. */
    private static String name(ByteBuffer font, int table, int id) {
        int count = font.getShort(table + 2) & 0xFFFF;
        int strings = table + (font.getShort(table + 4) & 0xFFFF);
        String found = "";
        for (int i = 0; i < count; i++) {
            int at = table + 6 + 12 * i;
            int platform = font.getShort(at) & 0xFFFF;
            int encoding = font.getShort(at + 2) & 0xFFFF;
            int language = font.getShort(at + 4) & 0xFFFF;
            int nameId = font.getShort(at + 6) & 0xFFFF;
            if (platform != 3 || encoding != 1 || nameId != id) {
                continue;
            }
            int length = font.getShort(at + 8) & 0xFFFF;
            int offset = font.getShort(at + 10) & 0xFFFF;
            byte[] bytes = new byte[length];
            font.get(strings + offset, bytes);
            String text = new String(bytes, StandardCharsets.UTF_16BE);
            if (language == 0x409) {
                return text;
            }
            if (found.isEmpty()) {
                found = text;
            }
        }
        return found;
    }
}
