package com.docreader.utils;

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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for adding text directly to PDF files.
 * Allows users to add text annotations at specific positions on PDF pages.
 */
public class PdfTextEditor {

    /**
     * Represents a text annotation to be added to a PDF
     */
    public static class TextAnnotation {
        public int pageNumber;      // 1-based page number
        public float x;             // X position (from left)
        public float y;             // Y position (from bottom in PDF coordinates)
        public String text;
        public float fontSize;
        public int color;           // Android color int

        public TextAnnotation(int pageNumber, float x, float y, String text, float fontSize, int color) {
            this.pageNumber = pageNumber;
            this.x = x;
            this.y = y;
            this.text = text;
            this.fontSize = fontSize;
            this.color = color;
        }
    }

    /**
     * Add text annotations to a PDF file and save to a new file.
     *
     * @param sourcePdfPath Path to the source PDF file
     * @param outputDir     Directory to save the output file
     * @param outputFileName Name for the output file
     * @param annotations   List of text annotations to add
     * @return The created PDF file with annotations
     * @throws Exception if operation fails
     */
    public static File addTextToPdf(String sourcePdfPath, File outputDir, String outputFileName,
                                     List<TextAnnotation> annotations) throws Exception {

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

            for (TextAnnotation annotation : annotations) {
                if (annotation.pageNumber > 0 && annotation.pageNumber <= pdfDocument.getNumberOfPages()) {
                    PdfPage page = pdfDocument.getPage(annotation.pageNumber);
                    Rectangle pageSize = page.getPageSize();

                    PdfCanvas pdfCanvas = new PdfCanvas(page);

                    // Convert Android color to iText color
                    int red = (annotation.color >> 16) & 0xFF;
                    int green = (annotation.color >> 8) & 0xFF;
                    int blue = annotation.color & 0xFF;
                    DeviceRgb color = new DeviceRgb(red, green, blue);

                    // Create canvas for text
                    try (Canvas canvas = new Canvas(pdfCanvas, pageSize)) {
                        Paragraph paragraph = new Paragraph(annotation.text)
                                .setFont(font)
                                .setFontSize(annotation.fontSize)
                                .setFontColor(color);

                        // Position text at the specified coordinates
                        canvas.showTextAligned(paragraph, annotation.x, annotation.y, TextAlignment.LEFT);
                    }
                }
            }
        }

        return outputFile;
    }

    /**
     * Add a single text annotation to a PDF.
     *
     * @param sourcePdfPath Path to the source PDF file
     * @param outputDir     Directory to save the output file
     * @param outputFileName Name for the output file
     * @param pageNumber    Page number (1-based)
     * @param x             X position
     * @param y             Y position (PDF coordinates - from bottom)
     * @param text          Text to add
     * @param fontSize      Font size
     * @param color         Text color (Android color int)
     * @return The created PDF file
     * @throws Exception if operation fails
     */
    public static File addSingleText(String sourcePdfPath, File outputDir, String outputFileName,
                                      int pageNumber, float x, float y, String text,
                                      float fontSize, int color) throws Exception {
        List<TextAnnotation> annotations = new ArrayList<>();
        annotations.add(new TextAnnotation(pageNumber, x, y, text, fontSize, color));
        return addTextToPdf(sourcePdfPath, outputDir, outputFileName, annotations);
    }

    /**
     * Get the page dimensions of a PDF page.
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
