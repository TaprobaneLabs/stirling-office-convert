package stirling.software.officeconvert.docx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open fonts the DOCX carries inside it (ECMA-376 Part 1, 17.8.1), for text the PDF sets in a font Word doesn't
 * have. Word draws that text in the embedded font, so its lines break where the PDF's do; with the Office font of
 * the same family (Cambria for Caladea) they run wider and reflow. Only the faces the document uses are embedded.
 */
final class EmbeddedFonts {

    static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/font";
    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.obfuscatedFont";

    private static final String[] FACES = {"Regular", "Bold", "Italic", "BoldItalic"};
    private static final String[] ELEMENTS = {"embedRegular", "embedBold", "embedItalic", "embedBoldItalic"};
    private static final Map<String, Boolean> BUNDLED = new ConcurrentHashMap<>();

    private final Map<String, BitSet> used = new TreeMap<>();

    /** Whether this jar holds every face of the family, so a document may name it. */
    static boolean bundled(String family) {
        return family != null && BUNDLED.computeIfAbsent(family, f -> {
            for (String face : FACES) {
                try (InputStream in = EmbeddedFonts.class.getResourceAsStream(resource(f, face))) {
                    if (in == null) {
                        return false;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
            return true;
        });
    }

    void use(String family, boolean bold, boolean italic) {
        if (bundled(family)) {
            used.computeIfAbsent(family, k -> new BitSet(FACES.length)).set((bold ? 1 : 0) | (italic ? 2 : 0));
        }
    }

    boolean isEmpty() {
        return used.isEmpty();
    }

    /** The w:embed* elements of a font table entry, in the schema's order. */
    void fontTableEntry(StringBuilder sb, String family) {
        BitSet faces = used.get(family);
        if (faces == null) {
            return;
        }
        faces.stream().forEach(face -> sb.append("<w:").append(ELEMENTS[face]).append(" r:id=\"")
                .append(relId(family, face)).append("\" w:fontKey=\"").append(key(family, face)).append("\"/>"));
    }

    String rels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        used.forEach((family, faces) -> faces.stream().forEach(face -> sb.append("<Relationship Id=\"")
                .append(relId(family, face)).append("\" Type=\"").append(REL).append("\" Target=\"")
                .append(part(family, face)).append("\"/>")));
        return sb.append("</Relationships>").toString();
    }

    interface PartWriter {
        void write(String name, byte[] bytes) throws IOException;
    }

    /** Writes each used face, obfuscated with its key, as a part next to the font table. */
    void writeParts(PartWriter out) throws IOException {
        for (Map.Entry<String, BitSet> e : used.entrySet()) {
            String family = e.getKey();
            for (int face = e.getValue().nextSetBit(0); face >= 0; face = e.getValue().nextSetBit(face + 1)) {
                try (InputStream in = EmbeddedFonts.class.getResourceAsStream(resource(family, FACES[face]))) {
                    if (in == null) {
                        throw new IOException("The font " + family + " " + FACES[face] + " is missing from the jar");
                    }
                    out.write("word/" + part(family, face), obfuscate(in.readAllBytes(), key(family, face)));
                }
            }
        }
    }

    /** XORs the font's first 32 bytes with the key's 16 bytes, read from the end of its GUID string (17.8.1). */
    static byte[] obfuscate(byte[] font, String key) {
        String hex = key.replaceAll("[{}-]", "");
        byte[] out = font.clone();
        for (int i = 0; i < 32 && i < out.length; i++) {
            int k = 15 - i % 16;
            out[i] ^= (byte) Integer.parseInt(hex, 2 * k, 2 * k + 2, 16);
        }
        return out;
    }

    /** A GUID fixed per face, so the same PDF always gives the same DOCX. */
    static String key(String family, int face) {
        UUID id = UUID.nameUUIDFromBytes((family + "-" + FACES[face]).getBytes(StandardCharsets.UTF_8));
        return "{" + id.toString().toUpperCase(Locale.ROOT) + "}";
    }

    private static String relId(String family, int face) {
        return "rIdFont" + family.replaceAll("[^A-Za-z0-9]", "") + FACES[face];
    }

    private static String part(String family, int face) {
        return "fonts/" + family.replaceAll("[^A-Za-z0-9]", "") + "-" + FACES[face] + ".odttf";
    }

    private static String resource(String family, String face) {
        return "fonts/" + family.replaceAll("[^A-Za-z0-9]", "") + "-" + face + ".ttf";
    }
}
