package com.docreader.utils;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

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

        PdfReader reader = null;
        PdfStamper stamper = null;
        FileOutputStream fos = null;

        try {
            reader = new PdfReader(sourcePdfPath);
            fos = new FileOutputStream(outputFile);
            stamper = new PdfStamper(reader, fos);

            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            for (TextAnnotation annotation : annotations) {
                if (annotation.pageNumber > 0 && annotation.pageNumber <= reader.getNumberOfPages()) {
                    PdfContentByte canvas = stamper.getOverContent(annotation.pageNumber);

                    // Convert Android color to RGB components
                    int red = (annotation.color >> 16) & 0xFF;
                    int green = (annotation.color >> 8) & 0xFF;
                    int blue = annotation.color & 0xFF;

                    canvas.saveState();
                    canvas.beginText();
                    canvas.setFontAndSize(font, annotation.fontSize);
                    canvas.setRGBColorFill(red, green, blue);
                    canvas.setTextMatrix(annotation.x, annotation.y);
                    // Use raw PDF operators to avoid AWT dependencies
                    String escaped = escapePdfString(annotation.text);
                    canvas.getInternalBuffer().append('(').append(escaped).append(") Tj\n");
                    canvas.endText();
                    canvas.restoreState();
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
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfPath);

            if (pageNumber > 0 && pageNumber <= reader.getNumberOfPages()) {
                Rectangle pageSize = reader.getPageSize(pageNumber);
                return new float[]{pageSize.getWidth(), pageSize.getHeight()};
            }
        } catch (Exception e) {
            AppLogger.e("PdfTextEditor", "Error", e);
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
