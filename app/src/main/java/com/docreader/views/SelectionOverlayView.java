package com.docreader.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom view for drawing selection rectangles on PDF pages.
 * Used for the "Cover & Replace" editing feature.
 */
public class SelectionOverlayView extends View {

    private Paint selectionPaint;
    private Paint borderPaint;
    private RectF selectionRect;
    private float startX, startY;
    private boolean isDrawing = false;
    private boolean hasSelection = false;

    private OnSelectionListener selectionListener;

    public interface OnSelectionListener {
        void onSelectionComplete(RectF selection);
        void onSelectionCleared();
    }

    public SelectionOverlayView(Context context) {
        super(context);
        init();
    }

    public SelectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SelectionOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Semi-transparent blue fill
        selectionPaint = new Paint();
        selectionPaint.setColor(Color.argb(50, 33, 150, 243));
        selectionPaint.setStyle(Paint.Style.FILL);

        // Blue border
        borderPaint = new Paint();
        borderPaint.setColor(Color.rgb(33, 150, 243));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);

        selectionRect = new RectF();
    }

    public void setSelectionListener(OnSelectionListener listener) {
        this.selectionListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = x;
                startY = y;
                isDrawing = true;
                hasSelection = false;
                selectionRect.set(x, y, x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    // Update selection rectangle
                    selectionRect.set(
                            Math.min(startX, x),
                            Math.min(startY, y),
                            Math.max(startX, x),
                            Math.max(startY, y)
                    );
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDrawing) {
                    isDrawing = false;
                    // Only consider it a valid selection if it has some size
                    if (selectionRect.width() > 20 && selectionRect.height() > 10) {
                        hasSelection = true;
                        if (selectionListener != null) {
                            selectionListener.onSelectionComplete(new RectF(selectionRect));
                        }
                    } else {
                        clearSelection();
                    }
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isDrawing || hasSelection) {
            // Draw semi-transparent fill
            canvas.drawRect(selectionRect, selectionPaint);
            // Draw border
            canvas.drawRect(selectionRect, borderPaint);
        }
    }

    /**
     * Clear the current selection
     */
    public void clearSelection() {
        hasSelection = false;
        isDrawing = false;
        selectionRect.set(0, 0, 0, 0);
        invalidate();
        if (selectionListener != null) {
            selectionListener.onSelectionCleared();
        }
    }

    /**
     * Check if there is a current selection
     */
    public boolean hasSelection() {
        return hasSelection;
    }

    /**
     * Get the current selection rectangle
     */
    public RectF getSelectionRect() {
        return hasSelection ? new RectF(selectionRect) : null;
    }

    /**
     * Set selection programmatically
     */
    public void setSelection(RectF rect) {
        selectionRect.set(rect);
        hasSelection = true;
        invalidate();
    }
}
