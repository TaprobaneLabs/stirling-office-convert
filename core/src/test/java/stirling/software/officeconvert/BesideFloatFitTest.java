package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A paragraph beside a picture keeps the PDF's line breaks on a page without flowing columns. It wasn't fitted there, as
 * the fit measured every line from the paragraph's indent to the column's edge, and Word, measuring a stand-in face, took a
 * word onto a line: the bullets beside NOAA's GeoXO icons and CMS's notices moved words between their lines. Beside a
 * picture on its left, Word starts each line where the picture leaves room, where the PDF's lines start, and the fit
 * measures from there. Beside one on its right, the picture's edge ends the lines unless the paragraph's own does first,
 * so the fit's edge, held inside the PDF's room, is written as the paragraph's right indent.
 */
class BesideFloatFitTest {

    private static final PDType1Font ARIAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    /** The PDF face's widths against its stand-in's. */
    private static final float WIDER = 1.04f;
    private static final float SIZE = 10;
    private static final float PITCH = 12;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    /** The picture: at the left margin, its text 9 pt to its right (the gap the converter keeps with nothing on its left). */
    private static final float PICTURE_W = 120;
    private static final float TEXT_X = LEFT + PICTURE_W + 9;
    private static final String TEXT = "Agencies should review the guidance each year and report on how the plan was carried out, "
            + "which offices took part, what the work cost and whether the results matched the goals set at the start. "
            + "Where a program missed its targets, the report explains why and what will change before the next review. "
            + "Staff who manage grants keep records of every award, the dates on which funds were paid and the names of "
            + "the people who approved each payment, so that auditors can trace a decision back to its source without "
            + "asking for more papers. Offices that share systems agree in writing on who owns the data, who may change "
            + "it and how long it is kept, and they test their backups at least twice a year.";

    /** A line that runs from the left margin past the picture's right edge. */
    private static final String FULL_WIDTH = "The offices named in this report keep their records in one shared system and review "
            + "them together each spring.";

    @TempDir Path dir;

    /** Whether the picture is on the paragraph's right instead of its left. */
    private boolean pictureOnRight;

    @Test
    void aParagraphBesideAPictureKeepsThePdfLineBreaks() throws Exception {
        List<String> words = Arrays.asList(TEXT.split(" "));
        List<List<String>> lines = breakLines(words, takesAWord(words));
        String xml = convert(lines);
        assertTrue(xml.contains("<wp:wrapSquare"), "the picture is a float the text wraps around");
        assertEquals(text(lines), wordBreaks(xml, lines), "Word breaks the paragraph where the PDF does");
    }

    @Test
    void aParagraphBesideAPictureOnItsRightKeepsThePdfLineBreaks() throws Exception {
        pictureOnRight = true;
        List<String> words = Arrays.asList(TEXT.split(" "));
        List<List<String>> lines = breakLines(words, takesAWord(words));
        String xml = convert(lines);
        assertTrue(xml.contains("<wp:wrapSquare"), "the picture is a float the text wraps around");
        assertEquals(text(lines), wordBreaks(xml, lines), "Word breaks the paragraph where the PDF does");
    }

    /**
     * A measure at which every PDF line leaves a few points for the edge, but the tightest less than the slack Word gets
     * past the widest line without a fit, so an unfitted paragraph takes a word there.
     */
    private static float takesAWord(List<String> words) {
        for (float m = 270; m < 340; m += 0.05f) {
            List<List<String>> lines = breakLines(words, m);
            float widest = 0;
            float tightest = Float.MAX_VALUE;
            for (int i = 0; i < lines.size(); i++) {
                widest = Math.max(widest, pdfWidth(String.join(" ", lines.get(i))));
                if (i + 1 < lines.size()) {
                    tightest = Math.min(tightest, pdfWidth(String.join(" ", lines.get(i)) + " " + lines.get(i + 1).getFirst()));
                }
            }
            if (lines.size() >= 6 && lines.size() <= 24 && tightest - widest > 2.5f && tightest - widest < 3.5f) {
                return m;
            }
        }
        throw new AssertionError("no measure with a window of a few points");
    }

