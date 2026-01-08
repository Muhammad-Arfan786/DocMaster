package com.docreader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

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
import com.itextpdf.kernel.pdf.canvas.parser.listener.IPdfTextLocation;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

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

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {

            int numPages = doc.getNumberOfPages();

            for (int pageNum = 1; pageNum <= numPages; pageNum++) {
                PdfPage page = doc.getPage(pageNum);
                String pageText = PdfTextExtractor.getTextFromPage(page);

                // Split into lines
                String[] lines = pageText.split("\n");
                Rectangle pageSize = page.getPageSize();
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

                    // Try to find exact position using regex search
                    float x = margin;
                    float y = currentY;
                    float width = pageWidth - (2 * margin);
                    float height = lineHeight;

                    // Search for this text to get accurate position
                    try {
                        RegexBasedLocationExtractionStrategy strategy =
                                new RegexBasedLocationExtractionStrategy(Pattern.quote(line.trim()));
                        com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor processor =
                                new com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(strategy);
                        processor.processPageContent(page);

                        Collection<IPdfTextLocation> locations = strategy.getResultantLocations();
                        if (!locations.isEmpty()) {
                            IPdfTextLocation loc = locations.iterator().next();
                            Rectangle rect = loc.getRectangle();
                            x = rect.getX();
                            y = rect.getY();
                            width = rect.getWidth();
                            height = rect.getHeight();
                        }
                    } catch (Exception e) {
                        // Use estimated position
                    }

                    TextBlock block = new TextBlock(line.trim(), pageNum, x, y, width, height);
                    blocks.add(block);

                    currentY -= lineHeight;
                }
            }

        } catch (Exception e) {
            AppLogger.e("VisualPdfEditor", "Error", e);
        }

        return blocks;
    }

    /**
     * Extract text blocks for a specific page
     */
    public static List<TextBlock> extractTextBlocksForPage(String pdfPath, int pageNumber) {
        List<TextBlock> blocks = new ArrayList<>();

        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {

            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                return blocks;
            }

            PdfPage page = doc.getPage(pageNumber);
            String pageText = PdfTextExtractor.getTextFromPage(page);
            Rectangle pageSize = page.getPageSize();
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

                // Try exact position
                try {
                    RegexBasedLocationExtractionStrategy strategy =
                            new RegexBasedLocationExtractionStrategy(Pattern.quote(line.trim()));
                    com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor processor =
                            new com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(strategy);
                    processor.processPageContent(page);

                    Collection<IPdfTextLocation> locations = strategy.getResultantLocations();
                    if (!locations.isEmpty()) {
                        IPdfTextLocation loc = locations.iterator().next();
                        Rectangle rect = loc.getRectangle();
                        x = rect.getX();
                        y = rect.getY();
                        width = rect.getWidth();
                        height = rect.getHeight();
                    }
                } catch (Exception e) {
                    // Use estimated
                }

                TextBlock block = new TextBlock(line.trim(), pageNumber, x, y, width, height);
                blocks.add(block);
                currentY -= lineHeight;
            }

        } catch (Exception e) {
            AppLogger.e("VisualPdfEditor", "Error", e);
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

            // Apply this edit
            try (PdfReader reader = new PdfReader(outputFile);
                 PdfWriter writer = new PdfWriter(new FileOutputStream(tempFile));
                 PdfDocument doc = new PdfDocument(reader, writer)) {

                PdfPage page = doc.getPage(block.pageNumber);
                PdfCanvas canvas = new PdfCanvas(page);
                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                // Cover old text with white
                canvas.saveState();
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.rectangle(block.pdfX - 2, block.pdfY - 2,
                        block.pdfWidth + 4, block.pdfHeight + 4);
                canvas.fill();
                canvas.restoreState();

                // Write new text
                canvas.saveState();
                canvas.beginText();
                canvas.setFontAndSize(font, block.fontSize);
                canvas.setFillColor(ColorConstants.BLACK);
                canvas.moveText(block.pdfX, block.pdfY + 2);
                canvas.showText(block.newText);
                canvas.endText();
                canvas.restoreState();
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

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument doc = new PdfDocument(reader, writer)) {

            PdfPage page = doc.getPage(pageNumber);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Find text location
            RegexBasedLocationExtractionStrategy strategy =
                    new RegexBasedLocationExtractionStrategy(Pattern.quote(findText));
            com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor processor =
                    new com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(strategy);
            processor.processPageContent(page);

            Collection<IPdfTextLocation> locations = strategy.getResultantLocations();

            if (!locations.isEmpty()) {
                PdfCanvas canvas = new PdfCanvas(page);

                for (IPdfTextLocation location : locations) {
                    Rectangle rect = location.getRectangle();

                    // Cover with white
                    canvas.saveState();
                    canvas.setFillColor(ColorConstants.WHITE);
                    canvas.rectangle(rect.getX() - 2, rect.getY() - 2,
                            rect.getWidth() + 4, rect.getHeight() + 4);
                    canvas.fill();
                    canvas.restoreState();

                    // Write replacement
                    float fontSize = Math.max(10, rect.getHeight() * 0.8f);
                    canvas.saveState();
                    canvas.beginText();
                    canvas.setFontAndSize(font, fontSize);
                    canvas.setFillColor(ColorConstants.BLACK);
                    canvas.moveText(rect.getX(), rect.getY() + 2);
                    canvas.showText(replaceText);
                    canvas.endText();
                    canvas.restoreState();
                }
            }
        }

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

        try (PdfReader reader = new PdfReader(sourcePdfPath);
             PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
             PdfDocument doc = new PdfDocument(reader, writer)) {

            PdfPage page = doc.getPage(pageNumber);
            PdfCanvas canvas = new PdfCanvas(page);
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            canvas.saveState();
            canvas.beginText();
            canvas.setFontAndSize(font, fontSize);
            canvas.setFillColor(new DeviceRgb((int)(r*255), (int)(g*255), (int)(b*255)));
            canvas.moveText(x, y);

            // Handle multi-line
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    canvas.moveText(0, -fontSize - 2);
                }
                canvas.showText(lines[i]);
            }

            canvas.endText();
            canvas.restoreState();
        }

        return outputFile;
    }

    /**
     * Get page dimensions
     */
    public static float[] getPageDimensions(String pdfPath, int pageNumber) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {

            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                return null;
            }

            PdfPage page = doc.getPage(pageNumber);
            Rectangle rect = page.getPageSize();
            return new float[]{rect.getWidth(), rect.getHeight()};

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get page count
     */
    public static int getPageCount(String pdfPath) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get full text of a page
     */
    public static String getPageText(String pdfPath, int pageNumber) {
        try (PdfReader reader = new PdfReader(pdfPath);
             PdfDocument doc = new PdfDocument(reader)) {

            if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                return "";
            }

            return PdfTextExtractor.getTextFromPage(doc.getPage(pageNumber));

        } catch (Exception e) {
            return "";
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
}
