package stirling.software.officeconvert.docx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Numbering;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.StyleSheet;

class MarkerFormatTest {

    @Test
    void aMarkerKeepsItsOwnWidthWhateverTheParagraphMarkSays() {
        // The Federal Register's square bullets: their items' text is set at 111%, which the paragraph mark carries, and
        // Word draws the marker in the mark's formatting wherever the level doesn't say otherwise
        Numbering numbering = new Numbering();
        Numbering.Instance list = numbering.create();
        RunStyle times = new RunStyle("Times New Roman", 9f, false, false, false, false, 0, -1, 0, false, 0f, 111);
        RunStyle square = new RunStyle("Segoe UI Symbol", 9f, false, false, false, false, 0, -1, 0, false, 0f, 100);
        RunStyle tight = new RunStyle("Arial", 9f, false, false, false, false, 0, -1, 0, false, -0.2f, 90);
        list.definition.levels[0] = new Numbering.Level("bullet", "■", square, 8.35f, 8.35f);
        list.definition.levels[1] = new Numbering.Level("decimal", "%2.", tight, 30f, 12f);
        String xml = NumberingPart.xml(numbering, new StyleSheet(times), new PartContext());
        String first = level(xml, 0);
        String second = level(xml, 1);
        assertTrue(first.contains("<w:w w:val=\"100\"/>"), "the bullet isn't widened to 111%: " + first);
        assertTrue(first.contains("<w:spacing w:val=\"0\"/>"), "nor spaced as the mark is: " + first);
        assertTrue(second.contains("<w:w w:val=\"90\"/>"), "a number keeps its own scale: " + second);
        assertTrue(second.contains("<w:spacing w:val=\"-4\"/>"), "and its own spacing: " + second);
    }

    private static String level(String xml, int ilvl) {
        int at = xml.indexOf("<w:lvl w:ilvl=\"" + ilvl + "\">");
        return xml.substring(at, xml.indexOf("</w:lvl>", at));
    }
}
