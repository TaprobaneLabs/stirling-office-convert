package stirling.software.officeconvert.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        float right = Float.MAX_VALUE;
        for (String line : LINES) {
            right = Math.min(right, LEFT + natural(line) - 1.5f);
        }
        ParaDraft d = justified(right);
        try (PDDocument doc = new PDDocument()) {
            PlacedContent content = new PlacedContent(doc, new DocStats(), new NoMedia(), 150f, false);
            List<TextFrames.Framed> framed = new TextFrames(content, 612f).frames(List.of(d), new ArrayList<>(), null);
            assertEquals(1, framed.size());
            TextShape shape = framed.getFirst().shape();
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

    private static float natural(String line) {
        return SubstituteMetrics.width(line, "Times New Roman", false, false, SIZE);
    }

    /** The lines justified to {@code right}: each word at its own width, the spaces between them shared out. */
    private static ParaDraft justified(float right) {
        List<Glyph> glyphs = new ArrayList<>();
        for (int r = 0; r < LINES.length; r++) {
            String[] words = LINES[r].split(" ");
            float letters = 0;
            for (String w : words) {
                letters += natural(w);
            }
            float gap = (right - LEFT - letters) / (words.length - 1);
            float x = LEFT;
            for (int wi = 0; wi < words.length; wi++) {
                for (char c : words[wi].toCharArray()) {
                    float w = SubstituteMetrics.width(String.valueOf(c), "Times New Roman", false, false, SIZE);
                    glyphs.add(new Glyph(String.valueOf(c), x, w, 100 + 12 * r, SIZE, 7f, 2f, TIMES, 0, glyphs.size(),
                            2.5f, false, false));
                    x += w;
                }
                x += gap;
            }
        }
        ParaDraft d = new ParaDraft(LEFT, right);
        d.lines.addAll(LineBuilder.build(glyphs));
        d.align = Paragraph.Align.JUSTIFY;
        d.justifySlack = 1.5f;
        d.pitch = 12f;
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
