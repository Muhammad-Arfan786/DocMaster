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
import com.itextpdf.kernel.utils.PdfMerger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manager class for PDF page operations.
 * Supports delete, rotate, reorder, add, duplicate, extract, and merge operations.
 *
 * All methods use try-with-resources for proper resource cleanup.
 */
public class PdfPageManager {

    private final Context context;
    private String pdfPath;

    public PdfPageManager(Context context, String pdfPath) {
        this.context = context;
        this.pdfPath = pdfPath;
    }

    /**
     * Get the number of pages in the PDF
     */
    public int getPageCount() throws IOException {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {
            return pdfDoc.getNumberOfPages();
        }
    }

    /**
     * Delete specific pages from PDF
     * @param pageNumbers List of page numbers to delete (1-based)
     * @return Path to new PDF
     */
    public String deletePages(List<Integer> pageNumbers) throws IOException {
        String outputPath = generateOutputPath("deleted");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            int totalPages = srcDoc.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                if (!pageNumbers.contains(i)) {
                    srcDoc.copyPagesTo(i, i, destDoc);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to delete pages: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Rotate specific pages
     * @param pageNumbers List of page numbers to rotate (1-based)
     * @param degrees Rotation degrees (90, 180, 270)
     * @return Path to new PDF
     */
    public String rotatePages(List<Integer> pageNumbers, int degrees) throws IOException {
        String outputPath = generateOutputPath("rotated");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            for (int pageNum : pageNumbers) {
                if (pageNum >= 1 && pageNum <= pdfDoc.getNumberOfPages()) {
                    PdfPage page = pdfDoc.getPage(pageNum);
                    int currentRotation = page.getRotation();
                    page.setRotation((currentRotation + degrees) % 360);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to rotate pages: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Rotate all pages
     */
    public String rotateAllPages(int degrees) throws IOException {
        String outputPath = generateOutputPath("rotated");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                int currentRotation = page.getRotation();
                page.setRotation((currentRotation + degrees) % 360);
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to rotate pages: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Reorder pages in PDF
     * @param newOrder List of page numbers in new order (1-based)
     * @return Path to new PDF
     */
    public String reorderPages(List<Integer> newOrder) throws IOException {
        String outputPath = generateOutputPath("reordered");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            for (int pageNum : newOrder) {
                if (pageNum >= 1 && pageNum <= srcDoc.getNumberOfPages()) {
                    srcDoc.copyPagesTo(pageNum, pageNum, destDoc);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to reorder pages: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Move a page to a new position
     * @param fromPage Current page number (1-based)
     * @param toPage Target position (1-based)
     * @return Path to new PDF
     */
    public String movePage(int fromPage, int toPage) throws IOException {
        int totalPages;
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument srcDoc = new PdfDocument(reader)) {
            totalPages = srcDoc.getNumberOfPages();
        }

        List<Integer> newOrder = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            if (i != fromPage) {
                newOrder.add(i);
            }
        }

        // Insert at new position
        int insertIndex = toPage - 1;
        if (fromPage < toPage) {
            insertIndex = toPage - 2;
        }
        if (insertIndex < 0) insertIndex = 0;
        if (insertIndex > newOrder.size()) insertIndex = newOrder.size();

        newOrder.add(insertIndex, fromPage);

        return reorderPages(newOrder);
    }

    /**
     * Add a blank page at specified position
     * @param afterPage Page number after which to insert (0 for beginning)
     * @param pageSize Size of new page (A4, LETTER, etc.)
     * @return Path to new PDF
     */
    public String addBlankPage(int afterPage, PageSize pageSize) throws IOException {
        String outputPath = generateOutputPath("added");

        if (afterPage == 0) {
            // Add blank page at beginning - requires two-step process
            return addBlankPageAtBeginning(pageSize);
        }

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            int totalPages = srcDoc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                srcDoc.copyPagesTo(i, i, destDoc);

                if (i == afterPage) {
                    destDoc.addNewPage(pageSize);
                }
            }

            // If afterPage is greater than total, add at end
            if (afterPage > totalPages) {
                destDoc.addNewPage(pageSize);
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add blank page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Helper method to add blank page at beginning
     */
    private String addBlankPageAtBeginning(PageSize pageSize) throws IOException {
        String outputPath = generateOutputPath("added");

        try (PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument destDoc = new PdfDocument(writer)) {

            // Add blank page first
            destDoc.addNewPage(pageSize);

            // Then copy all existing pages
            try (PdfReader reader = new PdfReader(pdfPath);
                 PdfDocument srcDoc = new PdfDocument(reader)) {
                srcDoc.copyPagesTo(1, srcDoc.getNumberOfPages(), destDoc);
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add blank page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Duplicate a page
     * @param pageNumber Page to duplicate (1-based)
     * @return Path to new PDF
     */
    public String duplicatePage(int pageNumber) throws IOException {
        String outputPath = generateOutputPath("duplicated");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            int totalPages = srcDoc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                srcDoc.copyPagesTo(i, i, destDoc);

                if (i == pageNumber) {
                    // Copy the page again (duplicate)
                    srcDoc.copyPagesTo(i, i, destDoc);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to duplicate page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Extract specific pages to a new PDF
     * @param pageNumbers List of page numbers to extract (1-based)
     * @return Path to new PDF containing only extracted pages
     */
    public String extractPages(List<Integer> pageNumbers) throws IOException {
        String outputPath = generateOutputPath("extracted");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            for (int pageNum : pageNumbers) {
                if (pageNum >= 1 && pageNum <= srcDoc.getNumberOfPages()) {
                    srcDoc.copyPagesTo(pageNum, pageNum, destDoc);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to extract pages: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Split PDF into individual pages
     * @return List of paths to individual page PDFs
     */
    public List<String> splitAllPages() throws IOException {
        List<String> outputPaths = new ArrayList<>();

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument srcDoc = new PdfDocument(reader)) {

            int totalPages = srcDoc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                String outputPath = generateOutputPath("page_" + i);

                try (PdfWriter writer = new PdfWriter(outputPath);
                     PdfDocument destDoc = new PdfDocument(writer)) {
                    srcDoc.copyPagesTo(i, i, destDoc);
                }

                outputPaths.add(outputPath);
            }
        } catch (Exception e) {
            // Clean up any created files on failure
            for (String path : outputPaths) {
                new File(path).delete();
            }
            throw new IOException("Failed to split pages: " + e.getMessage(), e);
        }

        return outputPaths;
    }

    /**
     * Merge multiple PDFs into one
     * @param pdfPaths List of PDF file paths to merge
     * @return Path to merged PDF
     */
    public static String mergePdfs(Context context, List<String> pdfPaths, String outputName) throws IOException {
        if (pdfPaths.isEmpty()) {
            throw new IOException("No PDFs to merge");
        }

        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String outputPath = new File(outputDir, outputName + "_merged_" + timestamp + ".pdf").getAbsolutePath();

        try (PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument mergedDoc = new PdfDocument(writer)) {

            PdfMerger merger = new PdfMerger(mergedDoc);

            for (String pdfPath : pdfPaths) {
                try (PdfReader reader = new PdfReader(pdfPath);
                     PdfDocument srcDoc = new PdfDocument(reader)) {
                    merger.merge(srcDoc, 1, srcDoc.getNumberOfPages());
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to merge PDFs: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Add an image as a new page
     * @param imagePath Path to the image file
     * @param afterPage Page number after which to insert (0 for beginning, -1 for end)
     * @return Path to new PDF
     */
    public String addImageAsPage(String imagePath, int afterPage) throws IOException {
        String outputPath = generateOutputPath("with_image");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            int totalPages = srcDoc.getNumberOfPages();

            // Load image
            ImageData imageData = ImageDataFactory.create(imagePath);
            float imageWidth = imageData.getWidth();
            float imageHeight = imageData.getHeight();

            // Create page size based on image dimensions
            PageSize pageSize = new PageSize(imageWidth, imageHeight);

            if (afterPage == 0) {
                // Add image page first
                PdfPage imagePage = destDoc.addNewPage(pageSize);
                PdfCanvas canvas = new PdfCanvas(imagePage);
                canvas.addImageAt(imageData, 0, 0, false);
            }

            for (int i = 1; i <= totalPages; i++) {
                srcDoc.copyPagesTo(i, i, destDoc);

                if (i == afterPage) {
                    PdfPage imagePage = destDoc.addNewPage(pageSize);
                    PdfCanvas canvas = new PdfCanvas(imagePage);
                    canvas.addImageAt(imageData, 0, 0, false);
                }
            }

            if (afterPage == -1 || afterPage > totalPages) {
                PdfPage imagePage = destDoc.addNewPage(pageSize);
                PdfCanvas canvas = new PdfCanvas(imagePage);
                canvas.addImageAt(imageData, 0, 0, false);
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add image as page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Add an image to an existing page
     * @param pageNumber Page to add image to (1-based)
     * @param imagePath Path to image
     * @param x X position
     * @param y Y position
     * @param width Desired width (0 for original)
     * @param height Desired height (0 for original)
     * @return Path to new PDF
     */
    public String addImageToPage(int pageNumber, String imagePath, float x, float y,
                                  float width, float height) throws IOException {
        String outputPath = generateOutputPath("image_added");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            if (pageNumber >= 1 && pageNumber <= pdfDoc.getNumberOfPages()) {
                PdfPage page = pdfDoc.getPage(pageNumber);
                PdfCanvas canvas = new PdfCanvas(page);

                ImageData imageData = ImageDataFactory.create(imagePath);

                float imgWidth = width > 0 ? width : imageData.getWidth();
                float imgHeight = height > 0 ? height : imageData.getHeight();

                canvas.addImageFittedIntoRectangle(imageData,
                        new Rectangle(x, y, imgWidth, imgHeight), false);
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add image to page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Add image from bitmap to page
     */
    public String addBitmapToPage(int pageNumber, Bitmap bitmap, float x, float y,
                                   float width, float height) throws IOException {
        String outputPath = generateOutputPath("image_added");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            if (pageNumber >= 1 && pageNumber <= pdfDoc.getNumberOfPages()) {
                PdfPage page = pdfDoc.getPage(pageNumber);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas = new PdfCanvas(page);

                try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] bitmapData = stream.toByteArray();

                    ImageData imageData = ImageDataFactory.create(bitmapData);

                    float imgWidth = width > 0 ? width : imageData.getWidth();
                    float imgHeight = height > 0 ? height : imageData.getHeight();

                    // Convert from top-left to bottom-left coordinates
                    float pdfY = pageSize.getHeight() - y - imgHeight;

                    canvas.addImageFittedIntoRectangle(imageData,
                            new Rectangle(x, pdfY, imgWidth, imgHeight), false);
                }
            }
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add bitmap to page: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Compress PDF by setting maximum compression level
     * @return Path to compressed PDF
     */
    public String compressPdf() throws IOException {
        String outputPath = generateOutputPath("compressed");

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfWriter writer = new PdfWriter(outputPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {

            writer.setCompressionLevel(9);
            srcDoc.copyPagesTo(1, srcDoc.getNumberOfPages(), destDoc);
        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to compress PDF: " + e.getMessage(), e);
        }

        return outputPath;
    }

    /**
     * Generate output file path with timestamp
     */
    private String generateOutputPath(String suffix) {
        File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File originalFile = new File(pdfPath);
        String baseName = originalFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(outputDir, baseName + "_" + suffix + "_" + timestamp + ".pdf").getAbsolutePath();
    }

    /**
     * Update the PDF path (after a modification)
     */
    public void setPdfPath(String newPath) {
        this.pdfPath = newPath;
    }

    public String getPdfPath() {
        return pdfPath;
    }
}
