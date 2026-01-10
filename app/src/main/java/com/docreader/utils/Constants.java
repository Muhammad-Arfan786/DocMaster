package com.docreader.utils;

/**
 * Application-wide constants.
 * Centralizes all magic numbers and configuration values following DRY principle.
 *
 * Organized by category for easy discovery and maintenance.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // ==================== BUFFER SIZES ====================

    /** Default buffer size for stream operations (8 KB) */
    public static final int STREAM_BUFFER_SIZE = 8192;

    /** Buffer size for file copy operations (4 KB) */
    public static final int FILE_COPY_BUFFER_SIZE = 4096;

    // ==================== BITMAP/IMAGE LIMITS ====================

    /** Maximum bitmap dimension to prevent OOM (pixels) */
    public static final int MAX_BITMAP_SIZE = 4096;

    /** Maximum bitmap dimension for compressed/preview images */
    public static final int MAX_PREVIEW_BITMAP_SIZE = 2048;

    /** Default DPI for PDF rendering */
    public static final int DEFAULT_PDF_DPI = 72;

    /** High quality DPI for PDF to image conversion */
    public static final int HIGH_QUALITY_DPI = 150;

    /** JPEG compression quality for PDF compression (0-100) */
    public static final int PDF_COMPRESSION_QUALITY = 60;

    /** JPEG compression quality for image export (0-100) */
    public static final int IMAGE_EXPORT_QUALITY = 90;

    // ==================== TIMEOUTS ====================

    /** Background task timeout in milliseconds (60 seconds) */
    public static final long BACKGROUND_TASK_TIMEOUT_MS = 60_000L;

    /** Executor shutdown timeout in seconds */
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

    /** Dialog action delay in milliseconds */
    public static final long DIALOG_ACTION_DELAY_MS = 500L;

    // ==================== LIMITS ====================

    /** Maximum number of recent files to store */
    public static final int MAX_RECENT_FILES = 20;

    /** Maximum filename length (filesystem limit) */
    public static final int MAX_FILENAME_LENGTH = 255;

    /** Maximum text input length for dialogs */
    public static final int MAX_TEXT_INPUT_LENGTH = 1000;

    /** Maximum watermark text length */
    public static final int MAX_WATERMARK_LENGTH = 100;

    /** Minimum PDFs required for merge operation */
    public static final int MIN_PDFS_FOR_MERGE = 2;

    // ==================== CACHE SETTINGS ====================

    /** Maximum cache age in milliseconds (7 days) */
    public static final long MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    /** Maximum cache size in bytes (100 MB) */
    public static final long MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024;

    // ==================== ZOOM SETTINGS ====================

    /** Default zoom level (percentage) */
    public static final int DEFAULT_ZOOM = 100;

    /** Minimum zoom level (percentage) */
    public static final int MIN_ZOOM = 25;

    /** Maximum zoom level (percentage) */
    public static final int MAX_ZOOM = 400;

    /** Zoom increment step (percentage) */
    public static final int ZOOM_STEP = 25;

    // ==================== PDF PAGE SIZES ====================
    // These avoid using PageSize class which requires java.awt.Color (not available on Android)

    /** A4 page width in points (595.28) */
    public static final float PAGE_WIDTH_A4 = 595f;

    /** A4 page height in points (841.89) */
    public static final float PAGE_HEIGHT_A4 = 842f;

    /** Letter page width in points */
    public static final float PAGE_WIDTH_LETTER = 612f;

    /** Letter page height in points */
    public static final float PAGE_HEIGHT_LETTER = 792f;

    // ==================== PDF SETTINGS ====================

    /** Default page number font size */
    public static final float PAGE_NUMBER_FONT_SIZE = 11f;

    /** Default watermark font size */
    public static final float DEFAULT_WATERMARK_FONT_SIZE = 48f;

    /** Default watermark opacity (0.0 - 1.0) */
    public static final float DEFAULT_WATERMARK_OPACITY = 0.3f;

    /** Default watermark rotation (degrees) */
    public static final int DEFAULT_WATERMARK_ROTATION = 45;

    /** Default text annotation font size */
    public static final float DEFAULT_ANNOTATION_FONT_SIZE = 14f;

    // ==================== DRAWING SETTINGS ====================

    /** Default pen brush size */
    public static final float DEFAULT_BRUSH_SIZE = 8f;

    /** Default highlighter size */
    public static final float DEFAULT_HIGHLIGHTER_SIZE = 30f;

    /** Default eraser size */
    public static final float DEFAULT_ERASER_SIZE = 40f;

    /** Highlighter opacity (0-255) */
    public static final int HIGHLIGHTER_ALPHA = 80;

    // ==================== FILE EXTENSIONS ====================

    public static final String EXT_PDF = ".pdf";
    public static final String EXT_DOC = ".doc";
    public static final String EXT_DOCX = ".docx";
    public static final String EXT_JPG = ".jpg";
    public static final String EXT_JPEG = ".jpeg";
    public static final String EXT_PNG = ".png";

    // ==================== MIME TYPES ====================

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_DOC = "application/msword";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_ALL = "*/*";

    // ==================== INTENT EXTRAS ====================

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_TOOL_ACTION = "tool_action";
    public static final String EXTRA_MERGE_FILES = "merge_files";
    public static final String EXTRA_ORIGINAL_PDF_PATH = "original_pdf_path";
    public static final String EXTRA_ORIGINAL_PDF_NAME = "original_pdf_name";

    // ==================== TOOL ACTIONS ====================

    public static final String ACTION_MERGE = "merge";
    public static final String ACTION_SPLIT = "split";
    public static final String ACTION_TO_IMAGE = "toimage";
    public static final String ACTION_WATERMARK = "watermark";
    public static final String ACTION_PAGE_NUMBERS = "pagenumbers";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_ROTATE = "rotate";
    public static final String ACTION_COPY = "copy";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_EXTRACT = "extract";

    // ==================== FILE TYPE IDENTIFIERS ====================

    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_DOC = "doc";
    public static final String TYPE_IMAGE = "image";

    // ==================== THREAD POOL SIZES ====================

    /** Number of threads for disk I/O operations */
    public static final int DISK_IO_THREAD_COUNT = 3;

    /** Number of threads for network operations */
    public static final int NETWORK_THREAD_COUNT = 3;
}
