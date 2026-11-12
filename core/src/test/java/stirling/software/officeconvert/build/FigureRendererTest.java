package stirling.software.officeconvert.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.GreyGroupPages;
import stirling.software.officeconvert.extract.OperatorBudget;

class FigureRendererTest {

    @Test
    void aDarkGreyInAnIsolatedGreyGroupKeepsItsTone() throws Exception {
        try (PDDocument doc = GreyGroupPages.darkGreyInAnIsolatedGroup(COSName.DEVICEGRAY)) {
            int rgb = new FigureRenderer(doc, true).renderImage(0).getRGB(100, 100);
            assertEquals(GreyGroupPages.GREY, rgb & 0xFF, 1, Integer.toHexString(rgb));
        }
    }

    /**
     * PDFBox draws a transparency group in full whatever the target's clip, so a figure drew every group on its page.
     * Groups outside the window are skipped unread: here they would use up the page's form budget before the one inside.
     */
    @Test
    void groupsOutsideTheWindowAreLeftUnread() throws Exception {
        String outside = "/Out Do ".repeat(OperatorBudget.MAX_FORMS);
        try (PDDocument doc = page("q 1 0 0 1 300 50 cm " + outside + "Q /In Do")) {
            FigureRenderer renderer = new FigureRenderer(doc, true);
            renderer.window(new Rectangle2D.Float(0, 0, 200, 200));
            int rgb = renderer.renderImage(0).getRGB(100, 100);
            assertEquals(0xFF0000, rgb & 0xFFFFFF, Integer.toHexString(rgb));
        }
    }

    /** Including a pattern's dots: PDFBox draws a tiling pattern's cell in the cell's own space, not the page's. */
    @Test
    void aWindowKeepsThePixelsItCovers() throws Exception {
        try (PDDocument doc = page("0 0 1 rg 20 20 360 60 re f q 1 0 0 1 120 0 cm /In Do Q q 1 0 0 1 300 50 cm /Out Do Q"
                + " /Pattern cs /Dots scn 160 60 80 80 re f")) {
            BufferedImage whole = new FigureRenderer(doc, true).renderImage(0);
            FigureRenderer windowed = new FigureRenderer(doc, true);
            windowed.window(new Rectangle2D.Float(150, 40, 100, 120));
            BufferedImage part = windowed.renderImage(0);
            for (int y = 40; y < 160; y++) {
                for (int x = 150; x < 250; x++) {
                    assertEquals(whole.getRGB(x, y), part.getRGB(x, y), x + "," + y);
                }
            }
        }
    }

    /**
     * PDFBox turns a NoRotate annotation on a turned page with the Java2D graphics alone, so its fills' bounds aren't
     * where it shows: a window over where it shows, and away from its rectangle, still draws it.
     */
    @Test
    void aNoRotateAnnotationOnATurnedPageKeepsWhatItShows() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 200));
            page.setRotation(90);
            doc.addPage(page);
            PDAppearanceStream look = new PDAppearanceStream(doc);
            look.setBBox(new PDRectangle(100, 30));
            try (OutputStream o = look.getContentStream().createOutputStream()) {
                o.write("1 0 0 rg 0 0 100 30 re f".getBytes(StandardCharsets.US_ASCII));
            }
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.setNormalAppearance(look);
            PDAnnotationLink note = new PDAnnotationLink();
            note.setRectangle(new PDRectangle(40, 120, 100, 30));
            note.setNoRotate(true);
            note.setAppearance(appearance);
            page.setAnnotations(List.of(note));

            BufferedImage whole = new FigureRenderer(doc, true).renderImage(0);
            Rectangle red = redBounds(whole);
            // Where the annotation shows, in page space: the page's turn undone, 2 pixels in from its edges
            AffineTransform toImage = new AffineTransform();
            toImage.translate(200, 0);
            toImage.rotate(Math.toRadians(90));
            toImage.translate(0, 200);
            toImage.scale(1, -1);
            FigureRenderer windowed = new FigureRenderer(doc, true);
            windowed.window(toImage.createInverse().createTransformedShape(
                    new Rectangle2D.Double(red.x + 2, red.y + 2, red.width - 4, red.height - 4)).getBounds2D());
            int x = red.x + red.width / 2;
            int y = red.y + red.height / 2;
            int rgb = windowed.renderImage(0).getRGB(x, y);
            assertEquals(0xFF0000, rgb & 0xFFFFFF, Integer.toHexString(rgb));
        }
    }

    private static Rectangle redBounds(BufferedImage image) {
        Rectangle box = null;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFF0000) {
                    box = box == null ? new Rectangle(x, y, 1, 1) : box.union(new Rectangle(x, y, 1, 1));
                }
            }
        }
        assertNotNull(box, "the annotation is drawn");
        return box;
    }

    /**
     * A 400 by 200 page whose content shows {@code In}, a red square in a group, {@code Out}, a small group, and
     * {@code Dots}, a pattern of black squares 8 points apart.
     */
    private static PDDocument page(String content) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(400, 200));
        doc.addPage(page);
        PDResources resources = new PDResources();
        resources.put(COSName.getPDFName("In"), group(doc, 200, "1 0 0 rg 50 50 100 100 re f"));
        resources.put(COSName.getPDFName("Out"), group(doc, 10, "0 1 0 rg 0 0 10 10 re f"));
        PDTilingPattern dots = new PDTilingPattern();
        dots.setPaintType(PDTilingPattern.PAINT_COLORED);
        dots.setTilingType(PDTilingPattern.TILING_CONSTANT_SPACING);
        dots.setBBox(new PDRectangle(8, 8));
        dots.setXStep(8);
        dots.setYStep(8);
        try (OutputStream o = dots.getContentStream().createOutputStream()) {
            o.write("0 0 0 rg 0 0 4 4 re f".getBytes(StandardCharsets.US_ASCII));
        }
        resources.put(COSName.getPDFName("Dots"), dots);
        page.setResources(resources);
        PDStream s = new PDStream(doc);
        try (OutputStream o = s.createOutputStream()) {
            o.write(content.getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(s);
        return doc;
    }

    private static PDFormXObject group(PDDocument doc, float side, String content) throws IOException {
        COSDictionary group = new COSDictionary();
        group.setItem(COSName.S, COSName.TRANSPARENCY);
        group.setItem(COSName.CS, COSName.DEVICERGB);
        PDFormXObject form = new PDFormXObject(doc);
        form.setBBox(new PDRectangle(side, side));
        form.getCOSObject().setItem(COSName.GROUP, group);
        try (OutputStream o = form.getContentStream().createOutputStream()) {
            o.write(content.getBytes(StandardCharsets.US_ASCII));
        }
        return form;
    }
}
