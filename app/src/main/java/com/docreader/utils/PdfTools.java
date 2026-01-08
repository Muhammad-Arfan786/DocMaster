package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive PDF Tools utility class.
 * Provides all common PDF operations in one place.
 *
 * All methods use try-with-resources for proper resource cleanup.
 */
public class PdfTools {

    private static final String TAG = "PdfTools";

    /**
     * Result class for PDF operations
     */
    public static class PdfResult {
        public boolean success;
        public String filePath;
        public String fileName;
        public String message;
        public long fileSize;

        public PdfResult(boolean success, String filePath, String fileName, String message) {
            this.success = success;
            this.filePath = filePath;
            this.fileName = fileName;
            this.message = message;
            if (filePath != null) {
                File f = new File(filePath);
                this.fileSize = f.exists() ? f.length() : 0;
            }
        }

        public String getFileSizeFormatted() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    // ==================== MERGE PDF ====================

    /**
     * Merge multiple PDF files into one
     */
    public static PdfResult mergePdfs(List<String> pdfPaths, File outputDir, String outputName) {
        if (pdfPaths == null || pdfPaths.size() < 2) {
            return new PdfResult(false, null, null, "Need at least 2 PDFs to merge");
        }

        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try (PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument mergedDoc = new PdfDocument(writer)) {

            int totalPages = 0;
            for (String pdfPath : pdfPaths) {
                try (PdfReader reader = new PdfReader(pdfPath);
                     PdfDocument sourceDoc = new PdfDocument(reader)) {
                    sourceDoc.copyPagesTo(1, sourceDoc.getNumberOfPages(), mergedDoc);
                    totalPages += sourceDoc.getNumberOfPages();
                }
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Merged " + pdfPaths.size() + " PDFs (" + totalPages + " pages)");

        } catch (Exception e) {
            AppLogger.e(TAG, "Merge PDFs failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== SPLIT PDF ====================

    /**
     * Split PDF into individual pages
     */
    public static PdfResult splitPdf(String pdfPath, File outputDir) {
        if (!outputDir.exists()) outputDir.mkdirs();

        File inputFile = new File(pdfPath);
        String baseName = inputFile.getName().replace(".pdf", "").replace(".PDF", "");
        List<String> outputFiles = new ArrayList<>();

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument sourceDoc = new PdfDocument(reader)) {

            int pageCount = sourceDoc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                String pageFileName = baseName + "_page_" + i + ".pdf";
                File pageFile = new File(outputDir, pageFileName);

                try (PdfWriter writer = new PdfWriter(pageFile);
                     PdfDocument pageDoc = new PdfDocument(writer)) {
                    sourceDoc.copyPagesTo(i, i, pageDoc);
                }

                outputFiles.add(pageFile.getAbsolutePath());
            }

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_pages",
                    "Split into " + pageCount + " separate PDF files");

        } catch (Exception e) {
            AppLogger.e(TAG, "Split PDF failed", e);
            // Clean up partial files
            for (String path : outputFiles) {
                new File(path).delete();
            }
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    /**
     * Extract specific pages from PDF
     */
    public static PdfResult extractPages(String pdfPath, File outputDir, String outputName, List<Integer> pages) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try (PdfReader srcReader = new PdfReader(pdfPath);
             PdfDocument sourceDoc = new PdfDocument(srcReader);
             PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument newDoc = new PdfDocument(writer)) {

            for (int pageNum : pages) {
                if (pageNum >= 1 && pageNum <= sourceDoc.getNumberOfPages()) {
                    sourceDoc.copyPagesTo(pageNum, pageNum, newDoc);
                }
            }

            int extractedCount = newDoc.getNumberOfPages();
            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Extracted " + extractedCount + " pages");

        } catch (Exception e) {
            AppLogger.e(TAG, "Extract pages failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== COMPRESS PDF ====================

    /**
     * Compress PDF by reducing image quality and optimizing
     */
    public static PdfResult compressPdf(Context context, String pdfPath, File outputDir, String outputName) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);
        File inputFile = new File(pdfPath);

        if (!inputFile.exists()) {
            return new PdfResult(false, null, null, "Source file not found");
        }

        long originalSize = inputFile.length();
        PdfRenderer renderer = null;
        ParcelFileDescriptor fd = null;

        try {
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            if (pageCount == 0) {
                return new PdfResult(false, null, null, "PDF has no pages");
            }

            try (PdfReader origReader = new PdfReader(pdfPath);
                 PdfDocument originalDoc = new PdfDocument(origReader);
                 PdfWriter writer = new PdfWriter(outputFile);
                 PdfDocument compressedDoc = new PdfDocument(writer);
                 Document document = new Document(compressedDoc)) {

                for (int i = 0; i < pageCount; i++) {
                    PdfPage origPage = originalDoc.getPage(i + 1);
                    float pageWidth = origPage.getPageSize().getWidth();
                    float pageHeight = origPage.getPageSize().getHeight();

                    // Render at lower DPI for compression
                    PdfRenderer.Page page = renderer.openPage(i);
                    float scale = 1.5f; // 108 DPI (72 * 1.5)
                    int bitmapWidth = (int) (pageWidth * scale);
                    int bitmapHeight = (int) (pageHeight * scale);

                    // Limit bitmap size to prevent memory issues
                    int maxSize = 2048;
                    if (bitmapWidth > maxSize || bitmapHeight > maxSize) {
                        float reduceScale = Math.min((float) maxSize / bitmapWidth, (float) maxSize / bitmapHeight);
                        bitmapWidth = (int) (bitmapWidth * reduceScale);
                        bitmapHeight = (int) (bitmapHeight * reduceScale);
                    }

                    Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();

                    // Compress to JPEG with quality 60 for better compression
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                        byte[] imageBytes = baos.toByteArray();
                        bitmap.recycle();

                        // Add to new PDF
                        ImageData imageData = ImageDataFactory.create(imageBytes);
                        Image pdfImage = new Image(imageData);

                        PageSize pageSize = new PageSize(pageWidth, pageHeight);
                        compressedDoc.addNewPage(pageSize);

                        pdfImage.setFixedPosition(i + 1, 0, 0);
                        pdfImage.scaleToFit(pageWidth, pageHeight);
                        document.add(pdfImage);
                    }
                }
            }

            long newSize = outputFile.length();
            float reduction = (1 - (float) newSize / originalSize) * 100;

            String resultMessage;
            if (reduction > 0) {
                resultMessage = String.format("Compressed: %.1f KB -> %.1f KB (%.0f%% smaller)",
                        originalSize / 1024.0, newSize / 1024.0, reduction);
            } else {
                resultMessage = String.format("Processed: %.1f KB -> %.1f KB (file was already optimized)",
                        originalSize / 1024.0, newSize / 1024.0);
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName, resultMessage);

        } catch (OutOfMemoryError e) {
            AppLogger.e(TAG, "Out of memory during compression", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Out of memory - PDF too large to compress");
        } catch (Exception e) {
            AppLogger.e(TAG, "Compress PDF failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            ResourceManager.closeQuietly(renderer);
            ResourceManager.closeQuietly(fd);
        }
    }

    // ==================== ADD PAGE NUMBERS ====================

    /**
     * Add page numbers to PDF
     */
    public static PdfResult addPageNumbers(String pdfPath, File outputDir, String outputName,
                                            String position, int startNumber) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);
        File tempFile = new File(outputDir, fileName + ".tmp");

        try {
            // Copy original first
            copyFile(new File(pdfPath), tempFile);

            try (PdfReader reader = new PdfReader(tempFile);
                 PdfWriter writer = new PdfWriter(outputFile);
                 PdfDocument doc = new PdfDocument(reader, writer)) {

                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                int pageCount = doc.getNumberOfPages();

                for (int i = 1; i <= pageCount; i++) {
                    PdfPage page = doc.getPage(i);
                    Rectangle pageSize = page.getPageSize();
                    PdfCanvas canvas = new PdfCanvas(page);

                    String pageNum = String.valueOf(startNumber + i - 1);

                    float x, y;
                    switch (position.toLowerCase()) {
                        case "top-left":
                            x = 40; y = pageSize.getHeight() - 30;
                            break;
                        case "top-center":
                            x = pageSize.getWidth() / 2; y = pageSize.getHeight() - 30;
                            break;
                        case "top-right":
                            x = pageSize.getWidth() - 40; y = pageSize.getHeight() - 30;
                            break;
                        case "bottom-left":
                            x = 40; y = 30;
                            break;
                        case "bottom-right":
                            x = pageSize.getWidth() - 40; y = 30;
                            break;
                        default: // bottom-center
                            x = pageSize.getWidth() / 2; y = 30;
                            break;
                    }

                    canvas.beginText();
                    canvas.setFontAndSize(font, 11);
                    canvas.setFillColor(ColorConstants.BLACK);
                    canvas.moveText(x, y);
                    canvas.showText(pageNum);
                    canvas.endText();
                }

                return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                        "Added page numbers to " + pageCount + " pages");
            }

        } catch (Exception e) {
            AppLogger.e(TAG, "Add page numbers failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            tempFile.delete();
        }
    }

    // ==================== PDF TO IMAGES ====================

    /**
     * Convert PDF pages to images
     */
    public static PdfResult pdfToImages(Context context, String pdfPath, File outputDir,
                                         String format, int dpi) {
        if (!outputDir.exists()) outputDir.mkdirs();

        File inputFile = new File(pdfPath);
        String baseName = inputFile.getName().replace(".pdf", "").replace(".PDF", "");
        List<String> outputFiles = new ArrayList<>();

        PdfRenderer renderer = null;
        ParcelFileDescriptor fd = null;

        try {
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            try (PdfReader reader = new PdfReader(pdfPath);
                 PdfDocument doc = new PdfDocument(reader)) {

                for (int i = 0; i < pageCount; i++) {
                    PdfPage page = doc.getPage(i + 1);
                    float pageWidth = page.getPageSize().getWidth();
                    float pageHeight = page.getPageSize().getHeight();

                    PdfRenderer.Page renderPage = renderer.openPage(i);

                    float scale = dpi / 72f;
                    int bitmapWidth = (int) (pageWidth * scale);
                    int bitmapHeight = (int) (pageHeight * scale);

                    // Limit size
                    int maxSize = 4096;
                    if (bitmapWidth > maxSize || bitmapHeight > maxSize) {
                        float reduceScale = Math.min((float) maxSize / bitmapWidth, (float) maxSize / bitmapHeight);
                        bitmapWidth = (int) (bitmapWidth * reduceScale);
                        bitmapHeight = (int) (bitmapHeight * reduceScale);
                    }

                    Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    renderPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    renderPage.close();

                    // Save image
                    String ext = format.equalsIgnoreCase("png") ? ".png" : ".jpg";
                    String imageFileName = baseName + "_page_" + (i + 1) + ext;
                    File imageFile = new File(outputDir, imageFileName);

                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        if (format.equalsIgnoreCase("png")) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        } else {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        }
                    }
                    bitmap.recycle();

                    outputFiles.add(imageFile.getAbsolutePath());
                }
            }

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_images",
                    "Converted " + pageCount + " pages to " + format.toUpperCase() + " images");

        } catch (Exception e) {
            AppLogger.e(TAG, "PDF to images failed", e);
            // Clean up partial files
            for (String path : outputFiles) {
                new File(path).delete();
            }
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            ResourceManager.closeQuietly(renderer);
            ResourceManager.closeQuietly(fd);
        }
    }

