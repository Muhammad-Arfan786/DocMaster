package com.docreader.utils;

import android.graphics.RectF;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Utility class for editing PDFs using the "Cover & Replace" method.
 * This covers selected areas with white and adds new text on top,
 * keeping the original PDF format intact.
 */
public class PdfCoverReplace {

    /**
     * Represents an edit operation - cover area and add replacement text
     */
    public static class EditOperation {
        public int pageNumber;      // 1-based page number
        public float x;             // X position (PDF coordinates - from left)
        public float y;             // Y position (PDF coordinates - from bottom)
        public float width;         // Width of area to cover
        public float height;        // Height of area to cover
        public String newText;      // Replacement text
        public float fontSize;      // Font size for new text
        public int textColor;       // Text color (Android color int)

        public EditOperation(int pageNumber, float x, float y, float width, float height,
                             String newText, float fontSize, int textColor) {
            this.pageNumber = pageNumber;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.newText = newText;
            this.fontSize = fontSize;
            this.textColor = textColor;
        }
    }

    /**
     * Apply cover & replace edits to a PDF file.
     *
     * @param sourcePdfPath  Path to the source PDF file
     * @param outputDir      Directory to save the output file
     * @param outputFileName Name for the output file
     * @param edits          List of edit operations to apply
     * @return The edited PDF file
     * @throws Exception if operation fails
     */
    public static File applyEdits(String sourcePdfPath, File outputDir, String outputFileName,
                                   List<EditOperation> edits) throws Exception {

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = outputFileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File outputFile = new File(outputDir, pdfFileName);

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            for (EditOperation edit : edits) {
                if (edit.pageNumber > 0 && edit.pageNumber <= reader.getNumberOfPages()) {
                    PdfContentByte canvas = stamper.getOverContent(edit.pageNumber);

                    // Step 1: Draw white rectangle to cover existing content
                    canvas.saveState();
                    canvas.setRGBColorFill(255, 255, 255);
                    canvas.rectangle(edit.x, edit.y, edit.width, edit.height);
                    canvas.fill();
                    canvas.restoreState();

                    // Step 2: Add new text on top of white area
                    if (edit.newText != null && !edit.newText.isEmpty()) {
                        // Convert Android color to RGB components
                        int red = (edit.textColor >> 16) & 0xFF;
                        int green = (edit.textColor >> 8) & 0xFF;
                        int blue = edit.textColor & 0xFF;

                        // Calculate text position (from top of covered area with padding)
                        float textX = edit.x + 2; // Small padding from left
                        float textY = edit.y + edit.height - edit.fontSize - 2; // From top with padding

                        canvas.saveState();
                        canvas.beginText();
                        canvas.setFontAndSize(font, edit.fontSize);
                        canvas.setRGBColorFill(red, green, blue);
                        canvas.setTextMatrix(textX, textY);

                        // Handle multi-line text - use raw PDF operators to avoid AWT dependencies
                        String[] lines = edit.newText.split("\n");
                        float lineHeight = edit.fontSize + 2;
                        for (int i = 0; i < lines.length; i++) {
                            if (i > 0) {
                                textY -= lineHeight;
                                canvas.setTextMatrix(textX, textY);
                            }
                            String escaped = escapePdfString(lines[i]);
                            canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                        }

                        canvas.endText();
                        canvas.restoreState();
                    }
                }
            }
        } finally {
            if (stamper != null) try { stamper.close(); } catch (Exception ignored) {}
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
        }

        return outputFile;
    }

    /**
     * Apply a single cover & replace edit.
     *
     * @param sourcePdfPath  Path to the source PDF file
     * @param outputDir      Directory to save the output file
     * @param outputFileName Name for the output file
     * @param pageNumber     Page number (1-based)
     * @param x              X position (PDF coordinates)
     * @param y              Y position (PDF coordinates)
     * @param width          Width of area to cover
     * @param height         Height of area to cover
     * @param newText        Replacement text
     * @param fontSize       Font size
     * @param textColor      Text color (Android color int)
     * @return The edited PDF file
     * @throws Exception if operation fails
     */
    public static File applySingleEdit(String sourcePdfPath, File outputDir, String outputFileName,
                                        int pageNumber, float x, float y, float width, float height,
                                        String newText, float fontSize, int textColor) throws Exception {
        java.util.ArrayList<EditOperation> edits = new java.util.ArrayList<>();
        edits.add(new EditOperation(pageNumber, x, y, width, height, newText, fontSize, textColor));
        return applyEdits(sourcePdfPath, outputDir, outputFileName, edits);
    }

    /**
     * Get page dimensions of a PDF page.
     *
     * @param pdfPath    Path to the PDF file
     * @param pageNumber Page number (1-based)
     * @return float array [width, height] or null if error
     */
    public static float[] getPageDimensions(String pdfPath, int pageNumber) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                Rectangle pageSize = reader.getPageSize(pageNumber);
                return new float[]{pageSize.getWidth(), pageSize.getHeight()};
            }
        } catch (Exception e) {
            AppLogger.e("PdfCoverReplace", "Error getting page dimensions", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return null;
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
