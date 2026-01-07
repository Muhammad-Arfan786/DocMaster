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
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IPdfTextLocation;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

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

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numPages = pdfDocument.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                PdfPage page = pdfDocument.getPage(pageNum);
                Rectangle pageSize = page.getPageSize();
                float pageHeight = pageSize.getHeight();

                // Extract text with location info
                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(page, strategy);

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

        try (PdfReader reader = new PdfReader(tempFile);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            for (TextEdit edit : edits) {
                if (edit.pageNumber > 0 && edit.pageNumber <= pdfDocument.getNumberOfPages()) {
                    applyEditToPage(pdfDocument, edit, font);
                }
            }
        }

        // Clean up temp file
        tempFile.delete();

        return outputFile;
    }

    /**
     * Apply a single edit to a page using cover and replace
     */
    private static void applyEditToPage(PdfDocument pdfDocument, TextEdit edit, PdfFont font) {
        try {
            PdfPage page = pdfDocument.getPage(edit.pageNumber);
            Rectangle pageSize = page.getPageSize();

            // Find the location of the old text using regex strategy
            RegexBasedLocationExtractionStrategy strategy =
                    new RegexBasedLocationExtractionStrategy(Pattern.quote(edit.oldText));

            // Use PdfCanvasProcessor to process the page with our strategy
            com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor processor =
                    new com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(strategy);
            processor.processPageContent(page);

            Collection<IPdfTextLocation> locations = strategy.getResultantLocations();

            if (!locations.isEmpty()) {
                PdfCanvas pdfCanvas = new PdfCanvas(page);

                for (IPdfTextLocation location : locations) {
                    Rectangle rect = location.getRectangle();

                    // Cover the old text with white rectangle
                    pdfCanvas.saveState();
                    pdfCanvas.setFillColor(ColorConstants.WHITE);
                    pdfCanvas.rectangle(rect.getX() - 2, rect.getY() - 2,
                            rect.getWidth() + 4, rect.getHeight() + 4);
                    pdfCanvas.fill();
                    pdfCanvas.restoreState();

                    // Draw the new text
                    float fontSize = edit.fontSize > 0 ? edit.fontSize : rect.getHeight() * 0.8f;
                    if (fontSize < 8) fontSize = 10;
                    if (fontSize > 24) fontSize = 12;

                    int red = (edit.color >> 16) & 0xFF;
                    int green = (edit.color >> 8) & 0xFF;
                    int blue = edit.color & 0xFF;
                    DeviceRgb color = new DeviceRgb(red, green, blue);

                    pdfCanvas.saveState();
                    pdfCanvas.beginText();
                    pdfCanvas.setFontAndSize(font, fontSize);
                    pdfCanvas.setFillColor(color);
                    pdfCanvas.moveText(rect.getX(), rect.getY() + 2);
                    pdfCanvas.showText(edit.newText);
                    pdfCanvas.endText();
                    pdfCanvas.restoreState();

                    break; // Only replace first occurrence per edit
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numPages = pdfDocument.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                PdfPage page = pdfDocument.getPage(pageNum);
                String pageText = PdfTextExtractor.getTextFromPage(page);

                if (pageText.contains(findText)) {
                    edits.add(new TextEdit(pageNum, findText, replaceText));
                }
            }
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

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument pdfDocument = new PdfDocument(reader, writer)) {

            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                PdfPage page = pdfDocument.getPage(pageNumber);
                PdfCanvas pdfCanvas = new PdfCanvas(page);
                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;
                DeviceRgb pdfColor = new DeviceRgb(red, green, blue);

                pdfCanvas.saveState();
                pdfCanvas.beginText();
                pdfCanvas.setFontAndSize(font, fontSize);
                pdfCanvas.setFillColor(pdfColor);
                pdfCanvas.moveText(x, y);
                pdfCanvas.showText(text);
                pdfCanvas.endText();
                pdfCanvas.restoreState();
            }
        }

        return outputFile;
    }

    /**
     * Get page count of PDF
     */
    public static int getPageCount(String pdfPath) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {
            return pdfDocument.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get text content of a specific page
     */
    public static String getPageText(String pdfPath, int pageNumber) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument pdfDocument = new PdfDocument(reader)) {
            if (pageNumber > 0 && pageNumber <= pdfDocument.getNumberOfPages()) {
                return PdfTextExtractor.getTextFromPage(pdfDocument.getPage(pageNumber));
            }
        } catch (Exception e) {
            e.printStackTrace();
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
}
