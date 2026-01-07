package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Editor that creates an image-based copy of the PDF for editing.
 *
 * Strategy:
 * 1. Render each page as a high-resolution image (preserves exact visual appearance)
 * 2. For areas to be edited: cover with white rectangle matching the background
 * 3. Add new text on top of the white rectangle
 *
 * This ensures the original visual appearance is maintained exactly,
 * while allowing text edits that blend in naturally.
 */
public class PdfImageCopyEditor {

    // High DPI for quality image rendering (300 DPI standard for print)
    private static final int RENDER_DPI = 300;
    private static final float PDF_POINTS_PER_INCH = 72f;

    /**
     * Represents a text edit to apply
     */
    public static class TextEdit {
        public int pageNumber;      // 1-indexed
        public float pdfX;          // X position in PDF coordinates
        public float pdfY;          // Y position in PDF coordinates (from bottom)
        public float width;         // Width of text area
        public float height;        // Height of text area
        public String originalText; // Original text (for reference)
        public String newText;      // New text to display
        public float fontSize;      // Font size in points
        public int textColor;       // Text color (Android Color)

        public TextEdit(int pageNumber, float x, float y, float w, float h,
                       String original, String newText, float fontSize) {
            this.pageNumber = pageNumber;
            this.pdfX = x;
            this.pdfY = y;
            this.width = w;
            this.height = h;
            this.originalText = original;
            this.newText = newText;
            this.fontSize = fontSize;
            this.textColor = Color.BLACK;
        }

        public TextEdit setColor(int color) {
            this.textColor = color;
            return this;
        }
    }

