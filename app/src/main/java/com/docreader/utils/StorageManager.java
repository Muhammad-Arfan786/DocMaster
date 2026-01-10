package com.docreader.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Storage manager for handling file save operations across Android versions.
 * Abstracts MediaStore (Android 10+) and legacy external storage APIs.
 *
 * Following SOLID principles:
 * - Single Responsibility: Only handles storage/file save operations
 * - Open/Closed: Easy to extend for new storage locations
 * - Dependency Inversion: Activities depend on this abstraction, not storage APIs directly
 */
public final class StorageManager {

    private static final String TAG = "StorageManager";

    private StorageManager() {
        // Prevent instantiation
    }

    // ==================== PUBLIC API ====================

    /**
     * Save a file to the Downloads folder.
     * Uses MediaStore on Android 10+, legacy storage on older versions.
     *
     * @param context Application context
     * @param sourceFile The source file to save
     * @param fileName Desired filename (will be sanitized)
     * @param mimeType MIME type of the file
     * @return SaveResult containing the saved file path/URI and status
     */
    @NonNull
    public static SaveResult saveToDownloads(@NonNull Context context,
                                              @NonNull File sourceFile,
                                              @NonNull String fileName,
                                              @NonNull String mimeType) {
        String sanitizedName = ValidationUtils.sanitizeFileName(fileName);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveWithMediaStore(context, sourceFile, sanitizedName, mimeType);
            } else {
                return saveWithLegacyStorage(sourceFile, sanitizedName);
            }
        } catch (IOException e) {
            AppLogger.e(TAG, "Failed to save file", e);
            return new SaveResult(false, null, null, "Save failed: " + e.getMessage());
        }
    }

    /**
     * Save a file to the Documents folder.
     *
     * @param context Application context
     * @param sourceFile The source file to save
     * @param fileName Desired filename
     * @param mimeType MIME type of the file
     * @return SaveResult with path and status
     */
    @NonNull
    public static SaveResult saveToDocuments(@NonNull Context context,
                                              @NonNull File sourceFile,
                                              @NonNull String fileName,
                                              @NonNull String mimeType) {
        String sanitizedName = ValidationUtils.sanitizeFileName(fileName);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveWithMediaStoreDocuments(context, sourceFile, sanitizedName, mimeType);
            } else {
                return saveToCDocumentsLegacy(sourceFile, sanitizedName);
            }
        } catch (IOException e) {
            AppLogger.e(TAG, "Failed to save file to Documents", e);
            return new SaveResult(false, null, null, "Save failed: " + e.getMessage());
        }
    }

    /**
     * Generate output file path in Documents directory.
     * Creates the directory if it doesn't exist.
     *
     * @param baseName Base filename without extension
     * @param suffix Additional suffix (e.g., "edited", "merged")
     * @param extension File extension including dot (e.g., ".pdf")
     * @return Generated file path
     */
    @NonNull
    public static String generateOutputPath(@NonNull String baseName,
                                             @NonNull String suffix,
                                             @NonNull String extension) {
        File outputDir = getDocumentsDirectory();
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String cleanBaseName = ValidationUtils.sanitizeFileName(baseName);

        // Remove existing extension from baseName if present
        if (cleanBaseName.toLowerCase().endsWith(extension.toLowerCase())) {
            cleanBaseName = cleanBaseName.substring(0, cleanBaseName.length() - extension.length());
        }

        String finalName = cleanBaseName + "_" + suffix + "_" + timestamp + extension;
        return new File(outputDir, finalName).getAbsolutePath();
    }

    /**
     * Get the Documents directory (for internal use).
     */
    @NonNull
    public static File getDocumentsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
    }

    /**
     * Get the Downloads directory (for internal use).
     */
    @NonNull
    public static File getDownloadsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    /**
     * Get MIME type from filename.
     */
    @NonNull
    public static String getMimeType(@NonNull String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(Constants.EXT_PDF)) {
            return Constants.MIME_PDF;
        } else if (lowerName.endsWith(Constants.EXT_DOCX)) {
            return Constants.MIME_DOCX;
        } else if (lowerName.endsWith(Constants.EXT_DOC)) {
            return Constants.MIME_DOC;
        } else if (lowerName.endsWith(Constants.EXT_JPG) || lowerName.endsWith(Constants.EXT_JPEG)) {
            return Constants.MIME_JPEG;
        } else if (lowerName.endsWith(Constants.EXT_PNG)) {
            return Constants.MIME_PNG;
        }
        return Constants.MIME_ALL;
    }

    // ==================== PRIVATE IMPLEMENTATION ====================

    /**
     * Save using MediaStore (Android 10+) to Downloads.
     */
    private static SaveResult saveWithMediaStore(Context context, File sourceFile,
                                                  String fileName, String mimeType) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            return new SaveResult(false, null, null, "Failed to create MediaStore entry");
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(uri);
             InputStream in = new FileInputStream(sourceFile)) {
            if (out == null) {
                throw new IOException("Failed to open output stream");
            }
            copyStream(in, out);
        } catch (IOException e) {
            // Clean up on failure
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }

        // Return a usable path (approximation since MediaStore doesn't give direct path)
        String displayPath = Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
        return new SaveResult(true, displayPath, uri, "Saved to Downloads");
    }

    /**
     * Save using MediaStore (Android 10+) to Documents.
     */
    private static SaveResult saveWithMediaStoreDocuments(Context context, File sourceFile,
                                                           String fileName, String mimeType) throws IOException {
        // On Android 10+, we use Downloads for PDFs as Documents has restrictions
        // Documents folder can be accessed but MediaStore.Documents is limited
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            // Fallback to Downloads if Documents fails
            return saveWithMediaStore(context, sourceFile, fileName, mimeType);
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(uri);
             InputStream in = new FileInputStream(sourceFile)) {
            if (out == null) {
                throw new IOException("Failed to open output stream");
            }
            copyStream(in, out);
        } catch (IOException e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }

        String displayPath = Environment.DIRECTORY_DOCUMENTS + "/" + fileName;
        return new SaveResult(true, displayPath, uri, "Saved to Documents");
    }

    /**
     * Save using legacy external storage (Android 9 and below) to Downloads.
     */
    private static SaveResult saveWithLegacyStorage(File sourceFile, String fileName) throws IOException {
        File downloadsDir = getDownloadsDirectory();
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File destFile = new File(downloadsDir, fileName);

        // Handle filename collision
        destFile = getUniqueFile(destFile);

        copyFile(sourceFile, destFile);

        return new SaveResult(true, destFile.getAbsolutePath(),
                Uri.fromFile(destFile), "Saved to Downloads");
    }

    /**
     * Save using legacy external storage to Documents.
     */
    private static SaveResult saveToCDocumentsLegacy(File sourceFile, String fileName) throws IOException {
        File documentsDir = getDocumentsDirectory();
        if (!documentsDir.exists()) {
            documentsDir.mkdirs();
        }

        File destFile = new File(documentsDir, fileName);
        destFile = getUniqueFile(destFile);

        copyFile(sourceFile, destFile);

        return new SaveResult(true, destFile.getAbsolutePath(),
                Uri.fromFile(destFile), "Saved to Documents");
    }

    /**
     * Get unique file by appending number if file exists.
     */
    private static File getUniqueFile(File file) {
        if (!file.exists()) {
            return file;
        }

        String name = file.getName();
        String baseName = name;
        String extension = "";

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }

        File parent = file.getParentFile();
        int counter = 1;
        File newFile;

        do {
            newFile = new File(parent, baseName + "_" + counter + extension);
            counter++;
        } while (newFile.exists() && counter < 1000);

        return newFile;
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[Constants.STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        out.flush();
    }

    // ==================== RESULT CLASS ====================

    /**
     * Result class for save operations.
     */
    public static class SaveResult {
        public final boolean success;
        @Nullable public final String path;
        @Nullable public final Uri uri;
        @NonNull public final String message;

        public SaveResult(boolean success, @Nullable String path,
                          @Nullable Uri uri, @NonNull String message) {
            this.success = success;
            this.path = path;
            this.uri = uri;
            this.message = message;
        }

        /**
         * Get display-friendly location string.
         */
        @NonNull
        public String getDisplayLocation() {
            if (path != null) {
                // Extract just the folder/filename portion
                int lastSep = path.lastIndexOf('/');
                if (lastSep > 0) {
                    String folder = path.substring(0, lastSep);
                    int prevSep = folder.lastIndexOf('/');
                    if (prevSep > 0) {
                        return path.substring(prevSep + 1);
                    }
                }
                return path;
            }
            return "Unknown location";
        }
    }
}
