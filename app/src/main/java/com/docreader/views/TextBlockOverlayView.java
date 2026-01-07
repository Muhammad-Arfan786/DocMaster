package com.docreader.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for displaying and interacting with text blocks on PDF pages.
 * Shows clickable text areas that users can tap to edit - like Word document editing.
 */
public class TextBlockOverlayView extends View {

    /**
     * Represents a text block with screen coordinates
     */
    public static class TextBlockRect {
        public String text;
        public String editedText;
        public int pageNumber;
        public RectF pdfRect;    // Original PDF coordinates
        public RectF screenRect; // Screen display coordinates
        public float fontSize;
        public boolean isEdited = false;
        public boolean isSelected = false;

        public TextBlockRect(String text, int pageNumber, float pdfX, float pdfY, float pdfW, float pdfH) {
            this.text = text;
            this.pageNumber = pageNumber;
            this.pdfRect = new RectF(pdfX, pdfY, pdfX + pdfW, pdfY + pdfH);
            this.screenRect = new RectF();
            this.fontSize = Math.max(10, pdfH * 0.7f);
        }

        public void setScreenRect(float x, float y, float w, float h) {
            screenRect.set(x, y, x + w, y + h);
        }

        public boolean containsPoint(float x, float y) {
            return screenRect.contains(x, y);
        }

        public void edit(String newText) {
            this.editedText = newText;
            this.isEdited = true;
        }

        public String getDisplayText() {
            return isEdited ? editedText : text;
        }
    }

    private List<TextBlockRect> textBlocks = new ArrayList<>();
    private TextBlockRect selectedBlock = null;

    private Paint normalBlockPaint;
    private Paint editedBlockPaint;
    private Paint selectedBlockPaint;
    private Paint borderPaint;
    private Paint selectedBorderPaint;
    private Paint textPaint;

    private OnTextBlockClickListener clickListener;
    private boolean isEnabled = false;

    // Scale factor for coordinate conversion
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float pdfPageWidth = 0f;
    private float pdfPageHeight = 0f;

    public interface OnTextBlockClickListener {
        void onTextBlockClicked(TextBlockRect block);
    }

    public TextBlockOverlayView(Context context) {
        super(context);
        init();
    }

    public TextBlockOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextBlockOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Normal text block - light blue highlight
        normalBlockPaint = new Paint();
        normalBlockPaint.setColor(Color.argb(30, 33, 150, 243));
        normalBlockPaint.setStyle(Paint.Style.FILL);

        // Edited text block - light green highlight
        editedBlockPaint = new Paint();
        editedBlockPaint.setColor(Color.argb(50, 76, 175, 80));
        editedBlockPaint.setStyle(Paint.Style.FILL);

        // Selected text block - yellow highlight
        selectedBlockPaint = new Paint();
        selectedBlockPaint.setColor(Color.argb(80, 255, 235, 59));
        selectedBlockPaint.setStyle(Paint.Style.FILL);

