package com.docreader.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.docreader.R;
import com.docreader.adapters.RecentFilesAdapter;
import com.docreader.databinding.ActivityMainBinding;
import com.docreader.models.RecentFile;
import com.docreader.utils.FileUtils;
import com.docreader.utils.PreferencesManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main activity displaying recent files and file picker.
 */
public class MainActivity extends AppCompatActivity implements RecentFilesAdapter.OnFileClickListener {

    private ActivityMainBinding binding;
    private PreferencesManager prefsManager;
    private RecentFilesAdapter adapter;

    // Track which tool was selected for multi-file operations
    private String pendingToolType = null;
    private List<String> selectedFilesForMerge = new ArrayList<>();

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    openFilePicker();
                } else {
                    Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleSelectedFile);

    // Launcher for PDF tools that require file selection
    private final ActivityResultLauncher<String[]> pdfToolLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleToolFileSelection);

    // Launcher for multiple file selection (merge)
    private final ActivityResultLauncher<String[]> multiPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::handleMergeFilesSelection);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefsManager = new PreferencesManager(this);

        setupToolbar();
        setupRecyclerView();
        setupFab();
        setupToolClickListeners();

        // Handle intent if opened from file
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentFiles();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
    }

    private void setupRecyclerView() {
        binding.recyclerRecentFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecentFilesAdapter(prefsManager.getRecentFiles(), this);
        binding.recyclerRecentFiles.setAdapter(adapter);

        binding.btnClearRecent.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clear_recent)
                    .setMessage("Are you sure you want to clear all recent files?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        prefsManager.clearRecentFiles();
                        loadRecentFiles();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupFab() {
        binding.fabOpenFile.setOnClickListener(v -> checkPermissionsAndOpenPicker());
    }

    private void loadRecentFiles() {
        List<RecentFile> files = prefsManager.getRecentFiles();
        adapter.updateFiles(files);

        if (files.isEmpty()) {
            binding.recyclerRecentFiles.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.btnClearRecent.setVisibility(View.GONE);
        } else {
            binding.recyclerRecentFiles.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
            binding.btnClearRecent.setVisibility(View.VISIBLE);
        }
    }

    private void checkPermissionsAndOpenPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - use media permissions
            openFilePicker();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            } else {
                openFilePicker();
            }
        } else {
            openFilePicker();
        }
    }

    private void openFilePicker() {
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        openDocumentLauncher.launch(mimeTypes);
    }

    private void handleSelectedFile(Uri uri) {
        if (uri == null) return;

        try {
            String fileName = FileUtils.getFileName(this, uri);
            if (fileName == null) {
                Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!FileUtils.isSupportedFormat(fileName)) {
                Toast.makeText(this, R.string.error_unsupported_format, Toast.LENGTH_SHORT).show();
                return;
            }

            // Copy to temp file for processing
            File tempFile = FileUtils.copyToTempFile(this, uri, fileName);

            // Add to recent files
            RecentFile recentFile = new RecentFile(
                    fileName,
                    tempFile.getAbsolutePath(),
                    RecentFile.getFileType(fileName),
                    System.currentTimeMillis()
            );
            prefsManager.addRecentFile(recentFile);

            // Open appropriate viewer
            openDocument(tempFile.getAbsolutePath(), fileName);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDocument(String path, String fileName) {
        Intent intent;
        if (FileUtils.isPdf(fileName)) {
            intent = new Intent(this, PdfViewerActivity.class);
        } else {
            intent = new Intent(this, DocViewerActivity.class);
        }
        intent.putExtra("file_path", path);
        intent.putExtra("file_name", fileName);
        startActivity(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        Uri uri = intent.getData();
        if (uri != null) {
            handleSelectedFile(uri);
        }
    }

    @Override
    public void onFileClick(RecentFile file) {
        File f = new File(file.getPath());
        if (f.exists()) {
            // Update last opened time
            file.setLastOpened(System.currentTimeMillis());
            prefsManager.addRecentFile(file);

            openDocument(file.getPath(), file.getName());
        } else {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            prefsManager.removeRecentFile(file.getPath());
            loadRecentFiles();
        }
    }

    @Override
    public void onRemoveClick(RecentFile file) {
        prefsManager.removeRecentFile(file.getPath());
        loadRecentFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem darkModeItem = menu.findItem(R.id.action_dark_mode);
        darkModeItem.setChecked(prefsManager.isDarkMode());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_dark_mode) {
            boolean newDarkMode = !prefsManager.isDarkMode();
            prefsManager.setDarkMode(newDarkMode);
            item.setChecked(newDarkMode);

            if (newDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_version) + "\n\n" + getString(R.string.about_description))
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Setup click listeners for all PDF tool icons
     */
    private void setupToolClickListeners() {
        // Row 1: Merge, Split, Compress, To Word
        binding.toolMergePdf.setOnClickListener(v -> {
            pendingToolType = "merge";
            Toast.makeText(this, "Select PDF files to merge", Toast.LENGTH_SHORT).show();
            multiPdfLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolSplitPdf.setOnClickListener(v -> {
            pendingToolType = "split";
            Toast.makeText(this, "Select a PDF file to split", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolCompressPdf.setOnClickListener(v -> {
            pendingToolType = "compress";
            Toast.makeText(this, "Select a PDF file to compress", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolPdfToWord.setOnClickListener(v -> {
            pendingToolType = "toword";
            Toast.makeText(this, "Select a PDF file to convert to Word", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        // Row 2: To Image, Watermark, Page Numbers, Delete
        binding.toolPdfToImage.setOnClickListener(v -> {
            pendingToolType = "toimage";
            Toast.makeText(this, "Select a PDF file to convert to images", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolWatermark.setOnClickListener(v -> {
            pendingToolType = "watermark";
            Toast.makeText(this, "Select a PDF file to add watermark", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolPageNumbers.setOnClickListener(v -> {
            pendingToolType = "pagenumbers";
            Toast.makeText(this, "Select a PDF file to add page numbers", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolDeletePages.setOnClickListener(v -> {
            pendingToolType = "delete";
            Toast.makeText(this, "Select a PDF file to delete pages", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        // Row 3: Rotate, Copy, Edit, Extract
        binding.toolRotate.setOnClickListener(v -> {
            pendingToolType = "rotate";
            Toast.makeText(this, "Select a PDF file to rotate pages", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolCopyPdf.setOnClickListener(v -> {
            pendingToolType = "copy";
            Toast.makeText(this, "Select a PDF file to copy", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolEditPdf.setOnClickListener(v -> {
            pendingToolType = "edit";
            Toast.makeText(this, "Select a PDF file to edit", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });

        binding.toolExtract.setOnClickListener(v -> {
            pendingToolType = "extract";
            Toast.makeText(this, "Select a PDF file to extract pages", Toast.LENGTH_SHORT).show();
            pdfToolLauncher.launch(new String[]{"application/pdf"});
        });
    }

    /**
     * Handle file selection for PDF tools
     */
    private void handleToolFileSelection(Uri uri) {
        if (uri == null || pendingToolType == null) {
            pendingToolType = null;
            return;
        }

        try {
            String fileName = FileUtils.getFileName(this, uri);
            if (fileName == null) {
                Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
                pendingToolType = null;
                return;
            }

            // Copy to temp file
            File tempFile = FileUtils.copyToTempFile(this, uri, fileName);

            // Open PDF viewer with the selected tool action
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("file_path", tempFile.getAbsolutePath());
            intent.putExtra("file_name", fileName);
            intent.putExtra("tool_action", pendingToolType);
            startActivity(intent);

            pendingToolType = null;

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show();
            pendingToolType = null;
        }
    }

    /**
     * Handle multiple file selection for merge operation
     */
    private void handleMergeFilesSelection(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            pendingToolType = null;
            return;
        }

        if (uris.size() < 2) {
            Toast.makeText(this, "Please select at least 2 PDF files to merge", Toast.LENGTH_SHORT).show();
            pendingToolType = null;
            return;
        }

        try {
            selectedFilesForMerge.clear();

            // Copy all files to temp
            for (Uri uri : uris) {
                String fileName = FileUtils.getFileName(this, uri);
                if (fileName != null) {
                    File tempFile = FileUtils.copyToTempFile(this, uri, fileName);
                    selectedFilesForMerge.add(tempFile.getAbsolutePath());
                }
            }

            if (selectedFilesForMerge.size() < 2) {
                Toast.makeText(this, "Failed to process files", Toast.LENGTH_SHORT).show();
                pendingToolType = null;
                return;
            }

            // Open first PDF with merge action and pass other files
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("file_path", selectedFilesForMerge.get(0));
            intent.putExtra("file_name", new File(selectedFilesForMerge.get(0)).getName());
            intent.putExtra("tool_action", "merge");
            intent.putStringArrayListExtra("merge_files", new ArrayList<>(selectedFilesForMerge));
            startActivity(intent);

            pendingToolType = null;

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show();
            pendingToolType = null;
        }
    }
}
