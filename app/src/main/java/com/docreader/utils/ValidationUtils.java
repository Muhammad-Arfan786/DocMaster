package com.docreader.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for input validation and sanitization.
 * Provides security-focused validation methods to prevent common attacks.
 *
 * Following SOLID principles:
 * - Single Responsibility: Only handles validation logic
 * - Open/Closed: Easy to extend with new validation methods
 */
public final class ValidationUtils {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int DEFAULT_MAX_TEXT_LENGTH = 1000;

    // Characters that are unsafe in filenames across platforms
    private static final String UNSAFE_FILENAME_CHARS = "<>:\"/\\|?*";

    private ValidationUtils() {
        // Prevent instantiation
    }

    // ==================== FILENAME VALIDATION ====================

    /**
     * Sanitize a filename to prevent path traversal attacks and invalid characters.
     *
     * @param fileName The original filename (may be null or contain malicious content)
     * @return A safe filename that can be used for file operations
     */
    @NonNull
    public static String sanitizeFileName(@Nullable String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unnamed_" + System.currentTimeMillis();
        }

        String sanitized = fileName.trim();

        // Remove path traversal sequences
        sanitized = sanitized.replace("..", "");
        sanitized = sanitized.replace("/", "");
        sanitized = sanitized.replace("\\", "");

        // Remove null bytes (security risk)
        sanitized = sanitized.replace("\0", "");

        // Remove unsafe characters
        for (char c : UNSAFE_FILENAME_CHARS.toCharArray()) {
            sanitized = sanitized.replace(String.valueOf(c), "");
        }

        // Remove leading/trailing dots and spaces
        sanitized = sanitized.replaceAll("^[.\\s]+|[.\\s]+$", "");

        // Limit length
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            // Preserve extension if possible
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0 && lastDot > sanitized.length() - 10) {
                String ext = sanitized.substring(lastDot);
                String name = sanitized.substring(0, MAX_FILENAME_LENGTH - ext.length());
                sanitized = name + ext;
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
            }
        }

        // If nothing left after sanitization, generate a safe name
        if (sanitized.isEmpty()) {
            return "unnamed_" + System.currentTimeMillis();
        }

        return sanitized;
    }

    /**
     * Check if a file path is safely within a base directory.
     * Prevents path traversal attacks by verifying canonical paths.
     *
     * @param baseDir The allowed base directory
     * @param targetFile The file to check
     * @return true if targetFile is within baseDir, false otherwise
     */
    public static boolean isPathSafe(@NonNull File baseDir, @NonNull File targetFile) {
        try {
            String basePath = baseDir.getCanonicalPath();
            String targetPath = targetFile.getCanonicalPath();
            return targetPath.startsWith(basePath + File.separator) || targetPath.equals(basePath);
        } catch (IOException e) {
            AppLogger.w("ValidationUtils", "Path safety check failed", e);
            return false;
        }
    }

    /**
     * Validate that a file path doesn't escape its intended directory.
     *
     * @param baseDir The allowed base directory
     * @param targetFile The file to validate
     * @throws SecurityException if path traversal is detected
     */
    public static void validatePathSafety(@NonNull File baseDir, @NonNull File targetFile)
            throws SecurityException {
        if (!isPathSafe(baseDir, targetFile)) {
            throw new SecurityException("Path traversal attempt detected: " + targetFile.getName());
        }
    }

    // ==================== TEXT INPUT VALIDATION ====================

    /**
     * Check if text input is valid (non-null, non-empty, within length limit).
     *
     * @param text The text to validate
     * @param maxLength Maximum allowed length
     * @return true if valid, false otherwise
     */
    public static boolean isValidTextInput(@Nullable String text, int maxLength) {
        return text != null && !text.trim().isEmpty() && text.length() <= maxLength;
    }

    /**
     * Check if text input is valid using default max length.
     *
     * @param text The text to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidTextInput(@Nullable String text) {
        return isValidTextInput(text, DEFAULT_MAX_TEXT_LENGTH);
    }

    /**
     * Sanitize text input by trimming and limiting length.
     *
     * @param text The text to sanitize
     * @param maxLength Maximum allowed length
     * @return Sanitized text, or empty string if null
     */
    @NonNull
    public static String sanitizeTextInput(@Nullable String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String sanitized = text.trim();

        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    // ==================== NUMERIC VALIDATION ====================

    /**
     * Validate that a number is within a specified range.
     *
     * @param value The value to check
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return true if value is within range
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Clamp a value to a specified range.
     *
     * @param value The value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp a float value to a specified range.
     *
     * @param value The value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== PAGE NUMBER VALIDATION ====================

    /**
     * Validate page numbers for PDF operations.
     *
     * @param pageNumber The page number to validate (1-based)
     * @param totalPages Total number of pages in the document
     * @return true if page number is valid
     */
    public static boolean isValidPageNumber(int pageNumber, int totalPages) {
        return pageNumber >= 1 && pageNumber <= totalPages;
    }

    /**
     * Validate a range of page numbers.
     *
     * @param startPage Start page (1-based, inclusive)
     * @param endPage End page (1-based, inclusive)
     * @param totalPages Total pages in document
     * @return true if range is valid
     */
    public static boolean isValidPageRange(int startPage, int endPage, int totalPages) {
        return startPage >= 1 && endPage >= startPage && endPage <= totalPages;
    }
}
