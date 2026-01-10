package com.docreader.utils;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class to convert PDF documents to Word (DOCX) format.
 * Extracts text from PDF preserving line structure, formatting and layout for easy editing.
 */
public class PdfToWordConverter {

    // Common patterns for detecting text formatting
    private static final Pattern HEADING_PATTERN = Pattern.compile("^[A-Z][A-Z0-9\\s]{2,}$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[•●○▪▸\\-\\*]\\s+.*");
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^\\d+[.)]\\s+.*");
    private static final Pattern ALL_CAPS_PATTERN = Pattern.compile("^[A-Z\\s]+$");

    /**
     * Data class to hold page text content with formatting info
     */
    public static class PageContent {
        public int pageNumber;
        public List<TextLine> lines;
        public float pageWidth;
        public float pageHeight;

        public PageContent(int pageNumber) {
            this.pageNumber = pageNumber;
            this.lines = new ArrayList<>();
        }
    }

    /**
     * Represents a line of text with formatting hints
     */
    public static class TextLine {
        public String text;
        public boolean isHeading;
        public boolean isBullet;
        public boolean isNumbered;
        public boolean isEmpty;
        public int indentLevel;

        public TextLine(String text) {
            this.text = text != null ? text : "";
            this.isEmpty = this.text.trim().isEmpty();
            this.isHeading = !isEmpty && detectHeading(this.text);
            this.isBullet = !isEmpty && BULLET_PATTERN.matcher(this.text).matches();
            this.isNumbered = !isEmpty && NUMBERED_PATTERN.matcher(this.text).matches();
            this.indentLevel = calculateIndent(this.text);
        }

        private boolean detectHeading(String text) {
            String trimmed = text.trim();
            // Short all-caps text is likely a heading
            if (trimmed.length() <= 60 && ALL_CAPS_PATTERN.matcher(trimmed).matches()) {
                return true;
            }
            // Text that looks like a heading pattern
            if (trimmed.length() <= 80 && HEADING_PATTERN.matcher(trimmed).matches()) {
                return true;
            }
            return false;
        }

        private int calculateIndent(String text) {
            if (text == null || text.isEmpty()) return 0;
            int spaces = 0;
            for (char c : text.toCharArray()) {
                if (c == ' ') spaces++;
                else if (c == '\t') spaces += 4;
                else break;
            }
            return spaces / 4; // Convert to indent levels
        }
    }

    /**
     * Convert a PDF file to DOCX format preserving line structure and formatting.
     */
    public static File convertToDocx(File pdfFile, File outputDir) throws Exception {
        if (!pdfFile.exists()) {
            throw new Exception("PDF file not found: " + pdfFile.getAbsolutePath());
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String baseName = pdfFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        String docxFileName = baseName + ".docx";
        File docxFile = new File(outputDir, docxFileName);

        // Extract text from PDF page by page with formatting
        List<PageContent> pages = extractPagesContent(pdfFile);

        // Check if we got any actual text content
        boolean hasContent = false;
        for (PageContent page : pages) {
            for (TextLine line : page.lines) {
                if (!line.isEmpty) {
                    hasContent = true;
                    break;
                }
            }
            if (hasContent) break;
        }

        if (!hasContent) {
            throw new Exception("No text content found in PDF. The PDF may contain only images or scanned content.");
        }

        // Create Word document
        XWPFDocument document = null;
        FileOutputStream fos = null;
        try {
            document = new XWPFDocument();

            for (int i = 0; i < pages.size(); i++) {
                PageContent page = pages.get(i);

                // Check if page has content
                boolean pageHasContent = false;
                for (TextLine line : page.lines) {
                    if (!line.isEmpty) {
                        pageHasContent = true;
                        break;
                    }
                }

                if (!pageHasContent && pages.size() > 1) {
                    // Skip empty pages but add a note
                    XWPFParagraph emptyNote = document.createParagraph();
                    emptyNote.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun noteRun = emptyNote.createRun();
                    noteRun.setItalic(true);
                    noteRun.setColor("999999");
                    noteRun.setText("[Page " + page.pageNumber + " - No text content]");

                    if (i < pages.size() - 1) {
                        XWPFParagraph breakPara = document.createParagraph();
                        XWPFRun breakRun = breakPara.createRun();
                        breakRun.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                    }
                    continue;
                }

                // Group lines into paragraphs for better readability
                StringBuilder paragraphBuilder = new StringBuilder();
                boolean inParagraph = false;

                for (int j = 0; j < page.lines.size(); j++) {
                    TextLine line = page.lines.get(j);

                    if (line.isEmpty) {
                        // Empty line ends a paragraph
                        if (inParagraph && paragraphBuilder.length() > 0) {
                            createParagraph(document, paragraphBuilder.toString().trim(), false, false, false, 0);
                            paragraphBuilder.setLength(0);
                            inParagraph = false;
                        }
                        continue;
                    }

                    // Check if this is a special line type
                    if (line.isHeading) {
                        // Flush current paragraph first
                        if (paragraphBuilder.length() > 0) {
                            createParagraph(document, paragraphBuilder.toString().trim(), false, false, false, 0);
                            paragraphBuilder.setLength(0);
                        }
                        createParagraph(document, line.text.trim(), true, false, false, 0);
                        inParagraph = false;
                    } else if (line.isBullet || line.isNumbered) {
                        // Flush current paragraph first
                        if (paragraphBuilder.length() > 0) {
                            createParagraph(document, paragraphBuilder.toString().trim(), false, false, false, 0);
                            paragraphBuilder.setLength(0);
                        }
                        createParagraph(document, line.text, false, line.isBullet, line.isNumbered, line.indentLevel);
                        inParagraph = false;
                    } else {
                        // Regular text - accumulate into paragraph
                        if (paragraphBuilder.length() > 0) {
                            // Check if we should start a new paragraph or continue
                            String currentText = paragraphBuilder.toString();
                            char lastChar = currentText.charAt(currentText.length() - 1);

                            // Continue paragraph if last line didn't end with sentence-ending punctuation
                            if (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == ':') {
                                // End current paragraph and start new one
                                createParagraph(document, currentText.trim(), false, false, false, 0);
                                paragraphBuilder.setLength(0);
                                paragraphBuilder.append(line.text);
                            } else {
                                // Continue the same paragraph
                                paragraphBuilder.append(" ").append(line.text.trim());
                            }
                        } else {
                            paragraphBuilder.append(line.text);
                        }
                        inParagraph = true;
                    }
                }

                // Flush any remaining paragraph content
                if (paragraphBuilder.length() > 0) {
                    createParagraph(document, paragraphBuilder.toString().trim(), false, false, false, 0);
                }

                // Add page break between pages (except last)
                if (i < pages.size() - 1) {
                    XWPFParagraph breakPara = document.createParagraph();
                    XWPFRun breakRun = breakPara.createRun();
                    breakRun.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                }
            }

            fos = new FileOutputStream(docxFile);
            document.write(fos);
            fos.flush();

        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }

        if (!docxFile.exists() || docxFile.length() == 0) {
            throw new Exception("Failed to create Word document");
        }

        return docxFile;
    }

    /**
     * Helper method to create a formatted paragraph
     */
    private static void createParagraph(XWPFDocument document, String text, boolean isHeading,
                                         boolean isBullet, boolean isNumbered, int indentLevel) {
        if (text == null || text.trim().isEmpty()) return;

        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();

        if (isHeading) {
            paragraph.setSpacingBefore(240);
            paragraph.setSpacingAfter(120);
            run.setBold(true);
            run.setFontSize(14);
            run.setFontFamily("Arial");
        } else if (isBullet || isNumbered) {
            paragraph.setIndentationLeft(720); // 0.5 inch
            paragraph.setSpacingAfter(60);
            run.setFontSize(11);
            run.setFontFamily("Calibri");
        } else {
            if (indentLevel > 0) {
                paragraph.setIndentationLeft(indentLevel * 360);
            }
            paragraph.setSpacingAfter(120);
            run.setFontSize(11);
            run.setFontFamily("Calibri");
        }

        run.setText(text);
    }

    /**
     * Extract content from PDF preserving line structure and formatting hints
     */
    public static List<PageContent> extractPagesContent(File pdfFile) throws Exception {
        List<PageContent> pages = new ArrayList<>();

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getAbsolutePath());
            PdfTextExtractor extractor = new PdfTextExtractor(reader);

            int numberOfPages = reader.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                PageContent pageContent = new PageContent(i);

                // Store page dimensions for layout reference
                Rectangle pageSize = reader.getPageSize(i);
                pageContent.pageWidth = pageSize.getWidth();
                pageContent.pageHeight = pageSize.getHeight();

                // Extract text from page
                String pageText = extractor.getTextFromPage(i);

                if (pageText == null || pageText.trim().isEmpty()) {
                    // Add empty page marker
                    pageContent.lines.add(new TextLine(""));
                    pages.add(pageContent);
                    continue;
                }

                // Filter out common watermark patterns
                pageText = filterWatermarks(pageText);

                // Process text line by line preserving structure
                String[] rawLines = pageText.split("\n");

                for (int j = 0; j < rawLines.length; j++) {
                    String line = rawLines[j];

                    // Skip lines that look like watermarks (diagonal text, repeated patterns)
                    if (isLikelyWatermark(line)) {
                        continue;
                    }

                    // Preserve the original line
                    pageContent.lines.add(new TextLine(line));
                }

                pages.add(pageContent);
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }

        return pages;
    }

    /**
     * Filter out common watermark text patterns
     */
    private static String filterWatermarks(String text) {
        if (text == null) return "";

        // Common watermark patterns to remove (case insensitive)
        String[] watermarkPatterns = {
            "CONFIDENTIAL",
            "DRAFT",
            "SAMPLE",
            "DO NOT COPY",
            "WATERMARK",
        };

        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            String upperLine = trimmedLine.toUpperCase();

            // Keep empty or very short lines
            if (trimmedLine.length() <= 2) {
                result.append(line).append("\n");
                continue;
            }

            // Check if line matches watermark patterns (only standalone watermarks)
            boolean isWatermark = false;
            for (String pattern : watermarkPatterns) {
                // Only consider it a watermark if the line is short and matches
                if (trimmedLine.length() < 30 && upperLine.contains(pattern)) {
                    isWatermark = true;
                    break;
                }
            }

            if (!isWatermark) {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Check if a line is likely a watermark
     */
    private static boolean isLikelyWatermark(String line) {
        if (line == null) return true;

        String trimmed = line.trim();

        // Empty or very short lines
        if (trimmed.isEmpty()) return false; // Keep empty lines for paragraph breaks

        // Lines with only repeated characters (like "----" or "====")
        if (trimmed.matches("^(.)\\1{3,}$")) return true;

        // Lines that are just a URL
        if (trimmed.matches("(?i)^https?://.*$") && trimmed.length() < 60) return true;

        // Lines that are just "Page X" or "X of Y"
        if (trimmed.matches("(?i)^page\\s+\\d+$")) return true;
        if (trimmed.matches("^\\d+\\s*(of|/)\\s*\\d+$")) return true;

        return false;
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

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getAbsolutePath());
            PdfTextExtractor extractor = new PdfTextExtractor(reader);

            int numberOfPages = reader.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                if (numberOfPages > 1) {
                    fullText.append("=== PAGE ").append(i).append(" ===\n");
                }

                String pageText = extractor.getTextFromPage(i);
                fullText.append(pageText);

                if (i < numberOfPages) {
                    fullText.append("\n\n");
                }
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
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

        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getAbsolutePath());
            PdfTextExtractor extractor = new PdfTextExtractor(reader);

            int numberOfPages = reader.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                // Page marker that will be used when converting back
                fullText.append("[PAGE:").append(i).append("]\n");

                String pageText = extractor.getTextFromPage(i);
                fullText.append(pageText);

                if (i < numberOfPages) {
                    fullText.append("\n");
                }
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }

        return fullText.toString();
    }
}
