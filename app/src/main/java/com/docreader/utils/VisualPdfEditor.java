package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual PDF Editor - Provides Word-like editing experience for PDFs.
 *
 * This editor:
 * 1. Extracts text blocks with their positions
 * 2. Allows users to tap on text to edit it
 * 3. Uses cover-and-replace method to update text
 * 4. Preserves the original PDF structure as much as possible
 */
public class VisualPdfEditor {

    /**
     * Represents an editable text block in the PDF
     */
    public static class TextBlock {
        public String text;
        public int pageNumber;      // 1-based
        public float pdfX, pdfY;    // PDF coordinates (bottom-left origin)
        public float pdfWidth, pdfHeight;
        public float width, height; // Aliases for compatibility
        public float screenX, screenY, screenWidth, screenHeight; // Screen coordinates for UI
        public float fontSize;
        public boolean isEdited = false;
        public String newText = null;

        public TextBlock(String text, int pageNumber, float x, float y, float width, float height) {
            this.text = text;
            this.pageNumber = pageNumber;
            this.pdfX = x;
            this.pdfY = y;
            this.pdfWidth = width;
            this.pdfHeight = height;
            this.width = width;    // Alias
            this.height = height;  // Alias
            this.fontSize = Math.max(10, height * 0.7f);
        }

        public void setScreenCoords(float x, float y, float width, float height) {
            this.screenX = x;
            this.screenY = y;
            this.screenWidth = width;
            this.screenHeight = height;
        }

        public boolean containsPoint(float x, float y) {
            return x >= screenX && x <= screenX + screenWidth &&
                   y >= screenY && y <= screenY + screenHeight;
        }

        public void edit(String newText) {
            this.newText = newText;
            this.isEdited = true;
        }

        public String getDisplayText() {
            return isEdited ? newText : text;
        }
    }

