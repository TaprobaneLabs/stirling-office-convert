package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.extract.PageData;

class UnsampledCopyTest {

    private static final String HEADER = "Annual Report on Program Operations FY2022";

    private static PageData page(int index) {
        return new PageData(index, 612, 792, 0, List.of(), List.of(), List.of(), null, List.of());
    }

    private static List<Line> lines(String header, float headerBaseline) {
        List<Glyph> glyphs = new ArrayList<>();
        place(glyphs, header, 150, headerBaseline, 9);
        for (int i = 0; i < 30; i++) {
            place(glyphs, "The agency spends most of its budget on direct services to tribes", 72, 120 + 18 * i, 11);
        }
        return LineBuilder.build(glyphs);
    }

    private static void place(List<Glyph> out, String text, float x, float baseline, float size) {
        for (char c : text.toCharArray()) {
            float w = (c == ' ' ? 0.25f : 0.5f) * size;
            out.add(new Glyph(String.valueOf(c), x, w, baseline, size, size * 0.75f, size * 0.25f, FontInfo.DEFAULT, 0,
                    out.size(), 0.25f * size, false, false));
            x += w;
        }
    }

    @Test
    void aLineInACopysSlotOnAnUnsampledPageNeedsTheCopysFixedDigits() {
        HeaderFooter hf = new HeaderFooter();
        for (int p = 0; p < 6; p++) {
            hf.addPage(page(p), lines(HEADER, 30));
        }
        // A contents page carries the header lower down: a copy of it
        hf.addPage(page(6), lines(HEADER, 50));
        hf.finish(40);

        List<Line> same = lines(HEADER, 50);
        assertEquals(1, hf.furniture(page(30), same).size(), "the copy in its slot on a page not sampled is set apart");
        List<Line> other = lines(HEADER.replace("FY2022", "FY2021"), 50);
        assertEquals(0, hf.furniture(page(31), other).size(), "a line there with another year isn't a copy");
    }

    @Test
    void aSectionNumberThatChangesIsNoPageNumber() {
        for (String copy : new String[] {"Quarterly Results for Quarter 2", "Quarterly Results for Quarter 3"}) {
            HeaderFooter hf = new HeaderFooter();
            for (int p = 0; p < 6; p++) {
                hf.addPage(page(p), lines("Quarterly Results for Quarter " + (p < 3 ? 1 : 2), 30));
            }
            hf.addPage(page(6), lines(copy, 50));
            hf.finish(7);
            boolean seen = copy.endsWith("2");
            assertEquals(seen ? 1 : 0, hf.furniture(page(6), lines(copy, 50)).size(),
                    seen ? "a copy of a quarter's header is set apart" : "a quarter no header had isn't a copy");
        }
    }
}
