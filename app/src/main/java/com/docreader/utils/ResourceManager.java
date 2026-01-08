package com.docreader.utils;

import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

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
 *   int pageCount = ResourceManager.withPdfReader(path, doc -> doc.getNumberOfPages());
 *
 *   ResourceManager.withPdfCopy(srcPath, destPath, (src, dest) -> {
 *       src.copyPagesTo(1, src.getNumberOfPages(), dest);
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
     * @param operation Operation to perform on the PDF document
     * @return Result of the operation
     */
    public static <T> T withPdfReader(String path, PdfReaderOperation<T> operation) throws IOException {
        try (PdfReader reader = new PdfReader(path);
             PdfDocument doc = new PdfDocument(reader)) {
            return operation.apply(doc);
        } catch (Exception e) {
            throw new IOException("PDF read operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute read-only operation on a PDF (void return).
     */
    public static void withPdfReaderVoid(String path, PdfReaderVoidOperation operation) throws IOException {
        try (PdfReader reader = new PdfReader(path);
             PdfDocument doc = new PdfDocument(reader)) {
            operation.apply(doc);
        } catch (Exception e) {
            throw new IOException("PDF read operation failed: " + e.getMessage(), e);
        }
    }

    // ==================== PDF COPY OPERATIONS ====================

    /**
     * Execute copy operation from source to destination PDF with automatic cleanup.
     *
     * @param srcPath Source PDF path
     * @param destPath Destination PDF path
     * @param operation Operation to perform
     * @return Result of the operation
     */
    public static <T> T withPdfCopy(String srcPath, String destPath,
                                     PdfCopyOperation<T> operation) throws IOException {
        try (PdfReader reader = new PdfReader(srcPath);
             PdfWriter writer = new PdfWriter(destPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {
            return operation.apply(srcDoc, destDoc);
        } catch (Exception e) {
            // Clean up partial output file on failure
            new File(destPath).delete();
            throw new IOException("PDF copy operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute copy operation (void return).
     */
    public static void withPdfCopyVoid(String srcPath, String destPath,
                                        PdfCopyVoidOperation operation) throws IOException {
        try (PdfReader reader = new PdfReader(srcPath);
             PdfWriter writer = new PdfWriter(destPath);
             PdfDocument srcDoc = new PdfDocument(reader);
             PdfDocument destDoc = new PdfDocument(writer)) {
            operation.apply(srcDoc, destDoc);
        } catch (Exception e) {
            new File(destPath).delete();
            throw new IOException("PDF copy operation failed: " + e.getMessage(), e);
        }
    }

    // ==================== PDF READ-WRITE OPERATIONS ====================

    /**
     * Execute read-write operation on a PDF (reads from src, writes to dest).
     *
     * @param srcPath Source PDF path
     * @param destPath Destination PDF path
     * @param operation Operation to perform
     * @return Result of the operation
     */
    public static <T> T withPdfReadWrite(String srcPath, String destPath,
                                          PdfReaderOperation<T> operation) throws IOException {
        try (PdfReader reader = new PdfReader(srcPath);
             PdfWriter writer = new PdfWriter(destPath);
             PdfDocument doc = new PdfDocument(reader, writer)) {
            return operation.apply(doc);
        } catch (Exception e) {
            new File(destPath).delete();
            throw new IOException("PDF read-write operation failed: " + e.getMessage(), e);
        }
    }

    // ==================== PDF RENDERER OPERATIONS ====================

    /**
     * Execute operation with PdfRenderer (Android native) with automatic cleanup.
     */
    public static <T> T withPdfRenderer(File file, PdfRendererOperation<T> operation) throws IOException {
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
        T apply(PdfDocument doc) throws Exception;
    }

    @FunctionalInterface
    public interface PdfReaderVoidOperation {
        void apply(PdfDocument doc) throws Exception;
    }

    @FunctionalInterface
    public interface PdfCopyOperation<T> {
        T apply(PdfDocument src, PdfDocument dest) throws Exception;
    }

    @FunctionalInterface
    public interface PdfCopyVoidOperation {
        void apply(PdfDocument src, PdfDocument dest) throws Exception;
    }

    @FunctionalInterface
    public interface PdfRendererOperation<T> {
        T apply(PdfRenderer renderer) throws Exception;
    }
}
