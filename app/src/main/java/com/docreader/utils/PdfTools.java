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
import com.itextpdf.layout.element.Paragraph;
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
 */
public class PdfTools {

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
        try {
            if (pdfPaths == null || pdfPaths.size() < 2) {
                return new PdfResult(false, null, null, "Need at least 2 PDFs to merge");
            }

            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfWriter writer = new PdfWriter(outputFile);
            PdfDocument mergedDoc = new PdfDocument(writer);

            for (String pdfPath : pdfPaths) {
                PdfDocument sourceDoc = new PdfDocument(new PdfReader(pdfPath));
                sourceDoc.copyPagesTo(1, sourceDoc.getNumberOfPages(), mergedDoc);
                sourceDoc.close();
            }

            int totalPages = mergedDoc.getNumberOfPages();
            mergedDoc.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Merged " + pdfPaths.size() + " PDFs (" + totalPages + " pages)");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== SPLIT PDF ====================

    /**
     * Split PDF into individual pages
     */
    public static PdfResult splitPdf(String pdfPath, File outputDir) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            File inputFile = new File(pdfPath);
            String baseName = inputFile.getName().replace(".pdf", "").replace(".PDF", "");

            PdfDocument sourceDoc = new PdfDocument(new PdfReader(pdfPath));
            int pageCount = sourceDoc.getNumberOfPages();

            List<String> outputFiles = new ArrayList<>();

            for (int i = 1; i <= pageCount; i++) {
                String pageFileName = baseName + "_page_" + i + ".pdf";
                File pageFile = new File(outputDir, pageFileName);

                PdfWriter writer = new PdfWriter(pageFile);
                PdfDocument pageDoc = new PdfDocument(writer);

                sourceDoc.copyPagesTo(i, i, pageDoc);
                pageDoc.close();

                outputFiles.add(pageFile.getAbsolutePath());
            }

            sourceDoc.close();

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_pages",
                    "Split into " + pageCount + " separate PDF files");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    /**
     * Extract specific pages from PDF
     */
    public static PdfResult extractPages(String pdfPath, File outputDir, String outputName, List<Integer> pages) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfDocument sourceDoc = new PdfDocument(new PdfReader(pdfPath));
            PdfWriter writer = new PdfWriter(outputFile);
            PdfDocument newDoc = new PdfDocument(writer);

            for (int pageNum : pages) {
                if (pageNum >= 1 && pageNum <= sourceDoc.getNumberOfPages()) {
                    sourceDoc.copyPagesTo(pageNum, pageNum, newDoc);
                }
            }

            int extractedCount = newDoc.getNumberOfPages();
            newDoc.close();
            sourceDoc.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Extracted " + extractedCount + " pages");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== COMPRESS PDF ====================

    /**
     * Compress PDF by reducing image quality and optimizing
     */
    public static PdfResult compressPdf(Context context, String pdfPath, File outputDir, String outputName) {
        PdfRenderer renderer = null;
        ParcelFileDescriptor fd = null;
        PdfDocument originalDoc = null;
        Document document = null;

        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            File inputFile = new File(pdfPath);
            if (!inputFile.exists()) {
                return new PdfResult(false, null, null, "Source file not found");
            }

            long originalSize = inputFile.length();

            // Render pages as compressed images and create new PDF
            fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            if (pageCount == 0) {
                return new PdfResult(false, null, null, "PDF has no pages");
            }

            // Get original page sizes
            originalDoc = new PdfDocument(new PdfReader(pdfPath));

            PdfWriter writer = new PdfWriter(outputFile);
            PdfDocument compressedDoc = new PdfDocument(writer);
            document = new Document(compressedDoc);

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
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                byte[] imageBytes = baos.toByteArray();
                bitmap.recycle();
                baos.close();

                // Add to new PDF
                ImageData imageData = ImageDataFactory.create(imageBytes);
                Image pdfImage = new Image(imageData);

                PageSize pageSize = new PageSize(pageWidth, pageHeight);
                compressedDoc.addNewPage(pageSize);

                pdfImage.setFixedPosition(i + 1, 0, 0);
                pdfImage.scaleToFit(pageWidth, pageHeight);
                document.add(pdfImage);
            }

