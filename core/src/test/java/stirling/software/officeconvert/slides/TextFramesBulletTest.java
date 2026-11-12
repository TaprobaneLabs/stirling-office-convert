package stirling.software.officeconvert.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture;

class TextFramesBulletTest {

    private static final FontInfo ARIAL = new FontInfo("Arial", false, false, false, false, false, "ArialMT", false);
    private static final float LEFT = 380f;
    private static final float TEXT = 11f;
    private static final float BULLET = 18f;
    private static final float PITCH = 13f;

    /**
     * NOAA GeoXO's panel: an 18-point bullet before 11-point text, its lines 13 points apart. The paragraph's size was its
     * first line's largest glyph, the bullet's, so the slide wrote the lines at 60% of 18-point spacing, which PowerPoint
     * takes of the 11-point runs: about 8 points, the lines over each other.
     */
    @Test
    void aBulletsOwnSizeDoesntSetItsParagraphsLineSpacing() throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        glyphs.add(glyph("•", LEFT, 500, BULLET, 0));
        word(glyphs, "Lightning", LEFT + 10, 500);
        word(glyphs, "detection", LEFT + 60, 500);
        word(glyphs, "storms", LEFT + 10, 500 + PITCH);
        word(glyphs, "predict", LEFT + 50, 500 + PITCH);
        ParaDraft d = new ParaDraft(LEFT, LEFT + 220);
        d.lines.addAll(LineBuilder.build(glyphs));
        d.align = Paragraph.Align.LEFT;
        d.pitch = PITCH;
        assertNotNull(Bullets.of(d));
        try (PDDocument doc = new PDDocument()) {
            PlacedContent content = new PlacedContent(doc, new DocStats(), new NoMedia(), 150f, false);
            List<TextFrames.Framed> framed = new TextFrames(content, 612f).frames(List.of(d), new ArrayList<>(), null);
            TextPara p = framed.getFirst().shape().paras().getFirst();
            assertEquals(TEXT, p.size(), 0.01f, p.toString());
            assertEquals(PITCH, p.lineHeight(), 0.3f, p.toString());
        }
    }

    private static void word(List<Glyph> glyphs, String text, float x, float baseline) {
        for (char c : text.toCharArray()) {
            Glyph g = glyph(String.valueOf(c), x, baseline, TEXT, glyphs.size());
            glyphs.add(g);
            x += g.width;
        }
    }

    private static Glyph glyph(String text, float x, float baseline, float size, int seq) {
        float w = SubstituteMetrics.width(text, "Arial", false, false, size);
        return new Glyph(text, x, w, baseline, size, 0.7f * size, 0.2f * size, ARIAL, 0, seq, 0.25f * size, false,
                false);
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
