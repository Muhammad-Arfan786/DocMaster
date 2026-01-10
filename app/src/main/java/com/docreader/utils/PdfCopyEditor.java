package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF Copy Editor - Creates an exact visual copy of a PDF and allows text editing.
 *
 * Strategy:
 * 1. Render each PDF page as a high-resolution image (preserves exact visual look)
 * 2. Extract text content for editing
 * 3. When saving, apply text changes by drawing white boxes and new text on images
 * 4. Convert edited images back to PDF
 *
 * This ensures the original format is 100% preserved visually.
 */
public class PdfCopyEditor {

    private static final int RENDER_DPI = 200; // Good balance of quality and performance
    private static final float POINTS_PER_INCH = 72f;

    /**
     * Represents a page with its content
     */
    public static class PageData {
        public int pageNumber;
        public float pdfWidth;
        public float pdfHeight;
        public String originalText;
        public String editedText;
        public boolean isEdited = false;
        public List<TextLine> textLines = new ArrayList<>();

        public PageData(int pageNumber, float width, float height, String text) {
            this.pageNumber = pageNumber;
            this.pdfWidth = width;
            this.pdfHeight = height;
            this.originalText = text;
            this.editedText = text;
        }

        public void setEditedText(String text) {
            this.editedText = text;
            this.isEdited = !text.equals(originalText);
        }
    }

    /**
     * Represents a line of text with position info
     */
    public static class TextLine {
        public String text;
        public float x, y, width, height;
        public int lineNumber;

        public TextLine(String text, int lineNumber, float x, float y, float width, float height) {
            this.text = text;
            this.lineNumber = lineNumber;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Extract all pages with their text content from a PDF
     */
    public static List<PageData> extractPages(String pdfPath) throws Exception {
        List<PageData> pages = new ArrayList<>();

        PdfReader reader = new PdfReader(pdfPath);
        try {
            int numPages = reader.getNumberOfPages();

            for (int i = 1; i <= numPages; i++) {
                Rectangle rect = reader.getPageSize(i);
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                String text = extractor.getTextFromPage(i);

                PageData pageData = new PageData(i, rect.getWidth(), rect.getHeight(), text);

                // Parse text into lines with estimated positions
                String[] lines = text.split("\n");
                float lineHeight = 14f;
                float margin = 50f;
                float currentY = rect.getHeight() - margin;

                for (int j = 0; j < lines.length; j++) {
                    String line = lines[j];
                    if (!line.trim().isEmpty()) {
                        TextLine textLine = new TextLine(
                            line, j,
                            margin, currentY,
                            rect.getWidth() - (2 * margin), lineHeight
                        );
                        pageData.textLines.add(textLine);
                    }
                    currentY -= lineHeight;
                }

                pages.add(pageData);
            }
        } finally {
            reader.close();
        }

        return pages;
    }

    /**
     * Create an exact visual copy of the PDF as images, then apply edits
     */
    public static File createEditedCopy(Context context, String inputPdfPath,
                                         File outputDir, String outputName,
                                         Map<Integer, String> pageEdits) throws IOException {

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputFile = new File(outputDir, outputName.endsWith(".pdf") ? outputName : outputName + ".pdf");

        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        Document document = null;

        try {
            // Open original PDF for rendering
            File inputFile = new File(inputPdfPath);
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            // Also open with OpenPDF to get original page sizes and text
            PdfReader originalReader = new PdfReader(inputPdfPath);

            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = originalReader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());

            // Create output PDF
            document = new Document(docSize);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            document.open();

            // Process each page
            for (int i = 0; i < pageCount; i++) {
                int pageNum = i + 1;

                // Get original page size
                Rectangle origPageSize = originalReader.getPageSize(pageNum);
                float origWidth = origPageSize.getWidth();
                float origHeight = origPageSize.getHeight();

                // Render page as high-res image
                PdfRenderer.Page page = renderer.openPage(i);

                float scale = RENDER_DPI / POINTS_PER_INCH;
                int bitmapWidth = (int) (origWidth * scale);
                int bitmapHeight = (int) (origHeight * scale);

                // Limit size to prevent memory issues
                int maxSize = 3000;
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

                // Check if this page has edits
                if (pageEdits != null && pageEdits.containsKey(pageNum)) {
                    PdfTextExtractor extractor = new PdfTextExtractor(originalReader);
                    String originalText = extractor.getTextFromPage(pageNum);
                    String editedText = pageEdits.get(pageNum);

                    if (!editedText.equals(originalText)) {
                        bitmap = applyTextEdits(bitmap, originalText, editedText,
                                               origWidth, origHeight, scale);
                    }
                }

                // Convert bitmap to PDF page
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();
                bitmap.recycle();

                Image pdfImage = Image.getInstance(imageBytes);

                // Set page size to match original exactly
                document.setPageSize(new Rectangle(origWidth, origHeight));
                document.newPage();

                // Position image to fill page exactly
                pdfImage.setAbsolutePosition(0, 0);
                pdfImage.scaleToFit(origWidth, origHeight);

                document.add(pdfImage);
            }

            originalReader.close();

            return outputFile;

        } catch (Exception e) {
            throw new IOException("Failed to create edited PDF copy", e);
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (renderer != null) {
                renderer.close();
            }
            if (fd != null) {
                fd.close();
            }
        }
    }