        // Border for normal blocks
        borderPaint = new Paint();
        borderPaint.setColor(Color.argb(80, 33, 150, 243));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f);

        // Border for selected block
        selectedBorderPaint = new Paint();
        selectedBorderPaint.setColor(Color.rgb(33, 150, 243));
        selectedBorderPaint.setStyle(Paint.Style.STROKE);
        selectedBorderPaint.setStrokeWidth(3f);

        // Text paint for showing edited text
        textPaint = new Paint();
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(10f);
        textPaint.setAntiAlias(true);
    }

    public void setOnTextBlockClickListener(OnTextBlockClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Set the PDF page dimensions for coordinate mapping
     */
    public void setPdfPageDimensions(float width, float height) {
        this.pdfPageWidth = width;
        this.pdfPageHeight = height;
        updateScaleFactors();
    }

    /**
     * Update scale factors when view size changes
     */
    private void updateScaleFactors() {
        if (pdfPageWidth > 0 && pdfPageHeight > 0 && getWidth() > 0 && getHeight() > 0) {
            scaleX = getWidth() / pdfPageWidth;
            scaleY = getHeight() / pdfPageHeight;

            // Update all text block screen coordinates
            for (TextBlockRect block : textBlocks) {
                float screenX = block.pdfRect.left * scaleX;
                // PDF Y is from bottom, screen Y is from top
                float screenY = getHeight() - (block.pdfRect.top * scaleY) - (block.pdfRect.height() * scaleY);
                float screenW = block.pdfRect.width() * scaleX;
                float screenH = block.pdfRect.height() * scaleY;
                block.setScreenRect(screenX, screenY, screenW, screenH);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScaleFactors();
    }

    /**
     * Set text blocks to display
     */
    public void setTextBlocks(List<TextBlockRect> blocks) {
        this.textBlocks.clear();
        if (blocks != null) {
            this.textBlocks.addAll(blocks);
        }
        updateScaleFactors();
        invalidate();
    }

    /**
     * Add a single text block
     */
    public void addTextBlock(TextBlockRect block) {
        textBlocks.add(block);
        updateScaleFactors();
        invalidate();
    }

    /**
     * Clear all text blocks
     */
    public void clearTextBlocks() {
        textBlocks.clear();
        selectedBlock = null;
        invalidate();
    }

    /**
     * Get all text blocks (for saving edits)
     */
    public List<TextBlockRect> getTextBlocks() {
        return new ArrayList<>(textBlocks);
    }

    /**
     * Get only edited text blocks
     */
    public List<TextBlockRect> getEditedBlocks() {
        List<TextBlockRect> edited = new ArrayList<>();
        for (TextBlockRect block : textBlocks) {
            if (block.isEdited) {
                edited.add(block);
            }
        }
        return edited;
    }

    /**
     * Check if there are any edits
     */
    public boolean hasEdits() {
        for (TextBlockRect block : textBlocks) {
            if (block.isEdited) return true;
        }
        return false;
    }

    /**
     * Enable/disable editing mode
     */
    public void setEditModeEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            selectedBlock = null;
        }
        invalidate();
    }

    public boolean isEditModeEnabled() {
        return isEnabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            // Find clicked block
            TextBlockRect clickedBlock = null;
            for (TextBlockRect block : textBlocks) {
                if (block.containsPoint(x, y)) {
                    clickedBlock = block;
                    break;
                }
            }

            if (clickedBlock != null) {
                // Deselect previous
                if (selectedBlock != null) {
                    selectedBlock.isSelected = false;
                }

                // Select new block
                selectedBlock = clickedBlock;
                selectedBlock.isSelected = true;
                invalidate();

                // Notify listener
                if (clickListener != null) {
                    clickListener.onTextBlockClicked(clickedBlock);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isEnabled || textBlocks.isEmpty()) {
            return;
        }

        for (TextBlockRect block : textBlocks) {
            RectF rect = block.screenRect;

            if (rect.width() <= 0 || rect.height() <= 0) {
                continue;
            }

            // Choose paint based on state
            if (block.isSelected) {
                canvas.drawRect(rect, selectedBlockPaint);
                canvas.drawRect(rect, selectedBorderPaint);
            } else if (block.isEdited) {
                canvas.drawRect(rect, editedBlockPaint);
                canvas.drawRect(rect, borderPaint);
            } else {
                canvas.drawRect(rect, normalBlockPaint);
                canvas.drawRect(rect, borderPaint);
            }
        }
    }

    /**
     * Update a text block with edited text
     */
    public void updateBlockText(TextBlockRect block, String newText) {
        block.edit(newText);
        invalidate();
    }

    /**
     * Clear selection
     */
    public void clearSelection() {
        if (selectedBlock != null) {
            selectedBlock.isSelected = false;
            selectedBlock = null;
            invalidate();
        }
    }

    /**
     * Get currently selected block
     */
    public TextBlockRect getSelectedBlock() {
        return selectedBlock;
    }
}
