package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manager class for handling PDF editing operations.
 * Supports adding text annotations, drawings, and saving modified PDFs.
 */
public class PdfEditManager {

    private Context context;
    private String originalPdfPath;
    private Map<Integer, List<TextAnnotation>> textAnnotations = new HashMap<>();
    private Map<Integer, Bitmap> drawingOverlays = new HashMap<>();

    // Undo stack to track all edit operations
    private List<EditAction> undoStack = new ArrayList<>();

    public PdfEditManager(Context context, String pdfPath) {
        this.context = context;
        this.originalPdfPath = pdfPath;
    }

    /**
     * Edit action types for undo functionality
     */
    public enum ActionType {
        TEXT_ANNOTATION,
        DRAWING_OVERLAY
    }

    /**
     * Represents an edit action that can be undone
     */
    public static class EditAction {
        public ActionType type;
        public int pageIndex;
        public Object data; // TextAnnotation or Bitmap reference

        public EditAction(ActionType type, int pageIndex, Object data) {
            this.type = type;
            this.pageIndex = pageIndex;
            this.data = data;
        }
    }

    /**
     * Add a text annotation to a specific page
     */
    public void addTextAnnotation(int pageIndex, float x, float y, String text, int color, float fontSize) {
        TextAnnotation annotation = new TextAnnotation(x, y, text, color, fontSize);

        if (!textAnnotations.containsKey(pageIndex)) {
            textAnnotations.put(pageIndex, new ArrayList<>());
        }
        textAnnotations.get(pageIndex).add(annotation);

        // Track for undo
        undoStack.add(new EditAction(ActionType.TEXT_ANNOTATION, pageIndex, annotation));
    }

