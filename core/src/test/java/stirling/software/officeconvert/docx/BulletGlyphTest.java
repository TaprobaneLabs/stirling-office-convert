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

    private static String level(String xml, int ilvl) {
        int start = xml.indexOf("<w:lvl w:ilvl=\"" + ilvl + "\">");
        return xml.substring(start, xml.indexOf("</w:lvl>", start));
    }
}
