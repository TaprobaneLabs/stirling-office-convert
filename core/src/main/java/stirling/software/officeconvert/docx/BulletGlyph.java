package stirling.software.officeconvert.docx;

import java.util.Locale;
import java.util.Set;

/**
 * A list bullet's character and the font Word must draw it in. Word and LibreOffice store Symbol
 * and Wingdings bullets in the private-use range U+F020 to U+F0FF, which only those fonts map; in
 * any other font Word draws an empty box. A PDF from LibreOffice sets such a bullet in OpenSymbol,
 * which the document names by an Office family, so the bullet goes back to the symbol font it came
 * from, as Word stores its own bullets. Other private-use characters have no font Word knows, so they
 * become a plain bullet. {@link RunXml} does the same for such characters in body text, such as a
 * bullet kept as text before a tab.
 */
record BulletGlyph(String text, String font) {

    private static final String BULLET = "•";

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
        if (!symbolRange(c)) {
            return new BulletGlyph(BULLET, null);
        }
        if (symbolFont(markerFont)) {
            return new BulletGlyph(text, null);
        }
        return new BulletGlyph(text, symbolFontFor(c));
    }

    /** Where Symbol and Wingdings characters sit when a document stores them as Unicode. */
    static boolean symbolRange(char c) {
        return c >= '' && c <= '';
    }

    /** The font that draws a character of {@link #symbolRange}: Wingdings for its bullets, else Symbol. */
    static String symbolFontFor(char c) {
        return WINGDINGS.contains(c) ? "Wingdings" : "Symbol";
    }

    static boolean symbolFont(String font) {
        if (font == null) {
            return false;
        }
        String f = font.toLowerCase(Locale.ROOT).replace(" ", "");
        return f.equals("symbol") || f.startsWith("wingdings") || f.startsWith("webdings") || f.contains("dingbats");
    }
}