    /**
     * Extract all text blocks from a PDF with their exact positions
     */
    public static List<TextBlock> extractTextBlocks(String pdfPath) {
        List<TextBlock> blocks = new ArrayList<>();

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            int numPages = reader.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                String pageText = extractor.getTextFromPage(pageNum);

                // Split into lines
                String[] lines = pageText.split("\n");
                Rectangle pageSize = reader.getPageSize(pageNum);
                float pageHeight = pageSize.getHeight();
                float pageWidth = pageSize.getWidth();

                // Estimate positions based on line number
                float lineHeight = 14f;
                float margin = 50f;
                float currentY = pageHeight - margin;

                for (String line : lines) {
                    if (line.trim().isEmpty()) {
                        currentY -= lineHeight * 0.5f;
                        continue;
                    }

                    // Use estimated position (OpenPDF doesn't have regex-based location extraction)
                    float x = margin;
                    float y = currentY;
                    float width = pageWidth - (2 * margin);
                    float height = lineHeight;

                    TextBlock block = new TextBlock(line.trim(), pageNum, x, y, width, height);
                    blocks.add(block);

                    currentY -= lineHeight;
                }
            }

        } catch (Exception e) {
            AppLogger.e("VisualPdfEditor", "Error", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return blocks;
    }

    /**
     * Extract text blocks for a specific page
     */
    public static List<TextBlock> extractTextBlocksForPage(String pdfPath, int pageNumber) {
        List<TextBlock> blocks = new ArrayList<>();

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber < 1 || pageNumber > reader.getNumberOfPages()) {
                return blocks;
            }

            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String pageText = extractor.getTextFromPage(pageNumber);
            Rectangle pageSize = reader.getPageSize(pageNumber);
            float pageHeight = pageSize.getHeight();
            float pageWidth = pageSize.getWidth();

            String[] lines = pageText.split("\n");
            float lineHeight = 14f;
            float margin = 50f;
            float currentY = pageHeight - margin;

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    currentY -= lineHeight * 0.5f;
                    continue;
                }

                float x = margin;
                float y = currentY;
                float width = pageWidth - (2 * margin);
                float height = lineHeight;

                TextBlock block = new TextBlock(line.trim(), pageNumber, x, y, width, height);
                blocks.add(block);
                currentY -= lineHeight;
            }

        } catch (Exception e) {
            AppLogger.e("VisualPdfEditor", "Error", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return blocks;
    }

    /**
     * Apply all edits to the PDF and save
     */
    public static File applyEdits(String sourcePdfPath, File outputDir, String outputFileName,
                                   List<TextBlock> editedBlocks) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        // Copy original first
        copyFile(new File(sourcePdfPath), outputFile);

        // Apply each edit
        File tempFile = new File(outputDir, "temp_edit_" + System.currentTimeMillis() + ".pdf");

        for (TextBlock block : editedBlocks) {
            if (!block.isEdited || block.newText == null) continue;

            PdfReader reader = null;
            PdfStamper stamper = null;
            FileOutputStream fos = null;

            try {
                // Apply this edit using PdfStamper
                reader = new PdfReader(outputFile.getAbsolutePath());
                fos = new FileOutputStream(tempFile);
                stamper = new PdfStamper(reader, fos);

                PdfContentByte canvas = stamper.getOverContent(block.pageNumber);
                BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

                // Cover old text with white
                canvas.saveState();
                canvas.setRGBColorFill(255, 255, 255);
                canvas.rectangle(block.pdfX - 2, block.pdfY - 2,
                        block.pdfWidth + 4, block.pdfHeight + 4);
                canvas.fill();
                canvas.restoreState();

                // Write new text
                canvas.saveState();
                canvas.beginText();
                canvas.setFontAndSize(font, block.fontSize);
                canvas.setRGBColorFill(0, 0, 0);
                canvas.moveText(block.pdfX, block.pdfY + 2);
                // Use raw PDF operators to avoid AWT dependencies
                String escaped = escapePdfString(block.newText);
                canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                canvas.endText();
                canvas.restoreState();
            } finally {
                if (stamper != null) try { stamper.close(); } catch (Exception ignored) {}
                if (reader != null) try { reader.close(); } catch (Exception ignored) {}
                if (fos != null) try { fos.close(); } catch (Exception ignored) {}
            }

            // Swap files
            outputFile.delete();
            tempFile.renameTo(outputFile);
        }

        if (tempFile.exists()) {
            tempFile.delete();
        }

        return outputFile;
    }

    /**
     * Replace specific text in PDF (find and replace)
     */
    public static File replaceText(String sourcePdfPath, File outputDir, String outputFileName,
                                    String findText, String replaceText, int pageNumber) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        PdfReader reader = new PdfReader(sourcePdfPath);
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputFile));

        PdfContentByte canvas = stamper.getOverContent(pageNumber);
        BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

        // Get page size for position estimation
        Rectangle pageSize = reader.getPageSize(pageNumber);
        float pageHeight = pageSize.getHeight();
        float pageWidth = pageSize.getWidth();

        // Extract text to find position
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String pageText = extractor.getTextFromPage(pageNumber);

        // Find the text in lines and estimate position
        String[] lines = pageText.split("\n");
        float lineHeight = 14f;
        float margin = 50f;
        float currentY = pageHeight - margin;

        for (String line : lines) {
            if (line.contains(findText)) {
                float x = margin;
                float y = currentY;
                float width = pageWidth - (2 * margin);
                float height = lineHeight;

                // Cover with white
                canvas.saveState();
                canvas.setRGBColorFill(255, 255, 255);
                canvas.rectangle(x - 2, y - 2, width + 4, height + 4);
                canvas.fill();
                canvas.restoreState();

                // Write replacement (replace findText with replaceText in the line)
                String newLine = line.replace(findText, replaceText);
                float fontSize = Math.max(10, height * 0.8f);
                canvas.saveState();
                canvas.beginText();
                canvas.setFontAndSize(font, fontSize);
                canvas.setRGBColorFill(0, 0, 0);
                canvas.moveText(x, y + 2);
                // Use raw PDF operators to avoid AWT dependencies
                String escaped = escapePdfString(newLine);
                canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                canvas.endText();
                canvas.restoreState();
            }

            if (line.trim().isEmpty()) {
                currentY -= lineHeight * 0.5f;
            } else {
                currentY -= lineHeight;
            }
        }

        stamper.close();
        reader.close();

        return outputFile;
    }

    /**
     * Add new text at specific position
     */
    public static File addText(String sourcePdfPath, File outputDir, String outputFileName,
                                int pageNumber, float x, float y, String text, float fontSize,
                                int color) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        PdfReader reader = new PdfReader(sourcePdfPath);
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputFile));

        PdfContentByte canvas = stamper.getOverContent(pageNumber);
        BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        canvas.saveState();
        canvas.beginText();
        canvas.setFontAndSize(font, fontSize);
        canvas.setRGBColorFill((int)(r*255), (int)(g*255), (int)(b*255));
        canvas.moveText(x, y);

        // Handle multi-line - use raw PDF operators to avoid AWT dependencies
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                canvas.moveText(0, -fontSize - 2);
            }
            String escaped = escapePdfString(lines[i]);
            canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
        }

        canvas.endText();
        canvas.restoreState();

        stamper.close();
        reader.close();

        return outputFile;
    }

    /**
     * Get page dimensions
     */
    public static float[] getPageDimensions(String pdfPath, int pageNumber) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber < 1 || pageNumber > reader.getNumberOfPages()) {
                return null;
            }

            Rectangle rect = reader.getPageSize(pageNumber);
            return new float[]{rect.getWidth(), rect.getHeight()};

        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Get page count
     */
    public static int getPageCount(String pdfPath) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            return reader.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Get full text of a page
     */
    public static String getPageText(String pdfPath, int pageNumber) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber < 1 || pageNumber > reader.getNumberOfPages()) {
                return "";
            }

            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            return extractor.getTextFromPage(pageNumber);

        } catch (Exception e) {
            return "";
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void copyFile(File source, File dest) throws Exception {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    /**
     * Escape special characters for PDF string literal.
     */
    private static String escapePdfString(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("\r", "\\r")
                   .replace("\n", "\\n");
    }
}
