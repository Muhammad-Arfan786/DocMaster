package com.docreader.utils;

import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Safe resource management utility for PDF operations.
 *
 * Provides try-with-resources wrappers to eliminate resource leaks.
 * All methods ensure proper cleanup even when exceptions occur.
 *
 * Usage:
 *   int pageCount = ResourceManager.withPdfReader(path, reader -> reader.getNumberOfPages());
 *
 *   ResourceManager.withPdfStamper(srcPath, destPath, (reader, stamper) -> {
 *       // modify PDF
 *       return destPath;
 *   });
 */
public final class ResourceManager {

    private ResourceManager() {
        // Prevent instantiation
    }

    // ==================== PDF READER OPERATIONS ====================

    /**
     * Execute read-only operation on a PDF with automatic resource cleanup.
     *
     * @param path Path to the PDF file
     * @param operation Operation to perform on the PDF reader
     * @return Result of the operation
     */
    public static <T> T withPdfReader(String path, PdfReaderOperation<T> operation) throws IOException {
        PdfReader reader = null;
        try {
            reader = new PdfReader(path);
            return operation.apply(reader);
        } catch (Exception e) {
            throw new IOException("PDF read operation failed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Execute read-only operation on a PDF (void return).
     */
    public static void withPdfReaderVoid(String path, PdfReaderVoidOperation operation) throws IOException {
        PdfReader reader = null;
        try {
            reader = new PdfReader(path);
            operation.apply(reader);
        } catch (Exception e) {
            throw new IOException("PDF read operation failed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== PDF STAMPER OPERATIONS (COPY/MODIFY) ====================

    /**
     * Execute copy/modify operation from source to destination PDF with automatic cleanup.
     * Uses PdfStamper for modifying existing PDFs.
     *
     * @param srcPath Source PDF path
     * @param destPath Destination PDF path
     * @param operation Operation to perform
     * @return Result of the operation
     */
    public static <T> T withPdfStamper(String srcPath, String destPath,
                                        PdfStamperOperation<T> operation) throws IOException {
        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;
        try {
            reader = new PdfReader(srcPath);
            fos = new FileOutputStream(destPath);
            stamper = new PdfStamper(reader, fos);
            return operation.apply(reader, stamper);
        } catch (Exception e) {
            // Clean up partial output file on failure
            new File(destPath).delete();
            throw new IOException("PDF stamper operation failed: " + e.getMessage(), e);
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
    }

    /**
     * Execute copy/modify operation (void return).
     */
    public static void withPdfStamperVoid(String srcPath, String destPath,
                                           PdfStamperVoidOperation operation) throws IOException {
        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;
        try {
            reader = new PdfReader(srcPath);
            fos = new FileOutputStream(destPath);
            stamper = new PdfStamper(reader, fos);
            operation.apply(reader, stamper);
        } catch (Exception e) {
            new File(destPath).delete();
            throw new IOException("PDF stamper operation failed: " + e.getMessage(), e);
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
    }

    // ==================== PDF READ-WRITE OPERATIONS ====================

    /**
     * Execute read-write operation on a PDF (reads from src, writes to dest).
     * Convenience wrapper that uses PdfStamper internally.
     *
     * @param srcPath Source PDF path
     * @param destPath Destination PDF path
     * @param operation Operation to perform
     * @return Result of the operation
     */
    public static <T> T withPdfReadWrite(String srcPath, String destPath,
                                          PdfReaderOperation<T> operation) throws IOException {
        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;
        try {
            reader = new PdfReader(srcPath);
            fos = new FileOutputStream(destPath);
            stamper = new PdfStamper(reader, fos);
            return operation.apply(reader);
        } catch (Exception e) {
            new File(destPath).delete();
            throw new IOException("PDF read-write operation failed: " + e.getMessage(), e);
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
    }

    // ==================== PDF RENDERER OPERATIONS ====================

    /**
     * Execute operation with PdfRenderer (Android native) with automatic cleanup.
     */
    public static <T> T withPdfRenderer(File file, PdfRendererOperation<T> operation) throws Exception {
        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            return operation.apply(renderer);
        } finally {
            closeQuietly(renderer);
            closeQuietly(fd);
        }
    }

    /**
     * Get page dimensions using Android's native PdfRenderer.
     * This avoids OpenPDF's getPageSize() which triggers java.awt.Color dependency.
     *
     * @param pdfPath Path to the PDF file
     * @param pageNumber 1-based page number
     * @return float array [width, height] in points (72 dpi), or default A4 if error
     */
    public static float[] getPageSize(String pdfPath, int pageNumber) {
        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        try {
            File file = new File(pdfPath);
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);

            int pageIndex = pageNumber - 1; // Convert to 0-based
            if (pageIndex < 0 || pageIndex >= renderer.getPageCount()) {
                return new float[]{Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4};
            }

            page = renderer.openPage(pageIndex);
            // PdfRenderer returns dimensions in points (72 dpi)
            float width = page.getWidth();
            float height = page.getHeight();
            return new float[]{width, height};
        } catch (Exception e) {
            AppLogger.w("ResourceManager", "Failed to get page size, using A4 default", e);
            return new float[]{Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4};
        } finally {
            if (page != null) page.close();
            closeQuietly(renderer);
            closeQuietly(fd);
        }
    }

    /**
     * Get page count using Android's native PdfRenderer.
     * This avoids OpenPDF's methods that might trigger java.awt dependencies.
     *
     * @param pdfPath Path to the PDF file
     * @return Number of pages, or 0 if error
     */
    public static int getPageCount(String pdfPath) {
        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        try {
            File file = new File(pdfPath);
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            return renderer.getPageCount();
        } catch (Exception e) {
            AppLogger.w("ResourceManager", "Failed to get page count", e);
            return 0;
        } finally {
            closeQuietly(renderer);
            closeQuietly(fd);
        }
    }

    // ==================== FILE STREAM OPERATIONS ====================

    /**
     * Copy file with automatic stream cleanup.
     */
    public static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
    }

    /**
     * Copy file from path strings.
     */
    public static void copyFile(String sourcePath, String destPath) throws IOException {
        copyFile(new File(sourcePath), new File(destPath));
    }

    /**
     * Copy input stream to output stream.
     */
    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
    }

    // ==================== CLEANUP UTILITIES ====================

    /**
     * Safely close any Closeable, logging errors instead of throwing.
     */
    public static void closeQuietly(Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                    AppLogger.w("Error closing resource", e);
                }
            }
        }
    }

    /**
     * Safely close AutoCloseable (like PdfRenderer).
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                    AppLogger.w("Error closing resource", e);
                }
            }
        }
    }

    // ==================== FUNCTIONAL INTERFACES ====================

    @FunctionalInterface
    public interface PdfReaderOperation<T> {
        T apply(PdfReader reader) throws Exception;
    }

    @FunctionalInterface
    public interface PdfReaderVoidOperation {
        void apply(PdfReader reader) throws Exception;
    }

    @FunctionalInterface
    public interface PdfStamperOperation<T> {
        T apply(PdfReader reader, PdfStamper stamper) throws Exception;
    }

    @FunctionalInterface
    public interface PdfStamperVoidOperation {
        void apply(PdfReader reader, PdfStamper stamper) throws Exception;
    }

    @FunctionalInterface
    public interface PdfRendererOperation<T> {
        T apply(PdfRenderer renderer) throws Exception;
    }
}
