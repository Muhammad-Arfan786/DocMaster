package com.docreader.utils;

import android.graphics.RectF;

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
 * Direct PDF text editor using "Cover and Replace" method.
 * This preserves the original PDF structure while allowing text edits.
 */
public class PdfDirectEditor {

    /**
     * Represents a text edit operation
     */
    public static class TextEdit {
        public int pageNumber;      // 1-based page number
        public String oldText;      // Text to find and replace
        public String newText;      // Replacement text
        public float fontSize;      // Font size for new text (0 = auto-detect)
        public int color;           // Text color (0 = black)

        public TextEdit(int pageNumber, String oldText, String newText) {
            this.pageNumber = pageNumber;
            this.oldText = oldText;
            this.newText = newText;
            this.fontSize = 0; // Auto
            this.color = 0xFF000000; // Black
        }

        public TextEdit(int pageNumber, String oldText, String newText, float fontSize, int color) {
            this.pageNumber = pageNumber;
            this.oldText = oldText;
            this.newText = newText;
            this.fontSize = fontSize;
            this.color = color;
        }
    }

    /**
     * Represents extracted text with its location
     */
    public static class TextBlock {
        public String text;
        public int pageNumber;
        public float x, y, width, height;
        public float fontSize;

        public TextBlock(String text, int pageNumber, float x, float y, float width, float height) {
            this.text = text;
            this.pageNumber = pageNumber;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fontSize = 12f; // Default
        }
    }

