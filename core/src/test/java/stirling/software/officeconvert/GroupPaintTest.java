package stirling.software.officeconvert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Paint inside a transparency group keeps the group's soft mask and opacity. PDFBox runs a group's content at full
 * opacity and without its mask, which it applies to the group as a whole, so a shape drawn from that content was solid:
 * USGS Fact Sheet 2023-3022's page 3 masks a blue rectangle the size of its lower half down to a faint tint, and Word
 * showed the half solid blue.
 */
class GroupPaintTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @TempDir Path dir;

    @Test
    void aShapeAGroupsSoftMaskHidesIsNotDrawnSolid() throws Exception {
        String xml = convert("masked", (doc, page, cs) -> {
            // a blue rectangle over the page's lower half, in a group whose luminosity mask is black: none of it shows
            PDFormXObject group = group(doc, page);
            try (PDFormContentStream g = new PDFormContentStream(group)) {
                g.setNonStrokingColor(0.12f, 0.37f, 0.75f);
                g.addRect(0, 0, 612, 400);
                g.fill();
            }
            PDFormXObject mask = group(doc, page);
            try (PDFormContentStream m = new PDFormContentStream(mask)) {
                m.setNonStrokingColor(0f, 0f, 0f);
                m.addRect(0, 0, 612, 792);
                m.fill();
            }
            COSDictionary smask = new COSDictionary();
            smask.setItem(COSName.TYPE, COSName.MASK);
            smask.setItem(COSName.S, COSName.LUMINOSITY);
            smask.setItem(COSName.G, mask.getCOSObject());
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(COSName.SMASK, smask);
            cs.saveGraphicsState();
            cs.setGraphicsStateParameters(state);
            cs.drawForm(group);
            cs.restoreGraphicsState();
            body(cs);
        });
        assertFalse(xml.contains("<a:srgbClr val=\"1F5EBF\"/>") || xml.contains("<a:srgbClr val=\"1F5FBF\"/>"),
                "no solid blue shape for the masked rectangle");
        assertTrue(xml.contains("easternmost islands"), "the text is there");
    }

    @Test
    void smallShapesSpreadAcrossAMaskedGroupStay() throws Exception {
        String xml = convert("spread", (doc, page, cs) -> {
            // two 20 pt squares far apart, filled at once, in a group whose mask shows all of it: their box spans the
            // page, but neither is a tint
            PDFormXObject group = group(doc, page);
            try (PDFormContentStream g = new PDFormContentStream(group)) {
                g.setNonStrokingColor(0.1f, 0.6f, 0.2f);
                g.addRect(60, 60, 20, 20);
                g.addRect(480, 300, 20, 20);
                g.fill();
            }
            PDFormXObject mask = group(doc, page);
            try (PDFormContentStream m = new PDFormContentStream(mask)) {
                m.setNonStrokingColor(1f, 1f, 1f);
                m.addRect(0, 0, 612, 792);
                m.fill();
            }
            COSDictionary smask = new COSDictionary();
            smask.setItem(COSName.TYPE, COSName.MASK);
            smask.setItem(COSName.S, COSName.LUMINOSITY);
            smask.setItem(COSName.G, mask.getCOSObject());
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(COSName.SMASK, smask);
            cs.saveGraphicsState();
            cs.setGraphicsStateParameters(state);
            cs.drawForm(group);
            cs.restoreGraphicsState();
            body(cs);
        });
        assertTrue(xml.contains("<a:srgbClr val=\"1A9933\"/>"), "the squares are still shapes");
    }

    @Test
    void aShapeInAHalfOpaqueGroupIsHalfOpaque() throws Exception {
        String xml = convert("faint", (doc, page, cs) -> {
            // a blue box in a group drawn at half opacity: on white paper, it shows half as strong
            PDFormXObject group = group(doc, page);
            try (PDFormContentStream g = new PDFormContentStream(group)) {
                g.setNonStrokingColor(0f, 0f, 1f);
                g.addRect(300, 500, 150, 100);
                g.fill();
            }
            PDExtendedGraphicsState half = new PDExtendedGraphicsState();
            half.setNonStrokingAlphaConstant(0.5f);
            cs.saveGraphicsState();
            cs.setGraphicsStateParameters(half);
            cs.drawForm(group);
            cs.restoreGraphicsState();
            body(cs);
        });
        assertFalse(xml.contains("<a:srgbClr val=\"0000FF\"/>"), "the box isn't drawn at full strength");
        assertTrue(xml.contains("<a:srgbClr val=\"8080FF\"/>") || xml.contains("<a:srgbClr val=\"7F7FFF\"/>"),
                "the box is drawn half as strong");
    }

    private interface Page {
        void draw(PDDocument doc, PDPage page, PDPageContentStream cs) throws IOException;
    }

    private String convert(String name, Page page) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p = new PDPage(PDRectangle.LETTER);
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                page.draw(doc, p, cs);
            }
            doc.save(pdf.toFile());
        }
        Path docx = dir.resolve(name + ".docx");
        PdfToDocx.convert(pdf, docx, PdfToDocx.Options.defaults());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A page-sized transparency group form for {@code page}. */
    private static PDFormXObject group(PDDocument doc, PDPage page) {
        PDFormXObject form = new PDFormXObject(doc);
        form.setBBox(page.getMediaBox());
        form.setResources(new PDResources());
        COSDictionary group = new COSDictionary();
        group.setItem(COSName.TYPE, COSName.GROUP);
        group.setItem(COSName.S, COSName.TRANSPARENCY);
        group.setItem(COSName.CS, COSName.DEVICERGB);
        form.getCOSObject().setItem(COSName.GROUP, group);
        return form;
    }

    private static void body(PDPageContentStream cs) throws IOException {
        String[] lines = {
            "American Samoa comprises the easternmost islands of a volcanic island chain in",
            "the South Pacific Ocean. Each island of American Samoa has a unique eruptive history",
            "and a possibility for future eruptions. The Geological Survey collaborates with the"
        };
        for (int i = 0; i < lines.length; i++) {
            cs.beginText();
            cs.setFont(FONT, 10);
            cs.newLineAtOffset(72, 720 - 14 * i);
            cs.showText(lines[i]);
            cs.endText();
        }
    }
}
