package stirling.software.officeconvert.docx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stirling.software.officeconvert.PdfToDocx;

class EmbeddedFontsTest {

    private static final String LIBERATION = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String[] LINES = {
        "The committee reviewed every budget line with care and noted where",
        "spending ran ahead of plan across each quarter of the year, and asked",
        "the office to report on the causes before the next meeting in spring."
    };

    @TempDir Path dir;

    @Test
    void textSetInCaladeaKeepsItsFontInsideTheDocument() throws Exception {
        Path docx = convert(pdf("caladea.pdf", "fonts/Caladea-Regular.ttf", "fonts/Caladea-Bold.ttf"));

        String fontTable = text(docx, "word/fontTable.xml");
        String document = text(docx, "word/document.xml") + text(docx, "word/styles.xml");
        assertTrue(document.contains("w:ascii=\"Caladea\""), "the text names Caladea, the font the PDF draws it in");
        assertFalse(document.contains("Cambria") || fontTable.contains("Cambria"),
                "not Word's Cambria, whose wider letters would break the lines elsewhere");
        assertTrue(fontTable.contains("<w:embedRegular") && fontTable.contains("<w:embedBold"),
                "the regular and bold faces are embedded");
        assertFalse(fontTable.contains("<w:embedItalic") || fontTable.contains("<w:embedBoldItalic"),
                "faces the document doesn't use are left out");

        assertFontPart(docx, fontTable, "embedRegular", "fonts/Caladea-Regular.ttf");
        assertFontPart(docx, fontTable, "embedBold", "fonts/Caladea-Bold.ttf");
        assertTrue(text(docx, "[Content_Types].xml").contains("Extension=\"odttf\" ContentType=\"" + EmbeddedFonts.CONTENT_TYPE),
                "embedded fonts have their content type");
        assertTrue(text(docx, "word/settings.xml").contains("<w:embedTrueTypeFonts/>"),
                "Word keeps the fonts when it saves the document");
    }

    @Test
    void officeFontsAreNotEmbedded() throws Exception {
        Path docx = convert(pdf("liberation.pdf", LIBERATION, LIBERATION));
        assertTrue(text(docx, "word/fontTable.xml").contains("w:name=\"Arial\""), "Liberation Sans stands in for Arial");
        assertFalse(text(docx, "word/fontTable.xml").contains("<w:embed"), "Word has Arial: nothing to embed");
        assertFalse(text(docx, "word/settings.xml").contains("embedTrueTypeFonts"));
        try (ZipFile z = new ZipFile(docx.toFile())) {
            assertNull(z.getEntry("word/_rels/fontTable.xml.rels"));
            assertTrue(z.stream().noneMatch(e -> e.getName().endsWith(".odttf")));
        }
    }

    @Test
    void keyBytesComeFromTheEndOfTheGuid() {
        byte[] out = EmbeddedFonts.obfuscate(new byte[40], "{00112233-4455-6677-8899-AABBCCDDEEFF}");
        assertEquals((byte) 0xFF, out[0]);
        assertEquals((byte) 0xEE, out[1]);
        assertEquals((byte) 0x00, out[15]);
        assertEquals((byte) 0xFF, out[16]);
        assertEquals((byte) 0x00, out[31]);
        assertEquals(0, out[32], "only the first 32 bytes are obfuscated");
        assertEquals(EmbeddedFonts.key("Caladea", 0), EmbeddedFonts.key("Caladea", 0), "the same font gets the same key");
    }

    private void assertFontPart(Path docx, String fontTable, String element, String resource) throws IOException {
        Matcher m = Pattern.compile("<w:" + element + " r:id=\"([^\"]+)\" w:fontKey=\"([^\"]+)\"/>").matcher(fontTable);
        assertTrue(m.find(), element + " has a relationship and a key");
        Matcher target = Pattern.compile("Id=\"" + m.group(1) + "\" Type=\"" + EmbeddedFonts.REL + "\" Target=\"([^\"]+)\"")
                .matcher(text(docx, "word/_rels/fontTable.xml.rels"));
        assertTrue(target.find(), element + "'s relationship points at a font part");
        try (ZipFile z = new ZipFile(docx.toFile()); InputStream original = EmbeddedFonts.class.getResourceAsStream(resource)) {
            ZipEntry part = z.getEntry("word/" + target.group(1));
            assertNotNull(part, "the font part is in the package");
            byte[] stored = z.getInputStream(part).readAllBytes();
            assertArrayEquals(original.readAllBytes(), EmbeddedFonts.obfuscate(stored, m.group(2)),
                    "the part is the font, obfuscated with its key");
        }
    }

    private Path pdf(String name, String regular, String bold) throws IOException {
        Path pdf = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDFont body = load(doc, regular);
            PDFont heading = load(doc, bold);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(heading, 14);
                cs.newLineAtOffset(72, 760);
                cs.showText("Quarterly budget review");
                cs.endText();
                for (int i = 0; i < 12; i++) {
                    cs.beginText();
                    cs.setFont(body, 11);
                    cs.newLineAtOffset(72, 730 - i * 14);
                    cs.showText(LINES[i % LINES.length]);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static PDFont load(PDDocument doc, String resource) throws IOException {
        try (InputStream ttf = resource.startsWith("/")
                ? EmbeddedFontsTest.class.getResourceAsStream(resource)
                : EmbeddedFonts.class.getResourceAsStream(resource)) {
            return PDType0Font.load(doc, ttf);
        }
    }

    private Path convert(Path pdf) throws IOException {
        Path docx = dir.resolve(pdf.getFileName().toString().replace(".pdf", ".docx"));
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        return docx;
    }

    private static String text(Path zip, String name) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry e = z.getEntry(name);
            return e == null ? "" : new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
