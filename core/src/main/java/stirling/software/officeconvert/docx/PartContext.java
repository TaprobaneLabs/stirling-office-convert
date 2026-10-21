package stirling.software.officeconvert.docx;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import stirling.software.officeconvert.model.Picture;
import stirling.software.officeconvert.model.Scripts;

final class PartContext {

    final Map<String, String> imageRels = new LinkedHashMap<>();
    final Map<String, String> linkRels = new LinkedHashMap<>();
    final Map<String, String> headerRelIds = new LinkedHashMap<>();
    final Set<String> fonts = new LinkedHashSet<>();
    final EmbeddedFonts embedded = new EmbeddedFonts();
    Scripts.Profile scripts = new Scripts.Profile();
    Map<String, String> openFonts = Map.of();
    private int nextRel = 20;
    private int nextDocPr = 1;
    private int nextBookmark = 1;

    /** The name the document gives a family: the open font the PDF set it in, when the DOCX can embed that font. */
    String name(String family) {
        String open = family == null ? null : openFonts.get(family);
        return open != null && EmbeddedFonts.bundled(open) ? open : family;
    }

    String imageRel(Picture.MediaRef ref) {
        return imageRels.computeIfAbsent(ref.name(), n -> "rId" + nextRel++);
    }

    String linkRel(String url) {
        return linkRels.computeIfAbsent(url, u -> "rId" + nextRel++);
    }

    int docPr() {
        return nextDocPr++;
    }

    int bookmark() {
        return nextBookmark++;
    }
}
