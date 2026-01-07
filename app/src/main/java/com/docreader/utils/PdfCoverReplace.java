package com.docreader.utils;

import android.graphics.RectF;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;

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

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            for (EditOperation edit : edits) {
                if (edit.pageNumber > 0 && edit.pageNumber <= pdfDocument.getNumberOfPages()) {
                    PdfPage page = pdfDocument.getPage(edit.pageNumber);
                    PdfCanvas pdfCanvas = new PdfCanvas(page);

                    // Step 1: Draw white rectangle to cover existing content
                    pdfCanvas.saveState();
                    pdfCanvas.setFillColor(ColorConstants.WHITE);
                    pdfCanvas.rectangle(edit.x, edit.y, edit.width, edit.height);
                    pdfCanvas.fill();
                    pdfCanvas.restoreState();

                    // Step 2: Add new text on top of white area
                    if (edit.newText != null && !edit.newText.isEmpty()) {
                        // Convert Android color to iText color
                        int red = (edit.textColor >> 16) & 0xFF;
                        int green = (edit.textColor >> 8) & 0xFF;
                        int blue = edit.textColor & 0xFF;
                        DeviceRgb textColor = new DeviceRgb(red, green, blue);

                        // Calculate text position (centered in the covered area)
                        float textX = edit.x + 2; // Small padding from left
                        float textY = edit.y + edit.height - edit.fontSize - 2; // From top with padding

                        // Create bounded area for text
                        Rectangle textArea = new Rectangle(edit.x, edit.y, edit.width, edit.height);

                        try (Canvas canvas = new Canvas(pdfCanvas, textArea)) {
                            Paragraph paragraph = new Paragraph(edit.newText)
                                    .setFont(font)
                                    .setFontSize(edit.fontSize)
                                    .setFontColor(textColor)
                                    .setMargin(2);

                            canvas.add(paragraph);
                        }
                    }
                }
            }
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
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                Rectangle pageSize = page.getPageSize();
                return new float[]{pageSize.getWidth(), pageSize.getHeight()};
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
