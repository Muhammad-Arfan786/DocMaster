package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

// PDFBox-Android imports (Android-compatible, no java.awt dependencies)
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.util.Matrix;

// OpenPDF imports - ONLY for rotation operations (no text, no java.awt)
// CAUTION: Document, Rectangle, PageSize, PdfCopy, showText trigger java.awt crash
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive PDF Tools utility class.
 * Provides all common PDF operations in one place.
 * Uses OpenPDF library (LGPL license - free for commercial use).
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
     * Merge multiple PDF files into one using PDFBox-Android.
     * PDFBox is Android-compatible and doesn't have java.awt dependencies.
     */
    public static PdfResult mergePdfs(List<String> pdfPaths, File outputDir, String outputName) {
        if (pdfPaths == null || pdfPaths.size() < 2) {
            return new PdfResult(false, null, null, "Need at least 2 PDFs to merge");
        }

        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        try {
            // Use PDFBox-Android for merging (no java.awt dependencies)
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(outputFile.getAbsolutePath());

            int totalPages = 0;
            for (String pdfPath : pdfPaths) {
                merger.addSource(new File(pdfPath));
                // Count pages using Android's PdfRenderer
                totalPages += ResourceManager.getPageCount(pdfPath);
            }

            // Perform the merge
            merger.mergeDocuments(null);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Merged " + pdfPaths.size() + " PDFs (" + totalPages + " pages)");

        } catch (Exception e) {
            AppLogger.e(TAG, "Merge PDFs failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== SPLIT PDF ====================

    /**
     * Split PDF into individual pages using PDFBox-Android.
     */
    public static PdfResult splitPdf(String pdfPath, File outputDir) {
        if (!outputDir.exists()) outputDir.mkdirs();

        File inputFile = new File(pdfPath);
        String baseName = inputFile.getName().replace(".pdf", "").replace(".PDF", "");
        List<String> outputFiles = new ArrayList<>();

        PDDocument document = null;
        try {
            document = PDDocument.load(inputFile);
            Splitter splitter = new Splitter();
            List<PDDocument> pages = splitter.split(document);

            int pageNum = 1;
            for (PDDocument page : pages) {
                String pageFileName = baseName + "_page_" + pageNum + ".pdf";
                File pageFile = new File(outputDir, pageFileName);
                page.save(pageFile);
                page.close();
                outputFiles.add(pageFile.getAbsolutePath());
                pageNum++;
            }

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_pages",
                    "Split into " + (pageNum - 1) + " separate PDF files");

        } catch (Exception e) {
            AppLogger.e(TAG, "Split PDF failed", e);
            for (String path : outputFiles) {
                new File(path).delete();
            }
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (document != null) try { document.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Extract specific pages from PDF using PDFBox-Android.
     */
    public static PdfResult extractPages(String pdfPath, File outputDir, String outputName, List<Integer> pages) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        PDDocument sourceDoc = null;
        PDDocument targetDoc = null;
        int extractedCount = 0;

        try {
            sourceDoc = PDDocument.load(new File(pdfPath));
            targetDoc = new PDDocument();

            for (int pageNum : pages) {
                // PDFBox uses 0-based index
                int pageIndex = pageNum - 1;
                if (pageIndex >= 0 && pageIndex < sourceDoc.getNumberOfPages()) {
                    PDPage page = sourceDoc.getPage(pageIndex);
                    targetDoc.importPage(page);
                    extractedCount++;
                }
            }

            targetDoc.save(outputFile);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Extracted " + extractedCount + " pages");

        } catch (Exception e) {
            AppLogger.e(TAG, "Extract pages failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (targetDoc != null) try { targetDoc.close(); } catch (Exception ignored) {}
            if (sourceDoc != null) try { sourceDoc.close(); } catch (Exception ignored) {}
        }
    }

    // ==================== COMPRESS PDF ====================

    /**
     * Compress PDF by reducing image quality and optimizing using PDFBox-Android.
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
        PDDocument outputDoc = null;

        try {
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            if (pageCount == 0) {
                return new PdfResult(false, null, null, "PDF has no pages");
            }

            outputDoc = new PDDocument();

            for (int i = 0; i < pageCount; i++) {
                // Get page size using Android's PdfRenderer
                PdfRenderer.Page page = renderer.openPage(i);
                float pageWidth = page.getWidth();
                float pageHeight = page.getHeight();

                // Render at lower DPI for compression
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

                // Create new page with original dimensions
                PDPage pdPage = new PDPage(new PDRectangle(pageWidth, pageHeight));
                outputDoc.addPage(pdPage);

                // Add bitmap as JPEG image to page
                PDImageXObject pdImage = LosslessFactory.createFromImage(outputDoc, bitmap);
                bitmap.recycle();

                PDPageContentStream contentStream = new PDPageContentStream(outputDoc, pdPage);
                contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                contentStream.close();
            }

            outputDoc.save(outputFile);

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
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Out of memory - PDF too large to compress");
        } catch (Exception e) {
            AppLogger.e(TAG, "Compress PDF failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (outputDoc != null) try { outputDoc.close(); } catch (Exception ignored) {}
            ResourceManager.closeQuietly(renderer);
            ResourceManager.closeQuietly(fd);
        }
    }

    // ==================== ADD PAGE NUMBERS ====================

    /**
     * Add page numbers to PDF using PDFBox-Android.
     * OpenPDF's PdfStamper text methods trigger java.awt.font.GlyphVector crash.
     */
    public static PdfResult addPageNumbers(String pdfPath, File outputDir, String outputName,
                                            String position, int startNumber) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        PDDocument document = null;

        try {
            document = PDDocument.load(new File(pdfPath));
            int pageCount = document.getNumberOfPages();
            PDType1Font font = PDType1Font.HELVETICA;
            float fontSize = 11;

            for (int i = 0; i < pageCount; i++) {
                PDPage page = document.getPage(i);
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();

                String pageNum = String.valueOf(startNumber + i);
                float textWidth = font.getStringWidth(pageNum) / 1000 * fontSize;

                float x, y;
                switch (position.toLowerCase()) {
                    case "top-left":
                        x = 40; y = pageHeight - 30;
                        break;
                    case "top-center":
                        x = (pageWidth - textWidth) / 2; y = pageHeight - 30;
                        break;
                    case "top-right":
                        x = pageWidth - 40 - textWidth; y = pageHeight - 30;
                        break;
                    case "bottom-left":
                        x = 40; y = 30;
                        break;
                    case "bottom-right":
                        x = pageWidth - 40 - textWidth; y = 30;
                        break;
                    default: // bottom-center
                        x = (pageWidth - textWidth) / 2; y = 30;
                        break;
                }

                // Use APPEND mode to add content on top of existing page
                PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.setNonStrokingColor(0, 0, 0); // Black
                contentStream.newLineAtOffset(x, y);
                contentStream.showText(pageNum);
                contentStream.endText();
                contentStream.close();
            }

            document.save(outputFile);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Added page numbers to " + pageCount + " pages");

        } catch (Exception e) {
            AppLogger.e(TAG, "Add page numbers failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== PDF TO IMAGES ====================

    /**
     * Convert PDF pages to images.
     * Uses ResourceManager.getPageSize() to avoid java.awt.Color crash.
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

            for (int i = 0; i < pageCount; i++) {
                // Use ResourceManager to get page size (avoids java.awt.Color crash)
                float[] pageDims = ResourceManager.getPageSize(pdfPath, i + 1);
                float pageWidth = pageDims[0];
                float pageHeight = pageDims[1];

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

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_images",
                    "Converted " + pageCount + " pages to " + format.toUpperCase() + " images");

        } catch (Exception e) {
            AppLogger.e(TAG, "PDF to images failed", e);
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
     * Add text watermark to PDF using PDFBox-Android.
     * OpenPDF's PdfStamper text methods trigger java.awt.font.GlyphVector crash.
     */
    public static PdfResult addWatermark(String pdfPath, File outputDir, String outputName,
                                          String watermarkText, float opacity, float fontSize,
                                          int rotation, int color) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        PDDocument document = null;

        try {
            document = PDDocument.load(new File(pdfPath));
            int pageCount = document.getNumberOfPages();
            PDType1Font font = PDType1Font.HELVETICA_BOLD;

            // Extract RGB from color (0-255 range to 0-1 range for PDFBox)
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            for (int i = 0; i < pageCount; i++) {
                PDPage page = document.getPage(i);
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();

                // Calculate text width for centering
                float textWidth = font.getStringWidth(watermarkText) / 1000 * fontSize;
                float centerX = pageWidth / 2;
                float centerY = pageHeight / 2;

                // Use APPEND mode to add content on top of existing page
                PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true);

                // Set transparency
                PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                graphicsState.setNonStrokingAlphaConstant(opacity);
                contentStream.setGraphicsStateParameters(graphicsState);

                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.setNonStrokingColor(r, g, b);

                // Apply rotation using transformation matrix
                // Move to center, rotate, then offset by half text width
                double radians = Math.toRadians(rotation);
                float cos = (float) Math.cos(radians);
                float sin = (float) Math.sin(radians);

                Matrix textMatrix = new Matrix(
                        cos, sin, -sin, cos,
                        centerX - (textWidth / 2) * cos,
                        centerY - (textWidth / 2) * sin
                );
                contentStream.setTextMatrix(textMatrix);
                contentStream.showText(watermarkText);
                contentStream.endText();
                contentStream.close();
            }

            document.save(outputFile);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Added watermark to " + pageCount + " pages");

        } catch (Exception e) {
            AppLogger.e(TAG, "Add watermark failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== DELETE PAGES ====================

    /**
     * Delete specific pages from PDF using PDFBox-Android.
     * Avoids Document, Rectangle, PdfCopy which trigger java.awt.Color crash.
     */
    public static PdfResult deletePages(String pdfPath, File outputDir, String outputName,
                                         List<Integer> pagesToDelete) {
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
        File outputFile = new File(outputDir, fileName);

        PDDocument document = null;

        try {
            document = PDDocument.load(new File(pdfPath));
            int originalCount = document.getNumberOfPages();

            // Sort page numbers in descending order to avoid index shifting
            List<Integer> sortedPages = new ArrayList<>(pagesToDelete);
            sortedPages.sort((a, b) -> b - a);

            int deletedCount = 0;
            for (int pageNum : sortedPages) {
                // PDFBox uses 0-based index
                int pageIndex = pageNum - 1;
                if (pageIndex >= 0 && pageIndex < document.getNumberOfPages()) {
                    document.removePage(pageIndex);
                    deletedCount++;
                }
            }

            int remainingPages = document.getNumberOfPages();

            if (remainingPages == 0) {
                document.close();
                return new PdfResult(false, null, null, "Cannot delete all pages");
            }

            document.save(outputFile);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Deleted " + deletedCount + " page(s), " + remainingPages + " remaining");

        } catch (Exception e) {
            AppLogger.e(TAG, "Delete pages failed", e);
            if (outputFile.exists()) outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
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

            PdfReader reader = new PdfReader(outputFile.getAbsolutePath());
            int pageCount = reader.getNumberOfPages();
            reader.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Created copy with " + pageCount + " pages");

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

        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputFile));

            int rotatedCount = 0;
            for (int pageNum : pagesToRotate) {
                if (pageNum >= 1 && pageNum <= reader.getNumberOfPages()) {
                    int currentRotation = reader.getPageRotation(pageNum);
                    reader.getPageN(pageNum).put(PdfName.ROTATE, new PdfNumber((currentRotation + degrees) % 360));
                    rotatedCount++;
                }
            }

            stamper.close();
            reader.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Rotated " + rotatedCount + " page(s) by " + degrees + " degrees");

        } catch (Exception e) {
            AppLogger.e(TAG, "Rotate pages failed", e);
            outputFile.delete();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
    }

    // ==================== GET PDF INFO ====================

    /**
     * Get information about a PDF.
     * Uses ResourceManager to avoid java.awt.Color crash.
     */
    public static String getPdfInfo(String pdfPath) {
        File file = new File(pdfPath);

        try {
            // Use ResourceManager to get page count and size (avoids java.awt.Color crash)
            int pageCount = ResourceManager.getPageCount(pdfPath);
            float[] pageDims = ResourceManager.getPageSize(pdfPath, 1);

            return "File: " + file.getName() + "\n" +
                    "Size: " + formatFileSize(file.length()) + "\n" +
                    "Pages: " + pageCount + "\n" +
                    "Page Size: " + String.format("%.0f x %.0f pts", pageDims[0], pageDims[1]);

        } catch (Exception e) {
            AppLogger.e(TAG, "Get PDF info failed", e);
            return "Error reading PDF: " + e.getMessage();
        }
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
            AppLogger.e(TAG, "Get page count failed", e);
            return 0;
        } finally {
            if (reader != null) reader.close();
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
