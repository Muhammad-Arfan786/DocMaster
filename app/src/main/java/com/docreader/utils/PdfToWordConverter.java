package com.docreader.utils;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to convert PDF documents to Word (DOCX) format.
 * Extracts text from PDF preserving line structure for easy editing.
 */
public class PdfToWordConverter {

    /**
     * Data class to hold page text content
     */
    public static class PageContent {
        public int pageNumber;
        public List<String> lines;

        public PageContent(int pageNumber) {
            this.pageNumber = pageNumber;
            this.lines = new ArrayList<>();
        }
    }

    /**
     * Convert a PDF file to DOCX format preserving line structure.
     */
    public static File convertToDocx(File pdfFile, File outputDir) throws Exception {
        String baseName = pdfFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        String docxFileName = baseName + ".docx";
        File docxFile = new File(outputDir, docxFileName);

        // Extract text from PDF page by page
        List<PageContent> pages = extractPagesContent(pdfFile);

        // Create Word document
        try (XWPFDocument document = new XWPFDocument()) {
            for (int i = 0; i < pages.size(); i++) {
                PageContent page = pages.get(i);

                // Add page header
                if (pages.size() > 1) {
                    XWPFParagraph headerPara = document.createParagraph();
                    XWPFRun headerRun = headerPara.createRun();
                    headerRun.setBold(true);
                    headerRun.setText("=== PAGE " + page.pageNumber + " ===");
                    headerRun.addBreak();
                }

                // Add each line as text
                for (String line : page.lines) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(line);
                }

                // Add page break between pages (except last)
                if (i < pages.size() - 1) {
                    XWPFParagraph breakPara = document.createParagraph();
                    XWPFRun breakRun = breakPara.createRun();
                    breakRun.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(docxFile)) {
                document.write(fos);
            }
        }

        return docxFile;
    }

    /**
     * Extract content from PDF preserving line structure
     */
    public static List<PageContent> extractPagesContent(File pdfFile) throws Exception {
        List<PageContent> pages = new ArrayList<>();

        try (PdfReader reader = new PdfReader(pdfFile);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numberOfPages = pdfDocument.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                PageContent pageContent = new PageContent(i);

                // Use LocationTextExtractionStrategy for better text positioning
                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i), strategy);

                // Split into lines
                String[] lines = pageText.split("\n");
                for (String line : lines) {
                    // Keep all lines, even empty ones to preserve spacing
                    pageContent.lines.add(line);
                }

                pages.add(pageContent);
            }
        }

        return pages;
    }

    /**
     * Convert PDF to DOCX and return the file path.
     */
    public static String convertToDocx(String pdfPath, File outputDir) throws Exception {
        File pdfFile = new File(pdfPath);
        File docxFile = convertToDocx(pdfFile, outputDir);
        return docxFile.getAbsolutePath();
    }

    /**
     * Extract text only from PDF without creating a Word document.
     * Preserves line breaks.
     */
    public static String extractText(File pdfFile) throws Exception {
        StringBuilder fullText = new StringBuilder();

        try (PdfReader reader = new PdfReader(pdfFile);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numberOfPages = pdfDocument.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                if (numberOfPages > 1) {
                    fullText.append("=== PAGE ").append(i).append(" ===\n");
                }

                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i), strategy);
                fullText.append(pageText);

                if (i < numberOfPages) {
                    fullText.append("\n\n");
                }
            }
        }

        return fullText.toString();
    }

    /**
     * Extract text for editing - returns structured text that can be converted back
     */
    public static String extractTextForEditing(String pdfPath) throws Exception {
        File pdfFile = new File(pdfPath);
        StringBuilder fullText = new StringBuilder();

        try (PdfReader reader = new PdfReader(pdfFile);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numberOfPages = pdfDocument.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                // Page marker that will be used when converting back
                fullText.append("[PAGE:").append(i).append("]\n");

                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                String pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i), strategy);
                fullText.append(pageText);

                if (i < numberOfPages) {
                    fullText.append("\n");
                }
            }
        }

        return fullText.toString();
    }
}
