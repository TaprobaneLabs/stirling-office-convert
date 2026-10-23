package stirling.software.officeconvert.build;

import java.io.IOException;
import java.util.List;

import stirling.software.officeconvert.model.Block;
import stirling.software.officeconvert.model.Numbering;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.Picture.MediaRef;
import stirling.software.officeconvert.model.Section;
import stirling.software.officeconvert.model.StyleSheet;

public interface DocSink {

    MediaRef media(byte[] bytes, String ext, int pixelWidth, int pixelHeight, Object key)
            throws IOException;

    MediaRef media(Object key);

    void begin(StyleSheet styles, HeaderFooterSet running);

    void block(Block block) throws IOException;

    void footnote(int id, List<Paragraph> paragraphs);

    /**
     * Whether this output sets text where the page has it, in text boxes, so a figure's labels can stay text over a
     * picture drawn without them. A plain-text output reads a picture's description instead, which holds them already.
     */
    default boolean placesText() {
        return true;
    }

    record HeaderFooterSet(
            List<Paragraph> header,
            List<Paragraph> footer,
            List<Paragraph> evenHeader,
            List<Paragraph> evenFooter,
            boolean titlePage) {}

    void finish(
            Section last,
            HeaderFooterSet running,
            Numbering numbering,
            StyleSheet styles,
            String title,
            String author)
            throws IOException;
}