    /**
     * Extract all text blocks from a PDF with their positions
     */
    public static List<TextBlock> extractTextBlocks(String pdfPath) throws Exception {
        List<TextBlock> blocks = new ArrayList<>();

        PdfReader reader = new PdfReader(pdfPath);
        try {
            int numPages = reader.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                Rectangle pageSize = reader.getPageSize(pageNum);
                float pageHeight = pageSize.getHeight();

                // Extract text with location info
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                String pageText = extractor.getTextFromPage(pageNum);

                // Split into lines and create blocks
                String[] lines = pageText.split("\n");
                float currentY = pageHeight - 50; // Start from top
                float lineHeight = 14f;

                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        TextBlock block = new TextBlock(
                                line.trim(),
                                pageNum,
                                50, // Default left margin
                                currentY,
                                pageSize.getWidth() - 100,
                                lineHeight
                        );
                        blocks.add(block);
                    }
                    currentY -= lineHeight;
                }
            }
        } finally {
            reader.close();
        }

        return blocks;
    }

    /**
     * Apply text edits to a PDF using cover and replace method.
     * Creates a copy of the original PDF with edits applied.
     */
    public static File applyEdits(String sourcePdfPath, File outputDir, String outputFileName,
                                   List<TextEdit> edits) throws Exception {

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        // First, copy the original PDF
        File tempFile = new File(outputDir, "temp_" + System.currentTimeMillis() + ".pdf");
        copyFile(new File(sourcePdfPath), tempFile);

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(tempFile.getAbsolutePath());
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            for (TextEdit edit : edits) {
                if (edit.pageNumber > 0 && edit.pageNumber <= reader.getNumberOfPages()) {
                    applyEditToPage(reader, stamper, edit, font);
                }
            }
        } finally {
            if (stamper != null) try { stamper.close(); } catch (Exception ignored) {}
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
            // Clean up temp file
            if (tempFile.exists()) tempFile.delete();
        }

        return outputFile;
    }

    /**
     * Apply a single edit to a page using cover and replace
     */
    private static void applyEditToPage(PdfReader reader, PdfStamper stamper, TextEdit edit, BaseFont font) {
        try {
            Rectangle pageSize = reader.getPageSize(edit.pageNumber);
            float pageHeight = pageSize.getHeight();
            float pageWidth = pageSize.getWidth();

            // Find the location of the old text by extracting and searching
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String pageText = extractor.getTextFromPage(edit.pageNumber);

            // Search for text in lines and estimate position
            String[] lines = pageText.split("\n");
            float lineHeight = 14f;
            float margin = 50f;
            float currentY = pageHeight - margin;

            for (String line : lines) {
                if (line.contains(edit.oldText)) {
                    PdfContentByte canvas = stamper.getOverContent(edit.pageNumber);

                    float x = margin;
                    float y = currentY;
                    float width = pageWidth - (2 * margin);
                    float height = lineHeight;

                    // Cover the old text with white rectangle
                    canvas.saveState();
                    canvas.setRGBColorFill(255, 255, 255);
                    canvas.rectangle(x - 2, y - 2, width + 4, height + 4);
                    canvas.fill();
                    canvas.restoreState();

                    // Draw the new text
                    float fontSize = edit.fontSize > 0 ? edit.fontSize : height * 0.8f;
                    if (fontSize < 8) fontSize = 10;
                    if (fontSize > 24) fontSize = 12;

                    int red = (edit.color >> 16) & 0xFF;
                    int green = (edit.color >> 8) & 0xFF;
                    int blue = edit.color & 0xFF;

                    // Replace old text with new in the line
                    String newLine = line.replace(edit.oldText, edit.newText);

                    canvas.saveState();
                    canvas.beginText();
                    canvas.setFontAndSize(font, fontSize);
                    canvas.setRGBColorFill(red, green, blue);
                    canvas.moveText(x, y + 2);
                    // Use raw PDF operators to avoid AWT dependencies
                    String escaped = escapePdfString(newLine);
                    canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                    canvas.endText();
                    canvas.restoreState();

                    break; // Only replace first occurrence per edit
                }

                if (line.trim().isEmpty()) {
                    currentY -= lineHeight * 0.5f;
                } else {
                    currentY -= lineHeight;
                }
            }
        } catch (Exception e) {
            AppLogger.e("PdfDirectEditor", "Error", e);
        }
    }

    /**
     * Replace text in PDF and save to original location (overwrite)
     */
    public static void applyEditsToOriginal(String pdfPath, List<TextEdit> edits) throws Exception {
        File pdfFile = new File(pdfPath);
        File parentDir = pdfFile.getParentFile();
        String tempName = "edited_" + System.currentTimeMillis() + ".pdf";

        // Apply edits to temp file
        File editedFile = applyEdits(pdfPath, parentDir, tempName, edits);

        // Replace original with edited
        if (pdfFile.delete()) {
            editedFile.renameTo(pdfFile);
        } else {
            // If delete fails, copy content
            copyFile(editedFile, pdfFile);
            editedFile.delete();
        }
    }

    /**
     * Find and replace all occurrences of text in entire PDF
     */
    public static File findAndReplaceAll(String sourcePdfPath, File outputDir, String outputFileName,
                                          String findText, String replaceText) throws Exception {
        List<TextEdit> edits = new ArrayList<>();

        PdfReader reader = new PdfReader(sourcePdfPath);
        try {
            int numPages = reader.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                String pageText = extractor.getTextFromPage(pageNum);

                if (pageText.contains(findText)) {
                    edits.add(new TextEdit(pageNum, findText, replaceText));
                }
            }
        } finally {
            reader.close();
        }

        if (edits.isEmpty()) {
            // No matches found, just copy the file
            File outputFile = new File(outputDir, outputFileName);
            copyFile(new File(sourcePdfPath), outputFile);
            return outputFile;
        }

        return applyEdits(sourcePdfPath, outputDir, outputFileName, edits);
    }

    /**
     * Add new text at specific position (doesn't replace existing text)
     */
    public static File addText(String sourcePdfPath, File outputDir, String outputFileName,
                                int pageNumber, float x, float y, String text,
                                float fontSize, int color) throws Exception {

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

        if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
            PdfContentByte canvas = stamper.getOverContent(pageNumber);
            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            canvas.saveState();
            canvas.beginText();
            canvas.setFontAndSize(font, fontSize);
            canvas.setRGBColorFill(red, green, blue);
            canvas.moveText(x, y);
            // Use raw PDF operators to avoid AWT dependencies
            String escaped = escapePdfString(text);
            canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
            canvas.endText();
            canvas.restoreState();
        }

        stamper.close();
        reader.close();

        return outputFile;
    }

    /**
     * Get page count of PDF
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
     * Get text content of a specific page
     */
    public static String getPageText(String pdfPath, int pageNumber) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);
            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                return extractor.getTextFromPage(pageNumber);
            }
        } catch (Exception e) {
            AppLogger.e("PdfDirectEditor", "Error", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return "";
    }

    /**
     * Copy file helper
     */
    private static void copyFile(File source, File dest) throws Exception {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
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