            renderer.close();
            renderer = null;
            fd.close();
            fd = null;
            originalDoc.close();
            originalDoc = null;
            document.close();
            document = null;

            long newSize = outputFile.length();
            float reduction = (1 - (float) newSize / originalSize) * 100;

            String resultMessage;
            if (reduction > 0) {
                resultMessage = String.format("Compressed: %.1f KB → %.1f KB (%.0f%% smaller)",
                        originalSize / 1024.0, newSize / 1024.0, reduction);
            } else {
                resultMessage = String.format("Processed: %.1f KB → %.1f KB (file was already optimized)",
                        originalSize / 1024.0, newSize / 1024.0);
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName, resultMessage);

        } catch (OutOfMemoryError e) {
            return new PdfResult(false, null, null, "Out of memory - PDF too large to compress");
        } catch (Exception e) {
            e.printStackTrace();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            // Clean up resources
            try {
                if (renderer != null) renderer.close();
                if (fd != null) fd.close();
                if (originalDoc != null) originalDoc.close();
                if (document != null) document.close();
            } catch (Exception ignored) {}
        }
    }

    // ==================== ADD PAGE NUMBERS ====================

    /**
     * Add page numbers to PDF
     */
    public static PdfResult addPageNumbers(String pdfPath, File outputDir, String outputName,
                                            String position, int startNumber) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            // Copy original first
            copyFile(new File(pdfPath), outputFile);

            PdfDocument doc = new PdfDocument(new PdfReader(outputFile), new PdfWriter(outputFile + ".tmp"));
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            int pageCount = doc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = doc.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page);

                String pageNum = String.valueOf(startNumber + i - 1);

                float x, y;
                TextAlignment align;

                switch (position.toLowerCase()) {
                    case "top-left":
                        x = 40; y = pageSize.getHeight() - 30;
                        align = TextAlignment.LEFT;
                        break;
                    case "top-center":
                        x = pageSize.getWidth() / 2; y = pageSize.getHeight() - 30;
                        align = TextAlignment.CENTER;
                        break;
                    case "top-right":
                        x = pageSize.getWidth() - 40; y = pageSize.getHeight() - 30;
                        align = TextAlignment.RIGHT;
                        break;
                    case "bottom-left":
                        x = 40; y = 30;
                        align = TextAlignment.LEFT;
                        break;
                    case "bottom-right":
                        x = pageSize.getWidth() - 40; y = 30;
                        align = TextAlignment.RIGHT;
                        break;
                    default: // bottom-center
                        x = pageSize.getWidth() / 2; y = 30;
                        align = TextAlignment.CENTER;
                        break;
                }

                canvas.beginText();
                canvas.setFontAndSize(font, 11);
                canvas.setFillColor(ColorConstants.BLACK);
                canvas.moveText(x, y);
                canvas.showText(pageNum);
                canvas.endText();
            }

            doc.close();

            // Replace original with temp file
            outputFile.delete();
            new File(outputFile + ".tmp").renameTo(outputFile);

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Added page numbers to " + pageCount + " pages");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== PDF TO IMAGES ====================

    /**
     * Convert PDF pages to images
     */
    public static PdfResult pdfToImages(Context context, String pdfPath, File outputDir,
                                         String format, int dpi) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            File inputFile = new File(pdfPath);
            String baseName = inputFile.getName().replace(".pdf", "").replace(".PDF", "");

            ParcelFileDescriptor fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fd);
            int pageCount = renderer.getPageCount();

            // Get page sizes from iText
            PdfDocument doc = new PdfDocument(new PdfReader(pdfPath));

            List<String> outputFiles = new ArrayList<>();

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

                FileOutputStream fos = new FileOutputStream(imageFile);
                if (format.equalsIgnoreCase("png")) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                }
                fos.close();
                bitmap.recycle();

                outputFiles.add(imageFile.getAbsolutePath());
            }

            renderer.close();
            fd.close();
            doc.close();

            return new PdfResult(true, outputDir.getAbsolutePath(), baseName + "_images",
                    "Converted " + pageCount + " pages to " + format.toUpperCase() + " images");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== ADD WATERMARK ====================

    /**
     * Add text watermark to PDF
     */
    public static PdfResult addWatermark(String pdfPath, File outputDir, String outputName,
                                          String watermarkText, float opacity, float fontSize,
                                          int rotation, int color) {
        PdfDocument doc = null;
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfReader reader = new PdfReader(pdfPath);
            PdfWriter writer = new PdfWriter(outputFile);
            doc = new PdfDocument(reader, writer);
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

            doc.close();
            doc = null;

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Added watermark to " + pageCount + " pages");

        } catch (Exception e) {
            e.printStackTrace();
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        } finally {
            if (doc != null) {
                try { doc.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== DELETE PAGES ====================

    /**
     * Delete specific pages from PDF
     */
    public static PdfResult deletePages(String pdfPath, File outputDir, String outputName,
                                         List<Integer> pagesToDelete) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfDocument sourceDoc = new PdfDocument(new PdfReader(pdfPath));
            PdfWriter writer = new PdfWriter(outputFile);
            PdfDocument newDoc = new PdfDocument(writer);

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
            newDoc.close();
            sourceDoc.close();

            if (remainingPages == 0) {
                outputFile.delete();
                return new PdfResult(false, null, null, "Cannot delete all pages");
            }

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Deleted " + deletedCount + " page(s), " + remainingPages + " remaining");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== COPY PDF ====================

    /**
     * Create a copy of PDF
     */
    public static PdfResult copyPdf(String pdfPath, File outputDir, String outputName) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            copyFile(new File(pdfPath), outputFile);

            PdfDocument doc = new PdfDocument(new PdfReader(outputFile));
            int pageCount = doc.getNumberOfPages();
            doc.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Created copy with " + pageCount + " pages");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== ROTATE PAGES ====================

    /**
     * Rotate pages in PDF
     */
    public static PdfResult rotatePages(String pdfPath, File outputDir, String outputName,
                                         List<Integer> pagesToRotate, int degrees) {
        try {
            if (!outputDir.exists()) outputDir.mkdirs();

            String fileName = outputName.endsWith(".pdf") ? outputName : outputName + ".pdf";
            File outputFile = new File(outputDir, fileName);

            PdfReader reader = new PdfReader(pdfPath);
            PdfWriter writer = new PdfWriter(outputFile);
            PdfDocument doc = new PdfDocument(reader, writer);

            int rotatedCount = 0;
            for (int pageNum : pagesToRotate) {
                if (pageNum >= 1 && pageNum <= doc.getNumberOfPages()) {
                    PdfPage page = doc.getPage(pageNum);
                    int currentRotation = page.getRotation();
                    page.setRotation((currentRotation + degrees) % 360);
                    rotatedCount++;
                }
            }

            doc.close();

            return new PdfResult(true, outputFile.getAbsolutePath(), fileName,
                    "Rotated " + rotatedCount + " page(s) by " + degrees + "°");

        } catch (Exception e) {
            return new PdfResult(false, null, null, "Error: " + e.getMessage());
        }
    }

    // ==================== GET PDF INFO ====================

    /**
     * Get information about a PDF
     */
    public static String getPdfInfo(String pdfPath) {
        try {
            File file = new File(pdfPath);
            PdfDocument doc = new PdfDocument(new PdfReader(pdfPath));

            int pageCount = doc.getNumberOfPages();
            PdfPage firstPage = doc.getPage(1);
            Rectangle size = firstPage.getPageSize();

            String info = "File: " + file.getName() + "\n" +
                    "Size: " + formatFileSize(file.length()) + "\n" +
                    "Pages: " + pageCount + "\n" +
                    "Page Size: " + String.format("%.0f x %.0f pts", size.getWidth(), size.getHeight());

            doc.close();
            return info;

        } catch (Exception e) {
            return "Error reading PDF: " + e.getMessage();
        }
    }

    /**
     * Get page count of PDF
     */
    public static int getPageCount(String pdfPath) {
        try {
            PdfDocument doc = new PdfDocument(new PdfReader(pdfPath));
            int count = doc.getNumberOfPages();
            doc.close();
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== HELPER METHODS ====================

    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
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
