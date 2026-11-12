package stirling.software.officeconvert.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.build.PlacedContent;
import stirling.software.officeconvert.build.SubstituteMetrics;
import stirling.software.officeconvert.extract.FontInfo;
import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.Box;
import stirling.software.officeconvert.layout.DocStats;
import stirling.software.officeconvert.layout.LineBuilder;
import stirling.software.officeconvert.layout.PageLayout;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture;

class TextFramesBoxedTest {

    private static final FontInfo ARIAL = new FontInfo("Arial", false, false, false, false, false, "ArialMT", false);
    private static final float SIZE = 9f;
    private static final float LEFT = 300f;
    private static final float PITCH = 11.5f;

    /**
     * USGS's "For more information" panel: an address of one-line paragraphs 11.5 points apart. A paragraph of one line
     * takes the page's usual pitch for its size, 23 points there, and only frames fitted it to its neighbours, so the
     * panel's lines came out twice as far apart as the PDF's and its last lines ran over the text below it.
     */
    @Test
    void aTextBoxsOneLineParagraphsKeepTheirPitch() throws IOException {
        List<ParaDraft> paras = new ArrayList<>();
        String[] lines = {"Director", "Survey", "Cajundome", "Lafayette"};
        for (int i = 0; i < lines.length; i++) {
            paras.add(line(lines[i], 500 + PITCH * i));
        }
        PageLayout.TextBoxItem tb = new PageLayout.TextBoxItem(new Box(LEFT - 6, 488, LEFT + 260, 545), -1, LEFT,
                LEFT + 250, paras, 0f, -1, 0f, false, true);
        try (PDDocument doc = new PDDocument()) {
            PlacedContent content = new PlacedContent(doc, new DocStats(), new NoMedia(), 150f, false);
            TextShape shape = new TextFrames(content, 612f).boxed(tb, new ArrayList<>(), null).shape();
            assertEquals(lines.length, shape.paras().size());
            for (TextPara p : shape.paras()) {
                assertEquals(PITCH, p.lineHeight(), 0.3f, p.toString());
            }
        }
    }

    /** One word on a line of its own, with the pitch the page's statistics give a paragraph of one line. */
    private static ParaDraft line(String word, float baseline) {
        List<Glyph> glyphs = new ArrayList<>();
        float x = LEFT;
        for (char c : word.toCharArray()) {
            float w = SubstituteMetrics.width(String.valueOf(c), "Arial", false, false, SIZE);
            glyphs.add(new Glyph(String.valueOf(c), x, w, baseline, SIZE, 0.7f * SIZE, 0.2f * SIZE, ARIAL, 0,
                    glyphs.size(), 0.25f * SIZE, false, false));
            x += w;
        }
        ParaDraft d = new ParaDraft(LEFT, LEFT + 250);
        d.lines.addAll(LineBuilder.build(glyphs));
        d.align = Paragraph.Align.LEFT;
        d.pitch = 2.5f * SIZE;
        return d;
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
