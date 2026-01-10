package com.docreader.utils;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Centralized logging utility for the application.
 *
 * Replaces all e.printStackTrace() calls with proper Android logging.
 * Debug logs are only shown in debug builds.
 *
 * Usage:
 *   AppLogger.d("Debug message");
 *   AppLogger.e("Error message", exception);
 *   AppLogger.w("Warning with exception", exception);
 */
public final class AppLogger {

    private static final String DEFAULT_TAG = "TenDocmas";
    // Note: In production, consider using BuildConfig.DEBUG when build is successful
    // For now, using a simple flag that can be toggled
    private static boolean DEBUG = true;

    private AppLogger() {
        // Prevent instantiation
    }

    // ==================== DEBUG ====================

    /**
     * Log debug message (only in debug builds).
     */
    public static void d(String message) {
        if (DEBUG) {
            Log.d(DEFAULT_TAG, message);
        }
    }

    /**
     * Log debug message with custom tag (only in debug builds).
     */
    public static void d(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message);
        }
    }

    /**
     * Log debug message with throwable (only in debug builds).
     */
    public static void d(String message, Throwable t) {
        if (DEBUG) {
            Log.d(DEFAULT_TAG, message, t);
        }
    }

    // ==================== INFO ====================

    /**
     * Log info message.
     */
    public static void i(String message) {
        Log.i(DEFAULT_TAG, message);
    }

    /**
     * Log info message with custom tag.
     */
    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    // ==================== WARNING ====================

    /**
     * Log warning message.
     */
    public static void w(String message) {
        Log.w(DEFAULT_TAG, message);
    }

    /**
     * Log warning message with custom tag.
     */
    public static void w(String tag, String message) {
        Log.w(tag, message);
    }

    /**
     * Log warning message with throwable.
     */
    public static void w(String message, Throwable t) {
        Log.w(DEFAULT_TAG, message + ": " + getStackTraceString(t));
    }

    /**
     * Log warning with custom tag and throwable.
     */
    public static void w(String tag, String message, Throwable t) {
        Log.w(tag, message, t);
    }

    // ==================== ERROR ====================

    /**
     * Log error message.
     */
    public static void e(String message) {
        Log.e(DEFAULT_TAG, message);
    }

    /**
     * Log error message with custom tag.
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    /**
     * Log error message with throwable.
     */
    public static void e(String message, Throwable t) {
        Log.e(DEFAULT_TAG, message, t);
    }

    /**
     * Log throwable only (replacement for e.printStackTrace()).
     */
    public static void e(Throwable t) {
        Log.e(DEFAULT_TAG, getStackTraceString(t));
    }

    /**
     * Log error with custom tag and throwable.
     */
    public static void e(String tag, String message, Throwable t) {
        Log.e(tag, message, t);
    }

    // ==================== UTILITIES ====================

    /**
     * Get full stack trace as string.
     */
    public static String getStackTraceString(Throwable t) {
        if (t == null) {
            return "null";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Log method entry (for debugging).
     */
    public static void methodEntry(String methodName) {
        if (DEBUG) {
            Log.d(DEFAULT_TAG, ">>> " + methodName);
        }
    }

    /**
     * Log method exit (for debugging).
     */
    public static void methodExit(String methodName) {
        if (DEBUG) {
            Log.d(DEFAULT_TAG, "<<< " + methodName);
        }
    }

    /**
     * Log performance timing (for debugging).
     */
    public static void timing(String operation, long startTimeMs) {
        if (DEBUG) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            Log.d(DEFAULT_TAG, operation + " took " + elapsed + "ms");
        }
    }
}
