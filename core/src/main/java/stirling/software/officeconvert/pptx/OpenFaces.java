package stirling.software.officeconvert.pptx;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open fonts a deck names in place of an Office family the PDF set them for (Cambria: Caladea), carried inside it as
 * PowerPoint keeps embedded fonts: EOT parts listed in the presentation's {@code p:embeddedFontLst}. PowerPoint's
 * Cambria sets text 3 to 5% wider than Caladea, so a two-column page set in Caladea wrapped a line in ten and its
 * text boxes grew over what was below. The faces are the ones the DOCX writer embeds; only those a deck uses go in.
 */
final class OpenFaces {

    static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/font";
    static final String CONTENT_TYPE = "application/x-fontdata";

    /** The DOCX writer's bundled faces (its {@code EmbeddedFonts}). */
    private static final String FONTS = "/stirling/software/officeconvert/docx/fonts/";
    private static final String[] FACES = {"Regular", "Bold", "Italic", "BoldItalic"};
    private static final String[] ELEMENTS = {"regular", "bold", "italic", "boldItalic"};
    private static final Map<String, Boolean> BUNDLED = new ConcurrentHashMap<>();

    private final Map<String, String> open;
    private final Map<String, BitSet> used = new TreeMap<>();

    OpenFaces(Map<String, String> open) {
        this.open = open;
    }

    /** The family to name for text in {@code family}: its open font where the deck can carry it, else itself. */
    String name(String family, boolean bold, boolean italic) {
        String font = family == null ? null : open.get(family);
        if (font == null || !bundled(font)) {
            return family;
        }
        used.computeIfAbsent(font, k -> new BitSet(FACES.length)).set((bold ? 1 : 0) | (italic ? 2 : 0));
        return font;
    }

    boolean isEmpty() {
        return used.isEmpty();
    }

    /** The {@code p:embeddedFontLst} of the presentation part, empty when no face is used. */
    String fontList() {
        if (used.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<p:embeddedFontLst>");
        used.forEach((family, faces) -> {
            sb.append("<p:embeddedFont><p:font typeface=\"").append(Ooxml.esc(family)).append("\" pitchFamily=\"18\" charset=\"0\"/>");
            faces.stream().forEach(face -> sb.append("<p:").append(ELEMENTS[face]).append(" r:id=\"").append(relId(family, face))
                    .append("\"/>"));
            sb.append("</p:embeddedFont>");
        });
        return sb.append("</p:embeddedFontLst>").toString();
    }

    /** The presentation part's relationships to the fonts. */
    String rels() {
        StringBuilder sb = new StringBuilder();
        used.forEach((family, faces) -> faces.stream().forEach(face -> sb.append("<Relationship Id=\"")
                .append(relId(family, face)).append("\" Type=\"").append(REL).append("\" Target=\"").append(part(family, face))
                .append("\"/>")));
        return sb.toString();
    }

    interface PartWriter {
        void write(String name, byte[] bytes) throws IOException;
    }

    void writeParts(PartWriter out) throws IOException {
        for (Map.Entry<String, BitSet> e : used.entrySet()) {
            String family = e.getKey();
            for (int face = e.getValue().nextSetBit(0); face >= 0; face = e.getValue().nextSetBit(face + 1)) {
                try (InputStream in = OpenFaces.class.getResourceAsStream(resource(family, FACES[face]))) {
                    if (in == null) {
                        throw new IOException("The font " + family + " " + FACES[face] + " is missing from the jar");
                    }
                    out.write("ppt/" + part(family, face), Eot.wrap(in.readAllBytes()));
                }
            }
        }
    }

    /** Whether this jar holds every face of the family. */
    static boolean bundled(String family) {
        return BUNDLED.computeIfAbsent(family, f -> {
            for (String face : FACES) {
                try (InputStream in = OpenFaces.class.getResourceAsStream(resource(f, face))) {
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

    private static String relId(String family, int face) {
        return "rIdFont" + family.replaceAll("[^A-Za-z0-9]", "") + FACES[face];
    }

    private static String part(String family, int face) {
        return "fonts/" + family.replaceAll("[^A-Za-z0-9]", "") + "-" + FACES[face] + ".fntdata";
    }

    private static String resource(String family, String face) {
        return FONTS + family.replaceAll("[^A-Za-z0-9]", "") + "-" + face + ".ttf";
    }
}
