package stirling.software.officeconvert.slides;

import java.util.ArrayList;
import java.util.List;

import stirling.software.officeconvert.extract.Glyph;
import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.layout.Word;
import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.model.Table;

/**
 * An OCR layer's words at the size that fills their boxes in their face's own widths. OCRmyPDF sizes a line's text from
 * its ink, about 0.73 of the type's size, and each word is then stretched to its box with a horizontal scale
 * ({@code OcrWords}), about 137%. Word documents set that scale on the characters. Slides have no character scale, so it
 * became letter spacing ({@code TextXml.spacing}): a scan's words came out small with their letters spread apart, and
 * PowerPoint's own PDF of them read as single letters. Here the usual scale goes into the size instead, which sets the
 * words at about the scan's size with their letters together; only each word's difference from it stays as spacing.
 * <ul>
 *   <li>A paragraph takes its own glyphs' usual scale: a letterhead set wider than the body below it, sized by the
 *       page's, came out letter-spaced again.</li>
 *   <li>A table takes one for all its cells. A cell holds a word or two whose boxes vary, and cells sized each by
 *       their own words jumped from one size to another down a statement's rows. A cell's letters are never spread
 *       to fill its box: its text sits at its edge, and spread letters read as single letters.</li>
 * </ul>
 * A slide's text boxes don't flow, so the larger size can't push anything down.
 */
final class OcrSizes {

    /** Text whose usual scale is this close to 100% keeps its size. */
    private static final float SAME = 0.03f;

    private OcrSizes() {}

    /** A paragraph's usual scale: the median of its glyphs'. */
    static float factor(ParaDraft d) {
        List<Float> scales = new ArrayList<>();
        add(d, scales);
        return median(scales);
    }

    /** A table's usual scale: the median of all its cells' glyphs'. */
    static float factor(List<List<ParaDraft>> cells) {
        List<Float> scales = new ArrayList<>();
        for (List<ParaDraft> cell : cells) {
            for (ParaDraft d : cell) {
                add(d, scales);
            }
        }
        return median(scales);
    }

    private static void add(ParaDraft d, List<Float> scales) {
        for (Line l : d.lines) {
            for (Word w : l.words) {
                for (Glyph g : w.glyphs) {
                    if (!g.isSpace()) {
                        scales.add(g.hscale);
                    }
                }
            }
        }
    }

    private static float median(List<Float> scales) {
        if (scales.isEmpty()) {
            return 1f;
        }
        scales.sort(null);
        float k = scales.get(scales.size() / 2);
        return Math.abs(k - 1f) < SAME ? 1f : Math.clamp(k, 0.5f, 2f);
    }

    /**
     * Whether a table's text is all an OCR layer's, so its cells are grown. A table printed on the page among the OCR
     * text already has its sizes, and grown it would overrun its cells.
     */
    static boolean unseen(List<List<ParaDraft>> cells) {
        boolean any = false;
        for (List<ParaDraft> cell : cells) {
            for (ParaDraft d : cell) {
                for (Line l : d.lines) {
                    for (Word w : l.words) {
                        for (Glyph g : w.glyphs) {
                            if (!g.isSpace()) {
                                if (!g.invisible) {
                                    return false;
                                }
                                any = true;
                            }
                        }
                    }
                }
            }
        }
        return any;
    }

    /** {@code size} grown by {@code k}, to the half point. */
    static float grown(float size, float k) {
        return k == 1f ? size : Math.max(1f, Math.round(size * k * 2f) / 2f);
    }

    /**
     * Each run's size grown by {@code k}, and its scale shrunk to keep its width; with {@code spread} false, to no more
     * than 100%, so its letters are never spread apart.
     */
    static void apply(Paragraph p, float k, boolean spread) {
        if (k == 1f) {
            return;
        }
        for (int i = 0; i < p.inlines.size(); i++) {
            Inline in = p.inlines.get(i);
            Inline out = switch (in) {
                case Inline.Text t -> new Inline.Text(t.text(), grow(t.style(), k, spread), t.link(), t.anchorPage());
                case Inline.Tab t -> new Inline.Tab(grow(t.style(), k, spread));
                default -> in;
            };
            p.inlines.set(i, out);
        }
        if (p.markStyle != null) {
            p.markStyle = grow(p.markStyle, k, spread);
        }
    }

    /** Every cell paragraph of a table whose text is all an OCR layer's ({@link #unseen}), by the table's scale. */
    static void apply(Table t, float k) {
        for (Table.Row row : t.rows) {
            for (Table.Cell cell : row.cells) {
                for (Paragraph p : cell.paragraphs) {
                    apply(p, k, false);
                }
            }
        }
    }

    private static RunStyle grow(RunStyle s, float k, boolean spread) {
        if (s == null) {
            return null;
        }
        float size = grown(s.size(), k);
        int scale = Math.clamp(Math.round(s.scale() * s.size() / size), 1, spread ? 600 : 100);
        return new RunStyle(s.font(), size, s.bold(), s.italic(), s.underline(), s.strike(), s.rgb(), s.highlight(),
                s.vertAlign(), s.symbol(), s.spacing(), scale, s.smallCaps(), s.border());
    }
}
