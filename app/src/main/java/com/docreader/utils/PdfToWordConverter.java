package com.docreader.utils;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Utility class to convert PDF documents to Word (DOCX) format.
 * Extracts text from PDF and creates an editable Word document.
 */
public class PdfToWordConverter {

    /**
     * Convert a PDF file to DOCX format.
     *
     * @param pdfFile  The source PDF file
     * @param outputDir The directory to save the output DOCX file
     * @return The created DOCX file
     * @throws Exception if conversion fails
     */
    public static File convertToDocx(File pdfFile, File outputDir) throws Exception {
        // Create output file name
        String baseName = pdfFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        String docxFileName = baseName + ".docx";
        File docxFile = new File(outputDir, docxFileName);

        // Extract text from PDF
        StringBuilder fullText = new StringBuilder();

        try (PdfReader reader = new PdfReader(pdfFile);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numberOfPages = pdfDocument.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i));
                fullText.append(pageText);

                // Add page separator for multi-page documents
                if (i < numberOfPages) {
                    fullText.append("\n\n--- Page ").append(i).append(" ---\n\n");
                }
            }
        }

        // Create Word document with extracted text
        try (XWPFDocument document = new XWPFDocument()) {
            String text = fullText.toString();

            // Split by double newlines to create paragraphs
            String[] paragraphs = text.split("\n\n");

            for (String para : paragraphs) {
                if (para.trim().isEmpty()) continue;

                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                // Handle single line breaks within paragraph
                String[] lines = para.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    run.setText(lines[i]);
                    if (i < lines.length - 1) {
                        run.addBreak();
                    }
                }
            }

            // Save the document
            try (FileOutputStream fos = new FileOutputStream(docxFile)) {
                document.write(fos);
            }
        }

        return docxFile;
    }

    /**
     * Convert PDF to DOCX and return the file path.
     *
     * @param pdfPath   Path to the source PDF file
     * @param outputDir Directory to save the output file
     * @return Path to the created DOCX file
     * @throws Exception if conversion fails
     */
    public static String convertToDocx(String pdfPath, File outputDir) throws Exception {
        File pdfFile = new File(pdfPath);
        File docxFile = convertToDocx(pdfFile, outputDir);
        return docxFile.getAbsolutePath();
    }

    /**
     * Extract text only from PDF without creating a Word document.
     *
     * @param pdfFile The PDF file to extract text from
     * @return The extracted text
     * @throws Exception if extraction fails
     */
    public static String extractText(File pdfFile) throws Exception {
        StringBuilder fullText = new StringBuilder();

        try (PdfReader reader = new PdfReader(pdfFile);
             PdfDocument pdfDocument = new PdfDocument(reader)) {

            int numberOfPages = pdfDocument.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                String pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i));
                fullText.append(pageText);

                if (i < numberOfPages) {
                    fullText.append("\n");
                }
            }
        }

        return fullText.toString();
    }
}
