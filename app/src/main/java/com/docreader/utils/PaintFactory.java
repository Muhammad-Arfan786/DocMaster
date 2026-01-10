package com.docreader.utils;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Factory class for creating Paint objects with common configurations.
 * Follows DRY principle by centralizing Paint initialization patterns.
 */
public class PaintFactory {

    private PaintFactory() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a fill paint with the specified color and alpha.
     *
     * @param color The color for the paint
     * @param alpha The alpha value (0-255)
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createFillPaint(@ColorInt int color, int alpha) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * Creates a fill paint from ARGB color values.
     *
     * @param alpha Alpha component (0-255)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createFillPaint(int alpha, int red, int green, int blue) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(alpha, red, green, blue));
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * Creates a stroke paint with the specified color, alpha, and stroke width.
     *
     * @param color The color for the paint
     * @param alpha The alpha value (0-255)
     * @param strokeWidth The stroke width in pixels
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createStrokePaint(@ColorInt int color, int alpha, float strokeWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    /**
     * Creates a stroke paint from ARGB color values.
     *
     * @param alpha Alpha component (0-255)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @param strokeWidth The stroke width in pixels
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createStrokePaint(int alpha, int red, int green, int blue, float strokeWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(alpha, red, green, blue));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    /**
     * Creates a highlight paint for text block visualization.
     * Used in TextBlockOverlayView and similar views.
     *
     * @param alpha Alpha component (0-255)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return Configured Paint object with FILL style
     */
    @NonNull
    public static Paint createHighlightPaint(int alpha, int red, int green, int blue) {
        return createFillPaint(alpha, red, green, blue);
    }

    /**
     * Creates a border paint for text block visualization.
     *
     * @param alpha Alpha component (0-255)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @param strokeWidth The stroke width in pixels
     * @return Configured Paint object with STROKE style
     */
    @NonNull
    public static Paint createBorderPaint(int alpha, int red, int green, int blue, float strokeWidth) {
        return createStrokePaint(alpha, red, green, blue, strokeWidth);
    }

    /**
     * Creates a text paint with the specified color and size.
     *
     * @param color The text color
     * @param textSize The text size in pixels
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createTextPaint(@ColorInt int color, float textSize) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * Creates a text paint with the specified color, size, and typeface.
     *
     * @param color The text color
     * @param textSize The text size in pixels
     * @param typeface The typeface to use
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createTextPaint(@ColorInt int color, float textSize, @NonNull Typeface typeface) {
        Paint paint = createTextPaint(color, textSize);
        paint.setTypeface(typeface);
        return paint;
    }

    /**
     * Creates a drawing paint for pen/brush strokes.
     *
     * @param color The stroke color
     * @param strokeWidth The stroke width in pixels
     * @return Configured Paint object with round stroke caps and joins
     */
    @NonNull
    public static Paint createDrawingPaint(@ColorInt int color, float strokeWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        return paint;
    }

    /**
     * Creates a highlighter paint with semi-transparency.
     *
     * @param color The base color
     * @param strokeWidth The stroke width in pixels
     * @param alpha The alpha value (0-255)
     * @return Configured Paint object
     */
    @NonNull
    public static Paint createHighlighterPaint(@ColorInt int color, float strokeWidth, int alpha) {
        Paint paint = createDrawingPaint(color, strokeWidth);
        paint.setAlpha(alpha);
        return paint;
    }

    /**
     * Creates an eraser paint for drawing operations.
     *
     * @param strokeWidth The stroke width in pixels
     * @return Configured Paint object with CLEAR Xfermode
     */
    @NonNull
    public static Paint createEraserPaint(float strokeWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(Color.TRANSPARENT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        return paint;
    }

    // ==================== Pre-configured paints for TextBlockOverlayView ====================

    /**
     * Creates the normal block highlight paint (blue, low alpha).
     */
    @NonNull
    public static Paint createNormalBlockPaint() {
        return createHighlightPaint(30, 33, 150, 243);  // Light blue fill
    }

    /**
     * Creates the normal block border paint (blue, medium alpha).
     */
    @NonNull
    public static Paint createNormalBlockBorderPaint() {
        return createBorderPaint(100, 33, 150, 243, 2f);  // Blue border
    }

    /**
     * Creates the edited block highlight paint (green, medium alpha).
     */
    @NonNull
    public static Paint createEditedBlockPaint() {
        return createHighlightPaint(50, 76, 175, 80);  // Light green fill
    }

    /**
     * Creates the edited block border paint (green, higher alpha).
     */
    @NonNull
    public static Paint createEditedBlockBorderPaint() {
        return createBorderPaint(150, 76, 175, 80, 3f);  // Green border
    }

    /**
     * Creates the selected block highlight paint (orange, medium alpha).
     */
    @NonNull
    public static Paint createSelectedBlockPaint() {
        return createHighlightPaint(70, 255, 152, 0);  // Light orange fill
    }

    /**
     * Creates the selected block border paint (orange, high alpha).
     */
    @NonNull
    public static Paint createSelectedBlockBorderPaint() {
        return createBorderPaint(200, 255, 152, 0, 4f);  // Orange border
    }
}
