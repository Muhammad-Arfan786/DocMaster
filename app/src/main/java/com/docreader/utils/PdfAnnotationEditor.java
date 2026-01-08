package com.docreader.utils;

import android.graphics.Color;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfFreeTextAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfStampAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Annotation Editor - Adds text as annotations/overlays
 * This preserves the original PDF 100% and only adds annotation layers.
 *
 * Annotations are separate from the PDF content and can be:
 * - Viewed in any PDF reader
 * - Edited or removed later
 * - Don't affect the original document structure
 */
public class PdfAnnotationEditor {

    /**
     * Text annotation to add to PDF
     */
    public static class TextNote {
        public int pageNumber;      // 1-based
        public float x, y;          // Position (from bottom-left in PDF coords)
        public float width, height; // Size of text box
        public String text;
        public float fontSize;
        public int textColor;
        public int bgColor;         // Background color (0 = transparent)
        public boolean isSticky;    // Sticky note (popup) or free text

        public TextNote(int pageNumber, float x, float y, String text) {
            this.pageNumber = pageNumber;
            this.x = x;
            this.y = y;
            this.width = 200;
            this.height = 50;
            this.text = text;
            this.fontSize = 12;
            this.textColor = Color.BLACK;
            this.bgColor = Color.WHITE;
            this.isSticky = false;
        }

        public TextNote setSize(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public TextNote setFontSize(float size) {
            this.fontSize = size;
            return this;
        }

        public TextNote setColors(int textColor, int bgColor) {
            this.textColor = textColor;
            this.bgColor = bgColor;
            return this;
        }

        public TextNote asSticky() {
            this.isSticky = true;
            return this;
        }
    }

    /**
     * Add text annotations to PDF (non-destructive)
     * The original PDF content is completely preserved.
     */
    public static File addTextAnnotations(String sourcePdfPath, File outputDir,
                                           String outputFileName, List<TextNote> notes) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            for (TextNote note : notes) {
                if (note.pageNumber > 0 && note.pageNumber <= pdfDocument.getNumberOfPages()) {
                    addAnnotation(pdfDocument, note);
                }
            }
        }

        return outputFile;
    }

    /**
     * Add a single annotation to the document
     */
    private static void addAnnotation(PdfDocument pdfDocument, TextNote note) throws Exception {
        PdfPage page = pdfDocument.getPage(note.pageNumber);

        if (note.isSticky) {
            // Add sticky note (popup comment)
            Rectangle rect = new Rectangle(note.x, note.y, 24, 24);
            PdfTextAnnotation sticky = new PdfTextAnnotation(rect);
            sticky.setContents(note.text);
            sticky.setTitle(new PdfString("Note"));

            // Set color
            float r = ((note.textColor >> 16) & 0xFF) / 255f;
            float g = ((note.textColor >> 8) & 0xFF) / 255f;
            float b = (note.textColor & 0xFF) / 255f;
            sticky.setColor(new float[]{r, g, b});

            page.addAnnotation(sticky);
        } else {
            // Add free text annotation (visible text box)
            Rectangle rect = new Rectangle(note.x, note.y, note.width, note.height);
            PdfFreeTextAnnotation freeText = new PdfFreeTextAnnotation(rect, new PdfString(note.text));

            // Set appearance
            float r = ((note.textColor >> 16) & 0xFF) / 255f;
            float g = ((note.textColor >> 8) & 0xFF) / 255f;
            float b = (note.textColor & 0xFF) / 255f;
            freeText.setColor(new float[]{r, g, b});

            // Set default appearance string for the text
            String da = String.format("/Helv %.1f Tf %.2f %.2f %.2f rg",
                    note.fontSize, r, g, b);
            freeText.setDefaultAppearance(new PdfString(da));

            page.addAnnotation(freeText);
        }
    }

    /**
     * Add text overlay on specific area (with optional white background to cover existing text)
     * This creates a visual overlay but preserves underlying PDF structure.
     */
    public static File addTextOverlay(String sourcePdfPath, File outputDir, String outputFileName,
                                       int pageNumber, float x, float y, float width, float height,
                                       String newText, float fontSize, int textColor,
                                       boolean coverBackground) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                PdfCanvas canvas = new PdfCanvas(page);
                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                if (coverBackground) {
                    // Draw white rectangle to cover existing content
                    canvas.saveState();
                    canvas.setFillColor(ColorConstants.WHITE);
                    canvas.rectangle(x, y, width, height);
                    canvas.fill();
                    canvas.restoreState();
                }

                // Draw the new text
                float r = ((textColor >> 16) & 0xFF) / 255f;
                float g = ((textColor >> 8) & 0xFF) / 255f;
                float b = (textColor & 0xFF) / 255f;
                DeviceRgb color = new DeviceRgb((int)(r*255), (int)(g*255), (int)(b*255));

                canvas.saveState();
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setFillColor(color);
                canvas.moveText(x + 2, y + height - fontSize - 2);

                // Handle multi-line text
                String[] lines = newText.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        canvas.moveText(0, -fontSize - 2);
                    }
                    canvas.showText(lines[i]);
                }

                canvas.endText();
                canvas.restoreState();
            }
        }

        return outputFile;
    }

    /**
     * Add semi-transparent text watermark/overlay (doesn't cover original)
     */
    public static File addTransparentText(String sourcePdfPath, File outputDir, String outputFileName,
                                           int pageNumber, float x, float y, String text,
                                           float fontSize, int color, float opacity) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                PdfCanvas canvas = new PdfCanvas(page);
                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                // Set transparency
                PdfExtGState gs = new PdfExtGState();
                gs.setFillOpacity(opacity);

                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                DeviceRgb pdfColor = new DeviceRgb((int)(r*255), (int)(g*255), (int)(b*255));

                canvas.saveState();
                canvas.setExtGState(gs);
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setFillColor(pdfColor);
                canvas.moveText(x, y);
                canvas.showText(text);
                canvas.endText();
                canvas.restoreState();
            }
        }

        return outputFile;
    }

    /**
     * Get all existing annotations from a PDF page
     */
    public static List<String> getAnnotations(String pdfPath, int pageNumber) {
        List<String> annotations = new ArrayList<>();

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                List<PdfAnnotation> annots = page.getAnnotations();

                for (PdfAnnotation annot : annots) {
                    PdfString contents = annot.getContents();
                    if (contents != null) {
                        annotations.add(contents.getValue());
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e("PdfAnnotationEditor", "Error getting annotations", e);
        }

        return annotations;
    }

    /**
     * Remove all annotations from a page
     */
    public static File removeAnnotations(String sourcePdfPath, File outputDir,
                                          String outputFileName, int pageNumber) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                List<PdfAnnotation> annots = new ArrayList<>(page.getAnnotations());

                for (PdfAnnotation annot : annots) {
                    page.removeAnnotation(annot);
                }
            }
        }

        return outputFile;
    }

    /**
     * Helper to copy file
     */
    private static void copyFile(File source, File dest) throws Exception {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
