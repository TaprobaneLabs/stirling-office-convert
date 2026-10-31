package stirling.software.officeconvert.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Glyphs the page's text leaves out, which a picture drawn without its text must still paint (GAO's brace, U+001F). */
class TextlessGlyphTest {

    @Test
    void aGlyphMappedToControlCharactersIsTextless() {
        assertTrue(Glyph.textless("\u001F"));
        assertTrue(Glyph.textless("\u0001\u0002"));
        assertTrue(Glyph.textless("�"), "a replacement character is no text either");
    }

    @Test
    void aGlyphWithTextOrNoMappingIsNot() {
        assertFalse(Glyph.textless("A"));
        assertFalse(Glyph.textless("\u001F}"), "a brace among control characters is text");
        assertFalse(Glyph.textless(null), "an unmapped glyph may still get its text from the font");
        assertFalse(Glyph.textless(""));
    }
}
