package stirling.software.officeconvert.pptx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.RunStyle;

class TextXmlTabsTest {

    private static final RunStyle PLAIN = new RunStyle("Arial", 9, false, false, false, false, 0, -1, 0, false);

    private static final RunStyle UNDERLINED = new RunStyle("Arial", 9, false, false, true, false, 0, -1, 0, false);

    private static String runs(List<Inline> inlines) {
        StringBuilder sb = new StringBuilder();
        new TextXml(new SlideRels("../slideLayouts/slideLayout1.xml"), page -> 0, new HashMap<>(), null,
                new OpenFaces(Map.of())).runs(sb, inlines);
        return sb.toString();
    }

    private static int tabs(String xml) {
        int n = 0;
        for (Matcher m = Pattern.compile("<a:t>\t</a:t>").matcher(xml); m.find();) {
            n++;
        }
        return n;
    }

    /**
     * A Loan Estimate's "Conventional, FHA, VA" line, whose checkbox glyph and drawn blank after VA are left out: its
     * last tab found no stop before the cell's edge, and PowerPoint put it on a line of its own.
     */
    @Test
    void aParagraphsTrailingTabsAreLeftOut() {
        String xml = runs(List.of(new Inline.Text("Conventional", PLAIN, null, -1), new Inline.Tab(PLAIN),
                new Inline.Text("FHA", PLAIN, null, -1), new Inline.Tab(PLAIN), new Inline.Text("VA", PLAIN, null, -1),
                new Inline.Tab(PLAIN), new Inline.Text(" ", PLAIN, null, -1), new Inline.Tab(null)));
        assertEquals(2, tabs(xml), xml);
    }

    @Test
    void anUnderlinedBlankAtTheEndStays() {
        String xml = runs(List.of(new Inline.Text("Name", PLAIN, null, -1), new Inline.Tab(UNDERLINED)));
        assertEquals(1, tabs(xml), xml);
    }
}
