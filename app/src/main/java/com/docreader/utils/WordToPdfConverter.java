package com.docreader.utils;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to convert text/Word content back to PDF format.
 * Uses iText7 to create PDF documents from text content.
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

        try (FileOutputStream fos = new FileOutputStream(pdfFile);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument, PageSize.A4)) {

            document.setMargins(margin, margin, margin, margin);

            // Use a standard font that supports more characters
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

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
        }

        return pdfFile;
    }

    /**
     * Convert structured text with [PAGE:n] markers
     */
    private static void convertStructuredText(Document document, String text, PdfFont font, float fontSize) {
        Pattern pagePattern = Pattern.compile("\\[PAGE:(\\d+)\\]");
        String[] parts = text.split("\\[PAGE:\\d+\\]");

        boolean firstPage = true;
        for (int i = 1; i < parts.length; i++) {
            if (!firstPage) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
            firstPage = false;

            String pageContent = parts[i].trim();
            addTextPreservingLines(document, pageContent, font, fontSize);
        }

        // Handle case where there are no page markers (single page or no markers)
        if (parts.length <= 1 && !text.trim().isEmpty()) {
            String content = text.replaceAll("\\[PAGE:\\d+\\]", "").trim();
            addTextPreservingLines(document, content, font, fontSize);
        }
    }

    /**
     * Convert text with visual === PAGE n === markers
     */
    private static void convertWithPageMarkers(Document document, String text, PdfFont font, float fontSize) {
        String[] pages = text.split("=== PAGE \\d+ ===");

        boolean firstPage = true;
        for (String pageContent : pages) {
            if (pageContent.trim().isEmpty()) continue;

            if (!firstPage) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
            firstPage = false;

            addTextPreservingLines(document, pageContent.trim(), font, fontSize);
        }
    }

    /**
     * Simple text conversion preserving line breaks
     */
    private static void convertSimpleText(Document document, String text, PdfFont font, float fontSize) {
        addTextPreservingLines(document, text, font, fontSize);
    }

    /**
     * Add text to document preserving line breaks
     */
    private static void addTextPreservingLines(Document document, String text, PdfFont font, float fontSize) {
        String[] lines = text.split("\n");

        for (String line : lines) {
            Paragraph paragraph = new Paragraph()
                    .setFont(font)
                    .setFontSize(fontSize)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setMarginTop(0)
                    .setMarginBottom(2);

            if (line.trim().isEmpty()) {
                // Add small spacing for empty lines
                paragraph.add(" ");
                paragraph.setMarginBottom(fontSize * 0.5f);
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
        List<PageSize> pageSizes = new ArrayList<>();
        try (com.itextpdf.kernel.pdf.PdfReader reader = new com.itextpdf.kernel.pdf.PdfReader(originalPdfPath);
             PdfDocument originalDoc = new PdfDocument(reader)) {
            for (int i = 1; i <= originalDoc.getNumberOfPages(); i++) {
                com.itextpdf.kernel.geom.Rectangle rect = originalDoc.getPage(i).getPageSize();
                pageSizes.add(new PageSize(rect));
            }
        } catch (Exception e) {
            // If we can't read original, use A4
            pageSizes.add(PageSize.A4);
        }

        try (FileOutputStream fos = new FileOutputStream(pdfFile);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdfDocument = new PdfDocument(writer)) {

            PageSize firstPageSize = pageSizes.isEmpty() ? PageSize.A4 : pageSizes.get(0);
            Document document = new Document(pdfDocument, firstPageSize);
            document.setMargins(36, 36, 36, 36);

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            float fontSize = 11f;

            // Parse pages from text
            if (text.contains("[PAGE:")) {
                String[] parts = text.split("\\[PAGE:\\d+\\]");
                int pageIndex = 0;

                for (int i = 1; i < parts.length; i++) {
                    if (pageIndex > 0) {
                        // Use original page size if available
                        PageSize pageSize = pageIndex < pageSizes.size() ?
                                pageSizes.get(pageIndex) : PageSize.A4;
                        document.add(new AreaBreak(pageSize));
                    }

                    String pageContent = parts[i].trim();
                    addTextPreservingLines(document, pageContent, font, fontSize);
                    pageIndex++;
                }
            } else {
                addTextPreservingLines(document, text, font, fontSize);
            }

            document.close();
        }

        return pdfFile;
    }
}
