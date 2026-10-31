package stirling.software.officeconvert.slides;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.Table;

/**
 * An OCR layer's words at the size that fills their boxes in their face's own widths. OCRmyPDF sizes a line's text from
 * its ink, about 0.73 of the type's size, and each word is then stretched to its box with a horizontal scale
 * ({@code OcrWords}), about 137%. Word documents set that scale on the characters. Slides have no character scale, so it
 * became letter spacing ({@code TextXml.spacing}): a scan's words came out small with their letters spread apart, and
 * PowerPoint's own PDF of them read as single letters. Here the page's usual scale goes into the size of its OCR text
 * instead, which sets the words at about the scan's size with their letters together; only each word's difference from
 * that scale stays as spacing. One factor for the page, not one per paragraph: a table cell holds a word or two, whose
 * boxes vary, and cells sized each by their own came out at sizes that jumped from row to row. A slide's text boxes don't
 * flow, so the larger size can't push anything down.
 */
final class OcrSizes {

    /** A page whose usual scale is this close to 100% keeps its sizes. */
    private static final float SAME = 0.03f;

    private OcrSizes() {}

    /** The usual horizontal scale of a page's OCR text: the median of its invisible glyphs', or 1 without any. */
    static float factor(Collection<Glyph> glyphs) {
        List<Float> scales = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.invisible && !g.isSpace()) {
                scales.add(g.hscale);
            }
        }
        if (scales.isEmpty()) {
            return 1f;
        }
        scales.sort(null);
        float k = scales.get(scales.size() / 2);
        return Math.abs(k - 1f) < SAME ? 1f : Math.clamp(k, 0.5f, 2f);
    }

    /** {@code size} grown by {@code k}, to the half point. */
    static float grown(float size, float k) {
        return k == 1f ? size : Math.max(1f, Math.round(size * k * 2f) / 2f);
    }

    /** Each run's size grown by {@code k}, and its scale shrunk to keep its width. */
    static void apply(Paragraph p, float k) {
        if (k == 1f) {
            return;
        }
        for (int i = 0; i < p.inlines.size(); i++) {
            Inline in = p.inlines.get(i);
            Inline out = switch (in) {
                case Inline.Text t -> new Inline.Text(t.text(), grow(t.style(), k), t.link(), t.anchorPage());
                case Inline.Tab t -> new Inline.Tab(grow(t.style(), k));
                default -> in;
            };
            p.inlines.set(i, out);
        }
        if (p.markStyle != null) {
            p.markStyle = grow(p.markStyle, k);
        }
    }

    /** Every cell paragraph of a table on a page whose text is its OCR layer. */
    static void apply(Table t, float k) {
        for (Table.Row row : t.rows) {
            for (Table.Cell cell : row.cells) {
                for (Paragraph p : cell.paragraphs) {
                    apply(p, k);
                }
            }
        }
    }

    private static RunStyle grow(RunStyle s, float k) {
        if (s == null) {
            return null;
        }
        float size = grown(s.size(), k);
        int scale = Math.clamp(Math.round(s.scale() * s.size() / size), 1, 600);
        return new RunStyle(s.font(), size, s.bold(), s.italic(), s.underline(), s.strike(), s.rgb(), s.highlight(),
                s.vertAlign(), s.symbol(), s.spacing(), scale, s.smallCaps(), s.border());
    }
}