    /**
     * Create an edited PDF by:
     * 1. Rendering original pages as high-res images
     * 2. Drawing white rectangles over edit areas
     * 3. Adding new text on the images
     * 4. Converting back to PDF
     */
    public static File createEditedPdf(Context context, String inputPath, File outputDir,
                                        String outputName, List<TextEdit> edits) throws IOException {

        File outputFile = new File(outputDir, outputName);
        File tempDir = new File(context.getCacheDir(), "pdf_edit_temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        PdfDocument pdfDoc = null;

        try {
            // Open original PDF for rendering
            File inputFile = new File(inputPath);
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            // Also open with iText to get original page sizes
            PdfDocument originalDoc = new PdfDocument(new PdfReader(inputPath));

            // Create output PDF
            PdfWriter writer = new PdfWriter(outputFile);
            pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Process each page
            for (int i = 0; i < pageCount; i++) {
                int pageNum = i + 1;

                // Get original page size
                PdfPage origPage = originalDoc.getPage(pageNum);
                float origWidth = origPage.getPageSize().getWidth();
                float origHeight = origPage.getPageSize().getHeight();

                // Collect edits for this page
                List<TextEdit> pageEdits = new ArrayList<>();
                for (TextEdit edit : edits) {
                    if (edit.pageNumber == pageNum) {
                        pageEdits.add(edit);
                    }
                }

                // Render page as high-res bitmap
                PdfRenderer.Page page = renderer.openPage(i);

                // Calculate render size based on DPI
                float scale = RENDER_DPI / PDF_POINTS_PER_INCH;
                int bitmapWidth = (int) (origWidth * scale);
                int bitmapHeight = (int) (origHeight * scale);

                // Limit max size to avoid memory issues
                int maxSize = 4096;
                if (bitmapWidth > maxSize || bitmapHeight > maxSize) {
                    float reduceScale = Math.min((float) maxSize / bitmapWidth,
                                                  (float) maxSize / bitmapHeight);
                    bitmapWidth = (int) (bitmapWidth * reduceScale);
                    bitmapHeight = (int) (bitmapHeight * reduceScale);
                    scale = scale * reduceScale;
                }

                Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                // Apply edits to the bitmap
                if (!pageEdits.isEmpty()) {
                    bitmap = applyEditsToBitmap(bitmap, pageEdits, origWidth, origHeight, scale);
                }

                // Convert bitmap to PDF page
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();
                bitmap.recycle();

                ImageData imageData = ImageDataFactory.create(imageBytes);
                Image pdfImage = new Image(imageData);

                // Set page size to match original
                PageSize pageSize = new PageSize(origWidth, origHeight);
                pdfDoc.addNewPage(pageSize);

                // Scale image to fit page exactly
                pdfImage.setFixedPosition(pageNum, 0, 0);
                pdfImage.scaleToFit(origWidth, origHeight);

                document.add(pdfImage);
            }

            originalDoc.close();
            document.close();

            return outputFile;

        } finally {
            if (renderer != null) {
                renderer.close();
            }
            if (fd != null) {
                fd.close();
            }
            if (pdfDoc != null && !pdfDoc.isClosed()) {
                pdfDoc.close();
            }

            // Clean up temp files
            deleteRecursive(tempDir);
        }
    }

    /**
     * Apply text edits directly to the bitmap
     */
    private static Bitmap applyEditsToBitmap(Bitmap original, List<TextEdit> edits,
                                              float pdfWidth, float pdfHeight, float scale) {
        // Create mutable copy
        Bitmap edited = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(edited);

        // Paints for editing
        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        for (TextEdit edit : edits) {
            // Convert PDF coordinates to bitmap coordinates
            // PDF: origin at bottom-left, Y increases upward
            // Bitmap: origin at top-left, Y increases downward
            float bitmapX = edit.pdfX * scale;
            float bitmapY = (pdfHeight - edit.pdfY - edit.height) * scale;
            float bitmapW = edit.width * scale;
            float bitmapH = edit.height * scale;

            // Draw white rectangle to cover original text
            // Add small padding for complete coverage
            float padding = 2 * scale;
            canvas.drawRect(
                bitmapX - padding,
                bitmapY - padding,
                bitmapX + bitmapW + padding,
                bitmapY + bitmapH + padding,
                whitePaint
            );

            // Draw new text
            textPaint.setColor(edit.textColor);
            textPaint.setTextSize(edit.fontSize * scale);

            // Position text (baseline is at bottom of text)
            float textX = bitmapX;
            float textY = bitmapY + bitmapH - (padding / 2);

            canvas.drawText(edit.newText, textX, textY, textPaint);
        }

        original.recycle();
        return edited;
    }

    /**
     * Create a high-quality image copy of the PDF (no edits)
     * This preserves the exact visual appearance
     */
    public static File createImageCopy(Context context, String inputPath,
                                        File outputDir, String outputName) throws IOException {
        return createEditedPdf(context, inputPath, outputDir, outputName, new ArrayList<>());
    }

    /**
     * Simple method to apply visual edits from VisualPdfEditor.TextBlock list
     */
    public static File applyVisualEdits(Context context, String inputPath,
                                         File outputDir, String outputName,
                                         List<VisualPdfEditor.TextBlock> blocks) throws IOException {

        List<TextEdit> edits = new ArrayList<>();

        for (VisualPdfEditor.TextBlock block : blocks) {
            if (block.isEdited && block.newText != null) {
                TextEdit edit = new TextEdit(
                    block.pageNumber,
                    block.pdfX,
                    block.pdfY,
                    block.width,
                    block.height,
                    block.text,
                    block.newText,
                    block.fontSize > 0 ? block.fontSize : 12f
                );
                edits.add(edit);
            }
        }

        return createEditedPdf(context, inputPath, outputDir, outputName, edits);
    }

    /**
     * Get the dimensions of a PDF page
     */
    public static float[] getPageDimensions(String pdfPath, int pageNumber) {
        try {
            PdfDocument doc = new PdfDocument(new PdfReader(pdfPath));
            PdfPage page = doc.getPage(pageNumber);
            float width = page.getPageSize().getWidth();
            float height = page.getPageSize().getHeight();
            doc.close();
            return new float[]{width, height};
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
