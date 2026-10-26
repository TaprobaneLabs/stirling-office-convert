package stirling.software.officeconvert.docx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Numbering;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.StyleSheet;
import stirling.software.officeconvert.sink.SampleDocument;

/**
 * A rounded text box's text starts where a square one's does. DrawingML sets a {@code roundRect}'s text in a rectangle 29%
 * of its corner radius in from each side, inside the insets, and Word and LibreOffice both follow it: a rounded panel's
 * lines started 1.8 pt further in than the PDF's and had 3.5 pt less room, so Word wrapped them elsewhere.
 */
class RoundedBoxInsetsTest {

    private static final float INSET = 13;

    @Test
    void aRoundedBoxsInsetsLeaveOutItsRounding() throws IOException {
        String rounded = box(true);
        float rounding = 0.29289f * value(rounded, "fmla=\"val (\\d+)\"") / 100000f * Math.min(244, 400);
        assertTrue(rounding > 1f, "the box is rounded: " + rounding);
        assertEquals(INSET, emu(rounded, "lIns=\"(\\d+)\"") + rounding, 0.01f, "its text starts 13 pt in");
        assertEquals(INSET, emu(rounded, "rIns=\"(\\d+)\"") + rounding, 0.01f, "and ends 13 pt in");
    }

    @Test
    void aSquareBoxKeepsItsInsets() throws IOException {
        String square = box(false);
        assertEquals(INSET, emu(square, "lIns=\"(\\d+)\""), 0.01f);
        assertEquals(INSET, emu(square, "rIns=\"(\\d+)\""), 0.01f);
    }

    private static String box(boolean rounded) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DocxWriter w = new DocxWriter(out)) {
            StyleSheet styles = new StyleSheet(SampleDocument.BODY);
            w.begin(styles, null);
            Paragraph anchor = SampleDocument.paragraph("Beside the panel");
            anchor.inlines.addFirst(new Inline.TextBox(27, 200, 244, 400, 0xD7E6F5, INSET, INSET, 9,
                    List.of(SampleDocument.paragraph("In the panel")), 0x4D80CC, 0.75f, rounded, false));
            w.block(anchor);
            w.finish(SampleDocument.section(1), null, new Numbering(), styles, "t", "a");
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.getName().equals("word/document.xml")) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher box = Pattern.compile("<wp:anchor .*?</wp:anchor>", Pattern.DOTALL).matcher(xml);
                    assertTrue(box.find(), "the box is anchored");
                    return box.group();
                }
            }
        }
        throw new AssertionError("no document part");
    }

    private static long value(String xml, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(xml);
        assertTrue(m.find(), pattern);
        return Long.parseLong(m.group(1));
    }

    private static float emu(String xml, String pattern) {
        return value(xml, pattern) / 12700f;
    }
}
