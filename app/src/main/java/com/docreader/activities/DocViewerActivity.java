package com.docreader.activities;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.docreader.R;
import com.docreader.databinding.ActivityDocViewerBinding;
import com.docreader.utils.AppLogger;
import com.docreader.utils.FileUtils;
import com.docreader.utils.PdfToWordConverter;
import com.docreader.utils.WordToPdfConverter;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for viewing and editing DOC and DOCX documents.
 */
public class DocViewerActivity extends AppCompatActivity {

    private ActivityDocViewerBinding binding;
    private String filePath;
    private String fileName;
    private String originalPdfPath;  // Original PDF path if converted from PDF
    private String originalPdfName;
    private String documentText = "";
    private String originalText = "";
    private float fontSize = 16f;
    private boolean isEditMode = false;
    private boolean hasChanges = false;
    private boolean isFromPdf = false;  // Flag to track if document came from PDF conversion
    private Menu optionsMenu;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDocViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");
        originalPdfPath = getIntent().getStringExtra("original_pdf_path");
        originalPdfName = getIntent().getStringExtra("original_pdf_name");
        isFromPdf = (originalPdfPath != null && !originalPdfPath.isEmpty());

        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupControls();
        setupBackHandler();
        loadDocument();

        // If coming from PDF conversion, show instructions
        if (isFromPdf) {
            binding.getRoot().postDelayed(() -> {
                Toast.makeText(this, "Tap 'Edit' to start editing, then 'Save PDF' when done", Toast.LENGTH_LONG).show();
            }, 1000);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Show original PDF name if came from PDF conversion
            String title = isFromPdf ? originalPdfName : (fileName != null ? fileName : "Document Viewer");
            getSupportActionBar().setTitle(title);
        }

