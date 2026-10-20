package stirling.software.officeconvert.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Advance widths of the fonts that come with Windows and Office (Cambria, Calibri, Georgia, …), for measuring
 * text Word will draw in one of them. The table is office-fonts.tsv, which topdf's OfficeFonts also reads and
 * describes: per family and style, two base-36 digits per character of {@link #RANGES}, in thousandths of an em.
 */
final class OfficeWidths {

    private static final int[][] RANGES = {{0x20, 0x7E}, {0xA0, 0x17F}, {0x192, 0x192}, {0x2C6, 0x2C7},
            {0x2D8, 0x2DD}, {0x384, 0x3CE}, {0x400, 0x45F}, {0x490, 0x491}, {0x2013, 0x2014}, {0x2018, 0x201E},
            {0x2020, 0x2022}, {0x2026, 0x2026}, {0x2030, 0x2030}, {0x2039, 0x203A}, {0x20AC, 0x20AC},
            {0x2116, 0x2116}, {0x2122, 0x2122}};

    private static final int CHARS = count();

    private static final int MISSING = 36 * 36 - 1;

    // Family, lower case, to its four styles (2 bold, 1 italic, 3 both, as in the table); null where it has none
    private static final Map<String, short[][]> TABLE = load();

    private OfficeWidths() {}

    /** The width of {@code text} in points, or NaN when the table lacks the family, the style or a character. */
    static float width(String text, String family, boolean bold, boolean italic, float size) {
        short[][] styles = family == null ? null : TABLE.get(family.toLowerCase(Locale.ROOT));
        short[] advances = styles == null ? null : styles[(bold ? 2 : 0) + (italic ? 1 : 0)];
        if (advances == null) {
            return Float.NaN;
        }
        float total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int at = index(cp);
            if (at < 0 || advances[at] < 0) {
                return Float.NaN;
            }
            total += advances[at];
        }
        return total / 1000f * size;
    }

    private static int index(int codePoint) {
        int base = 0;
        for (int[] r : RANGES) {
            if (codePoint < r[0]) {
                return -1;
            }
            if (codePoint <= r[1]) {
                return base + codePoint - r[0];
            }
            base += r[1] - r[0] + 1;
        }
        return -1;
    }

    private static int count() {
        int n = 0;
        for (int[] r : RANGES) {
            n += r[1] - r[0] + 1;
        }
        return n;
    }

    private static Map<String, short[][]> load() {
        Map<String, short[][]> m = new HashMap<>();
        try (InputStream in = OfficeWidths.class.getResourceAsStream("office-fonts.tsv")) {
            if (in == null) {
                return Map.of();
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t");
                if (f.length != 6 && f.length != 10 || f[5].length() != 2 * CHARS) {
                    continue;
                }
                short[] advances = new short[CHARS];
                for (int i = 0; i < CHARS; i++) {
                    int v = Integer.parseInt(f[5], 2 * i, 2 * i + 2, 36);
                    advances[i] = (short) (v == MISSING ? -1 : v);
                }
                m.computeIfAbsent(f[0].strip().toLowerCase(Locale.ROOT), k -> new short[4][])[Integer.parseInt(f[1]) & 3] =
                        advances;
            }
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
        return m;
    }
}
