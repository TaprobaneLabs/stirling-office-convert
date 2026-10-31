package stirling.software.officeconvert.slides;

import java.util.ArrayList;
import java.util.Comparator;
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
 * PowerPoint's own PDF of them read as single letters. Here a paragraph's usual scale goes into its size instead, which
 * sets the words at about the scan's size with their letters together; only each word's difference from that scale
 * stays as spacing. A slide's text boxes don't flow, so the larger size can't push anything down.
 */
final class OcrSizes {

    /** A paragraph whose usual scale is this close to 100% keeps its size. */
    private static final float SAME = 0.03f;

    private OcrSizes() {}

    /** The usual horizontal scale of a paragraph's glyphs: their median, one vote per glyph. */
    static float factor(ParaDraft d) {
        List<Float> scales = new ArrayList<>();
        for (Line l : d.lines) {
            for (Word w : l.words) {
                for (Glyph g : w.glyphs) {
                    if (!g.isSpace()) {
                        scales.add(g.hscale);
                    }
                }
            }
        }
        if (scales.isEmpty()) {
            return 1f;
        }
        scales.sort(null);
        return settle(scales.get(scales.size() / 2));
    }

    /** The usual scale of a paragraph's runs, one vote per character that isn't a space. */
    static float factor(Paragraph p) {
        List<float[]> votes = new ArrayList<>();
        int total = 0;
        for (Inline in : p.inlines) {
            if (in instanceof Inline.Text t) {
                int n = (int) t.text().chars().filter(c -> !Character.isWhitespace(c)).count();
                if (n > 0) {
                    votes.add(new float[] {t.style().scale() / 100f, n});
                    total += n;
                }
            }
        }
        if (total == 0) {
            return 1f;
        }
        votes.sort(Comparator.comparingDouble(v -> v[0]));
        int seen = 0;
        for (float[] v : votes) {
            seen += (int) v[1];
            if (2 * seen > total) {
                return settle(v[0]);
            }
        }
        return 1f;
    }

    private static float settle(float k) {
        return Math.abs(k - 1f) < SAME ? 1f : Math.clamp(k, 0.5f, 2f);
    }

    /** The size a run or paragraph of {@code size} takes, grown by {@code k}, to the half point. */
    static float grown(float size, float k) {
        return Math.max(1f, Math.round(size * k * 2f) / 2f);
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

    /** Every cell paragraph of an OCR page's table, each by its own usual scale. */
    static void apply(Table t) {
        for (Table.Row row : t.rows) {
            for (Table.Cell cell : row.cells) {
                for (Paragraph p : cell.paragraphs) {
                    apply(p, factor(p));
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
