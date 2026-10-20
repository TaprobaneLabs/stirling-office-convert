package stirling.software.officeconvert.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.junit.jupiter.api.Test;

class UnembeddedFontTest {

    @Test
    void unembeddedTimesStaysTimes() throws IOException {
        FontInfo info = new FontResolver().resolve(unembedded("TimesNewRomanPSMT", 34));
        assertEquals("Times New Roman", info.family());
        assertTrue(info.serif());
        assertFalse(info.substituted());
    }

    @Test
    void unembeddedStylesKeepTheirFamily() throws IOException {
        FontResolver fonts = new FontResolver();
        FontInfo bold = fonts.resolve(unembedded("TimesNewRomanPS-BoldMT", 34));
        assertEquals("Times New Roman", bold.family());
        assertTrue(bold.bold());
        FontInfo italic = fonts.resolve(unembedded("TimesNewRomanPS-ItalicMT", 98));
        assertEquals("Times New Roman", italic.family());
        assertTrue(italic.italic());
    }

    @Test
    void unembeddedOfficeFontsAreExact() throws IOException {
        // Word draws them with the font the PDF's widths came from, so text set in them keeps its page breaks
        assertTrue(new FontResolver().resolve(unembedded("TimesNewRomanPSMT", 34)).exact());
        assertTrue(new FontResolver().resolve(unembedded("CourierNewPSMT", 33)).exact());
        assertFalse(new FontResolver().resolve(unembedded("MinionPro-Regular", 34)).exact(), "a stand-in isn't exact");
    }

    @Test
    void unembeddedCourierStaysCourier() throws IOException {
        assertEquals("Courier New", new FontResolver().resolve(unembedded("CourierNewPSMT", 33)).family());
    }

    /** A TrueType font as Word's PDF export writes one it was not allowed to embed: name, widths, descriptor. */
    private static PDTrueTypeFont unembedded(String name, int flags) throws IOException {
        COSArray widths = new COSArray();
        for (int c = 32; c <= 126; c++) {
            widths.add(COSInteger.get(500));
        }
        COSDictionary descriptor = new COSDictionary();
        descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, name);
        descriptor.setInt(COSName.FLAGS, flags);
        COSArray box = new COSArray();
        for (float v : new float[] {-568, -216, 2046, 891}) {
            box.add(new COSFloat(v));
        }
        descriptor.setItem(COSName.FONT_BBOX, box);
        descriptor.setInt(COSName.ITALIC_ANGLE, (flags & 64) != 0 ? -16 : 0);
        descriptor.setInt(COSName.ASCENT, 891);
        descriptor.setInt(COSName.DESCENT, -216);
        descriptor.setInt(COSName.CAP_HEIGHT, 662);
        descriptor.setInt(COSName.STEM_V, 80);
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TRUE_TYPE);
        font.setName(COSName.BASE_FONT, name);
        font.setInt(COSName.FIRST_CHAR, 32);
        font.setInt(COSName.LAST_CHAR, 126);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);
        font.setItem(COSName.FONT_DESC, descriptor);
        return new PDTrueTypeFont(font);
    }
}
