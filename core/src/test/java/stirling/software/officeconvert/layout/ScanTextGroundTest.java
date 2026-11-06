package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;

/**
 * Which regions of a scan's ink are the ground of a panel of text rather than a picture (ScanRegions.textGround()). A phone
 * photo of CDC's HPV fact sheet has a grey sidebar of light text; the grey around its last address block read as a
 * picture, which took the block's words with it.
 */
class ScanTextGroundTest {

    /** The grey sidebar's last block, as the photo's OCR layer has it (two lines boxed at twice the others' size). */
    private static List<Line> addressBlock() {
        return List.of(line("American Sexual Health", 406, 555, 7.2f), line("Association (ASHA)", 407, 567, 6.9f),
                line("http://www.ashastd.org/std-sti/", 404, 579, 7.5f), line("hpv.html", 406, 591, 7.2f),
                line("P.O. Box 13827", 407, 603, 7.7f), line("Research Triangle Park,", 406, 615, 7.2f),
                line("NC 27709-3827", 406, 631, 15.9f), line("1-800-783-9877", 407, 644, 16.2f));
    }

    @Test
    void aPanelsStackedLinesMakeItTheirGround() {
        assertTrue(ScanRegions.textGround(new Box(399, 537, 559, 657), addressBlock()));
    }

    @Test
    void aChartsLegendStacksButCoversLittleOfIt() {
        List<Line> lines = new ArrayList<>();
        lines.add(line("Imports", 320, 130, 7));
        lines.add(line("Exports", 320, 142, 7));
        lines.add(line("Balance", 320, 154, 7));
        for (int i = 0; i < 5; i++) {
            lines.add(line(String.valueOf(10 * i), 106, 290 - 45 * i, 7));
        }
        lines.add(line("Billions of dollars", 150, 296, 7));
        assertFalse(ScanRegions.textGround(new Box(100, 100, 400, 300), lines), "a chart's labels and legend");
    }

    @Test
    void aPhotosCaptionIsNotItsGround() {
        List<Line> caption = List.of(line("Field crews measure the river", 110, 220, 7),
                line("below the dam in the spring", 110, 232, 7), line("of 2024", 110, 244, 7));
        assertFalse(ScanRegions.textGround(new Box(100, 60, 340, 250), caption),
                "three stacked lines over a twentieth of a photo");
    }

    @Test
    void labelsApartAreNot() {
        // Large labels over a fifth of a plan, none under another
        List<Line> labels = List.of(line("North field", 105, 120, 12), line("Pump house", 190, 145, 12),
                line("Old barn", 110, 170, 12), line("River bank", 190, 195, 12), line("Track", 120, 215, 12));
        assertFalse(ScanRegions.textGround(new Box(100, 100, 260, 220), labels), "a plan's labels stand apart");
    }

    @Test
    void linesWhoseWordsStayOutsideDontCount() {
        // A column of text beside a picture, ending just short of it: no word's middle is inside
        List<Line> column = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            column.add(line("text that runs up to the picture", 250, 412 + 12 * i, 7));
        }
        assertFalse(ScanRegions.textGround(new Box(360, 400, 560, 500), column));
    }

    /**
     * A line of OCR text: glyphs half an em wide with OCRmyPDF's NotoSans metrics (ascent 1.069, descent 0.293 of the
     * size), so lines 12 points apart at 7 points stand at a paragraph's pitch, as the photo's do.
     */
    private static Line line(String text, float x, float baseline, float size) {
        List<Glyph> glyphs = new ArrayList<>();
        float advance = size * 0.5f;
        for (char c : text.toCharArray()) {
            glyphs.add(new Glyph(String.valueOf(c), x, advance, baseline, size, size * 1.069f, size * 0.293f,
                    FontInfo.DEFAULT, 0, glyphs.size(), size * 0.25f, false, false));
            x += advance;
        }
        return LineBuilder.build(glyphs).getFirst();
    }
}
