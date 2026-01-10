package com.docreader.utils;

import android.graphics.Color;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfAnnotation;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;

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

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            for (TextNote note : notes) {
                if (note.pageNumber > 0 && note.pageNumber <= reader.getNumberOfPages()) {
                    addAnnotation(stamper, reader, note);
                }
            }
        } finally {
            if (stamper != null) {
                try { stamper.close(); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        return outputFile;
    }

    /**
     * Add a single annotation to the document
     */
    private static void addAnnotation(PdfStamper stamper, PdfReader reader, TextNote note) throws Exception {
        if (note.isSticky) {
            // Add sticky note (popup comment)
            Rectangle rect = new Rectangle(note.x, note.y, note.x + 24, note.y + 24);

            // Set color
            float r = ((note.textColor >> 16) & 0xFF) / 255f;
            float g = ((note.textColor >> 8) & 0xFF) / 255f;
            float b = (note.textColor & 0xFF) / 255f;

            PdfAnnotation sticky = PdfAnnotation.createText(
                    stamper.getWriter(),
                    rect,
                    "Note",
                    note.text,
                    false,
                    null
            );
            // Note: setColor requires java.awt.Color which is not available on Android
            // Annotation will use default color

            stamper.addAnnotation(sticky, note.pageNumber);
        } else {
            // Add free text annotation (visible text box)
            Rectangle rect = new Rectangle(note.x, note.y, note.x + note.width, note.y + note.height);

            // Set appearance
            float r = ((note.textColor >> 16) & 0xFF) / 255f;
            float g = ((note.textColor >> 8) & 0xFF) / 255f;
            float b = (note.textColor & 0xFF) / 255f;

            PdfAnnotation freeText = PdfAnnotation.createFreeText(
                    stamper.getWriter(),
                    rect,
                    note.text,
                    null
            );

            // Set default appearance string for the text
            String da = String.format("/Helv %.1f Tf %.2f %.2f %.2f rg",
                    note.fontSize, r, g, b);
            freeText.put(PdfName.DA, new PdfString(da));
            // Note: setColor requires java.awt.Color which is not available on Android

            stamper.addAnnotation(freeText, note.pageNumber);
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

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                PdfContentByte canvas = stamper.getOverContent(pageNumber);
                BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

                if (coverBackground) {
                    // Draw white rectangle to cover existing content
                    canvas.saveState();
                    canvas.setRGBColorFill(255, 255, 255);
                    canvas.rectangle(x, y, width, height);
                    canvas.fill();
                    canvas.restoreState();
                }

                // Draw the new text
                int r = (textColor >> 16) & 0xFF;
                int g = (textColor >> 8) & 0xFF;
                int b = textColor & 0xFF;

                canvas.saveState();
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setRGBColorFill(r, g, b);
                canvas.setTextMatrix(x + 2, y + height - fontSize - 2);

                // Handle multi-line text - use raw PDF operators to avoid AWT dependencies
                String[] lines = newText.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        canvas.setTextMatrix(x + 2, y + height - fontSize - 2 - (i * (fontSize + 2)));
                    }
                    String escaped = escapePdfString(lines[i]);
                    canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                }

                canvas.endText();
                canvas.restoreState();
            }
        } finally {
            if (stamper != null) {
                try { stamper.close(); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
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

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                PdfContentByte canvas = stamper.getOverContent(pageNumber);
                BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

                // Set transparency
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(opacity);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                canvas.saveState();
                canvas.setGState(gs);
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setRGBColorFill(r, g, b);
                canvas.setTextMatrix(x, y);
                // Use raw PDF operators to avoid AWT dependencies
                String escaped = escapePdfString(text);
                canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                canvas.endText();
                canvas.restoreState();
            }
        } finally {
            if (stamper != null) {
                try { stamper.close(); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        return outputFile;
    }

    /**
     * Get all existing annotations from a PDF page
     */
    public static List<String> getAnnotations(String pdfPath, int pageNumber) {
        List<String> annotations = new ArrayList<>();

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                PdfDictionary page = reader.getPageN(pageNumber);
                PdfArray annots = page.getAsArray(PdfName.ANNOTS);

                if (annots != null) {
                    for (int i = 0; i < annots.size(); i++) {
                        PdfDictionary annot = annots.getAsDict(i);
                        if (annot != null) {
                            PdfString contents = annot.getAsString(PdfName.CONTENTS);
                            if (contents != null) {
                                annotations.add(contents.toUnicodeString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e("PdfAnnotationEditor", "Error getting annotations", e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
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

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                PdfDictionary page = reader.getPageN(pageNumber);
                // Remove annotations array from page
                page.remove(PdfName.ANNOTS);
            }
        } finally {
            if (stamper != null) {
                try { stamper.close(); } catch (Exception ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
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

    /**
     * Escape special characters for PDF string literal.
     */
    private static String escapePdfString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("\r", "\\r")
                   .replace("\n", "\\n");
    }
}