    // ==================== ADD WATERMARK ====================

    /**
     * Add text watermark to PDF
     */
    public static PdfResult addWatermark(String pdfPath, File outputDir, String outputName,
                                          String watermarkText, float opacity, float fontSize,
                                          int rotation, int color) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument doc = new PdfDocument(reader, writer)) {

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            int pageCount = doc.getNumberOfPages();

            // Extract RGB from color
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = doc.getPage(i);
                Rectangle pageSize = page.getPageSize();

                // Create canvas on top of existing content
                PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), doc);

                canvas.saveState();

                // Set transparency
                PdfExtGState gs = new PdfExtGState();
                gs.setFillOpacity(opacity);
                canvas.setExtGState(gs);

                // Calculate center position
                float centerX = pageSize.getWidth() / 2;
                float centerY = pageSize.getHeight() / 2;

                // Calculate text width for centering
                float textWidth = font.getWidth(watermarkText, fontSize);

                // Apply rotation transformation around center
                double radians = Math.toRadians(rotation);
                float cos = (float) Math.cos(radians);
                float sin = (float) Math.sin(radians);

                // Transform: translate to center, rotate
                canvas.concatMatrix(cos, sin, -sin, cos, centerX, centerY);

                // Draw text centered at origin (which is now at page center)
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setFillColor(new DeviceRgb(r, g, b));
                canvas.moveText(-textWidth / 2, -fontSize / 3);
                canvas.showText(watermarkText);
                canvas.endText();

                canvas.restoreState();
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Added watermark to " + pageCount + " pages");

        } catch (Exception e) {
            AppLogger.e(TAG, "Add watermark failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== DELETE PAGES ====================

    /**
     * Delete specific pages from PDF
     */
    public static PdfResult deletePages(String pdfPath, File outputDir, String outputName,
                                         List<Integer> pagesToDelete) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try (PdfReader srcReader = new PdfReader(pdfPath);
             PdfDocument sourceDoc = new PdfDocument(srcReader);
             PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument newDoc = new PdfDocument(writer)) {

            int originalCount = sourceDoc.getNumberOfPages();
            int deletedCount = 0;

            for (int i = 1; i <= originalCount; i++) {
                if (!pagesToDelete.contains(i)) {
                    sourceDoc.copyPagesTo(i, i, newDoc);
                } else {
                    deletedCount++;
                }
            }

            int remainingPages = newDoc.getNumberOfPages();

            if (remainingPages == 0) {
                // Will be deleted in finally or after close
                return new PdfResult(false, null, null, "Cannot delete all pages");
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Deleted " + deletedCount + " page(s), " + remainingPages + " remaining");

        } catch (Exception e) {
            AppLogger.e(TAG, "Delete pages failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== COPY PDF ====================

    /**
     * Create a copy of PDF
     */
    public static PdfResult copyPdf(String pdfPath, File outputDir, String outputName) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try {
            copyFile(new File(pdfPath), outputFile);

            try (PdfReader reader = new PdfReader(outputFile);
                 PdfDocument doc = new PdfDocument(reader)) {
                int pageCount = doc.getNumberOfPages();
                return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                        "Created copy with " + pageCount + " pages");
            }

        } catch (Exception e) {
            AppLogger.e(TAG, "Copy PDF failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== ROTATE PAGES ====================

    /**
     * Rotate pages in PDF
     */
    public static PdfResult rotatePages(String pdfPath, File outputDir, String outputName,
                                         List<Integer> pagesToRotate, int degrees) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputFile);
             PdfDocument doc = new PdfDocument(reader, writer)) {

            int rotatedCount = 0;
            for (int pageNum : pagesToRotate) {
                if (pageNum >= 1 && pageNum <= doc.getNumberOfPages()) {
                    PdfPage page = doc.getPage(pageNum);
                    int currentRotation = page.getRotation();
                    page.setRotation((currentRotation + degrees) % 360);
                    rotatedCount++;
                }
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Rotated " + rotatedCount + " page(s) by " + degrees + " degrees");

        } catch (Exception e) {
            AppLogger.e(TAG, "Rotate pages failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== GET PDF INFO ====================

    /**
     * Get information about a PDF
     */
    public static String getPdfInfo(String pdfPath) {
        File file = new File(pdfPath);

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {

            int pageCount = doc.getNumberOfPages();
            PdfPage firstPage = doc.getPage(1);
            Rectangle size = firstPage.getPageSize();

            return "File: " + file.getName() + "\n" +
                    "Size: " + formatFileSize(file.length()) + "\n" +
                    "Pages: " + pageCount + "\n" +
                    "Page Size: " + String.format("%.0f x %.0f pts", size.getWidth(), size.getHeight());

        } catch (Exception e) {
            AppLogger.e(TAG, "Get PDF info failed", e);
            return "Error reading PDF: " + e.getMessage();
        }
    }

    /**
     * Get page count of PDF
     */
    public static int getPageCount(String pdfPath) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            AppLogger.e(TAG, "Get page count failed", e);
            return 0;
        }
    }

    // ==================== HELPER METHODS ====================

    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
}
