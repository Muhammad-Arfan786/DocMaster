package com.docreader.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for file operations.
 */
public class FileUtils {

    /**
     * Get file name from URI.
     */
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Copy URI content to a temporary file.
     */
    public static File copyToTempFile(Context context, Uri uri, String fileName) throws Exception {
        File tempFile = new File(context.getCacheDir(), fileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {

            if (inputStream == null) {
                throw new Exception("Cannot open input stream");
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }

    /**
     * Get file extension.
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return "";
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Check if file is PDF.
     */
    public static boolean isPdf(String fileName) {
        return "pdf".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is DOC.
     */
    public static boolean isDoc(String fileName) {
        return "doc".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is DOCX.
     */
    public static boolean isDocx(String fileName) {
        return "docx".equals(getFileExtension(fileName));
    }

    /**
     * Check if file is a Word document.
     */
    public static boolean isWordDocument(String fileName) {
        return isDoc(fileName) || isDocx(fileName);
    }

    /**
     * Check if file format is supported.
     */
    public static boolean isSupportedFormat(String fileName) {
        return isPdf(fileName) || isWordDocument(fileName);
    }

    /**
     * Format file size for display.
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Get actual file path from URI.
     * If the URI is a content URI, copies to cache and returns cache file path.
     */
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        // If it's a file URI, return the path directly
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // For content URIs, copy to cache
        try {
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                fileName = "temp_" + System.currentTimeMillis();
            }

            File cacheFile = new File(context.getCacheDir(), fileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(cacheFile)) {

                if (inputStream == null) {
                    return null;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return cacheFile.getAbsolutePath();

        } catch (Exception e) {
            AppLogger.e("FileUtils", "Error", e);
            return null;
        }
    }
}
