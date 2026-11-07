package stirling.software.officeconvert.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.build.PlacedContent;
import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.DocStats;
import stirling.software.officeconvert.layout.LineBuilder;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture;

class TextFramesFitTest {

    private static final FontInfo TIMES = new FontInfo("Times New Roman", false, false, true, false, false,
            "TimesNewRomanPSMT", false);
    private static final float SIZE = 10f;
    private static final float LEFT = 72f;
    /** Lines of one width, so each is set as tight as the others. */
    private static final String[] LINES = {
        "the rule takes effect on the date of its publication in the Register",
        "the rule takes effect on the date of its publication in the Register",
        "the rule takes effect on the date of its publication in the Register"};

    /**
     * A justified paragraph whose PDF sets each line's words closer than a space apart, as a Federal Register column
     * does: at its natural spacing every line runs past the box and PowerPoint wraps it, a line more for each. The
     * fit narrows each line's characters, as the DOCX writer does, until it holds.
     */
    @Test
    void aTightlyJustifiedParagraphKeepsItsLinesInItsBox() throws IOException {
        float right = tightRight("Times New Roman", 1.5f);
        ParaDraft d = justified(right, TIMES, "Times New Roman");
        try (PDDocument doc = new PDDocument()) {
            TextShape shape = frame(doc, d);
            TextPara p = shape.paras().getFirst();
            float room = shape.frame().width() - shape.insetLeft() - shape.insetRight() - p.marginLeft() - p.marginRight();
            float[] spacing = spacingPerChar(p.content());
            int at = 0;
            for (String line : LINES) {
                float width = SubstituteMetrics.width(line, "Times New Roman", false, false, SIZE);
                for (int i = 0; i < line.length(); i++) {
                    width += spacing[at + i];
                }
                assertTrue(width <= room + 0.05f, "\"" + line + "\" " + width + " in " + room);
                at += line.length() + 1;
            }
        }
    }

    /**
     * A paragraph in Franklin Gothic Book, which the PDF set and PowerPoint has: the slide names it in place of its
     * Arial stand-in, with no spacing or scale, so a fit measured in Arial would pair its margin with other widths. It
     * isn't fitted: its margin stays where its lines end, and its runs name the Office font. (A paragraph in one font
     * rarely moves the fit's edge, as its width fix and squeezes hold it at the preferred one, but a fit that does
     * would leave a restored paragraph's margin for widths it no longer has.)
     */
    @Test
    void aParagraphInARestoredOfficeFontIsntFitted() throws IOException {
        FontInfo franklin = new FontInfo("Arial", false, false, false, false, false, "FranklinGothic-Book", true);
        String[] lines = {"the rule takes effect on the date", "the rule takes effect on the date",
            "the rule takes effect on the date"};
        float right = tightRight(lines, "Arial", 20f, 9f);
        ParaDraft d = justified(lines, right, franklin, "Arial", 20f);
        assertTrue(OfficeFonts.restores(d));
        assertFalse(OfficeFonts.restores(justified(tightRight("Times New Roman", 1.5f), TIMES, "Times New Roman")));
        try (PDDocument doc = new PDDocument()) {
            TextShape shape = frame(doc, d);
            TextPara p = shape.paras().getFirst();
            assertEquals(right, shape.frame().x() + shape.frame().width() - shape.insetRight() - p.marginRight(), 0.05f);
            for (Inline in : p.content().inlines) {
                if (in instanceof Inline.Text t) {
                    assertEquals("Franklin Gothic Book", t.style().font());
                }
            }
        }
    }

    private static TextShape frame(PDDocument doc, ParaDraft d) throws IOException {
        PlacedContent content = new PlacedContent(doc, new DocStats(), new NoMedia(), 150f, false);
        List<TextFrames.Framed> framed = new TextFrames(content, 612f).frames(List.of(d), new ArrayList<>(), null);
        assertEquals(1, framed.size());
        return framed.getFirst().shape();
    }

    /** A right edge {@code by} points short of every line's natural width. */
    private static float tightRight(String[] lines, String family, float size, float by) {
        float right = Float.MAX_VALUE;
        for (String line : lines) {
            right = Math.min(right, LEFT + SubstituteMetrics.width(line, family, false, false, size) - by);
        }
        return right;
    }

    private static float tightRight(String family, float by) {
        return tightRight(LINES, family, SIZE, by);
    }

    private static ParaDraft justified(float right, FontInfo font, String family) {
        return justified(LINES, right, font, family, SIZE);
    }

    /** The lines justified to {@code right}: each word at its own width, the spaces between them shared out. */
    private static ParaDraft justified(String[] lines, float right, FontInfo font, String family, float size) {
        List<Glyph> glyphs = new ArrayList<>();
        for (int r = 0; r < lines.length; r++) {
            String[] words = lines[r].split(" ");
            float letters = 0;
            for (String w : words) {
                letters += SubstituteMetrics.width(w, family, false, false, size);
            }
            float gap = (right - LEFT - letters) / (words.length - 1);
            float x = LEFT;
            for (int wi = 0; wi < words.length; wi++) {
                for (char c : words[wi].toCharArray()) {
                    float w = SubstituteMetrics.width(String.valueOf(c), family, false, false, size);
                    glyphs.add(new Glyph(String.valueOf(c), x, w, 100 + 1.2f * size * r, size, 0.7f * size, 0.2f * size,
                            font, 0, glyphs.size(), 0.25f * size, false, false));
                    x += w;
                }
                x += gap;
            }
        }
        ParaDraft d = new ParaDraft(LEFT, right);
        d.lines.addAll(LineBuilder.build(glyphs));
        d.align = Paragraph.Align.JUSTIFY;
        d.justifySlack = 1.5f;
        d.pitch = 1.2f * size;
        return d;
    }

    /** Each character's character spacing in points, in the paragraph's text order. */
    private static float[] spacingPerChar(Paragraph p) {
        List<Float> out = new ArrayList<>();
        for (Inline in : p.inlines) {
            if (in instanceof Inline.Text t) {
                // At scale 100 the spacing PowerPoint gets is the run's own
                assertEquals(100, t.style().scale());
                float spc = t.style().spacing();
                for (int i = 0; i < t.text().length(); i++) {
                    out.add(spc);
                }
            }
        }
        float[] a = new float[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    private static final class NoMedia implements PlacedContent.MediaStore {

        @Override
        public Picture.MediaRef media(Object key) {
            return null;
        }

        @Override
        public Picture.MediaRef media(byte[] bytes, String ext, int pixelWidth, int pixelHeight, Object key) {
            return null;
        }
    }
}
