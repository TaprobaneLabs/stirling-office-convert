package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A paragraph in a text box keeps its PDF lines in Word. Unfitted, it was set to the room its widest PDF line takes, and
 * Word, measuring a stand-in face, wrapped words onto other lines: CDC's sheet on violence against American Indian and
 * Alaska Native people set its sidebar in Myriad Pro, and its lines moved by whole words.
 */
class BoxFitTest {

    private static final PDType1Font ARIAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    /** The PDF face's vowels against its stand-in's: wider, so no one spacing makes up every line's width. */
    private static final float VOWELS_WIDER = 1.12f;
    private static final float SIZE = 9;
    private static final float H = PDRectangle.LETTER.getHeight();
    private static final float PANEL_X = 27;
    private static final float PANEL_W = 244;
    private static final float TEXT_X = 40;
    /** The box's room: its text edges 13 pt in from each side. */
    private static final float ROOM = PANEL_W - 2 * (TEXT_X - PANEL_X);
    private static final String TEXT = "The agency works with tribal partners to understand violence experienced by the people"
            + " it serves and to address the issue of missing or murdered people by collecting data that can guide prevention"
            + " efforts, supporting violence prevention programs that reach the places where the people they help live and"
            + " work, and sharing what it learns with the communities, the states and the other agencies that run programs"
            + " of their own, so that each can see which approaches reduce violence and which do not before it spends more";

    @TempDir Path dir;

    @Test
    void thePanelsParagraphKeepsItsPdfLines() throws Exception {
        List<List<String>> lines = breakLines(Arrays.asList(TEXT.split(" ")), measure());
        Docx docx = convert(lines);
        assertEquals(text(lines), wordBreaks(docx), "Word breaks the panel's paragraph where the PDF does");
    }

    /**
     * A measure close enough to the box's room for the lines to read as one paragraph (each line and the next word take
     * more than the room less a line's edge allowance) where Word, set to the widest PDF line's room, would break the
     * lines elsewhere.
     */
    private static float measure() {
        for (float m = ROOM - 1.75f; m < ROOM; m += 0.05f) {
            List<List<String>> lines = breakLines(Arrays.asList(TEXT.split(" ")), m);
            float widest = 0;
            for (List<String> l : lines) {
                widest = Math.max(widest, pdfWidth(String.join(" ", l)));
            }
            if (lines.size() >= 6 && !breaks(lines, widest, guessedSpacing()).equals(text(lines))) {
                return m;
            }
        }
        throw new AssertionError("no measure at which the unfitted room loses a break");
    }

    /** Word's breaks of these words in Arial with a spacing after every character, in a room this wide. */
    private static List<String> breaks(List<List<String>> lines, float room, float spacing) {
        String all = String.join(" ", text(lines));
        float[] extra = new float[all.length()];
        Arrays.fill(extra, spacing);
        return wordBreaks(all, extra, room);
    }

