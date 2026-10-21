package stirling.software.officeconvert.sheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import stirling.software.officeconvert.layout.Line;
import stirling.software.officeconvert.layout.ParaDraft;
import stirling.software.officeconvert.layout.Word;

final class ColumnSplit {

    /** Text between wider gaps in a one-line cell, with its words' extents. */
    record Piece(String text, float x, float right, List<Piece> words) {}

    private static final float AGREE = 0.6f;

    private static final Pattern NUMBER = Pattern.compile("[-+−(]?[$€£]?\\d[\\d,.]*%?\\)?");

    private ColumnSplit() {}

    static List<Piece> pieces(List<ParaDraft> paras) {
        if (paras.size() != 1 || paras.getFirst().lines.size() != 1) {
            return null;
        }
        Line line = paras.getFirst().lines.getFirst();
        if (line.words.isEmpty() || line.words.stream().anyMatch(w -> w.glyphs.stream().anyMatch(LogicalText::isRtl))) {
            return null;
        }
        List<Piece> out = new ArrayList<>();
        List<Piece> words = new ArrayList<>();
        for (int i = 0; i < line.words.size(); i++) {
            if (i > 0 && line.gaps[i] != Line.SPACE) {
                out.add(piece(words));
                words = new ArrayList<>();
            }
            Word w = line.words.get(i);
            words.add(new Piece(w.text, w.x, w.right, List.of()));
        }
        out.add(piece(words));
        return out;
    }

    private static Piece piece(List<Piece> words) {
        StringBuilder sb = new StringBuilder();
        for (Piece w : words) {
            sb.append(sb.isEmpty() ? "" : " ").append(w.text());
        }
        return new Piece(sb.toString(), words.getFirst().x(), words.getLast().right(), List.copyOf(words));
    }

    /**
     * A piece across a cut is split at the cut when every word is a number: two quarters' figures a space apart
     * ("3.3 7.0") under a year whose quarters have no rules between them. Labels stay whole.
     */
    static List<Piece> atCuts(Piece p, float[] cuts) {
        if (p.words().size() < 2 || part(cuts, p.x()) == part(cuts, p.words().getLast().x())
                || !p.words().stream().allMatch(w -> NUMBER.matcher(w.text()).matches())) {
            return List.of(p);
        }
        List<Piece> out = new ArrayList<>();
        List<Piece> run = new ArrayList<>();
        for (Piece w : p.words()) {
            if (!run.isEmpty() && part(cuts, w.x()) != part(cuts, run.getFirst().x())) {
                out.add(piece(run));
                run = new ArrayList<>();
            }
            run.add(w);
        }
        out.add(piece(run));
        return out;
    }

    static List<float[]> plan(int cols, List<Integer> colOf, List<Integer> spanOf, List<List<Piece>> pieces) {
        List<float[]> out = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            Map<Integer, Integer> counts = new HashMap<>();
            int filled = 0;
            for (int i = 0; i < pieces.size(); i++) {
                if (colOf.get(i) == c && spanOf.get(i) == 1 && pieces.get(i) != null) {
                    filled++;
                    counts.merge(pieces.get(i).size(), 1, Integer::sum);
                }
            }
            int n = 1;
            int best = 0;
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                if (e.getKey() >= 2 && (e.getValue() > best || e.getValue() == best && e.getKey() < n)) {
                    n = e.getKey();
                    best = e.getValue();
                }
            }
            out.add(n < 2 || best < 2 || best < AGREE * filled ? new float[0] : cuts(c, n, colOf, spanOf, pieces));
        }
        return out;
    }

    private static float[] cuts(int c, int n, List<Integer> colOf, List<Integer> spanOf, List<List<Piece>> pieces) {
        float[] cut = new float[n - 1];
        for (int k = 0; k + 1 < n; k++) {
            float end = -Float.MAX_VALUE;
            float start = Float.MAX_VALUE;
            for (int i = 0; i < pieces.size(); i++) {
                List<Piece> p = pieces.get(i);
                if (colOf.get(i) == c && spanOf.get(i) == 1 && p != null && p.size() == n) {
                    end = Math.max(end, p.get(k).right());
                    start = Math.min(start, p.get(k + 1).x());
                }
            }
            if (end >= start - 1f) {
                return new float[0];
            }
            cut[k] = (end + start) / 2f;
        }
        return cut;
    }

    static int part(float[] cuts, float x) {
        int k = 0;
        while (k < cuts.length && x >= cuts[k]) {
            k++;
        }
        return k;
    }
}
