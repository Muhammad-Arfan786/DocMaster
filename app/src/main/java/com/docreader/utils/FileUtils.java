package com.docreader.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for file operations.
 * Provides safe file handling with path traversal prevention.
 *
 * Following SOLID principles:
 * - Single Responsibility: Only handles file utility operations
 * - Uses ValidationUtils for security (DRY)
 */
public final class FileUtils {

    private static final String TAG = "FileUtils";

    private FileUtils() {
        // Prevent instantiation
    }

    /**
     * Get file name from URI.
     *
     * @param context Application context
     * @param uri Content URI
     * @return File name, or null if cannot be determined
     */
    @Nullable
    public static String getFileName(@NonNull Context context, @Nullable Uri uri) {
        if (uri == null) {
            return null;
        }

        String result = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                AppLogger.w(TAG, "Failed to get filename from content URI", e);
            }
        }

        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1 && cut < path.length() - 1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;
                }
            }
        }

        return result;
    }

    /**
     * Copy URI content to a temporary file with path traversal protection.
     *
     * @param context Application context
     * @param uri Source content URI
     * @param fileName Desired filename (will be sanitized)
     * @return The created temporary file
     * @throws IOException if copy fails
     * @throws SecurityException if path traversal is detected
     */
    @NonNull
    public static File copyToTempFile(@NonNull Context context,
                                       @NonNull Uri uri,
                                       @NonNull String fileName) throws IOException, SecurityException {
        // CRITICAL FIX: Sanitize filename to prevent path traversal attacks
        String sanitizedName = ValidationUtils.sanitizeFileName(fileName);

        File cacheDir = context.getCacheDir();
        File tempFile = new File(cacheDir, sanitizedName);

        // Verify the file path is within the cache directory
        ValidationUtils.validatePathSafety(cacheDir, tempFile);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {

            if (inputStream == null) {
                throw new IOException("Cannot open input stream for URI: " + uri);
            }

            byte[] buffer = new byte[Constants.FILE_COPY_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        AppLogger.d(TAG, "Copied URI to temp file: " + sanitizedName);
        return tempFile;
    }

    /**
     * Get file extension from filename.
     *
     * @param fileName Filename or path
     * @return Lowercase extension without dot, or empty string
     */
    @NonNull
    public static String getFileExtension(@Nullable String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Check if file is PDF.
     */
    public static boolean isPdf(@Nullable String fileName) {
        return "pdf".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is DOC.
     */
    public static boolean isDoc(@Nullable String fileName) {
        return "doc".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is DOCX.
     */
    public static boolean isDocx(@Nullable String fileName) {
        return "docx".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is a Word document (DOC or DOCX).
     */
    public static boolean isWordDocument(@Nullable String fileName) {
        return isDoc(fileName) || isDocx(fileName);
    }

    /**
     * Check if file format is supported by the app.
     */
    public static boolean isSupportedFormat(@Nullable String fileName) {
        return isPdf(fileName) || isWordDocument(fileName);
    }

    /**
     * Format file size for display.
     *
     * @param size Size in bytes
     * @return Human-readable size string
     */
    @NonNull
    public static String formatFileSize(long size) {
        if (size < 0) {
            return "0 B";
        } else if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Get actual file path from URI.
     * For content URIs, copies to cache and returns cache file path.
     * Uses path traversal protection.
     *
     * @param context Application context
     * @param uri Source URI
     * @return File path, or null on error
     */
    @Nullable
    public static String getPathFromUri(@NonNull Context context, @Nullable Uri uri) {
        if (uri == null) {
            return null;
        }

        // If it's a file URI, return the path directly
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // For content URIs, copy to cache
        try {
            String fileName = getFileName(context, uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "temp_" + System.currentTimeMillis();
            }

            // Use copyToTempFile which has path traversal protection
            File cacheFile = copyToTempFile(context, uri, fileName);
            return cacheFile.getAbsolutePath();

        } catch (SecurityException e) {
            AppLogger.e(TAG, "Security error getting path from URI", e);
            return null;
        } catch (IOException e) {
            AppLogger.e(TAG, "IO error getting path from URI", e);
            return null;
        } catch (Exception e) {
            AppLogger.e(TAG, "Unexpected error getting path from URI", e);
            return null;
        }
    }

    /**
     * Get file type identifier for the app.
     *
     * @param fileName Filename
     * @return Type string: "pdf", "doc", or "unknown"
     */
    @NonNull
    public static String getFileType(@Nullable String fileName) {
        if (isPdf(fileName)) {
            return Constants.TYPE_PDF;
        } else if (isWordDocument(fileName)) {
            return Constants.TYPE_DOC;
        }
        return "unknown";
    }

    /**
     * Get MIME type from filename.
     *
     * @param fileName Filename
     * @return MIME type string
     */
    @NonNull
    public static String getMimeType(@Nullable String fileName) {
        return StorageManager.getMimeType(fileName != null ? fileName : "");
    }

    /**
     * Delete a file safely.
     *
     * @param file File to delete
     * @return true if deleted or didn't exist
     */
    public static boolean deleteFile(@Nullable File file) {
        if (file == null) {
            return true;
        }
        if (!file.exists()) {
            return true;
        }
        boolean deleted = file.delete();
        if (!deleted) {
            AppLogger.w(TAG, "Failed to delete file: " + file.getName());
        }
        return deleted;
    }

    /**
     * Delete a file by path safely.
     *
     * @param path File path
     * @return true if deleted or didn't exist
     */
    public static boolean deleteFile(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        return deleteFile(new File(path));
    }
}
