package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// PDFBox-Android imports (Android-compatible, no java.awt dependencies)
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager class for handling PDF editing operations.
 * Supports adding text annotations, drawings, and saving modified PDFs.
 *
 * Thread-safe: Uses concurrent collections for multi-threaded access.
 *
 * Following SOLID principles:
 * - Single Responsibility: Manages PDF edit state and saving
 * - Uses proper resource management with try-with-resources
 */
public class PdfEditManager {

    private static final String TAG = "PdfEditManager";

    private final Context context;
    private final String originalPdfPath;

    // Thread-safe collections for concurrent access from UI and background threads
    private final Map<Integer, List<TextAnnotation>> textAnnotations = new ConcurrentHashMap<>();
    private final Map<Integer, Bitmap> drawingOverlays = new ConcurrentHashMap<>();
    private final List<EditAction> undoStack = Collections.synchronizedList(new ArrayList<>());

    public PdfEditManager(@NonNull Context context, @NonNull String pdfPath) {
        this.context = context.getApplicationContext();
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
        public final ActionType type;
        public final int pageIndex;
        public final Object data;

        public EditAction(@NonNull ActionType type, int pageIndex, @Nullable Object data) {
            this.type = type;
            this.pageIndex = pageIndex;
            this.data = data;
        }
    }

    /**
     * Add a text annotation to a specific page.
     *
     * @param pageIndex Page index (0-based)
     * @param x Normalized X position (0-1)
     * @param y Normalized Y position (0-1)
     * @param text Text content
     * @param color Android color int
     * @param fontSize Font size in points
     */
    public void addTextAnnotation(int pageIndex, float x, float y,
                                   @NonNull String text, int color, float fontSize) {
        TextAnnotation annotation = new TextAnnotation(x, y, text, color, fontSize);

        textAnnotations.computeIfAbsent(pageIndex, k -> Collections.synchronizedList(new ArrayList<>()));
        textAnnotations.get(pageIndex).add(annotation);

        undoStack.add(new EditAction(ActionType.TEXT_ANNOTATION, pageIndex, annotation));

        AppLogger.d(TAG, "Added text annotation on page " + pageIndex);
    }

    /**
     * Set the drawing overlay bitmap for a specific page.
     * Creates a copy of the bitmap to prevent external modifications.
     *
     * @param pageIndex Page index (0-based)
     * @param bitmap The drawing bitmap (will be copied)
     */
    public void setDrawingOverlay(int pageIndex, @Nullable Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            AppLogger.w(TAG, "Attempted to set null or recycled bitmap overlay");
            return;
        }

        // Recycle existing overlay for this page
        Bitmap existing = drawingOverlays.get(pageIndex);
        if (existing != null && !existing.isRecycled()) {
            existing.recycle();
        }