    /** The spacing the converter gives the stand-in, near enough: the PDF face's extra width shared over its characters. */
    private static float guessedSpacing() {
        float pdf = pdfWidth(TEXT);
        float arial = arialWidth(TEXT);
        return Math.round((pdf - arial) / TEXT.length() * 20) / 20f;
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

    /** The box's paragraph and where Word sets the box's text. */
    private record Docx(String para, float textLeft, float textRight) {}

    private Docx convert(List<List<String>> lines) throws IOException {
        Path pdf = dir.resolve("panel.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDTrueTypeFont(face()));
            resources.put(COSName.getPDFName("F2"), ARIAL);
            page.setResources(resources);
            StringBuilder content = new StringBuilder();
            // A blue panel down the left of the page, its paragraph set in a face Word lacks
            content.append(String.format(Locale.ROOT, "0.84 0.9 0.96 rg %.2f %.2f %.2f %.2f re f 0 g%n", PANEL_X, H - 600,
                    PANEL_W, 400f));
            content.append(String.format(Locale.ROOT, "BT /F2 %.0f Tf %.2f %.2f Td (What the agency is doing) Tj ET%n", SIZE,
                    TEXT_X, H - 222));
            for (int i = 0; i < lines.size(); i++) {
                content.append(String.format(Locale.ROOT, "BT /F1 %.0f Tf %.2f %.2f Td (%s) Tj ET%n", SIZE, TEXT_X,
                        H - 240 - 12 * i, String.join(" ", lines.get(i))));
            }
            // Text running beside it
            String[] beside = {
                "Homicide was the fourth leading cause of death among the young in",
                "the years the report covers, and the sixth among women of every age.",
                "Intimate partner violence was a factor in 38% of the cases counted",
                "in the states that took part in the national system that year.",
            };
            for (int i = 0; i < 24; i++) {
                content.append(String.format(Locale.ROOT, "BT /F2 %.0f Tf %.2f %.2f Td (%s) Tj ET%n", SIZE, 284.4f,
                        H - 222 - 13 * i, beside[i % beside.length]));
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.toString().getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            doc.save(pdf.toFile());
        }
        Path out = dir.resolve("panel.docx");
        PdfToDocx.convert(pdf, out, PdfToDocx.Options.defaults());
        String xml;
        try (ZipFile zip = new ZipFile(out.toFile())) {
            xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
        String first = String.join(" ", lines.getFirst().subList(0, 3));
        Matcher m = Pattern.compile("<wp:anchor (?:(?!</wp:anchor>).)*?<wps:cNvSpPr txBox=\"1\"/>.*?</wp:anchor>", Pattern.DOTALL)
                .matcher(xml);
        while (m.find()) {
            String box = m.group();
            Matcher p = Pattern.compile("<w:p>(?:(?!</w:p>).)*?" + Pattern.quote(first) + ".*?</w:p>", Pattern.DOTALL).matcher(box);
            if (!p.find()) {
                continue;
            }
            assertTrue(box.contains("prst=\"rect\""), "a square box, whose text Word sets inside its insets");
            float x = emu(box, "<wp:posOffset>(-?\\d+)</wp:posOffset>");
            float w = emu(box, "<wp:extent cx=\"(\\d+)\"");
            return new Docx(p.group(), x + emu(box, "lIns=\"(\\d+)\""), x + w - emu(box, "rIns=\"(\\d+)\""));
        }
        throw new AssertionError("the panel isn't a text box holding the paragraph");
    }

    /** The paragraph broken as Word breaks it: Arial's widths, its runs' character spacing and the box's room. */
    private static List<String> wordBreaks(Docx docx) {
        float room = docx.textRight - docx.textLeft - twips(docx.para, "w:left") - twips(docx.para, "w:right");
        StringBuilder chars = new StringBuilder();
        List<Float> spacing = new ArrayList<>();
        Matcher run = Pattern.compile("<w:r>(?:<w:rPr>(.*?)</w:rPr>)?((?:<w:t[^>]*>[^<]*</w:t>)+)</w:r>").matcher(docx.para);
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
        float[] extra = new float[spacing.size()];
        for (int i = 0; i < extra.length; i++) {
            extra[i] = spacing.get(i);
        }
        return wordBreaks(chars.toString(), extra, room);
    }

    private static List<String> wordBreaks(String chars, float[] spacing, float room) {
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
                word += arialWidth(String.valueOf(chars.charAt(k))) + spacing[k];
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
                used += arialWidth(" ") + spacing[end];
            }
            i = end + 1;
        }
        out.add(line.toString().strip());
        return out;
    }

    /** Myriad Pro as a PDF names it without embedding it, its vowels wider than Arial's. */
    private static COSDictionary face() {
        COSArray widths = new COSArray();
        for (int c = 32; c <= 126; c++) {
            widths.add(COSInteger.get(Math.round(arial(c) * ("aeiouAEIOU".indexOf(c) >= 0 ? VOWELS_WIDER : 1f))));
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

    private static float arial(int c) {
        try {
            return ARIAL.getWidth(c);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float pdfWidth(String s) {
        float w = 0;
        for (char c : s.toCharArray()) {
            w += Math.round(arial(c) * ("aeiouAEIOU".indexOf(c) >= 0 ? VOWELS_WIDER : 1f));
        }
        return w / 1000f * SIZE;
    }

    /** Arial's width in Word. */
    private static float arialWidth(String s) {
        try {
            return ARIAL.getStringWidth(s) / 1000f * SIZE;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static COSArray numbers(float... v) {
        COSArray a = new COSArray();
        for (float f : v) {
            a.add(new COSFloat(f));
        }
        return a;
    }

    private static float emu(String xml, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(xml);
        assertTrue(m.find(), pattern);
        return Long.parseLong(m.group(1)) / 12700f;
    }

    private static float twips(String para, String attr) {
        Matcher ind = Pattern.compile("<w:ind [^>]*" + attr + "=\"(-?\\d+)\"").matcher(para);
        return ind.find() ? Integer.parseInt(ind.group(1)) / 20f : 0f;
    }
}