    /**
     * Set the drawing overlay bitmap for a specific page
     */
    public void setDrawingOverlay(int pageIndex, Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            drawingOverlays.put(pageIndex, copy);

            // Track for undo
            undoStack.add(new EditAction(ActionType.DRAWING_OVERLAY, pageIndex, copy));
        }
    }

    /**
     * Undo the last edit action
     * @return true if an action was undone, false if nothing to undo
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        EditAction lastAction = undoStack.remove(undoStack.size() - 1);

        switch (lastAction.type) {
            case TEXT_ANNOTATION:
                // Remove the text annotation
                if (textAnnotations.containsKey(lastAction.pageIndex)) {
                    List<TextAnnotation> annotations = textAnnotations.get(lastAction.pageIndex);
                    annotations.remove(lastAction.data);
                    if (annotations.isEmpty()) {
                        textAnnotations.remove(lastAction.pageIndex);
                    }
                }
                break;

            case DRAWING_OVERLAY:
                // Remove the drawing overlay
                Bitmap removedBitmap = drawingOverlays.remove(lastAction.pageIndex);
                if (removedBitmap != null && !removedBitmap.isRecycled()) {
                    removedBitmap.recycle();
                }
                break;
        }

        return true;
    }

    /**
     * Check if there are any actions to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Clear the undo stack
     */
    public void clearUndoStack() {
        undoStack.clear();
    }

    /**
     * Clear all annotations for a page
     */
    public void clearPageAnnotations(int pageIndex) {
        textAnnotations.remove(pageIndex);
        if (drawingOverlays.containsKey(pageIndex)) {
            Bitmap bitmap = drawingOverlays.remove(pageIndex);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    /**
     * Clear all annotations
     */
    public void clearAllAnnotations() {
        textAnnotations.clear();
        for (Bitmap bitmap : drawingOverlays.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        drawingOverlays.clear();
    }

    /**
     * Check if there are any unsaved changes
     */
    public boolean hasChanges() {
        return !textAnnotations.isEmpty() || !drawingOverlays.isEmpty();
    }

    /**
     * Save the edited PDF to a new file
     * @return The path to the saved file, or null if failed
     */
    public String saveEditedPdf() throws IOException {
        // Generate output file name
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File originalFile = new File(originalPdfPath);
        String baseName = originalFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputFile = new File(outputDir, baseName + "_edited_" + timestamp + ".pdf");
        return saveEditedPdfTo(outputFile.getAbsolutePath());
    }

    /**
     * Save the edited PDF to a specific path
     */
    public String saveEditedPdfTo(String outputPath) throws IOException {
        PdfReader reader = new PdfReader(originalPdfPath);
        PdfWriter writer = new PdfWriter(outputPath);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);

        try {
            int numberOfPages = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                int pageIndex = i - 1;
                PdfPage page = pdfDoc.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page);

                // Add drawing overlay if exists
                if (drawingOverlays.containsKey(pageIndex)) {
                    Bitmap overlay = drawingOverlays.get(pageIndex);
                    if (overlay != null && !overlay.isRecycled()) {
                        addBitmapToPage(canvas, overlay, pageSize);
                    }
                }

                // Add text annotations
                if (textAnnotations.containsKey(pageIndex)) {
                    List<TextAnnotation> annotations = textAnnotations.get(pageIndex);
                    for (TextAnnotation annotation : annotations) {
                        addTextToPage(canvas, annotation, pageSize);
                    }
                }
            }

            pdfDoc.close();
            return outputPath;

        } catch (Exception e) {
            pdfDoc.close();
            // Delete failed output file
            new File(outputPath).delete();
            throw new IOException("Failed to save PDF: " + e.getMessage(), e);
        }
    }

    private void addBitmapToPage(PdfCanvas canvas, Bitmap bitmap, Rectangle pageSize) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bitmapData = stream.toByteArray();

        ImageData imageData = ImageDataFactory.create(bitmapData);

        // Scale bitmap to fit page
        float scaleX = pageSize.getWidth() / bitmap.getWidth();
        float scaleY = pageSize.getHeight() / bitmap.getHeight();

        canvas.saveState();
        canvas.addImageFittedIntoRectangle(imageData,
                new Rectangle(0, 0, pageSize.getWidth(), pageSize.getHeight()), false);
        canvas.restoreState();
    }

    private void addTextToPage(PdfCanvas canvas, TextAnnotation annotation, Rectangle pageSize) {
        try {
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Convert Android coordinates to PDF coordinates (flip Y axis)
            float pdfX = annotation.x * pageSize.getWidth();
            float pdfY = pageSize.getHeight() - (annotation.y * pageSize.getHeight());

            // Convert Android color to iText color
            int red = (annotation.color >> 16) & 0xFF;
            int green = (annotation.color >> 8) & 0xFF;
            int blue = annotation.color & 0xFF;

            canvas.beginText()
                    .setFontAndSize(font, annotation.fontSize)
                    .setColor(new DeviceRgb(red, green, blue), true)
                    .moveText(pdfX, pdfY)
                    .showText(annotation.text)
                    .endText();

        } catch (Exception e) {
            AppLogger.e("PdfEditManager", "Error", e);
        }
    }

    /**
     * Get the list of text annotations for a page
     */
    public List<TextAnnotation> getTextAnnotations(int pageIndex) {
        return textAnnotations.getOrDefault(pageIndex, new ArrayList<>());
    }

    /**
     * Remove a specific text annotation
     */
    public void removeTextAnnotation(int pageIndex, int annotationIndex) {
        if (textAnnotations.containsKey(pageIndex)) {
            List<TextAnnotation> annotations = textAnnotations.get(pageIndex);
            if (annotationIndex >= 0 && annotationIndex < annotations.size()) {
                annotations.remove(annotationIndex);
            }
        }
    }

    /**
     * Text annotation data class
     */
    public static class TextAnnotation {
        public float x;      // Normalized X position (0-1)
        public float y;      // Normalized Y position (0-1)
        public String text;
        public int color;
        public float fontSize;

        public TextAnnotation(float x, float y, String text, int color, float fontSize) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
        }
    }
}