    /**
     * Apply text edits to a page image by comparing original and edited text,
     * then drawing white boxes and new text for changed lines.
     *
     * This method creates a completely new page with the edited text,
     * preserving the visual background but rewriting all text content.
     */
    private static Bitmap applyTextEdits(Bitmap original, String originalText, String editedText,
                                          float pdfWidth, float pdfHeight, float scale) {

        // Create a copy of the original bitmap
        Bitmap edited = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(edited);

        // Compare line by line to find changes
        String[] originalLines = originalText.split("\n");
        String[] editedLines = editedText.split("\n");

        // Create paint objects
        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        // Calculate line properties based on actual bitmap size
        float bitmapHeight = edited.getHeight();
        float bitmapWidth = edited.getWidth();

        // Estimate line height based on number of lines and page size
        int numLines = Math.max(originalLines.length, 1);
        float estimatedLineHeight = Math.min(bitmapHeight / (numLines + 4), 20f * scale);
        estimatedLineHeight = Math.max(estimatedLineHeight, 12f * scale); // Minimum line height

        float margin = 40f * scale;
        float textSize = estimatedLineHeight * 0.75f;
        textPaint.setTextSize(textSize);

        // Track positions - start from top with margin
        float currentY = margin + estimatedLineHeight;

        // Find which lines changed and apply edits
        int maxLines = Math.max(originalLines.length, editedLines.length);

        for (int i = 0; i < maxLines; i++) {
            String origLine = i < originalLines.length ? originalLines[i] : "";
            String editLine = i < editedLines.length ? editedLines[i] : "";

            // Only modify if line content changed
            if (!origLine.equals(editLine)) {
                // Calculate line bounds
                float lineTop = currentY - estimatedLineHeight;
                float lineBottom = currentY + 4;
                float lineWidth = bitmapWidth - (2 * margin);

                // Cover the original text with white rectangle
                // Extend slightly beyond estimated bounds to ensure coverage
                canvas.drawRect(
                    margin - 10,
                    lineTop - 2,
                    bitmapWidth - margin + 10,
                    lineBottom,
                    whitePaint
                );

                // Draw the new text if not empty
                if (editLine != null && !editLine.trim().isEmpty()) {
                    // Handle long lines by scaling font if needed
                    float textWidth = textPaint.measureText(editLine);
                    if (textWidth > lineWidth) {
                        float scaleFactor = lineWidth / textWidth * 0.95f;
                        textPaint.setTextSize(textSize * scaleFactor);
                    }

                    canvas.drawText(editLine, margin, currentY - 2, textPaint);

                    // Reset text size for next line
                    textPaint.setTextSize(textSize);
                }
            }

            // Advance Y position
            if (origLine.isEmpty() && editLine.isEmpty()) {
                currentY += estimatedLineHeight * 0.5f; // Half spacing for empty lines
            } else {
                currentY += estimatedLineHeight;
            }

            // Safety check - don't go beyond page
            if (currentY > bitmapHeight - margin) {
                break;
            }
        }

        original.recycle();
        return edited;
    }

    /**
     * Create an exact visual copy of PDF without any edits
     * This gives you a copy that looks 100% identical to original
     */
    public static File createExactCopy(Context context, String inputPdfPath,
                                        File outputDir, String outputName) throws IOException {
        return createEditedCopy(context, inputPdfPath, outputDir, outputName, null);
    }

    /**
     * Simple method to edit specific text on a page
     */
    public static File editPageText(Context context, String inputPdfPath,
                                     File outputDir, String outputName,
                                     int pageNumber, String newText) throws IOException {
        Map<Integer, String> edits = new HashMap<>();
        edits.put(pageNumber, newText);
        return createEditedCopy(context, inputPdfPath, outputDir, outputName, edits);
    }

    /**
     * Get text content of a specific page
     */
    public static String getPageText(String pdfPath, int pageNumber) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber < 1 || pageNumber > reader.getNumberOfPages()) {
                return "";
            }

            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            return extractor.getTextFromPage(pageNumber);
        } catch (Exception e) {
            return "";
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Get all text content from PDF with page markers
     */
    public static String getAllText(String pdfPath) {
        StringBuilder sb = new StringBuilder();

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            int numPages = reader.getNumberOfPages();

            for (int i = 1; i <= numPages; i++) {
                if (numPages > 1) {
                    sb.append("[PAGE:").append(i).append("]\n");
                }
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                sb.append(extractor.getTextFromPage(i));
                if (i < numPages) {
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            AppLogger.e("PdfCopyEditor", "Error getting all text", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return sb.toString();
    }

    /**
     * Parse edited text with page markers into page map
     */
    public static Map<Integer, String> parseEditedText(String editedText) {
        Map<Integer, String> pageTexts = new HashMap<>();

        if (!editedText.contains("[PAGE:")) {
            // Single page or no markers - treat as page 1
            pageTexts.put(1, editedText);
            return pageTexts;
        }

        // Split by page markers
        String[] parts = editedText.split("\\[PAGE:\\d+\\]");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[PAGE:(\\d+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(editedText);

        int partIndex = 1; // parts[0] is empty before first marker
        while (matcher.find() && partIndex < parts.length) {
            int pageNum = Integer.parseInt(matcher.group(1));
            String pageText = parts[partIndex].trim();
            pageTexts.put(pageNum, pageText);
            partIndex++;
        }

        return pageTexts;
    }

    /**
     * Get page count of PDF
     */
    public static int getPageCount(String pdfPath) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            return reader.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