        binding.toolbar.setNavigationOnClickListener(v -> handleBackPress());
    }

    private void setupControls() {
        // Font size controls
        binding.btnFontSmaller.setOnClickListener(v -> decreaseFontSize());
        binding.btnFontLarger.setOnClickListener(v -> increaseFontSize());

        // Edit/Save button
        binding.btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                saveDocument();
            } else {
                enterEditMode();
            }
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    private void handleBackPress() {
        if (isEditMode && hasChanges) {
            showUnsavedChangesDialog();
        } else if (isEditMode) {
            exitEditMode();
        } else {
            finish();
        }
    }

    private void loadDocument() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvContent.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                String text;
                if (isFromPdf && originalPdfPath != null) {
                    // Load text directly from original PDF with page markers for better editing
                    text = PdfToWordConverter.extractTextForEditing(originalPdfPath);
                } else if (FileUtils.isDocx(fileName)) {
                    text = loadDocx(file);
                } else {
                    text = loadDoc(file);
                }

                documentText = text;
                originalText = text;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvContent.setVisibility(View.VISIBLE);
                    // Display text without page markers for viewing
                    String displayText = text.replaceAll("\\[PAGE:\\d+\\]\n?", "--- Page Break ---\n");
                    binding.tvContent.setText(displayText);
                    binding.tvContent.setTextSize(fontSize);
                    binding.etContent.setTextSize(fontSize);
                });

            } catch (Exception e) {
                AppLogger.e("DocViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.error_loading + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private String loadDocx(File file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n\n");
            }
        }
        return text.toString().trim();
    }

    private String loadDoc(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {

            return extractor.getText();
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        binding.tvContent.setVisibility(View.GONE);
        binding.etContent.setVisibility(View.VISIBLE);
        binding.etContent.setText(documentText);
        binding.etContent.setTextSize(fontSize);
        binding.etContent.requestFocus();

        binding.btnEditSave.setText(isFromPdf ? "Save PDF" : "Save");
        binding.btnEditSave.setIconResource(R.drawable.ic_save);

        if (getSupportActionBar() != null) {
            String title = isFromPdf ? "Editing PDF: " + originalPdfName : "Editing: " + fileName;
            getSupportActionBar().setTitle(title);
        }

        updateMenuVisibility();

        // Track changes
        binding.etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                hasChanges = !s.toString().equals(originalText);
            }
        });
    }

    private void exitEditMode() {
        isEditMode = false;
        hasChanges = false;
        binding.etContent.setVisibility(View.GONE);
        binding.tvContent.setVisibility(View.VISIBLE);
        binding.tvContent.setText(documentText);

        binding.btnEditSave.setText("Edit");
        binding.btnEditSave.setIconResource(R.drawable.ic_edit);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        updateMenuVisibility();
    }

    private void saveDocument() {
        String newText = binding.etContent.getText().toString();

        binding.progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                // If document came from PDF, save back as PDF
                if (isFromPdf) {
                    saveToPdf(newText);
                    return;
                }

                File file = new File(filePath);

                if (FileUtils.isDocx(fileName)) {
                    saveDocx(file, newText);
                } else {
                    // For .doc files, save as new .docx
                    saveAsNewDocx(newText);
                    return;
                }

                documentText = newText;
                originalText = newText;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    hasChanges = false;
                    Toast.makeText(this, "Document saved successfully", Toast.LENGTH_SHORT).show();
                    exitEditMode();
                });

            } catch (Exception e) {
                AppLogger.e("DocViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveToPdf(String text) {
        try {
            // Get the original PDF file name without extension
            String baseName = originalPdfName != null ? originalPdfName : "document";
            if (baseName.toLowerCase().endsWith(".pdf")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            String pdfFileName = baseName + "_edited.pdf";

            // First create PDF in cache directory with proper formatting
            File tempPdfFile;
            if (isFromPdf && originalPdfPath != null) {
                // Use original PDF page sizes for better formatting
                tempPdfFile = WordToPdfConverter.convertToPdfMatchingOriginal(
                        text, originalPdfPath, getCacheDir(), pdfFileName);
            } else {
                tempPdfFile = WordToPdfConverter.convertToPdf(text, getCacheDir(), pdfFileName);
            }

            String finalPath;
            String displayLocation;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : Use MediaStore to save to Downloads
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, pdfFileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri);
                         FileInputStream in = new FileInputStream(tempPdfFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    finalPath = tempPdfFile.getAbsolutePath(); // Use temp for opening
                    displayLocation = "Downloads/" + pdfFileName;
                } else {
                    throw new Exception("Failed to create file in Downloads");
                }
            } else {
                // Android 9 and below: Save directly to Downloads folder
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                File destFile = new File(downloadsDir, pdfFileName);

                // Copy temp file to Downloads
                try (FileInputStream in = new FileInputStream(tempPdfFile);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                finalPath = destFile.getAbsolutePath();
                displayLocation = "Downloads/" + pdfFileName;
            }

            documentText = text;
            originalText = text;

            String pathToOpen = finalPath;
            String fileNameToOpen = pdfFileName;
            String locationToShow = displayLocation;

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                hasChanges = false;
                exitEditMode();

                // Show save location to user
                new AlertDialog.Builder(this)
                        .setTitle("PDF Saved Successfully")
                        .setMessage("Your edited PDF has been saved to:\n\n" + locationToShow + "\n\nWould you like to open it?")
                        .setPositiveButton("Open", (d, w) -> {
                            Intent intent = new Intent(this, PdfViewerActivity.class);
                            intent.putExtra("file_path", pathToOpen);
                            intent.putExtra("file_name", fileNameToOpen);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Close", null)
                        .show();
            });

        } catch (Exception e) {
            AppLogger.e("DocViewerActivity", "Error", e);
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void saveDocx(File file, String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] paragraphs = text.split("\n\n");

            for (String para : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                // Handle line breaks within paragraph
                String[] lines = para.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    run.setText(lines[i]);
                    if (i < lines.length - 1) {
                        run.addBreak();
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                document.write(fos);
            }
        }
    }

    private void saveAsNewDocx(String text) {
        try {
            // Create new file name
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            String newFileName = baseName + "_edited_" + timestamp + ".docx";

            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }

            File newFile = new File(getCacheDir(), newFileName);

            try (XWPFDocument document = new XWPFDocument()) {
                String[] paragraphs = text.split("\n\n");

                for (String para : paragraphs) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(para);
                }

                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    document.write(fos);
                }
            }

            // Update current file path
            filePath = newFile.getAbsolutePath();
            fileName = newFileName;
            documentText = text;
            originalText = text;

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                hasChanges = false;
                Toast.makeText(this, "Saved as: " + newFileName, Toast.LENGTH_LONG).show();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(fileName);
                }
                exitEditMode();
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void saveAs() {
        String newText = isEditMode ? binding.etContent.getText().toString() : documentText;

        // Create dialog for file name
        android.widget.EditText input = new android.widget.EditText(this);
        String baseName = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        input.setText(baseName + "_copy");

        new AlertDialog.Builder(this)
                .setTitle("Save As")
                .setMessage("Enter file name (will be saved as .docx)")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        executor.execute(() -> {
                            try {
                                File newFile = new File(getCacheDir(), newName + ".docx");

                                try (XWPFDocument document = new XWPFDocument()) {
                                    String[] paragraphs = newText.split("\n\n");
                                    for (String para : paragraphs) {
                                        XWPFParagraph paragraph = document.createParagraph();
                                        XWPFRun run = paragraph.createRun();
                                        run.setText(para);
                                    }

                                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                                        document.write(fos);
                                    }
                                }

                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Saved: " + newFile.getName(), Toast.LENGTH_SHORT).show();
                                });

                            } catch (Exception e) {
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveToOriginalPdf() {
        if (!isFromPdf || originalPdfPath == null || originalPdfPath.isEmpty()) {
            Toast.makeText(this, "No original PDF to save to", Toast.LENGTH_SHORT).show();
            return;
        }

        String textToSave = isEditMode ? binding.etContent.getText().toString() : documentText;

        new AlertDialog.Builder(this)
                .setTitle("Save to Original PDF")
                .setMessage("This will REPLACE the original PDF file:\n\n" + originalPdfName + "\n\nAre you sure?")
                .setPositiveButton("Replace", (dialog, which) -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    executor.execute(() -> {
                        try {
                            // Create temp PDF with matching page structure
                            File tempPdf = WordToPdfConverter.convertToPdfMatchingOriginal(
                                    textToSave, originalPdfPath, getCacheDir(), "temp_save.pdf");

                            // Copy to original PDF location
                            File originalFile = new File(originalPdfPath);
                            try (FileInputStream in = new FileInputStream(tempPdf);
                                 FileOutputStream out = new FileOutputStream(originalFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }

                            // Clean up temp file
                            tempPdf.delete();

                            documentText = textToSave;
                            originalText = textToSave;

                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                hasChanges = false;
                                Toast.makeText(this, "Saved to original PDF!", Toast.LENGTH_SHORT).show();

                                new AlertDialog.Builder(this)
                                        .setTitle("Saved Successfully")
                                        .setMessage("Your changes have been saved to:\n\n" + originalPdfName + "\n\nWould you like to open it?")
                                        .setPositiveButton("Open PDF", (d, w) -> {
                                            Intent intent = new Intent(this, PdfViewerActivity.class);
                                            intent.putExtra("file_path", originalPdfPath);
                                            intent.putExtra("file_name", originalPdfName);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .setNegativeButton("Continue Editing", null)
                                        .show();
                            });

                        } catch (Exception e) {
                            AppLogger.e("DocViewerActivity", "Error", e);
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveAsPdf() {
        String textToSave = isEditMode ? binding.etContent.getText().toString() : documentText;

        // Create dialog for file name
        android.widget.EditText input = new android.widget.EditText(this);
        String baseName = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        input.setText(baseName + "_edited");

        new AlertDialog.Builder(this)
                .setTitle("Save As PDF")
                .setMessage("Enter file name for PDF")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        binding.progressBar.setVisibility(View.VISIBLE);
                        executor.execute(() -> {
                            try {
                                File pdfFile = WordToPdfConverter.convertToPdf(
                                        textToSave,
                                        getCacheDir(),
                                        newName
                                );

                                runOnUiThread(() -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Saved as PDF: " + pdfFile.getName(), Toast.LENGTH_LONG).show();

                                    // Ask if user wants to open the PDF
                                    new AlertDialog.Builder(this)
                                            .setTitle("PDF Created")
                                            .setMessage("Would you like to open the PDF?")
                                            .setPositiveButton("Open", (d, w) -> {
                                                Intent intent = new Intent(this, PdfViewerActivity.class);
                                                intent.putExtra("file_path", pdfFile.getAbsolutePath());
                                                intent.putExtra("file_name", pdfFile.getName());
                                                startActivity(intent);
                                            })
                                            .setNegativeButton("No", null)
                                            .show();
                                });

                            } catch (Exception e) {
                                AppLogger.e("DocViewerActivity", "Error", e);
                                runOnUiThread(() -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnsavedChangesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. What would you like to do?")
                .setPositiveButton("Save", (dialog, which) -> saveDocument())
                .setNegativeButton("Discard", (dialog, which) -> {
                    hasChanges = false;
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void updateMenuVisibility() {
        if (optionsMenu != null) {
            optionsMenu.findItem(R.id.action_undo).setVisible(isEditMode);
            optionsMenu.findItem(R.id.action_redo).setVisible(isEditMode);
            optionsMenu.findItem(R.id.action_save).setVisible(isEditMode);
            // Show "Save to Original PDF" only if document came from PDF conversion
            MenuItem saveToOriginal = optionsMenu.findItem(R.id.action_save_to_original);
            if (saveToOriginal != null) {
                saveToOriginal.setVisible(isFromPdf);
            }
        }
    }

    private void increaseFontSize() {
        if (fontSize < 32f) {
            fontSize += 2f;
            binding.tvContent.setTextSize(fontSize);
            binding.etContent.setTextSize(fontSize);
        }
    }

    private void decreaseFontSize() {
        if (fontSize > 10f) {
            fontSize -= 2f;
            binding.tvContent.setTextSize(fontSize);
            binding.etContent.setTextSize(fontSize);
        }
    }

    private void shareDocument() {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (FileUtils.isDocx(fileName)) {
                shareIntent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            } else {
                shareIntent.setType("application/msword");
            }
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Document"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_doc_editor, menu);
        optionsMenu = menu;
        updateMenuVisibility();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            saveDocument();
            return true;
        } else if (id == R.id.action_save_as) {
            saveAs();
            return true;
        } else if (id == R.id.action_save_as_pdf) {
            saveAsPdf();
            return true;
        } else if (id == R.id.action_save_to_original) {
            saveToOriginalPdf();
            return true;
        } else if (id == R.id.action_share) {
            shareDocument();
            return true;
        } else if (id == android.R.id.home) {
            handleBackPress();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
