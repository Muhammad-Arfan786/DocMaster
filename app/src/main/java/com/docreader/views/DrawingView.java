package com.docreader.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for drawing annotations on PDF pages.
 */
public class DrawingView extends View {

    public enum Tool {
        PEN,
        HIGHLIGHTER,
        ERASER,
        TEXT,
        NONE
    }

    private Path currentPath;
    private Paint drawPaint;
    private Paint canvasPaint;
    private Canvas drawCanvas;
    private Bitmap canvasBitmap;

    private List<DrawingPath> paths = new ArrayList<>();
    private List<DrawingPath> undonePaths = new ArrayList<>();

    private Tool currentTool = Tool.NONE;
    private int currentColor = Color.RED;
    private float brushSize = 8f;
    private float highlighterSize = 30f;
    private float eraserSize = 40f;

    private boolean isEnabled = false;
    private OnTextPlacementListener textPlacementListener;

    public interface OnTextPlacementListener {
        void onTextPlacement(float x, float y);
    }

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        currentPath = new Path();

        drawPaint = new Paint();
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        canvasPaint = new Paint(Paint.DITHER_FLAG);

        setupPen();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w > 0 && h > 0) {
            // CRITICAL FIX: Recycle old bitmap before creating new one to prevent memory leak
            if (canvasBitmap != null && !canvasBitmap.isRecycled()) {
                canvasBitmap.recycle();
                canvasBitmap = null;
            }

            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            drawCanvas = new Canvas(canvasBitmap);

            // Redraw existing paths on new canvas
            for (DrawingPath dp : paths) {
                drawCanvas.drawPath(dp.path, dp.paint);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (canvasBitmap != null) {
            canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        }

        if (currentTool != Tool.NONE && currentTool != Tool.TEXT) {
            canvas.drawPath(currentPath, drawPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled || currentTool == Tool.NONE) {
            return false;
        }

        float touchX = event.getX();
        float touchY = event.getY();

        if (currentTool == Tool.TEXT) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (textPlacementListener != null) {
                    textPlacementListener.onTextPlacement(touchX, touchY);
                }
            }
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath.moveTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_MOVE:
                currentPath.lineTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP:
                if (currentTool == Tool.ERASER) {
                    drawCanvas.drawPath(currentPath, drawPaint);
                } else {
                    drawCanvas.drawPath(currentPath, drawPaint);
                    paths.add(new DrawingPath(new Path(currentPath), new Paint(drawPaint)));
                }
                currentPath.reset();
                undonePaths.clear();
                break;
            default:
                return false;
        }

        invalidate();
        return true;
    }

    public void setTool(Tool tool) {
        this.currentTool = tool;

        switch (tool) {
            case PEN:
                setupPen();
                break;
            case HIGHLIGHTER:
                setupHighlighter();
                break;
            case ERASER:
                setupEraser();
                break;
            case TEXT:
            case NONE:
                break;
        }
    }

    private void setupPen() {
        drawPaint.setColor(currentColor);
        drawPaint.setStrokeWidth(brushSize);
        drawPaint.setAlpha(255);
        drawPaint.setXfermode(null);
    }

    private void setupHighlighter() {
        drawPaint.setColor(currentColor);
        drawPaint.setStrokeWidth(highlighterSize);
        drawPaint.setAlpha(80);
        drawPaint.setXfermode(null);
    }

    private void setupEraser() {
        drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        drawPaint.setStrokeWidth(eraserSize);
        drawPaint.setAlpha(255);
    }

    public void setColor(int color) {
        this.currentColor = color;
        if (currentTool == Tool.PEN) {
            drawPaint.setColor(color);
            drawPaint.setAlpha(255);
        } else if (currentTool == Tool.HIGHLIGHTER) {
            drawPaint.setColor(color);
            drawPaint.setAlpha(80);
        }
    }

    public void setBrushSize(float size) {
        this.brushSize = size;
        if (currentTool == Tool.PEN) {
            drawPaint.setStrokeWidth(size);
        }
    }

    public void setEditEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            currentTool = Tool.NONE;
        }
    }

    public boolean isEditEnabled() {
        return isEnabled;
    }

    public void setOnTextPlacementListener(OnTextPlacementListener listener) {
        this.textPlacementListener = listener;
    }

    public void undo() {
        if (!paths.isEmpty()) {
            undonePaths.add(paths.remove(paths.size() - 1));
            redrawCanvas();
        }
    }

    public void redo() {
        if (!undonePaths.isEmpty()) {
            paths.add(undonePaths.remove(undonePaths.size() - 1));
            redrawCanvas();
        }
    }

    public void clearAll() {
        paths.clear();
        undonePaths.clear();
        if (drawCanvas != null) {
            drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        invalidate();
    }

    private void redrawCanvas() {
        if (drawCanvas != null) {
            drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (DrawingPath dp : paths) {
                drawCanvas.drawPath(dp.path, dp.paint);
            }
            invalidate();
        }
    }

    public Bitmap getDrawingBitmap() {
        return canvasBitmap;
    }

    public boolean hasDrawings() {
        return !paths.isEmpty();
    }

    public Tool getCurrentTool() {
        return currentTool;
    }

    public int getCurrentColor() {
        return currentColor;
    }

    /**
     * Release resources when view is detached.
     * Called automatically when view is removed from window.
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    /**
     * Release all bitmap resources to prevent memory leaks.
     * Call this when done with the drawing view.
     */
    public void release() {
        if (canvasBitmap != null && !canvasBitmap.isRecycled()) {
            canvasBitmap.recycle();
            canvasBitmap = null;
        }
        drawCanvas = null;
        paths.clear();
        undonePaths.clear();
    }

    /**
     * Inner class to store path with its paint settings
     */
    private static class DrawingPath {
        Path path;
        Paint paint;

        DrawingPath(Path path, Paint paint) {
            this.path = path;
            this.paint = paint;
        }
    }
}