    private static List<List<String>> breakLines(List<String> words, float measure) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        for (String word : words) {
            List<String> longer = new ArrayList<>(line);
            longer.add(word);
            if (!line.isEmpty() && pdfWidth(String.join(" ", longer)) > measure) {
                lines.add(line);
                line = new ArrayList<>();
            }
            line.add(word);
        }
        lines.add(line);
        return lines;
    }

    private static List<String> text(List<List<String>> lines) {
        return lines.stream().map(l -> String.join(" ", l)).toList();
    }

    private String convert(List<List<String>> lines) throws IOException {
        Path pdf = dir.resolve("beside.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDTrueTypeFont(widerFace()));
            BufferedImage photo = new BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = photo.createGraphics();
            g.setColor(new Color(40, 90, 150));
            g.fillRect(0, 0, 60, 80);
            g.setColor(new Color(230, 180, 60));
            g.fillOval(10, 10, 40, 40);
            g.dispose();
            resources.put(COSName.getPDFName("Im1"), LosslessFactory.createFromImage(doc, photo));
            page.setResources(resources);
            float height = lines.size() * PITCH + 20;
            // On the right, the picture stands 12 pt past the widest line; the converter wraps text 9 pt from it, the gap it
            // gives the side with nothing beside it, so Word's room past the widest line is 3 pt, more than the window
            float widest = 0;
            for (List<String> l : lines) {
                widest = Math.max(widest, pdfWidth(String.join(" ", l)));
            }
            float pictureX = pictureOnRight ? LEFT + widest + 12 : LEFT;
            float textX = pictureOnRight ? LEFT : TEXT_X;
            StringBuilder content = new StringBuilder(String.format(Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im1 Do Q%n",
                    PICTURE_W, height, pictureX, TOP + SIZE - height + 4));
            for (int i = 0; i < lines.size(); i++) {
                content.append(String.format(Locale.ROOT, "BT /F1 %.0f Tf %.2f %.2f Td (%s) Tj ET%n", SIZE, textX,
                        TOP - i * PITCH, String.join(" ", lines.get(i))));
            }
            if (pictureOnRight) {
                // Text under the picture runs the column's full width, so the picture stands inside the column
                resources.put(COSName.getPDFName("F2"), ARIAL);
                for (int i = 0; i < 3; i++) {
                    content.append(String.format(Locale.ROOT, "BT /F2 %.0f Tf %.2f %.2f Td (%s) Tj ET%n", SIZE, LEFT,
                            TOP + SIZE - height - 20 - i * PITCH, FULL_WIDTH));
                }
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.toString().getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve("beside.docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Myriad Pro as a PDF names it without embedding it, with widths 4% wider than Arial's. */
    private static COSDictionary widerFace() {
        COSArray widths = new COSArray();
        for (int c = 32; c <= 126; c++) {
            widths.add(COSInteger.get(Math.round(arial(c) * WIDER)));
        }
        COSDictionary descriptor = new COSDictionary();
        descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, "MyriadPro-Regular");
        descriptor.setInt(COSName.FLAGS, 32);
        descriptor.setItem(COSName.FONT_BBOX, numbers(-157, -250, 1126, 952));
        descriptor.setInt(COSName.ITALIC_ANGLE, 0);
        descriptor.setInt(COSName.ASCENT, 750);
        descriptor.setInt(COSName.DESCENT, -250);
        descriptor.setInt(COSName.CAP_HEIGHT, 674);
        descriptor.setInt(COSName.STEM_V, 88);
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TRUE_TYPE);
        font.setName(COSName.BASE_FONT, "MyriadPro-Regular");
        font.setInt(COSName.FIRST_CHAR, 32);
        font.setInt(COSName.LAST_CHAR, 126);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);
        font.setItem(COSName.FONT_DESC, descriptor);
        return font;
    }

    private static COSArray numbers(float... v) {
        COSArray a = new COSArray();
        for (float f : v) {
            a.add(new COSFloat(f));
        }
        return a;
    }

    /**
     * The paragraph broken as Word breaks it: Arial's widths and its runs' character spacing, each line from the
     * paragraph's indent or where a picture on its left leaves room, whichever is further, to the page's right margin
     * less its indent or where a picture on its right leaves room, whichever comes first.
     */
    private List<String> wordBreaks(String xml, List<List<String>> lines) {
        String first = String.join(" ", lines.getFirst().subList(0, 3));
        Matcher pm = Pattern.compile("<w:p>(?:(?!</w:p>).)*?" + Pattern.quote(first) + ".*?</w:p>", Pattern.DOTALL).matcher(xml);
        assertTrue(pm.find(), "the paragraph");
        String para = pm.group();
        Matcher margins = Pattern.compile("<w:pgMar [^>]*w:right=\"(\\d+)\"[^>]*w:left=\"(\\d+)\"").matcher(xml);
        assertTrue(margins.find(), "page margins");
        float right = Integer.parseInt(margins.group(1)) / 20f;
        float left = Integer.parseInt(margins.group(2)) / 20f;
        Matcher anchor = Pattern.compile("<wp:anchor [^>]*distR=\"(\\d+)\"(?:(?!</wp:anchor>).)*?<wp:positionH relativeFrom=\"page\">"
                + "<wp:posOffset>(-?\\d+)</wp:posOffset>(?:(?!</wp:anchor>).)*?<wp:extent cx=\"(\\d+)\"(?:(?!</wp:anchor>).)*?<wp:wrapSquare",
                Pattern.DOTALL).matcher(xml);
        assertTrue(anchor.find(), "the picture's anchor");
        float dist = Long.parseLong(anchor.group(1)) / 12700f;
        float x = Long.parseLong(anchor.group(2)) / 12700f;
        float w = Long.parseLong(anchor.group(3)) / 12700f;
        float indLeft = twips(para, "w:left");
        float indRight = twips(para, "w:right");
        float start = pictureOnRight ? left + indLeft : Math.max(left + indLeft, x + w + dist);
        float limit = PDRectangle.LETTER.getWidth() - right - indRight;
        if (pictureOnRight) {
            limit = Math.min(limit, x - dist);
        }
        float room = limit - start;
        StringBuilder chars = new StringBuilder();
        List<Float> spacing = new ArrayList<>();
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?((?:<w:t[^>]*>[^<]*</w:t>)+)</w:r>").matcher(para);
        while (run.find()) {
            Matcher sp = Pattern.compile("<w:spacing w:val=\"(-?\\d+)\"/>").matcher(run.group(1) == null ? "" : run.group(1));
            float extra = sp.find() ? Integer.parseInt(sp.group(1)) / 20f : 0;
            Matcher t = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(run.group(2));
            while (t.find()) {
                for (char c : t.group(1).toCharArray()) {
                    chars.append(c);
                    spacing.add(extra);
                }
            }
        }
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        float used = 0;
        int i = 0;
        while (i < chars.length()) {
            int end = i;
            while (end < chars.length() && chars.charAt(end) != ' ') {
                end++;
            }
            float word = 0;
            for (int k = i; k < end; k++) {
                word += arialWidth(String.valueOf(chars.charAt(k))) + spacing.get(k);
            }
            if (line.length() > 0 && used + word > room) {
                out.add(line.toString().strip());
                line.setLength(0);
                used = 0;
            }
            line.append(chars, i, end);
            used += word;
            if (end < chars.length()) {
                line.append(' ');
                used += arialWidth(" ") + spacing.get(end);
            }
            i = end + 1;
        }
        out.add(line.toString().strip());
        return out;
    }

    private static float twips(String para, String attr) {
        Matcher ind = Pattern.compile("<w:ind [^>]*" + attr + "=\"(-?\\d+)\"").matcher(para);
        return ind.find() ? Integer.parseInt(ind.group(1)) / 20f : 0f;
    }

    private static float arial(int c) {
        try {
            return ARIAL.getWidth(c);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Arial's width in Word. */
    private static float arialWidth(String s) {
        try {
            return ARIAL.getStringWidth(s) / 1000f * SIZE;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The PDF face's width. */
    private static float pdfWidth(String s) {
        float w = 0;
        for (char c : s.toCharArray()) {
            w += Math.round(arial(c) * WIDER) / 1000f * SIZE;
        }
        return w;
    }
}
