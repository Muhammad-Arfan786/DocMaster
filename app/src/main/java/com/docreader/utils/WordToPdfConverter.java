package com.docreader.utils;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to convert text/Word content back to PDF format.
 * Uses OpenPDF to create PDF documents from text content.
 * Preserves line breaks and page structure.
 */
public class WordToPdfConverter {

    /**
     * Convert text content to PDF file preserving line breaks.
     */
    public static File convertToPdf(String text, File outputDir, String fileName) throws Exception {
        return convertToPdfWithOptions(text, outputDir, fileName, 11f, 36f);
    }

    /**
     * Convert text to PDF with custom options
     */
    public static File convertToPdfWithOptions(String text, File outputDir, String fileName,
                                                float fontSize, float margin) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = fileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File pdfFile = new File(outputDir, pdfFileName);

        Document document = null;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(pdfFile);
            // Use Constants to avoid PageSize class (requires java.awt.Color not available on Android)
            Rectangle a4Size = new Rectangle(Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4);
            document = new Document(a4Size, margin, margin, margin, margin);
            PdfWriter.getInstance(document, fos);
            document.open();

            // Use a standard font that supports more characters
            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            Font font = new Font(baseFont, fontSize);

            // Check if text has page markers
            if (text.contains("[PAGE:")) {
                // Parse structured text with page markers
                convertStructuredText(document, text, font, fontSize);
            } else if (text.contains("=== PAGE ")) {
                // Parse text with visual page markers
                convertWithPageMarkers(document, text, font, fontSize);
            } else {
                // Simple text conversion - preserve each line
                convertSimpleText(document, text, font, fontSize);
            }
        } finally {
            if (document != null && document.isOpen()) {
                try { document.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        return pdfFile;
    }

    /**
     * Convert structured text with [PAGE:n] markers
     */
    private static void convertStructuredText(Document document, String text, Font font, float fontSize) throws Exception {
        String[] parts = text.split("\\[PAGE:\\d+\\]");

        boolean firstPage = true;

        // Process all parts including content before first marker (index 0)
        for (int i = 0; i < parts.length; i++) {
            String pageContent = parts[i].trim();
            if (pageContent.isEmpty()) continue;

            if (!firstPage) {
                document.newPage();
            }
            firstPage = false;

            addTextPreservingLines(document, pageContent, font, fontSize);
        }

        // Handle case where text is empty after processing
        if (firstPage && !text.trim().isEmpty()) {
            String content = text.replaceAll("\\[PAGE:\\d+\\]", "").trim();
            if (!content.isEmpty()) {
                addTextPreservingLines(document, content, font, fontSize);
            }
        }
    }

    /**
     * Convert text with visual === PAGE n === markers
     */
    private static void convertWithPageMarkers(Document document, String text, Font font, float fontSize) throws Exception {
        String[] pages = text.split("=== PAGE \\d+ ===");

        boolean firstPage = true;
        for (String pageContent : pages) {
            if (pageContent.trim().isEmpty()) continue;

            if (!firstPage) {
                document.newPage();
            }
            firstPage = false;

            addTextPreservingLines(document, pageContent.trim(), font, fontSize);
        }
    }

    /**
     * Simple text conversion preserving line breaks
     */
    private static void convertSimpleText(Document document, String text, Font font, float fontSize) throws Exception {
        addTextPreservingLines(document, text, font, fontSize);
    }

    /**
     * Add text to document preserving line breaks
     */
    private static void addTextPreservingLines(Document document, String text, Font font, float fontSize) throws Exception {
        String[] lines = text.split("\n");

        for (String line : lines) {
            Paragraph paragraph = new Paragraph();
            paragraph.setFont(font);
            paragraph.setAlignment(Element.ALIGN_LEFT);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(2);

            if (line.trim().isEmpty()) {
                // Add small spacing for empty lines
                paragraph.add(" ");
                paragraph.setSpacingAfter(fontSize * 0.5f);
            } else {
                paragraph.add(line);
            }

            document.add(paragraph);
        }
    }

    /**
     * Convert text content to PDF and return the file path.
     */
    public static String convertToPdf(String text, File outputDir, String fileName, boolean returnPath) throws Exception {
        File pdfFile = convertToPdf(text, outputDir, fileName);
        return pdfFile.getAbsolutePath();
    }

    /**
     * Convert text content to PDF with custom font size.
     */
    public static File convertToPdfWithFontSize(String text, File outputDir, String fileName, float fontSize) throws Exception {
        return convertToPdfWithOptions(text, outputDir, fileName, fontSize, 36f);
    }

    /**
     * Convert to PDF matching original page structure
     * Takes the original PDF path to get page sizes
     */
    public static File convertToPdfMatchingOriginal(String text, String originalPdfPath,
                                                     File outputDir, String fileName) throws Exception {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String pdfFileName = fileName;
        if (!pdfFileName.toLowerCase().endsWith(".pdf")) {
            pdfFileName = pdfFileName + ".pdf";
        }
        File pdfFile = new File(outputDir, pdfFileName);

        // Get original PDF page sizes
        List<Rectangle> pageSizes = new ArrayList<>();
        PdfReader originalReader = null;
        try {
            originalReader = new PdfReader(originalPdfPath);
            for (int i = 1; i <= originalReader.getNumberOfPages(); i++) {
                Rectangle rect = originalReader.getPageSize(i);
                pageSizes.add(rect);
            }
        } catch (Exception e) {
            // If we can't read original, use A4 (avoid PageSize class - uses java.awt.Color)
            pageSizes.add(new Rectangle(Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4));
        } finally {
            if (originalReader != null) {
                try { originalReader.close(); } catch (Exception ignored) {}
            }
        }

        Document document = null;
        FileOutputStream fos = null;
        try {
            Rectangle defaultA4 = new Rectangle(Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4);
            Rectangle firstPageSize = pageSizes.isEmpty() ? defaultA4 : pageSizes.get(0);
            fos = new FileOutputStream(pdfFile);
            document = new Document(firstPageSize, 36, 36, 36, 36);
            PdfWriter.getInstance(document, fos);
            document.open();

            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            Font font = new Font(baseFont, 11f);
            float fontSize = 11f;

            // Parse pages from text
            if (text.contains("[PAGE:")) {
                String[] parts = text.split("\\[PAGE:\\d+\\]");
                int pageIndex = 0;

                for (int i = 1; i < parts.length; i++) {
                    if (pageIndex > 0) {
                        // Use original page size if available
                        Rectangle pageSize = pageIndex < pageSizes.size() ?
                                pageSizes.get(pageIndex) : defaultA4;
                        document.setPageSize(pageSize);
                        document.newPage();
                    }

                    String pageContent = parts[i].trim();
                    addTextPreservingLines(document, pageContent, font, fontSize);
                    pageIndex++;
                }
            } else {
                addTextPreservingLines(document, text, font, fontSize);
            }
        } finally {
            if (document != null && document.isOpen()) {
                try { document.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        return pdfFile;
    }
}