        // Create a copy to ensure we own the bitmap
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy != null) {
            drawingOverlays.put(pageIndex, copy);
            undoStack.add(new EditAction(ActionType.DRAWING_OVERLAY, pageIndex, copy));
            AppLogger.d(TAG, "Set drawing overlay on page " + pageIndex);
        }
    }

    /**
     * Undo the last edit action.
     *
     * @return true if an action was undone, false if nothing to undo
     */
    public boolean undo() {
        synchronized (undoStack) {
            if (undoStack.isEmpty()) {
                return false;
            }

            EditAction lastAction = undoStack.remove(undoStack.size() - 1);

            switch (lastAction.type) {
                case TEXT_ANNOTATION:
                    List<TextAnnotation> annotations = textAnnotations.get(lastAction.pageIndex);
                    if (annotations != null) {
                        annotations.remove(lastAction.data);
                        if (annotations.isEmpty()) {
                            textAnnotations.remove(lastAction.pageIndex);
                        }
                    }
                    break;

                case DRAWING_OVERLAY:
                    Bitmap removedBitmap = drawingOverlays.remove(lastAction.pageIndex);
                    if (removedBitmap != null && !removedBitmap.isRecycled()) {
                        removedBitmap.recycle();
                    }
                    break;
            }

            AppLogger.d(TAG, "Undo: " + lastAction.type);
            return true;
        }
    }

    /**
     * Check if there are any actions to undo.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Clear the undo stack.
     */
    public void clearUndoStack() {
        synchronized (undoStack) {
            undoStack.clear();
        }
    }

    /**
     * Clear all annotations for a specific page.
     *
     * @param pageIndex Page index (0-based)
     */
    public void clearPageAnnotations(int pageIndex) {
        textAnnotations.remove(pageIndex);

        Bitmap bitmap = drawingOverlays.remove(pageIndex);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    /**
     * Clear all annotations and overlays.
     */
    public void clearAllAnnotations() {
        textAnnotations.clear();

        for (Bitmap bitmap : drawingOverlays.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        drawingOverlays.clear();

        AppLogger.d(TAG, "Cleared all annotations");
    }

    /**
     * Check if there are any unsaved changes.
     */
    public boolean hasChanges() {
        return !textAnnotations.isEmpty() || !drawingOverlays.isEmpty();
    }

    /**
     * Save the edited PDF to a new file in Documents folder.
     *
     * @return The path to the saved file
     * @throws IOException if save fails
     */
    @NonNull
    public String saveEditedPdf() throws IOException {
        String outputPath = StorageManager.generateOutputPath(
                new File(originalPdfPath).getName(),
                "edited",
                Constants.EXT_PDF
        );
        return saveEditedPdfTo(outputPath);
    }

    /**
     * Save the edited PDF to a specific path using PDFBox-Android.
     * PDFBox is Android-compatible and doesn't have java.awt dependencies.
     *
     * @param outputPath Destination file path
     * @return The output path on success
     * @throws IOException if save fails
     */
    @NonNull
    public String saveEditedPdfTo(@NonNull String outputPath) throws IOException {
        File outputFile = new File(outputPath);

        // Ensure parent directory exists
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        PDDocument document = null;

        try {
            document = PDDocument.load(new File(originalPdfPath));
            int numberOfPages = document.getNumberOfPages();

            for (int i = 0; i < numberOfPages; i++) {
                PDPage page = document.getPage(i);
                PDRectangle pageSize = page.getMediaBox();

                // Check if there are any annotations for this page
                Bitmap overlay = drawingOverlays.get(i);
                List<TextAnnotation> annotations = textAnnotations.get(i);

                boolean hasOverlay = overlay != null && !overlay.isRecycled();
                boolean hasAnnotations = annotations != null && !annotations.isEmpty();

                if (!hasOverlay && !hasAnnotations) {
                    continue; // Skip pages with no edits
                }

                // Create content stream in APPEND mode
                PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                try {
                    // Add drawing overlay if exists
                    if (hasOverlay) {
                        addBitmapToPage(document, contentStream, overlay, pageSize);
                    }

                    // Add text annotations
                    if (hasAnnotations) {
                        synchronized (annotations) {
                            for (TextAnnotation annotation : annotations) {
                                addTextToPage(contentStream, annotation, pageSize);
                            }
                        }
                    }
                } finally {
                    contentStream.close();
                }
            }

            document.save(outputFile);
            AppLogger.i(TAG, "PDF saved to: " + outputPath);
            return outputPath;

        } catch (Exception e) {
            // Clean up partial output file on failure
            if (outputFile.exists()) {
                outputFile.delete();
            }
            AppLogger.e(TAG, "Failed to save PDF", e);
            throw new IOException("Failed to save PDF: " + e.getMessage(), e);
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Add bitmap overlay to PDF page using PDFBox-Android.
     */
    private void addBitmapToPage(@NonNull PDDocument document,
                                  @NonNull PDPageContentStream contentStream,
                                  @NonNull Bitmap bitmap,
                                  @NonNull PDRectangle pageSize) throws IOException {
        try {
            PDImageXObject image = LosslessFactory.createFromImage(document, bitmap);
            contentStream.drawImage(image, 0, 0, pageSize.getWidth(), pageSize.getHeight());
        } catch (Exception e) {
            throw new IOException("Failed to add bitmap to page", e);
        }
    }

    /**
     * Add text annotation to PDF page using PDFBox-Android.
     */
    private void addTextToPage(@NonNull PDPageContentStream contentStream,
                                @NonNull TextAnnotation annotation,
                                @NonNull PDRectangle pageSize) {
        try {
            PDType1Font font = PDType1Font.HELVETICA;

            // Convert Android coordinates to PDF coordinates (flip Y axis)
            float pdfX = annotation.x * pageSize.getWidth();
            float pdfY = pageSize.getHeight() - (annotation.y * pageSize.getHeight());

            // Convert Android color to RGB values (0-1 range)
            float red = ((annotation.color >> 16) & 0xFF) / 255f;
            float green = ((annotation.color >> 8) & 0xFF) / 255f;
            float blue = (annotation.color & 0xFF) / 255f;

            contentStream.beginText();
            contentStream.setFont(font, annotation.fontSize);
            contentStream.setNonStrokingColor(red, green, blue);
            contentStream.newLineAtOffset(pdfX, pdfY);
            contentStream.showText(annotation.text);
            contentStream.endText();

        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to add text annotation", e);
        }
    }

    /**
     * Get the list of text annotations for a page.
     *
     * @param pageIndex Page index (0-based)
     * @return List of annotations (never null)
     */
    @NonNull
    public List<TextAnnotation> getTextAnnotations(int pageIndex) {
        List<TextAnnotation> annotations = textAnnotations.get(pageIndex);
        return annotations != null ? new ArrayList<>(annotations) : new ArrayList<>();
    }

    /**
     * Remove a specific text annotation.
     *
     * @param pageIndex Page index (0-based)
     * @param annotationIndex Index of annotation to remove
     */
    public void removeTextAnnotation(int pageIndex, int annotationIndex) {
        List<TextAnnotation> annotations = textAnnotations.get(pageIndex);
        if (annotations != null && annotationIndex >= 0 && annotationIndex < annotations.size()) {
            synchronized (annotations) {
                annotations.remove(annotationIndex);
            }
        }
    }

    /**
     * Get the original PDF path.
     */
    @NonNull
    public String getOriginalPdfPath() {
        return originalPdfPath;
    }

    /**
     * Text annotation data class.
     */
    public static class TextAnnotation {
        public final float x;
        public final float y;
        public final String text;
        public final int color;
        public final float fontSize;

        public TextAnnotation(float x, float y, @NonNull String text, int color, float fontSize) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
        }
    }
}
