package stirling.software.officeconvert.docx;

import java.util.Locale;
import java.util.Set;

/**
 * A list bullet's character and the font Word must draw it in. Word and LibreOffice store Symbol
 * and Wingdings bullets in the private-use range U+F020 to U+F0FF, which only those fonts map; in
 * any other font Word draws an empty box. A PDF from LibreOffice sets such a bullet in OpenSymbol,
 * which the document names by an Office family, so the bullet goes back to the symbol font it came
 * from, as Word stores its own bullets. Other private-use markers, an icon font's among them, become a
 * plain bullet. In body text, where Wingdings' bullets share code points with icon fonts' icons,
 * {@link RunXml} moves only Symbol's bullet (a bullet kept as text before a tab) and leaves other
 * private-use characters as they are.
 */
record BulletGlyph(String text, String font) {

    private static final String BULLET = "•";

    /** Symbol's bullet •, the one Word and LibreOffice lists use; it isn't an icon font's code point. */
    static final char SYMBOL_BULLET = '';

    /** Wingdings' bullets in Word's bullet library: ▪ ❖ ➢ ✓ ■ □ ❑. */
    private static final Set<Character> WINGDINGS =
            Set.of('', '', '', '', '', '', '');

    /** {@code font} is null when the marker keeps its own font. */
    static BulletGlyph of(String text, String markerFont) {
        if (text == null || text.isEmpty()) {
            return new BulletGlyph(text, null);
        }
        char c = text.charAt(0);
        if (c < '' || c > '') {
            return new BulletGlyph(text, null);
        }
        if (symbolFont(markerFont)) {
            return new BulletGlyph(text, null);
        }
        String font = symbolFontFor(c);
        // An icon font (Font Awesome and the like) shares the range; its marker has no font here.
        return font != null ? new BulletGlyph(text, font) : new BulletGlyph(BULLET, null);
    }

    /**
     * The symbol font that draws a known Symbol or Wingdings list bullet stored as Unicode (U+F0xx),
     * or null: other code points in the range may be an icon font's, which Symbol would draw as
     * something else.
     */
    static String symbolFontFor(char c) {
        return c == SYMBOL_BULLET ? "Symbol" : WINGDINGS.contains(c) ? "Wingdings" : null;
    }

    static boolean symbolFont(String font) {
        if (font == null) {
            return false;
        }
        String f = font.toLowerCase(Locale.ROOT).replace(" ", "");
        return f.equals("symbol") || f.startsWith("wingdings") || f.startsWith("webdings") || f.contains("dingbats");
    }
}
