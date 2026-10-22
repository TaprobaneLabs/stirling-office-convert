package stirling.software.officeconvert.docx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Numbering;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.StyleSheet;

class BulletGlyphTest {

    private static final String SYMBOL_BULLET = "";

    @Test
    void aSymbolBulletInAnOfficeFontGoesBackToSymbol() {
        BulletGlyph g = BulletGlyph.of(SYMBOL_BULLET, "Arial");
        assertEquals(SYMBOL_BULLET, g.text());
        assertEquals("Symbol", g.font(), "Arial has no glyph at U+F0B7, so Word would draw a box");
    }

    @Test
    void aWingdingsBulletGoesBackToWingdings() {
        assertEquals("Wingdings", BulletGlyph.of("", "Calibri").font());
        assertEquals("Wingdings", BulletGlyph.of("", null).font());
    }

    @Test
    void aBulletAlreadyInASymbolFontKeepsIt() {
        assertNull(BulletGlyph.of(SYMBOL_BULLET, "Symbol").font());
        assertNull(BulletGlyph.of("", "Wingdings").font());
    }

    @Test
    void otherPrivateUseCharactersBecomeAPlainBullet() {
        BulletGlyph g = BulletGlyph.of("", "Arial");
        assertEquals("•", g.text());
        assertNull(g.font());
    }

    @Test
    void anIconFontsMarkerBecomesAPlainBulletNotASymbolGlyph() {
        // Font Awesome's square sits at U+F0C8, where Symbol has a different glyph.
        BulletGlyph g = BulletGlyph.of("", "Arial");
        assertEquals("•", g.text());
        assertNull(g.font());
    }

    @Test
    void anIconInBodyTextIsLeftAsItIs() {
        RunStyle arial = new RunStyle("Arial", 11f, false, false, false, false, 0, -1, 0, false);
        StringBuilder sb = new StringBuilder();
        // Font Awesome's envelope (U+F0E0) and eye (U+F06E, also a Wingdings bullet) stay as they are.
        new RunXml(new PartContext()).textRun(sb, SYMBOL_BULLET + "x mail  view", arial, arial);
        String xml = sb.toString();
        assertTrue(xml.contains("x mail  view"), "icons keep the run's font: " + xml);
        assertFalse(xml.contains("Wingdings"), xml);
        assertTrue(xml.contains("<w:rFonts w:ascii=\"Symbol\""), "Symbol's bullet still moves");
    }

    @Test
    void ordinaryBulletsAreLeftAlone() {
        for (String b : new String[] {"•", "▪", "-", "o"}) {
            BulletGlyph g = BulletGlyph.of(b, "Arial");
            assertEquals(b, g.text());
            assertNull(g.font());
        }
    }

    @Test
    void theNumberingPartNamesSymbolForALibreOfficeBullet() {
        Numbering numbering = new Numbering();
        Numbering.Instance list = numbering.create();
        RunStyle arial = new RunStyle("Arial", 11f, false, false, false, false, 0, -1, 0, false);
        list.definition.levels[0] = new Numbering.Level("bullet", SYMBOL_BULLET, arial, 36f, 18f);
        list.definition.levels[1] = new Numbering.Level("bullet", SYMBOL_BULLET, null, 72f, 18f);
        String xml = NumberingPart.xml(numbering, new StyleSheet(arial), new PartContext());
        String first = level(xml, 0);
        String second = level(xml, 1);
        assertTrue(first.contains("<w:rFonts w:ascii=\"Symbol\""), first);
        assertFalse(first.contains("w:ascii=\"Arial\""), "the marker isn't left in Arial");
        assertTrue(second.contains("<w:rFonts w:ascii=\"Symbol\""), "a level with no marker style gets the font too");
    }

    @Test
    void aBulletKeptAsTextIsSetInSymbol() {
        RunStyle arial = new RunStyle("Arial", 11f, false, false, false, false, 0, -1, 0, false);
        StringBuilder sb = new StringBuilder();
        new RunXml(new PartContext()).textRun(sb, SYMBOL_BULLET, arial, arial);
        assertTrue(sb.toString().contains("<w:rFonts w:ascii=\"Symbol\""), sb.toString());
    }

    @Test
    void onlyTheSymbolCharactersOfARunChangeFont() {
        RunStyle arial = new RunStyle("Arial", 11f, false, false, false, false, 0, -1, 0, false);
        StringBuilder sb = new StringBuilder();
        new RunXml(new PartContext()).textRun(sb, SYMBOL_BULLET + " Risk assessment", arial, arial);
        String xml = sb.toString();
        assertEquals(2, xml.split("<w:r>", -1).length - 1, xml);
        String words = xml.substring(xml.lastIndexOf("<w:r>"));
        assertTrue(words.contains("Risk assessment"));
        assertFalse(words.contains("Symbol"), "the words keep the run's font");
    }

    @Test
    void aRunAlreadyInSymbolIsLeftAsItIs() {
        RunStyle symbol = new RunStyle("Symbol", 11f, false, false, false, false, 0, -1, 0, true);
        RunStyle arial = new RunStyle("Arial", 11f, false, false, false, false, 0, -1, 0, false);
        StringBuilder sb = new StringBuilder();
        new RunXml(new PartContext()).textRun(sb, SYMBOL_BULLET + "x", symbol, arial);
        assertEquals(1, sb.toString().split("<w:r>", -1).length - 1, sb.toString());
    }

    private static String level(String xml, int ilvl) {
        int start = xml.indexOf("<w:lvl w:ilvl=\"" + ilvl + "\">");
        return xml.substring(start, xml.indexOf("</w:lvl>", start));
    }
}
