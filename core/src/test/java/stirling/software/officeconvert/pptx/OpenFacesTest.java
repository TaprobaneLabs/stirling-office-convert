package stirling.software.officeconvert.pptx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.model.Inline;
import stirling.software.officeconvert.model.Paragraph;
import stirling.software.officeconvert.model.RunStyle;
import stirling.software.officeconvert.slides.Frame;
import stirling.software.officeconvert.slides.Slide;
import stirling.software.officeconvert.slides.TextPara;
import stirling.software.officeconvert.slides.TextShape;

class OpenFacesTest {

    /**
     * A two-column page set in Caladea, which the PDF names Cambria's family: PowerPoint's Cambria sets it 5% wider, so
     * the deck names Caladea and carries it, as the DOCX writer does.
     */
    @Test
    void aDeckSetInCaladeaNamesItAndCarriesIt() throws IOException {
        Map<String, byte[]> deck = deck(Map.of("Cambria", "Caladea"));
        String slide = text(deck, "ppt/slides/slide1.xml");
        assertTrue(slide.contains("<a:latin typeface=\"Caladea\""), slide);
        String presentation = text(deck, "ppt/presentation.xml");
        assertTrue(presentation.contains(" embedTrueTypeFonts=\"1\""), presentation);
        assertTrue(presentation.contains("<p:notesSz cx=\"6858000\" cy=\"9144000\"/><p:embeddedFontLst><p:embeddedFont>"
                + "<p:font typeface=\"Caladea\" pitchFamily=\"18\" charset=\"0\"/><p:regular r:id=\"rIdFontCaladeaRegular\"/>"
                + "<p:bold r:id=\"rIdFontCaladeaBold\"/></p:embeddedFont></p:embeddedFontLst>"), presentation);
        String rels = text(deck, "ppt/_rels/presentation.xml.rels");
        assertTrue(rels.contains("Id=\"rIdFontCaladeaBold\" Type=\"" + OpenFaces.REL + "\" Target=\"fonts/Caladea-Bold.fntdata\""),
                rels);
        assertTrue(text(deck, "[Content_Types].xml").contains("<Default Extension=\"fntdata\" ContentType=\"application/x-fontdata\"/>"));
        assertNotNull(deck.get("ppt/fonts/Caladea-Regular.fntdata"));
        assertNotNull(deck.get("ppt/fonts/Caladea-Bold.fntdata"));
        assertNull(deck.get("ppt/fonts/Caladea-Italic.fntdata"), "only the faces the deck uses");
    }

    @Test
    void aDeckWithoutOpenFontsNamesCambria() throws IOException {
        Map<String, byte[]> deck = deck(Map.of());
        assertTrue(text(deck, "ppt/slides/slide1.xml").contains("<a:latin typeface=\"Cambria\""));
        assertFalse(text(deck, "ppt/presentation.xml").contains("embeddedFont"));
        assertFalse(text(deck, "[Content_Types].xml").contains("fntdata"));
        assertTrue(deck.keySet().stream().noneMatch(n -> n.startsWith("ppt/fonts/")));
    }

    /**
     * PowerPoint keeps embedded fonts as EOT. It drew ours only once the root string checksum read 0x50475342, an
     * empty root string's; with 0 it fell back to Calibri.
     */
    @Test
    void eotWrapsTheFontWhole() throws IOException {
        byte[] ttf;
        try (InputStream in = OpenFacesTest.class.getResourceAsStream("/stirling/software/officeconvert/docx/fonts/Caladea-Italic.ttf")) {
            ttf = in.readAllBytes();
        }
        byte[] eot = Eot.wrap(ttf);
        ByteBuffer b = ByteBuffer.wrap(eot).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(eot.length, b.getInt(0), "EOTSize");
        assertEquals(ttf.length, b.getInt(4), "FontDataSize");
        assertEquals(0x00020002, b.getInt(8), "version 2.2");
        assertEquals(0, b.getInt(12), "no subsetting, compression or XOR");
        assertEquals(1, eot[27], "italic");
        assertEquals(0x504C, b.getShort(34) & 0xFFFF, "magic number");
        int pos = 80;
        String[] names = new String[4];
        for (int i = 0; i < 4; i++) {
            int size = b.getShort(pos + 2) & 0xFFFF;
            names[i] = new String(eot, pos + 4, size, StandardCharsets.UTF_16LE);
            pos += 4 + size;
        }
        assertEquals("Caladea\0", names[0]);
        assertEquals("Italic\0", names[1]);
        assertEquals(0, b.getShort(pos + 2), "no root string");
        assertEquals(0x50475342, b.getInt(pos + 4), "an empty root string's checksum");
        assertArrayEquals(ttf, Arrays.copyOfRange(eot, eot.length - ttf.length, eot.length));
    }

    private static Map<String, byte[]> deck(Map<String, String> open) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PptxWriter writer = new PptxWriter(out)) {
            writer.openFonts(open);
            writer.begin(612, 792, 1, 1);
            Paragraph p = new Paragraph();
            p.inlines.add(new Inline.Text("Customers in the north region ", style(false), null, -1));
            p.inlines.add(new Inline.Text("asked for weekend collection.", style(true), null, -1));
            TextPara para = new TextPara(p, Paragraph.Align.LEFT, 0, 0, 0, 12, 0, 10, 10, 10, null, Set.of());
            TextShape shape = new TextShape(new Frame(50, 60, 250, 20), List.of(para), 0, 0, 0, -1, -1, 0, 0, true, false);
            writer.slide(new Slide(1, -1, List.of(shape)));
            writer.finish("t", "a");
        }
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                parts.put(e.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static RunStyle style(boolean bold) {
        return new RunStyle("Cambria", 10, bold, false, false, false, 0, -1, 0, false, 0f, 100, false);
    }

    private static String text(Map<String, byte[]> parts, String name) {
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }
}
