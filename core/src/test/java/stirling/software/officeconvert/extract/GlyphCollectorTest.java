package stirling.software.officeconvert.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GlyphCollectorTest {

    @Test
    void mapsWindows1252BytesPassedThroughAsC1Controls() {
        assertEquals("’", GlyphCollector.clean("\u0092"));
        assertEquals("“A”", GlyphCollector.clean("\u0093A\u0094"));
        assertEquals("–", GlyphCollector.clean("\u0096"));
    }

    @Test
    void dropsUndefinedAndControlCharacters() {
        assertNull(GlyphCollector.clean("\u0081"));
        assertEquals("a b", GlyphCollector.clean("a\tb"));
        assertNull(GlyphCollector.clean(""));
    }

    @Test
    void readsASymbolFontsC1ControlsAsABullet() {
        // CDC's kidney disease fact sheet: Wingdings bullets InDesign maps to U+0083 and U+008A
        FontInfo wingdings = new FontInfo("Wingdings", false, false, false, false, true, "Wingdings-Regular", false);
        assertEquals("•", GlyphCollector.clean(GlyphCollector.symbolCode("\u0083", wingdings)));
        assertEquals("•", GlyphCollector.clean(GlyphCollector.symbolCode("\u008A", wingdings)));
        // A text font's C1 controls are still Windows-1252 bytes
        assertEquals("ƒ", GlyphCollector.clean(GlyphCollector.symbolCode("\u0083", FontInfo.DEFAULT)));
        // A symbol font's other characters are left as they come
        assertEquals("", GlyphCollector.symbolCode("", wingdings));
        assertEquals("A", GlyphCollector.symbolCode("A", wingdings));
        assertNull(GlyphCollector.symbolCode(null, wingdings));
    }

    @Test
    void decomposesLigatures() {
        assertEquals("fi", GlyphCollector.clean("ﬁ"));
    }
}
