package com.docreader.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Cache manager for temporary file operations.
 * Handles cache cleanup, temp file creation, and size management.
 *
 * Following SOLID principles:
 * - Single Responsibility: Only handles cache operations
 * - DRY: Centralizes all cache-related logic
 */
public final class CacheManager {

    private static final String TAG = "CacheManager";

    private CacheManager() {
        // Prevent instantiation
    }

    // ==================== CACHE CLEANUP ====================

    /**
     * Clean up old cache files asynchronously.
     * Removes files older than MAX_CACHE_AGE_MS and trims cache size.
     *
     * @param context Application context
     */
    public static void cleanupCacheAsync(@NonNull Context context) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            cleanupOldFiles(context.getCacheDir());
            trimCacheSize(context.getCacheDir());
            AppLogger.d(TAG, "Cache cleanup completed");
        });
    }

    /**
     * Clean up cache synchronously (call from background thread only).
     *
     * @param context Application context
     */
    public static void cleanupCache(@NonNull Context context) {
        cleanupOldFiles(context.getCacheDir());
        trimCacheSize(context.getCacheDir());
    }

    /**
     * Delete files older than MAX_CACHE_AGE_MS.
     */
    private static void cleanupOldFiles(@NonNull File cacheDir) {
        long now = System.currentTimeMillis();
        File[] files = cacheDir.listFiles();

        if (files == null) {
            return;
        }

        int deletedCount = 0;
        for (File file : files) {
            if (file.isFile()) {
                long age = now - file.lastModified();
                if (age > Constants.MAX_CACHE_AGE_MS) {
                    if (file.delete()) {
                        deletedCount++;
                    } else {
                        AppLogger.w(TAG, "Failed to delete old cache file: " + file.getName());
                    }
                }
            } else if (file.isDirectory()) {
                // Recursively clean subdirectories
                cleanupOldFiles(file);
            }
        }

        if (deletedCount > 0) {
            AppLogger.d(TAG, "Deleted " + deletedCount + " old cache files");
        }
    }

    /**
     * Trim cache size using LRU deletion if exceeds MAX_CACHE_SIZE_BYTES.
     */
    private static void trimCacheSize(@NonNull File cacheDir) {
        long totalSize = calculateDirSize(cacheDir);

        if (totalSize <= Constants.MAX_CACHE_SIZE_BYTES) {
            return;
        }

        AppLogger.d(TAG, "Cache size " + formatSize(totalSize) +
                " exceeds limit, trimming...");

        // Get all files sorted by last modified (oldest first)
        File[] files = cacheDir.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        // Delete oldest files until under limit
        for (File file : files) {
            if (totalSize <= Constants.MAX_CACHE_SIZE_BYTES) {
                break;
            }

            long fileSize = file.length();
            if (file.delete()) {
                totalSize -= fileSize;
                AppLogger.d(TAG, "Trimmed: " + file.getName());
            }
        }
    }

    // ==================== TEMP FILE CREATION ====================

    /**
     * Create a safe temporary file in the cache directory.
     * Sanitizes the filename to prevent path traversal.
     *
     * @param context Application context
     * @param fileName Desired filename (will be sanitized)
     * @return The created temp file
     * @throws SecurityException if path traversal is detected
     */
    @NonNull
    public static File getTempFile(@NonNull Context context, @NonNull String fileName)
            throws SecurityException {
        String sanitizedName = ValidationUtils.sanitizeFileName(fileName);
        File cacheDir = context.getCacheDir();
        File tempFile = new File(cacheDir, sanitizedName);

        // Verify path safety
        ValidationUtils.validatePathSafety(cacheDir, tempFile);

        return tempFile;
    }

    /**
     * Create a unique temporary file with a prefix and extension.
     *
     * @param context Application context
     * @param prefix Filename prefix
     * @param extension File extension (including dot)
     * @return The created temp file
     */
    @NonNull
    public static File createTempFile(@NonNull Context context,
                                       @NonNull String prefix,
                                       @NonNull String extension) {
        File cacheDir = context.getCacheDir();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sanitizedPrefix = ValidationUtils.sanitizeFileName(prefix);
        String fileName = sanitizedPrefix + "_" + timestamp + extension;

        return new File(cacheDir, fileName);
    }

    /**
     * Get or create an "edited" subdirectory in cache.
     *
     * @param context Application context
     * @return The edited subdirectory
     */
    @NonNull
    public static File getEditedCacheDir(@NonNull Context context) {
        File editedDir = new File(context.getCacheDir(), "edited");
        if (!editedDir.exists()) {
            editedDir.mkdirs();
        }
        return editedDir;
    }

    // ==================== CACHE INFO ====================

    /**
     * Get total cache size.
     *
     * @param context Application context
     * @return Total cache size in bytes
     */
    public static long getCacheSize(@NonNull Context context) {
        return calculateDirSize(context.getCacheDir());
    }

    /**
     * Get formatted cache size string.
     *
     * @param context Application context
     * @return Formatted size string (e.g., "45.3 MB")
     */
    @NonNull
    public static String getCacheSizeFormatted(@NonNull Context context) {
        return formatSize(getCacheSize(context));
    }

    /**
     * Get number of files in cache.
     *
     * @param context Application context
     * @return Number of cached files
     */
    public static int getCacheFileCount(@NonNull Context context) {
        return countFiles(context.getCacheDir());
    }

    /**
     * Clear all cache files.
     *
     * @param context Application context
     */
    public static void clearAllCache(@NonNull Context context) {
        deleteDirectory(context.getCacheDir(), false);
        AppLogger.i(TAG, "All cache cleared");
    }

    /**
     * Delete a specific file from cache if it exists.
     *
     * @param file The file to delete
     * @return true if deleted successfully
     */
    public static boolean deleteFile(@Nullable File file) {
        if (file != null && file.exists() && file.isFile()) {
            return file.delete();
        }
        return false;
    }

    // ==================== HELPER METHODS ====================

    private static long calculateDirSize(@NonNull File dir) {
        long size = 0;
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirSize(file);
                }
            }
        }

        return size;
    }

    private static int countFiles(@NonNull File dir) {
        int count = 0;
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    count++;
                } else if (file.isDirectory()) {
                    count += countFiles(file);
                }
            }
        }

        return count;
    }

    private static void deleteDirectory(@NonNull File dir, boolean deleteRoot) {
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file, true);
                } else {
                    file.delete();
                }
            }
        }

        if (deleteRoot) {
            dir.delete();
        }
    }

    @NonNull
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
