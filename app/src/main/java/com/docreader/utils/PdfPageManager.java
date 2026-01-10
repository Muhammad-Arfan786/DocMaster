package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manager class for PDF page operations.
 * Supports delete, rotate, reorder, add, duplicate, extract, and merge operations.
 * Uses OpenPDF library (LGPL license - free for commercial use).
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
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            return reader.getNumberOfPages();
        } finally {
            if (reader != null) reader.close();
        }
    }

    /**
     * Delete specific pages from PDF
     * @param pageNumbers List of page numbers to delete (1-based)
     * @return Path to new PDF
     */
    public String deletePages(List<Integer> pageNumbers) throws IOException {
        String outputPath = generateOutputPath("deleted");
        PdfReader reader = null;
        Document document = null;

        try {
            reader = new PdfReader(pdfPath);
            int totalPages = reader.getNumberOfPages();

            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = reader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            for (int i = 1; i <= totalPages; i++) {
                if (!pageNumbers.contains(i)) {
                    PdfImportedPage page = copy.getImportedPage(reader, i);
                    copy.addPage(page);
                }
            }

            document.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to delete pages: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (reader != null) reader.close();
        }
    }

    /**
     * Rotate specific pages
     * @param pageNumbers List of page numbers to rotate (1-based)
     * @param degrees Rotation degrees (90, 180, 270)
     * @return Path to new PDF
     */
    public String rotatePages(List<Integer> pageNumbers, int degrees) throws IOException {
        String outputPath = generateOutputPath("rotated");
        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

            for (int pageNum : pageNumbers) {
                if (pageNum >= 1 && pageNum <= reader.getNumberOfPages()) {
                    int currentRotation = reader.getPageRotation(pageNum);
                    reader.getPageN(pageNum).put(PdfName.ROTATE, new PdfNumber((currentRotation + degrees) % 360));
                }
            }

            stamper.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to rotate pages: " + e.getMessage(), e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Rotate all pages
     */
    public String rotateAllPages(int degrees) throws IOException {
        String outputPath = generateOutputPath("rotated");
        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                int currentRotation = reader.getPageRotation(i);
                reader.getPageN(i).put(PdfName.ROTATE, new PdfNumber((currentRotation + degrees) % 360));
            }

            stamper.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to rotate pages: " + e.getMessage(), e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Reorder pages in PDF
     * @param newOrder List of page numbers in new order (1-based)
     * @return Path to new PDF
     */
    public String reorderPages(List<Integer> newOrder) throws IOException {
        String outputPath = generateOutputPath("reordered");
        PdfReader reader = null;
        Document document = null;

        try {
            reader = new PdfReader(pdfPath);
            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = reader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            for (int pageNum : newOrder) {
                if (pageNum >= 1 && pageNum <= reader.getNumberOfPages()) {
                    PdfImportedPage page = copy.getImportedPage(reader, pageNum);
                    copy.addPage(page);
                }
            }

            document.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to reorder pages: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (reader != null) reader.close();
        }
    }

    /**
     * Move a page to a new position
     * @param fromPage Current page number (1-based)
     * @param toPage Target position (1-based)
     * @return Path to new PDF
     */
    public String movePage(int fromPage, int toPage) throws IOException {
        PdfReader reader = null;
        int totalPages;
        try {
            reader = new PdfReader(pdfPath);
            totalPages = reader.getNumberOfPages();
        } finally {
            if (reader != null) reader.close();
        }

        List<Integer> newOrder = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            if (i != fromPage) {
                newOrder.add(i);
            }
        }

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
     * @param pageWidth Width of new page
     * @param pageHeight Height of new page
     * @return Path to new PDF
     */
    public String addBlankPage(int afterPage, float pageWidth, float pageHeight) throws IOException {
        String outputPath = generateOutputPath("added");
        String blankPagePath = null;
        PdfReader reader = null;
        PdfReader blankReader = null;
        Document document = null;
        FileOutputStream fos = null;

        try {
            // First, create a temporary blank page PDF
            blankPagePath = generateOutputPath("blank_temp");
            Rectangle pageSize = new Rectangle(pageWidth, pageHeight);
            Document blankDoc = new Document(pageSize);
            PdfWriter.getInstance(blankDoc, new FileOutputStream(blankPagePath));
            blankDoc.open();
            blankDoc.newPage();
            blankDoc.close();

            // Now read both the original and blank page PDFs
            reader = new PdfReader(pdfPath);
            blankReader = new PdfReader(blankPagePath);
            int totalPages = reader.getNumberOfPages();

            document = new Document(pageSize);
            fos = new FileOutputStream(outputPath);
            PdfCopy copy = new PdfCopy(document, fos);
            document.open();

            if (afterPage == 0) {
                // Add blank page first
                copy.addPage(copy.getImportedPage(blankReader, 1));
            }

            for (int i = 1; i <= totalPages; i++) {
                PdfImportedPage page = copy.getImportedPage(reader, i);
                copy.addPage(page);

                if (i == afterPage) {
                    // Re-read blank page for each insertion
                    copy.addPage(copy.getImportedPage(blankReader, 1));
                }
            }

            if (afterPage > totalPages) {
                copy.addPage(copy.getImportedPage(blankReader, 1));
            }

            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add blank page: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
            if (reader != null) reader.close();
            if (blankReader != null) blankReader.close();
            if (blankPagePath != null) new File(blankPagePath).delete();
        }
    }

    /**
     * Duplicate a page
     * @param pageNumber Page to duplicate (1-based)
     * @return Path to new PDF
     */
    public String duplicatePage(int pageNumber) throws IOException {
        String outputPath = generateOutputPath("duplicated");
        PdfReader reader = null;
        Document document = null;

        try {
            reader = new PdfReader(pdfPath);
            int totalPages = reader.getNumberOfPages();

            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = reader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            for (int i = 1; i <= totalPages; i++) {
                PdfImportedPage page = copy.getImportedPage(reader, i);
                copy.addPage(page);

                if (i == pageNumber) {
                    // Duplicate the page
                    PdfImportedPage dupPage = copy.getImportedPage(reader, i);
                    copy.addPage(dupPage);
                }
            }

            document.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to duplicate page: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (reader != null) reader.close();
        }
    }

    /**
     * Extract specific pages to a new PDF
     * @param pageNumbers List of page numbers to extract (1-based)
     * @return Path to new PDF containing only extracted pages
     */
    public String extractPages(List<Integer> pageNumbers) throws IOException {
        String outputPath = generateOutputPath("extracted");
        PdfReader reader = null;
        Document document = null;

        try {
            reader = new PdfReader(pdfPath);
            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = reader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            for (int pageNum : pageNumbers) {
                if (pageNum >= 1 && pageNum <= reader.getNumberOfPages()) {
                    PdfImportedPage page = copy.getImportedPage(reader, pageNum);
                    copy.addPage(page);
                }
            }

            document.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to extract pages: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (reader != null) reader.close();
        }
    }

    /**
     * Split PDF into individual pages
     * @return List of paths to individual page PDFs
     */
    public List<String> splitAllPages() throws IOException {
        List<String> outputPaths = new ArrayList<>();
        PdfReader reader = null;

        try {
            reader = new PdfReader(pdfPath);
            int totalPages = reader.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                String outputPath = generateOutputPath("page_" + i);

                // Get page size for this page (avoids PageSize class - uses java.awt.Color)
                Rectangle pageSize = reader.getPageSize(i);
                Rectangle docSize = new Rectangle(pageSize.getWidth(), pageSize.getHeight());
                Document document = new Document(docSize);
                PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
                document.open();

                PdfImportedPage page = copy.getImportedPage(reader, i);
                copy.addPage(page);

                document.close();
                outputPaths.add(outputPath);
            }

            reader.close();
            return outputPaths;

        } catch (Exception e) {
            for (String path : outputPaths) {
                new File(path).delete();
            }
            throw new IOException("Failed to split pages: " + e.getMessage(), e);
        } finally {
            if (reader != null) reader.close();
        }
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

        Document document = null;
        List<PdfReader> openedReaders = new ArrayList<>();
        try {
            // Get page size from first PDF (avoids PageSize class - uses java.awt.Color)
            PdfReader firstReader = new PdfReader(pdfPaths.get(0));
            Rectangle firstSize = firstReader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            firstReader.close();

            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            for (String pdfPath : pdfPaths) {
                PdfReader reader = new PdfReader(pdfPath);
                openedReaders.add(reader);
                int pageCount = reader.getNumberOfPages();

                for (int i = 1; i <= pageCount; i++) {
                    PdfImportedPage page = copy.getImportedPage(reader, i);
                    copy.addPage(page);
                }
                reader.close();
            }

            document.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to merge PDFs: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
        }
    }

    /**
     * Add an image as a new page
     * @param imagePath Path to the image file
     * @param afterPage Page number after which to insert (0 for beginning, -1 for end)
     * @return Path to new PDF
     */
    public String addImageAsPage(String imagePath, int afterPage) throws IOException {
        String outputPath = generateOutputPath("with_image");
        PdfReader reader = null;
        Document document = null;

        try {
            reader = new PdfReader(pdfPath);
            int totalPages = reader.getNumberOfPages();

            Image image = Image.getInstance(imagePath);
            float imageWidth = image.getWidth();
            float imageHeight = image.getHeight();
            Rectangle imagePageSize = new Rectangle(imageWidth, imageHeight);

            // Get first page size (avoids PageSize class - uses java.awt.Color)
            Rectangle firstSize = reader.getPageSize(1);
            Rectangle docSize = new Rectangle(firstSize.getWidth(), firstSize.getHeight());
            document = new Document(docSize);
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(outputPath));
            document.open();

            if (afterPage == 0) {
                // Create image page first
                Document imgDoc = new Document(imagePageSize);
                String tempPath = outputPath + ".temp";
                PdfWriter imgWriter = PdfWriter.getInstance(imgDoc, new FileOutputStream(tempPath));
                imgDoc.open();
                image.setAbsolutePosition(0, 0);
                imgDoc.add(image);
                imgDoc.close();

                PdfReader imgReader = new PdfReader(tempPath);
                copy.addPage(copy.getImportedPage(imgReader, 1));
                imgReader.close();
                new File(tempPath).delete();
            }

            for (int i = 1; i <= totalPages; i++) {
                PdfImportedPage page = copy.getImportedPage(reader, i);
                copy.addPage(page);

                if (i == afterPage) {
                    Document imgDoc = new Document(imagePageSize);
                    String tempPath = outputPath + ".temp";
                    PdfWriter imgWriter = PdfWriter.getInstance(imgDoc, new FileOutputStream(tempPath));
                    imgDoc.open();
                    image.setAbsolutePosition(0, 0);
                    imgDoc.add(image);
                    imgDoc.close();

                    PdfReader imgReader = new PdfReader(tempPath);
                    copy.addPage(copy.getImportedPage(imgReader, 1));
                    imgReader.close();
                    new File(tempPath).delete();
                }
            }

            if (afterPage == -1 || afterPage > totalPages) {
                Document imgDoc = new Document(imagePageSize);
                String tempPath = outputPath + ".temp";
                PdfWriter imgWriter = PdfWriter.getInstance(imgDoc, new FileOutputStream(tempPath));
                imgDoc.open();
                image.setAbsolutePosition(0, 0);
                imgDoc.add(image);
                imgDoc.close();

                PdfReader imgReader = new PdfReader(tempPath);
                copy.addPage(copy.getImportedPage(imgReader, 1));
                imgReader.close();
                new File(tempPath).delete();
            }

            document.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add image as page: " + e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) document.close();
            if (reader != null) reader.close();
        }
    }

    /**
     * Add an image to an existing page
     */
    public String addImageToPage(int pageNumber, String imagePath, float x, float y,
                                  float width, float height) throws IOException {
        String outputPath = generateOutputPath("image_added");
        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

            if (pageNumber >= 1 && pageNumber <= reader.getNumberOfPages()) {
                Image image = Image.getInstance(imagePath);

                float imgWidth = width > 0 ? width : image.getWidth();
                float imgHeight = height > 0 ? height : image.getHeight();
                image.scaleAbsolute(imgWidth, imgHeight);
                image.setAbsolutePosition(x, y);

                PdfContentByte content = stamper.getOverContent(pageNumber);
                content.addImage(image);
            }

            stamper.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add image to page: " + e.getMessage(), e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Add image from bitmap to page
     */
    public String addBitmapToPage(int pageNumber, Bitmap bitmap, float x, float y,
                                   float width, float height) throws IOException {
        String outputPath = generateOutputPath("image_added");
        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

            if (pageNumber >= 1 && pageNumber <= reader.getNumberOfPages()) {
                Rectangle pageSize = reader.getPageSize(pageNumber);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byte[] bitmapData = stream.toByteArray();
                stream.close();

                Image image = Image.getInstance(bitmapData);

                float imgWidth = width > 0 ? width : image.getWidth();
                float imgHeight = height > 0 ? height : image.getHeight();
                image.scaleAbsolute(imgWidth, imgHeight);

                // Convert from top-left to bottom-left coordinates
                float pdfY = pageSize.getHeight() - y - imgHeight;
                image.setAbsolutePosition(x, pdfY);

                PdfContentByte content = stamper.getOverContent(pageNumber);
                content.addImage(image);
            }

            stamper.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to add bitmap to page: " + e.getMessage(), e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Compress PDF by copying with full compression
     * @return Path to compressed PDF
     */
    public String compressPdf() throws IOException {
        String outputPath = generateOutputPath("compressed");
        PdfReader reader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfPath);
            stamper = new PdfStamper(reader, new FileOutputStream(outputPath));
            stamper.setFullCompression();

            stamper.close();
            reader.close();
            return outputPath;

        } catch (Exception e) {
            new File(outputPath).delete();
            throw new IOException("Failed to compress PDF: " + e.getMessage(), e);
        } finally {
            try {
                if (stamper != null) stamper.close();
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
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
