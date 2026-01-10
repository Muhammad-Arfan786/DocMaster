package com.docreader.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.docreader.R;
import com.docreader.databinding.ActivityPdfViewerBinding;
import com.docreader.databinding.BottomSheetPdfEditorBinding;
import com.docreader.models.Note;
import com.docreader.utils.AppExecutors;
import com.docreader.utils.AppLogger;
import com.docreader.utils.Constants;
import com.docreader.utils.FileUtils;
import com.docreader.utils.NotesManager;
import com.docreader.utils.PreferencesManager;
import com.docreader.utils.PdfCoverReplace;
import com.docreader.models.RecentFile;
import com.docreader.utils.PdfEditManager;
import com.docreader.utils.PdfPageManager;
import com.docreader.utils.PdfTextEditor;
import com.docreader.utils.PdfAnnotationEditor;
import com.docreader.utils.VisualPdfEditor;
import com.docreader.utils.PdfImageCopyEditor;
import com.docreader.utils.PdfCopyEditor;
import com.docreader.utils.PdfTools;
import com.docreader.views.DrawingView;
import com.docreader.views.TextBlockOverlayView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.lowagie.text.PageSize;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity for viewing and editing PDF documents.
 * Supports page management, drawing, text annotations, images, and more.
 */
public class PdfViewerActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private ActivityPdfViewerBinding binding;

    // Activity Result Launchers (replacing deprecated startActivityForResult)
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> pickPdfMergeLauncher;
    private String filePath;
    private String fileName;
    private int totalPages = 0;
    private int currentPage = 0;
    private float currentZoom = 1.0f;
    private NotesManager notesManager;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private List<ImageView> pageViews = new ArrayList<>();

    // Edit mode
    private boolean isEditMode = false;
    private PdfEditManager pdfEditManager;
    private PdfPageManager pdfPageManager;
    private int currentColor = Color.RED;
    private DrawingView.Tool currentTool = DrawingView.Tool.NONE;

    // Merge PDFs
    private List<String> pdfsToMerge = new ArrayList<>();

    // Edit Text mode (Cover & Replace)
    private boolean isEditTextMode = false;
    private List<PdfCoverReplace.EditOperation> editOperations = new ArrayList<>();
    private int editTextColor = Color.BLACK;
    private float editTextSize = 12f;
    private int selectedPageIndex = -1;

    // Visual Edit mode (Tap-to-edit like Word)
    private boolean isVisualEditMode = false;
    private List<VisualPdfEditor.TextBlock> visualTextBlocks = new ArrayList<>();
    private TextBlockOverlayView textBlockOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPdfViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");

        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        notesManager = new NotesManager(this);
        pdfEditManager = new PdfEditManager(this, filePath);
        pdfPageManager = new PdfPageManager(this, filePath);

        // Initialize Activity Result Launchers
        initActivityResultLaunchers();

        // Setup back press handling
        setupBackPressHandler();

        setupToolbar();
        setupControls();
        setupEditToolbar();
        loadPdf();

        // Check if opened with a specific tool action from main page
        handleToolAction();
    }

    /**
     * Handle tool action passed from MainActivity
     */
    private void handleToolAction() {
        String toolAction = getIntent().getStringExtra("tool_action");
        if (toolAction == null) return;

        // Delay to ensure PDF is loaded
        binding.getRoot().postDelayed(() -> {
            switch (toolAction) {
                case "merge":
                    List<String> mergeFiles = getIntent().getStringArrayListExtra("merge_files");
                    if (mergeFiles != null && mergeFiles.size() >= 2) {
                        performMerge(mergeFiles);
                    } else {
                        showMergePdfDialog();
                    }
                    break;
                case "split":
                    showSplitPdfDialog();
                    break;
                case "toimage":
                    showPdfToImagesDialog();
                    break;
                case "watermark":
                    showAddWatermarkDialog();
                    break;
                case "pagenumbers":
                    showAddPageNumbersDialog();
                    break;
                case "delete":
                    showDeletePagesDialog();
                    break;
                case "rotate":
                    showRotatePagesDialog();
                    break;
                case "copy":
                    showCopyPdfDialog();
                    break;
                case "edit":
                    // Enable edit mode
                    enterEditMode();
                    break;
                case "extract":
                    showExtractPagesToolDialog();
                    break;
            }
        }, 500);
    }

    /**
     * Perform merge with pre-selected files
     */
    private void performMerge(List<String> filePaths) {
        new AlertDialog.Builder(this)
                .setTitle("Merge PDFs")
                .setMessage("Merge " + filePaths.size() + " PDF files?")
                .setPositiveButton("Merge", (dialog, which) -> {
                    try {
                        File outputDir = new File(getCacheDir(), "edited");
                        String outputName = "merged_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
                        PdfTools.PdfResult result = PdfTools.mergePdfs(filePaths, outputDir, outputName);
                        showToolResultDialog(result, "Merge PDFs");
                    } catch (Exception e) {
                        Toast.makeText(this, "Merge error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Initialize Activity Result Launchers (replacing deprecated startActivityForResult)
     */
    private void initActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handleImagePickResult(result.getData());
                    }
                }
        );

        pickPdfMergeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handleMergePdfPickResult(result.getData());
                    }
                }
        );
    }

    /**
     * Handle image pick result from launcher
     */
    private void handleImagePickResult(Intent data) {
        Uri imageUri = data.getData();
        if (imageUri != null) {
            addImageToPdf(imageUri);
        }
    }

    /**
     * Handle PDF merge pick result from launcher
     */
    private void handleMergePdfPickResult(Intent data) {
        List<Uri> pdfUris = new ArrayList<>();

        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                pdfUris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            pdfUris.add(data.getData());
        }

        if (!pdfUris.isEmpty()) {
            mergePdfs(pdfUris);
        }
    }

    /**
     * Setup back press handling using OnBackPressedCallback (replacing deprecated onBackPressed)
     */
    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isEditMode) {
                    exitEditMode();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Viewer");
        }

        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupControls() {
        binding.btnZoomIn.setOnClickListener(v -> zoomIn());
        binding.btnZoomOut.setOnClickListener(v -> zoomOut());
        binding.btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        binding.btnNextPage.setOnClickListener(v -> goToNextPage());
        binding.fabSaveEdit.setOnClickListener(v -> saveEditedPdf());
        updateZoomLabel();
    }

    private void setupEditToolbar() {
        binding.btnPen.setOnClickListener(v -> selectTool(DrawingView.Tool.PEN, binding.btnPen));
        binding.btnHighlighter.setOnClickListener(v -> selectTool(DrawingView.Tool.HIGHLIGHTER, binding.btnHighlighter));
        binding.btnText.setOnClickListener(v -> selectTool(DrawingView.Tool.TEXT, binding.btnText));
        binding.btnEraser.setOnClickListener(v -> selectTool(DrawingView.Tool.ERASER, binding.btnEraser));

        binding.btnColorRed.setOnClickListener(v -> selectColor(Color.RED, binding.btnColorRed));
        binding.btnColorBlue.setOnClickListener(v -> selectColor(Color.parseColor("#2196F3"), binding.btnColorBlue));
        binding.btnColorGreen.setOnClickListener(v -> selectColor(Color.parseColor("#4CAF50"), binding.btnColorGreen));
        binding.btnColorYellow.setOnClickListener(v -> selectColor(Color.parseColor("#FFEB3B"), binding.btnColorYellow));

        binding.btnUndo.setOnClickListener(v -> binding.drawingView.undo());
        binding.btnRedo.setOnClickListener(v -> binding.drawingView.redo());
        binding.btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All")
                    .setMessage("Clear all drawings on this page?")
                    .setPositiveButton("Clear", (dialog, which) -> binding.drawingView.clearAll())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.drawingView.setOnTextPlacementListener((x, y) -> showAddTextDialog(x, y));
        binding.btnColorRed.setSelected(true);
    }

    private void selectTool(DrawingView.Tool tool, ImageButton button) {
        currentTool = tool;
        binding.btnPen.setSelected(false);
        binding.btnHighlighter.setSelected(false);
        binding.btnText.setSelected(false);
        binding.btnEraser.setSelected(false);
        button.setSelected(true);
        binding.drawingView.setTool(tool);
    }

    private void selectColor(int color, ImageButton button) {
        currentColor = color;
        binding.btnColorRed.setSelected(false);
        binding.btnColorBlue.setSelected(false);
        binding.btnColorGreen.setSelected(false);
        binding.btnColorYellow.setSelected(false);
        button.setSelected(true);
        binding.drawingView.setColor(color);
    }

    private void loadPdf() {
        binding.progressBar.setVisibility(View.VISIBLE);

        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();

            renderAllPages();

            binding.progressBar.setVisibility(View.GONE);
            updatePageInfo();

        } catch (IOException e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void reloadPdf() {
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }

            for (ImageView iv : pageViews) {
                iv.setImageBitmap(null);
            }
            pageViews.clear();

            File file = new File(filePath);
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();

            if (currentPage >= totalPages) {
                currentPage = totalPages - 1;
            }
            if (currentPage < 0) {
                currentPage = 0;
            }

            pdfPageManager.setPdfPath(filePath);
            pdfEditManager = new PdfEditManager(this, filePath);

            renderAllPages();
            updatePageInfo();

        } catch (IOException e) {
            Toast.makeText(this, "Error reloading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void renderAllPages() {
        binding.pagesContainer.removeAllViews();
        pageViews.clear();

        for (int i = 0; i < totalPages; i++) {
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            imageView.setLayoutParams(params);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            final int pageIndex = i;
            imageView.setOnClickListener(v -> {
                currentPage = pageIndex;
                updatePageInfo();
            });

            binding.pagesContainer.addView(imageView);
            pageViews.add(imageView);

            renderPage(i);
        }
    }

    private void renderPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPages) {
            return;
        }

        // FIX: Use try-finally to ensure page is always closed even on exception
        PdfRenderer.Page page = null;
        try {
            page = pdfRenderer.openPage(pageIndex);

            int screenWidth = getResources().getDisplayMetrics().widthPixels - 32;
            float aspectRatio = (float) page.getHeight() / page.getWidth();

            // Calculate display size
            int displayWidth = (int) (screenWidth * currentZoom);
            int displayHeight = (int) (displayWidth * aspectRatio);

            // Quality boost: render at 1.5x for better text clarity (balanced)
            float qualityBoost = 1.5f;
            int renderWidth = (int) (displayWidth * qualityBoost);
            int renderHeight = (int) (renderWidth * aspectRatio);

            // Ensure minimum render width for readable text (1200px - balanced)
            int minRenderWidth = 1200;
            if (renderWidth < minRenderWidth && currentZoom <= 1.0f) {
                renderWidth = minRenderWidth;
                renderHeight = (int) (renderWidth * aspectRatio);
            }

            // Cap at 2048 for performance (reduced from 4096)
            int maxSize = 2048;
            if (renderWidth > maxSize) {
                renderWidth = maxSize;
                renderHeight = (int) (renderWidth * aspectRatio);
            }
            if (renderHeight > maxSize) {
                renderHeight = maxSize;
                renderWidth = (int) (renderHeight / aspectRatio);
            }

            // Create bitmap and render
            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            if (pageIndex < pageViews.size()) {
                ImageView imageView = pageViews.get(pageIndex);
                imageView.setImageBitmap(bitmap);

                // Update layout
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) imageView.getLayoutParams();
                params.width = displayWidth;
                params.height = displayHeight;
                imageView.setLayoutParams(params);
            }
        } finally {
            // Always close page to prevent resource leak
            if (page != null) {
                page.close();
            }
        }
    }

    // ==================== PDF EDITOR BOTTOM SHEET ====================

    private void showPdfEditorBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        BottomSheetPdfEditorBinding sheetBinding = BottomSheetPdfEditorBinding.inflate(getLayoutInflater());
        bottomSheet.setContentView(sheetBinding.getRoot());

        // Page operations
        sheetBinding.btnDeletePage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showDeletePageDialog();
        });

        sheetBinding.btnRotatePage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showRotatePageDialog();
        });

        sheetBinding.btnDuplicatePage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            duplicateCurrentPage();
        });

        sheetBinding.btnExtractPage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showExtractPagesDialog();
        });

        sheetBinding.btnAddBlankPage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showAddBlankPageDialog();
        });

        sheetBinding.btnReorderPages.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showReorderPagesDialog();
        });

        sheetBinding.btnSplitPdf.setOnClickListener(v -> {
            bottomSheet.dismiss();
            splitPdfIntoPages();
        });

        // Content operations
        sheetBinding.btnAddImage.setOnClickListener(v -> {
            bottomSheet.dismiss();
            pickImageToAdd();
        });

        sheetBinding.btnAddSignature.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showSignatureDialog();
        });

        sheetBinding.btnAddWatermark.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showWatermarkDialog();
        });

        sheetBinding.btnMergePdfs.setOnClickListener(v -> {
            bottomSheet.dismiss();
            pickPdfsToMerge();
        });

        bottomSheet.show();
    }

    // ==================== PAGE OPERATIONS ====================

    private void showDeletePageDialog() {
        if (totalPages <= 1) {
            Toast.makeText(this, "Cannot delete the only page", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Page")
                .setMessage("Delete page " + (currentPage + 1) + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCurrentPage())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrentPage() {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                List<Integer> pagesToDelete = Collections.singletonList(currentPage + 1);
                String newPath = pdfPageManager.deletePages(pagesToDelete);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Page deleted", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showRotatePageDialog() {
        String[] options = {"Rotate 90° Right", "Rotate 90° Left", "Rotate 180°", "Rotate All Pages 90° Right"};

        new AlertDialog.Builder(this)
                .setTitle("Rotate Page " + (currentPage + 1))
                .setItems(options, (dialog, which) -> {
                    int degrees = 0;
                    boolean allPages = false;

                    switch (which) {
                        case 0: degrees = 90; break;
                        case 1: degrees = 270; break;
                        case 2: degrees = 180; break;
                        case 3: degrees = 90; allPages = true; break;
                    }

                    rotatePage(degrees, allPages);
                })
                .show();
    }

    private void rotatePage(int degrees, boolean allPages) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String newPath;
                if (allPages) {
                    newPath = pdfPageManager.rotateAllPages(degrees);
                } else {
                    newPath = pdfPageManager.rotatePages(Collections.singletonList(currentPage + 1), degrees);
                }

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Page rotated", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void duplicateCurrentPage() {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String newPath = pdfPageManager.duplicatePage(currentPage + 1);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Page duplicated", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showExtractPagesDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g., 1,3,5 or 1-5");
        input.setText(String.valueOf(currentPage + 1));

        new AlertDialog.Builder(this)
                .setTitle("Extract Pages")
                .setMessage("Enter page numbers to extract:")
                .setView(input)
                .setPositiveButton("Extract", (dialog, which) -> {
                    String pages = input.getText().toString();
                    extractPages(pages);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void extractPages(String pageSpec) {
        List<Integer> pages = parsePageSpec(pageSpec);
        if (pages.isEmpty()) {
            Toast.makeText(this, "Invalid page specification", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String newPath = pdfPageManager.extractPages(pages);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showSavedFileDialog(newPath, "Pages extracted to:");
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showAddBlankPageDialog() {
        String[] options = {"Before current page", "After current page", "At the end"};

        new AlertDialog.Builder(this)
                .setTitle("Add Blank Page")
                .setItems(options, (dialog, which) -> {
                    int afterPage;
                    switch (which) {
                        case 0: afterPage = currentPage; break;
                        case 1: afterPage = currentPage + 1; break;
                        default: afterPage = totalPages; break;
                    }
                    addBlankPage(afterPage);
                })
                .show();
    }

    private void addBlankPage(int afterPage) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String newPath = pdfPageManager.addBlankPage(afterPage, Constants.PAGE_WIDTH_A4, Constants.PAGE_HEIGHT_A4);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Blank page added", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showReorderPagesDialog() {
        EditText input = new EditText(this);
        StringBuilder hint = new StringBuilder("Current order: ");
        for (int i = 1; i <= totalPages; i++) {
            hint.append(i);
            if (i < totalPages) hint.append(",");
        }
        input.setHint(hint.toString());

        new AlertDialog.Builder(this)
                .setTitle("Reorder Pages")
                .setMessage("Enter new page order (e.g., 3,1,2):")
                .setView(input)
                .setPositiveButton("Reorder", (dialog, which) -> {
                    String order = input.getText().toString();
                    reorderPages(order);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reorderPages(String orderSpec) {
        List<Integer> newOrder = parsePageSpec(orderSpec);
        if (newOrder.isEmpty()) {
            Toast.makeText(this, "Invalid page order", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String newPath = pdfPageManager.reorderPages(newOrder);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    currentPage = 0;
                    reloadPdf();
                    Toast.makeText(this, "Pages reordered", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void splitPdfIntoPages() {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                List<String> paths = pdfPageManager.splitAllPages();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("PDF Split")
                            .setMessage("PDF split into " + paths.size() + " files.\n\nSaved to Documents folder.")
                            .setPositiveButton("OK", null)
                            .show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==================== CONTENT OPERATIONS ====================

    private void pickImageToAdd() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void addImageToPdf(Uri imageUri) {
        String[] options = {"Add as new page", "Add to current page"};

        new AlertDialog.Builder(this)
                .setTitle("Add Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        addImageAsNewPage(imageUri);
                    } else {
                        addImageToCurrentPage(imageUri);
                    }
                })
                .show();
    }

    private void addImageAsNewPage(Uri imageUri) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String imagePath = FileUtils.getPathFromUri(this, imageUri);
                if (imagePath == null) {
                    throw new IOException("Cannot access image file");
                }

                String newPath = pdfPageManager.addImageAsPage(imagePath, currentPage + 1);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Image added as new page", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addImageToCurrentPage(Uri imageUri) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String imagePath = FileUtils.getPathFromUri(this, imageUri);
                if (imagePath == null) {
                    throw new IOException("Cannot access image file");
                }

                String newPath = pdfPageManager.addImageToPage(currentPage + 1, imagePath, 50, 50, 200, 200);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    filePath = newPath;
                    reloadPdf();
                    Toast.makeText(this, "Image added to page", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showSignatureDialog() {
        Toast.makeText(this, "Draw your signature using the Pen tool", Toast.LENGTH_LONG).show();
        enterEditMode();
        selectTool(DrawingView.Tool.PEN, binding.btnPen);
    }

    private void showWatermarkDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter watermark text");

        new AlertDialog.Builder(this)
                .setTitle("Add Watermark")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        addWatermark(text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addWatermark(String text) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Add text annotation to center of each page as watermark
                for (int i = 0; i < totalPages; i++) {
                    pdfEditManager.addTextAnnotation(i, 0.5f, 0.5f, text, Color.argb(50, 128, 128, 128), 48f);
                }

                String newPath = pdfEditManager.saveEditedPdf();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    pdfEditManager.clearAllAnnotations();
                    showSavedFileDialog(newPath, "Watermark added. Saved to:");
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void pickPdfsToMerge() {
        pdfsToMerge.clear();
        pdfsToMerge.add(filePath); // Start with current PDF

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickPdfMergeLauncher.launch(Intent.createChooser(intent, "Select PDFs to merge"));
    }

    private void mergePdfs(List<Uri> pdfUris) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                List<String> pdfPaths = new ArrayList<>();
                pdfPaths.add(filePath); // Current PDF first

                for (Uri uri : pdfUris) {
                    String path = FileUtils.getPathFromUri(this, uri);
                    if (path != null) {
                        pdfPaths.add(path);
                    }
                }

                if (pdfPaths.size() < 2) {
                    throw new IOException("Need at least 2 PDFs to merge");
                }

                String mergedPath = PdfPageManager.mergePdfs(this, pdfPaths, "merged");

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("PDFs Merged")
                            .setMessage("Merged " + pdfPaths.size() + " PDFs.\n\nSaved to:\n" + mergedPath)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Open", (d, w) -> {
                                filePath = mergedPath;
                                reloadPdf();
                            })
                            .show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==================== EDIT MODE ====================

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            enterEditMode();
        } else {
            exitEditMode();
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        binding.editToolbar.setVisibility(View.VISIBLE);
        binding.drawingView.setVisibility(View.VISIBLE);
        binding.fabSaveEdit.setVisibility(View.VISIBLE);
        binding.drawingView.setEditEnabled(true);
        binding.scrollView.setNestedScrollingEnabled(false);
        selectTool(DrawingView.Tool.PEN, binding.btnPen);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Mode - Page " + (currentPage + 1));
        }

        Toast.makeText(this, "Edit mode enabled", Toast.LENGTH_SHORT).show();
    }

    private void exitEditMode() {
        if (binding.drawingView.hasDrawings() || pdfEditManager.hasChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("Save your changes?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        saveEditedPdf();
                        finishExitEditMode();
                    })
                    .setNegativeButton("Discard", (dialog, which) -> {
                        binding.drawingView.clearAll();
                        pdfEditManager.clearAllAnnotations();
                        finishExitEditMode();
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            finishExitEditMode();
        }
    }

    private void finishExitEditMode() {
        isEditMode = false;
        binding.editToolbar.setVisibility(View.GONE);
        binding.drawingView.setVisibility(View.GONE);
        binding.fabSaveEdit.setVisibility(View.GONE);
        binding.drawingView.setEditEnabled(false);
        binding.drawingView.setTool(DrawingView.Tool.NONE);
        binding.scrollView.setNestedScrollingEnabled(true);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Viewer");
        }
    }

    private void showAddTextDialog(float x, float y) {
        EditText input = new EditText(this);
        input.setHint("Enter text...");

        new AlertDialog.Builder(this)
                .setTitle("Add Text")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        float normalizedX = x / binding.drawingView.getWidth();
                        float normalizedY = y / binding.drawingView.getHeight();
                        pdfEditManager.addTextAnnotation(currentPage, normalizedX, normalizedY, text, currentColor, 14f);
                        Toast.makeText(this, "Text added", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveEditedPdf() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return;
        }
        performSave();
    }

    /**
     * Show save options dialog - allows saving current PDF with any edits/annotations
     */
    private void showSaveOptionsDialog() {
        String[] options = {
            "Save as New Copy",
            "Save to Original (Overwrite)"
        };

        new AlertDialog.Builder(this)
            .setTitle("Save PDF")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Save as new copy
                    saveAsNewCopy();
                } else {
                    // Overwrite original
                    confirmOverwriteOriginal();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Save PDF as a new copy to Downloads folder
     */
    private void saveAsNewCopy() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Capture any current drawings
        if (binding.drawingView.hasDrawings()) {
            Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
            if (drawingBitmap != null) {
                pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
            }
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // First save to temp location
                String tempPath = pdfEditManager.saveEditedPdf();
                File tempFile = new File(tempPath);

                // Generate proper filename
                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String newFileName = baseName + "_edited_" + timestamp + ".pdf";

                // Save to Downloads folder
                String finalPath = saveFileToDownloads(tempFile, newFileName);

                // Delete temp file
                tempFile.delete();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    if (finalPath != null) {
                        // Add to recent files
                        addToRecentFiles(finalPath, newFileName);
                        showSavedFileDialogWithOpen(finalPath, newFileName, "PDF saved to Downloads:");
                    } else {
                        Toast.makeText(this, "Error saving to Downloads", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Save file to Downloads folder and return the path
     */
    private String saveFileToDownloads(File sourceFile, String fileName) {
        try {
            // Determine MIME type based on file extension
            String mimeType = "application/pdf";
            if (fileName.toLowerCase().endsWith(".docx")) {
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (fileName.toLowerCase().endsWith(".doc")) {
                mimeType = "application/msword";
            } else if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.toLowerCase().endsWith(".png")) {
                mimeType = "image/png";
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                         java.io.FileInputStream in = new java.io.FileInputStream(sourceFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    // Return a path that can be used
                    return android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + fileName;
                }
            } else {
                // Older Android versions
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                File destFile = new File(downloadsDir, fileName);

                try (java.io.FileInputStream in = new java.io.FileInputStream(sourceFile);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                return destFile.getAbsolutePath();
            }
        } catch (Exception e) {
            AppLogger.e("PdfViewerActivity", "Error", e);
        }
        return null;
    }

    /**
     * Add saved file to recent files list
     */
    private void addToRecentFiles(String path, String name) {
        PreferencesManager prefsManager = new PreferencesManager(this);
        // Detect file type from extension
        String fileType = "pdf";
        String lowerName = name.toLowerCase();
        if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
            fileType = "doc";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")) {
            fileType = "image";
        }
        RecentFile recentFile = new RecentFile(name, path, fileType, System.currentTimeMillis());
        prefsManager.addRecentFile(recentFile);
    }

    /**
     * Show saved file dialog with option to open the file
     */
    private void showSavedFileDialogWithOpen(String path, String fileName, String message) {
        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage(message + "\n\n" + fileName)
                .setPositiveButton("Open", (d, w) -> {
                    // Open the saved PDF
                    filePath = path;
                    this.fileName = fileName;
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(fileName);
                    }
                    reloadPdf();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /**
     * Confirm before overwriting the original file
     */
    private void confirmOverwriteOriginal() {
        new AlertDialog.Builder(this)
            .setTitle("Overwrite Original?")
            .setMessage("This will replace the original PDF file. This action cannot be undone.\n\nAre you sure?")
            .setPositiveButton("Overwrite", (dialog, which) -> saveToOriginal())
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Save edits directly to the original PDF file
     */
    private void saveToOriginal() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Capture any current drawings
        if (binding.drawingView.hasDrawings()) {
            Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
            if (drawingBitmap != null) {
                pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
            }
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // First save to temp location
                String tempPath = pdfEditManager.saveEditedPdf();
                File tempFile = new File(tempPath);
                File originalFile = new File(filePath);

                // CRITICAL FIX: Use try-with-resources to prevent stream leak
                try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(originalFile)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.flush();
                }

                // Delete temp file
                tempFile.delete();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "PDF saved to original file!", Toast.LENGTH_SHORT).show();

                    // Add to recent files
                    addToRecentFiles(filePath, fileName);

                    // Reload the PDF to show saved changes
                    reloadPdf();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Perform undo operation - undoes the last edit action
     */
    private void performUndo() {
        // First try to undo drawing view strokes
        if (binding.drawingView.hasDrawings()) {
            binding.drawingView.undo();
            Toast.makeText(this, "Undo: Drawing removed", Toast.LENGTH_SHORT).show();
            return;
        }

        // Then try to undo PDF edit manager actions
        if (pdfEditManager.canUndo()) {
            pdfEditManager.undo();
            Toast.makeText(this, "Undo: Edit removed", Toast.LENGTH_SHORT).show();
            // Refresh the page display
            renderPage(currentPage);
            return;
        }

        Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
    }

    private void performSave() {
        binding.progressBar.setVisibility(View.VISIBLE);

        if (binding.drawingView.hasDrawings()) {
            Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
            if (drawingBitmap != null) {
                pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
            }
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String savedPath = pdfEditManager.saveEditedPdf();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.drawingView.clearAll();
                    pdfEditManager.clearAllAnnotations();
                    showSavedFileDialog(savedPath, "PDF saved to:");
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showSavedFileDialog(String path, String message) {
        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage(message + "\n" + path)
                .setPositiveButton("OK", null)
                .setNeutralButton("Open", (d, w) -> openSavedPdf(path))
                .show();
    }

    private void openSavedPdf(String path) {
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer available", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== HELPER METHODS ====================

    private List<Integer> parsePageSpec(String spec) {
        List<Integer> pages = new ArrayList<>();
        try {
            String[] parts = spec.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.contains("-")) {
                    String[] range = part.split("-");
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    for (int i = start; i <= end; i++) {
                        if (i >= 1 && i <= totalPages) {
                            pages.add(i);
                        }
                    }
                } else {
                    int page = Integer.parseInt(part);
                    if (page >= 1 && page <= totalPages) {
                        pages.add(page);
                    }
                }
            }
        } catch (Exception e) {
            // Invalid format
        }
        return pages;
    }

    private void updatePageInfo() {
        String pageInfo = String.format("%d / %d", currentPage + 1, totalPages);
        if (notesManager.pageHasNotes(filePath, currentPage)) {
            pageInfo += " *";
        }
        binding.tvPageInfo.setText(pageInfo);

        if (isEditMode && getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Mode - Page " + (currentPage + 1));
        }
    }

    private void updateZoomLabel() {
        binding.tvZoom.setText(String.format("%d%%", (int) (currentZoom * 100)));
    }

    private void zoomIn() {
        if (currentZoom < 3.0f) {
            currentZoom += 0.25f;
            updateZoomLabel();
            rerenderPages();
        }
    }

    private void zoomOut() {
        if (currentZoom > 0.5f) {
            currentZoom -= 0.25f;
            updateZoomLabel();
            rerenderPages();
        }
    }

    private void rerenderPages() {
        binding.progressBar.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;
                runOnUiThread(() -> renderPage(pageIndex));
            }
            runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
        });
    }

    private void goToPreviousPage() {
        if (currentPage > 0) {
            if (isEditMode && binding.drawingView.hasDrawings()) {
                Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
                if (drawingBitmap != null) {
                    pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
                }
                binding.drawingView.clearAll();
            }
            currentPage--;
            scrollToPage(currentPage);
            updatePageInfo();
        }
    }

    private void goToNextPage() {
        if (currentPage < totalPages - 1) {
            if (isEditMode && binding.drawingView.hasDrawings()) {
                Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
                if (drawingBitmap != null) {
                    pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
                }
                binding.drawingView.clearAll();
            }
            currentPage++;
            scrollToPage(currentPage);
            updatePageInfo();
        }
    }

    private void scrollToPage(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < pageViews.size()) {
            ImageView pageView = pageViews.get(pageIndex);
            binding.scrollView.smoothScrollTo(0, pageView.getTop());
        }
    }

    private void showPageNotesDialog() {
        List<Note> pageNotes = notesManager.getNotesForPage(filePath, currentPage);
        if (pageNotes.isEmpty()) {
            showAddNoteDialog();
        } else {
            // Show existing notes
            StringBuilder notesList = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            for (Note note : pageNotes) {
                notesList.append(note.getContent()).append("\n");
                notesList.append("(").append(dateFormat.format(new Date(note.getCreatedAt()))).append(")\n\n");
            }

            new AlertDialog.Builder(this)
                    .setTitle("Notes - Page " + (currentPage + 1))
                    .setMessage(notesList.toString().trim())
                    .setPositiveButton("Add More", (d, w) -> showAddNoteDialog())
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private void showAddNoteDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter your note...");
        input.setMinLines(3);

        new AlertDialog.Builder(this)
                .setTitle("Add Note - Page " + (currentPage + 1))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String noteText = input.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        Note note = new Note(filePath, currentPage, noteText);
                        notesManager.addNote(note);
                        Toast.makeText(this, "Note added", Toast.LENGTH_SHORT).show();
                        updatePageInfo();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareDocument() {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }


    private void showAddTextDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        // Page number
        android.widget.TextView pageLabel = new android.widget.TextView(this);
        pageLabel.setText("Page number (1-" + totalPages + "):");
        layout.addView(pageLabel);

        EditText pageInput = new EditText(this);
        pageInput.setText(String.valueOf(currentPage + 1));
        pageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(pageInput);

        // Position info
        android.widget.TextView posLabel = new android.widget.TextView(this);
        posLabel.setText("\nPosition (% from left, % from top):");
        layout.addView(posLabel);

        LinearLayout posRow = new LinearLayout(this);
        posRow.setOrientation(LinearLayout.HORIZONTAL);

        EditText leftInput = new EditText(this);
        leftInput.setHint("Left %");
        leftInput.setText("10");
        leftInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        leftInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        posRow.addView(leftInput);

        EditText topInput = new EditText(this);
        topInput.setHint("Top %");
        topInput.setText("10");
        topInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        topInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        posRow.addView(topInput);

        layout.addView(posRow);

        // Text input
        android.widget.TextView textLabel = new android.widget.TextView(this);
        textLabel.setText("\nText to add:");
        layout.addView(textLabel);

        EditText textInput = new EditText(this);
        textInput.setHint("Enter your text here");
        textInput.setMinLines(3);
        layout.addView(textInput);

        // Font size
        android.widget.TextView sizeLabel = new android.widget.TextView(this);
        sizeLabel.setText("\nFont size:");
        layout.addView(sizeLabel);

        EditText sizeInput = new EditText(this);
        sizeInput.setText("12");
        sizeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(sizeInput);

        new AlertDialog.Builder(this)
                .setTitle("Add Text to PDF")
                .setView(layout)
                .setPositiveButton("Add Text", (dialog, which) -> {
                    try {
                        int pageNum = Integer.parseInt(pageInput.getText().toString());
                        float leftPct = Float.parseFloat(leftInput.getText().toString()) / 100f;
                        float topPct = Float.parseFloat(topInput.getText().toString()) / 100f;
                        String text = textInput.getText().toString();
                        float fontSize = Float.parseFloat(sizeInput.getText().toString());

                        if (text.isEmpty()) {
                            Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        addTextToPdf(pageNum, leftPct, topPct, text, fontSize);
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addTextToPdf(int pageNumber, float leftPct, float topPct, String text, float fontSize) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Get page dimensions
                float[] dims = PdfCoverReplace.getPageDimensions(filePath, pageNumber);
                if (dims == null) {
                    throw new Exception("Cannot read page dimensions");
                }

                float pdfWidth = dims[0];
                float pdfHeight = dims[1];

                // Calculate position (convert from top-left to PDF bottom-left coords)
                float x = leftPct * pdfWidth;
                float y = pdfHeight - (topPct * pdfHeight) - fontSize;

                // Create output file
                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String outputName = baseName + "_edited.pdf";

                File outputFile = PdfAnnotationEditor.addTransparentText(
                        filePath, getCacheDir(), outputName,
                        pageNumber, x, y, text, fontSize, Color.BLACK, 1.0f);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("Text Added")
                            .setMessage("Text has been added to the PDF.\n\nThe original PDF is unchanged.")
                            .setPositiveButton("Open New PDF", (d, w) -> {
                                Intent intent = new Intent(this, PdfViewerActivity.class);
                                intent.putExtra("file_path", outputFile.getAbsolutePath());
                                intent.putExtra("file_name", outputFile.getName());
                                startActivity(intent);
                                finish();
                            })
                            .setNeutralButton("Replace Original", (d, w) -> {
                                saveEditedToOriginal(outputFile);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddStickyNoteDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        // Page number
        android.widget.TextView pageLabel = new android.widget.TextView(this);
        pageLabel.setText("Page number (1-" + totalPages + "):");
        layout.addView(pageLabel);

        EditText pageInput = new EditText(this);
        pageInput.setText(String.valueOf(currentPage + 1));
        pageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(pageInput);

        // Note text
        android.widget.TextView textLabel = new android.widget.TextView(this);
        textLabel.setText("\nNote content:");
        layout.addView(textLabel);

        EditText textInput = new EditText(this);
        textInput.setHint("Enter your note here");
        textInput.setMinLines(3);
        layout.addView(textInput);

        new AlertDialog.Builder(this)
                .setTitle("Add Sticky Note")
                .setMessage("Adds a note icon that shows your text when clicked.")
                .setView(layout)
                .setPositiveButton("Add Note", (dialog, which) -> {
                    try {
                        int pageNum = Integer.parseInt(pageInput.getText().toString());
                        String text = textInput.getText().toString();

                        if (text.isEmpty()) {
                            Toast.makeText(this, "Please enter note text", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        addStickyNoteToPdf(pageNum, text);
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addStickyNoteToPdf(int pageNumber, String noteText) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Get page dimensions
                float[] dims = PdfCoverReplace.getPageDimensions(filePath, pageNumber);
                if (dims == null) {
                    throw new Exception("Cannot read page dimensions");
                }

                float pdfHeight = dims[1];

                // Position note at top-right of page
                float x = dims[0] - 50;
                float y = pdfHeight - 50;

                // Create sticky note
                List<PdfAnnotationEditor.TextNote> notes = new ArrayList<>();
                PdfAnnotationEditor.TextNote note = new PdfAnnotationEditor.TextNote(pageNumber, x, y, noteText);
                note.asSticky();
                note.setColors(Color.YELLOW, Color.YELLOW);
                notes.add(note);

                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String outputName = baseName + "_noted.pdf";

                File outputFile = PdfAnnotationEditor.addTextAnnotations(
                        filePath, getCacheDir(), outputName, notes);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("Note Added")
                            .setMessage("Sticky note has been added.\n\nThe original PDF is unchanged.")
                            .setPositiveButton("Open New PDF", (d, w) -> {
                                Intent intent = new Intent(this, PdfViewerActivity.class);
                                intent.putExtra("file_path", outputFile.getAbsolutePath());
                                intent.putExtra("file_name", outputFile.getName());
                                startActivity(intent);
                                finish();
                            })
                            .setNeutralButton("Replace Original", (d, w) -> {
                                saveEditedToOriginal(outputFile);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveEditedToOriginal(File editedFile) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Copy edited file to original location
                File originalFile = new File(filePath);
                java.io.FileInputStream in = new java.io.FileInputStream(editedFile);
                java.io.FileOutputStream out = new java.io.FileOutputStream(originalFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                in.close();
                out.close();

                // Delete temp file
                editedFile.delete();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Saved to original file!", Toast.LENGTH_SHORT).show();
                    // Reload the PDF
                    loadPdf();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==================== EDIT TEXT MODE (Cover & Replace) ====================

    private void toggleEditTextMode() {
        isEditTextMode = !isEditTextMode;

        if (isEditTextMode) {
            // Exit draw mode if active
            if (isEditMode) {
                toggleEditMode();
            }

            Toast.makeText(this, "Tap on the page where you want to edit text", Toast.LENGTH_LONG).show();
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("EDIT MODE - Tap page to edit");
            }

            // Set click listeners on page views for edit mode
            for (int i = 0; i < pageViews.size(); i++) {
                ImageView pageView = pageViews.get(i);
                final int pageIndex = i;

                pageView.setOnClickListener(v -> {
                    if (isEditTextMode) {
                        selectedPageIndex = pageIndex;
                        showEditAreaDialog(pageIndex + 1, pageView);
                    }
                });
            }
        } else {
            // Save edits if any, then exit mode
            if (!editOperations.isEmpty()) {
                saveEditOperations();
            } else {
                exitEditTextMode();
            }
        }
    }

    private void exitEditTextMode() {
        isEditTextMode = false;
        selectedPageIndex = -1;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        // Restore normal click listeners
        for (int i = 0; i < pageViews.size(); i++) {
            ImageView pageView = pageViews.get(i);
            final int pageIndex = i;
            pageView.setOnClickListener(v -> {
                currentPage = pageIndex;
                updatePageInfo();
            });
        }

        Toast.makeText(this, "Edit mode closed", Toast.LENGTH_SHORT).show();
    }

    private void showEditAreaDialog(int pageNumber, ImageView pageView) {
        // Get PDF page dimensions
        float[] pageDims = PdfCoverReplace.getPageDimensions(filePath, pageNumber);
        if (pageDims == null) {
            Toast.makeText(this, "Error reading page dimensions", Toast.LENGTH_SHORT).show();
            return;
        }

        float pdfWidth = pageDims[0];
        float pdfHeight = pageDims[1];

        // Create dialog layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // Instructions
        android.widget.TextView instructions = new android.widget.TextView(this);
        instructions.setText("Enter area to edit (% from top-left):\nPage size: " + (int)pdfWidth + " x " + (int)pdfHeight);
        instructions.setPadding(0, 0, 0, 20);
        layout.addView(instructions);

        // Position inputs in a grid
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        EditText leftInput = new EditText(this);
        leftInput.setHint("Left %");
        leftInput.setText("10");
        leftInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        leftInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(leftInput);

        EditText topInput = new EditText(this);
        topInput.setHint("Top %");
        topInput.setText("10");
        topInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        topInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(topInput);
        layout.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        EditText widthInput = new EditText(this);
        widthInput.setHint("Width %");
        widthInput.setText("30");
        widthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        widthInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(widthInput);

        EditText heightInput = new EditText(this);
        heightInput.setHint("Height %");
        heightInput.setText("5");
        heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        heightInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(heightInput);
        layout.addView(row2);

        // Replacement text
        EditText textInput = new EditText(this);
        textInput.setHint("Enter replacement text");
        textInput.setMinLines(2);
        layout.addView(textInput);

        // Font size
        EditText sizeInput = new EditText(this);
        sizeInput.setHint("Font size (default: 12)");
        sizeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sizeInput.setText(String.valueOf((int) editTextSize));
        layout.addView(sizeInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit Text on Page " + pageNumber)
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        float leftPct = Float.parseFloat(leftInput.getText().toString()) / 100f;
                        float topPct = Float.parseFloat(topInput.getText().toString()) / 100f;
                        float widthPct = Float.parseFloat(widthInput.getText().toString()) / 100f;
                        float heightPct = Float.parseFloat(heightInput.getText().toString()) / 100f;

                        // Convert percentage to PDF coordinates
                        float pdfX = leftPct * pdfWidth;
                        float pdfW = widthPct * pdfWidth;
                        float pdfH = heightPct * pdfHeight;
                        float pdfY = pdfHeight - (topPct * pdfHeight) - pdfH; // PDF Y is from bottom

                        String newText = textInput.getText().toString();
                        try {
                            editTextSize = Float.parseFloat(sizeInput.getText().toString());
                        } catch (NumberFormatException e) {
                            editTextSize = 12f;
                        }

                        // Add edit operation
                        editOperations.add(new PdfCoverReplace.EditOperation(
                                pageNumber, pdfX, pdfY, pdfW, pdfH,
                                newText, editTextSize, editTextColor
                        ));

                        Toast.makeText(this, "Edit added! Tap another area or tap 'Edit Text' to save.", Toast.LENGTH_SHORT).show();

                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Color", (dialog, which) -> {
                    showEditColorPickerSimple(pageNumber, pageView);
                })
                .show();
    }

    private void showEditColorPickerSimple(int pageNumber, ImageView pageView) {
        String[] colors = {"Black", "Red", "Blue", "Green", "White"};
        int[] colorValues = {Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.WHITE};

        new AlertDialog.Builder(this)
                .setTitle("Select Text Color")
                .setItems(colors, (dialog, which) -> {
                    editTextColor = colorValues[which];
                    Toast.makeText(this, colors[which] + " selected", Toast.LENGTH_SHORT).show();
                    showEditAreaDialog(pageNumber, pageView);
                })
                .show();
    }

    private void saveEditOperations() {
        if (editOperations.isEmpty()) {
            Toast.makeText(this, "No edits to save", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Generate output file name
                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String outputFileName = baseName + "_edited.pdf";

                // Apply edits to PDF
                File outputFile = PdfCoverReplace.applyEdits(
                        filePath,
                        getCacheDir(),
                        outputFileName,
                        editOperations
                );

                // Copy to Downloads folder using MediaStore (Android 10+)
                String savedPath = outputFile.getAbsolutePath();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, outputFileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                             java.io.FileInputStream in = new java.io.FileInputStream(outputFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }

                String finalPath = savedPath;
                String finalFileName = outputFileName;

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    editOperations.clear();
                    isEditTextMode = false;

                    new AlertDialog.Builder(this)
                            .setTitle("PDF Saved!")
                            .setMessage("Your edited PDF saved to:\n\nDownloads/" + finalFileName + "\n\nOriginal PDF is unchanged.")
                            .setPositiveButton("Open Edited", (d, w) -> {
                                filePath = finalPath;
                                fileName = finalFileName;
                                try {
                                    if (pdfRenderer != null) pdfRenderer.close();
                                    if (fileDescriptor != null) fileDescriptor.close();
                                } catch (IOException e) {
                                    AppLogger.e("PdfViewerActivity", "Error", e);
                                }
                                loadPdf();
                                if (getSupportActionBar() != null) {
                                    getSupportActionBar().setTitle(fileName);
                                }
                            })
                            .setNegativeButton("Close", (d, w) -> {
                                exitEditTextMode();
                            })
                            .show();
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==================== VISUAL EDIT MODE (Tap-to-Edit) ====================

    private void toggleVisualEditMode() {
        if (isVisualEditMode) {
            exitVisualEditMode();
        } else {
            enterVisualEditMode();
        }
    }

    private void enterVisualEditMode() {
        // Exit other modes if active
        if (isEditMode) {
            finishExitEditMode();
        }
        if (isEditTextMode) {
            exitEditTextMode();
        }

        // Clear any previous visual edit state
        isVisualEditMode = false;
        visualTextBlocks.clear();

        binding.progressBar.setVisibility(View.VISIBLE);

        // Extract text blocks for current page in background
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Extract text blocks for current page
                List<VisualPdfEditor.TextBlock> blocks =
                        VisualPdfEditor.extractTextBlocksForPage(filePath, currentPage + 1);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    if (blocks.isEmpty()) {
                        Toast.makeText(this, "No text found on this page", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    visualTextBlocks.clear();
                    visualTextBlocks.addAll(blocks);

                    isVisualEditMode = true;

                    // Show instructions
                    showVisualEditInstructions(blocks.size());

                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle("VISUAL EDIT - Tap text to edit");
                    }

                    // Show clickable text block list
                    showTextBlocksList();
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error extracting text: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showVisualEditInstructions(int blockCount) {
        new AlertDialog.Builder(this)
                .setTitle("Visual Edit Mode")
                .setMessage("Found " + blockCount + " text blocks on page " + (currentPage + 1) + ".\n\n" +
                        "How to edit:\n" +
                        "1. Tap on any text line to edit it\n" +
                        "2. Type your new text\n" +
                        "3. Save when done\n\n" +
                        "Note: The original font/size may not be exactly preserved.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showTextBlocksList() {
        if (visualTextBlocks.isEmpty()) {
            Toast.makeText(this, "No text blocks found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create list of text blocks for selection
        String[] items = new String[visualTextBlocks.size()];
        for (int i = 0; i < visualTextBlocks.size(); i++) {
            String text = visualTextBlocks.get(i).text;
            // Truncate long text for display
            if (text.length() > 50) {
                text = text.substring(0, 47) + "...";
            }
            // Mark edited blocks
            if (visualTextBlocks.get(i).isEdited) {
                items[i] = "[EDITED] " + text;
            } else {
                items[i] = (i + 1) + ". " + text;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tap a text line to edit (Page " + (currentPage + 1) + ")");
        builder.setItems(items, (dialog, which) -> {
            showEditTextBlockDialog(visualTextBlocks.get(which), which);
        });
        builder.setPositiveButton("Save Edits", (dialog, which) -> {
            saveVisualEdits();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            exitVisualEditMode();
        });
        builder.setNeutralButton("Refresh List", (dialog, which) -> {
            showTextBlocksList();
        });
        builder.setOnCancelListener(dialog -> {
            // Show list again if dismissed without action
            if (isVisualEditMode && hasVisualEdits()) {
                askToSaveVisualEdits();
            } else if (isVisualEditMode) {
                showTextBlocksList();
            }
        });
        builder.show();
    }

    private void showEditTextBlockDialog(VisualPdfEditor.TextBlock block, int index) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        // Show original text
        android.widget.TextView originalLabel = new android.widget.TextView(this);
        originalLabel.setText("Original text:");
        originalLabel.setTextColor(Color.GRAY);
        layout.addView(originalLabel);

        android.widget.TextView originalText = new android.widget.TextView(this);
        originalText.setText(block.text);
        originalText.setPadding(0, 5, 0, 20);
        originalText.setTextColor(Color.BLACK);
        layout.addView(originalText);

        // Edit text input
        android.widget.TextView editLabel = new android.widget.TextView(this);
        editLabel.setText("New text:");
        layout.addView(editLabel);

        EditText editInput = new EditText(this);
        editInput.setText(block.isEdited ? block.newText : block.text);
        editInput.setMinLines(2);
        editInput.setSelection(editInput.getText().length()); // Cursor at end
        layout.addView(editInput);

        // Position info
        android.widget.TextView posInfo = new android.widget.TextView(this);
        posInfo.setText(String.format("\nPosition: %.0f, %.0f (Page %d)", block.pdfX, block.pdfY, block.pageNumber));
        posInfo.setTextColor(Color.GRAY);
        posInfo.setTextSize(11);
        layout.addView(posInfo);

        new AlertDialog.Builder(this)
                .setTitle("Edit Text")
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    String newText = editInput.getText().toString();
                    if (!newText.equals(block.text)) {
                        block.edit(newText);
                        Toast.makeText(this, "Text updated", Toast.LENGTH_SHORT).show();
                    }
                    showTextBlocksList();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    showTextBlocksList();
                })
                .setNeutralButton("Revert", (dialog, which) -> {
                    block.isEdited = false;
                    block.newText = null;
                    Toast.makeText(this, "Reverted to original", Toast.LENGTH_SHORT).show();
                    showTextBlocksList();
                })
                .show();
    }

    private boolean hasVisualEdits() {
        for (VisualPdfEditor.TextBlock block : visualTextBlocks) {
            if (block.isEdited) return true;
        }
        return false;
    }

    private void askToSaveVisualEdits() {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved Edits")
                .setMessage("You have unsaved text edits. What would you like to do?")
                .setPositiveButton("Save", (d, w) -> saveVisualEdits())
                .setNegativeButton("Discard", (d, w) -> exitVisualEditMode())
                .setNeutralButton("Continue Editing", (d, w) -> showTextBlocksList())
                .show();
    }

    private void saveVisualEdits() {
        // Filter to only edited blocks
        List<VisualPdfEditor.TextBlock> editedBlocks = new ArrayList<>();
        for (VisualPdfEditor.TextBlock block : visualTextBlocks) {
            if (block.isEdited) {
                editedBlocks.add(block);
            }
        }

        if (editedBlocks.isEmpty()) {
            Toast.makeText(this, "No edits to save", Toast.LENGTH_SHORT).show();
            exitVisualEditMode();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Generate output file name
                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String outputFileName = baseName + "_edited.pdf";

                // Use image-based copy editor for exact visual preservation
                File outputFile = PdfImageCopyEditor.applyVisualEdits(
                        this,
                        filePath,
                        getCacheDir(),
                        outputFileName,
                        editedBlocks
                );

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("Edits Saved!")
                            .setMessage("Saved " + editedBlocks.size() + " text edit(s).\n\n" +
                                    "The original PDF is unchanged.")
                            .setPositiveButton("Open Edited PDF", (d, w) -> {
                                // Open the new file in this activity
                                filePath = outputFile.getAbsolutePath();
                                fileName = outputFile.getName();
                                try {
                                    if (pdfRenderer != null) pdfRenderer.close();
                                    if (fileDescriptor != null) fileDescriptor.close();
                                } catch (IOException e) {
                                    AppLogger.e("PdfViewerActivity", "Error", e);
                                }
                                loadPdf();
                                if (getSupportActionBar() != null) {
                                    getSupportActionBar().setTitle(fileName);
                                }
                                exitVisualEditMode();
                            })
                            .setNeutralButton("Save to Original", (d, w) -> {
                                saveEditedToOriginal(outputFile);
                                exitVisualEditMode();
                            })
                            .setNegativeButton("Close", (d, w) -> {
                                exitVisualEditMode();
                            })
                            .show();
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exitVisualEditMode() {
        isVisualEditMode = false;
        visualTextBlocks.clear();

        // Reset the pdfEditManager for fresh start next time
        pdfEditManager = new PdfEditManager(this, filePath);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        Toast.makeText(this, "Visual edit mode closed", Toast.LENGTH_SHORT).show();
    }

    // ==================== EDIT AS COPY (Image-based) ====================

    /**
     * Edit PDF as a copy - creates an exact visual copy and allows text editing like Word
     * The original PDF format is 100% preserved because we use image-based rendering
     */
    private void editAsCopy() {
        new AlertDialog.Builder(this)
                .setTitle("Edit Copy (Like Word)")
                .setMessage("This will:\n\n" +
                        "1. Create an exact visual copy of your PDF\n" +
                        "2. Open a text editor where you can edit like Word\n" +
                        "3. Save your changes to the PDF copy\n\n" +
                        "The original PDF will NOT be changed.\n" +
                        "The copy will look exactly like the original!")
                .setPositiveButton("Continue", (d, w) -> startEditAsCopy())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startEditAsCopy() {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Extract all text from PDF with page markers
                String allText = PdfCopyEditor.getAllText(filePath);
                int pageCount = PdfCopyEditor.getPageCount(filePath);

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    showCopyEditDialog(allText, pageCount);
                });

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showCopyEditDialog(String originalText, int pageCount) {
        // Create a full-screen edit dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Info text
        android.widget.TextView info = new android.widget.TextView(this);
        info.setText("Edit your PDF text below (" + pageCount + " page" + (pageCount > 1 ? "s" : "") + ").\n" +
                     "Page markers [PAGE:n] show page breaks - don't remove them!");
        info.setTextColor(Color.GRAY);
        info.setPadding(0, 0, 0, 16);
        layout.addView(info);

        // Scrollable edit text
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        EditText editText = new EditText(this);
        editText.setText(originalText);
        editText.setTextSize(14f);
        editText.setMinLines(20);
        editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editText.setBackgroundColor(Color.WHITE);
        editText.setPadding(16, 16, 16, 16);
        scrollView.addView(editText);
        layout.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit PDF Copy")
                .setView(layout)
                .setPositiveButton("Save as PDF", null) // Set later to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Save to Original", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            // Save as new PDF
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String editedText = editText.getText().toString();
                if (editedText.equals(originalText)) {
                    Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                saveCopyWithEdits(editedText, false);
            });

            // Save to original (replace)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String editedText = editText.getText().toString();
                if (editedText.equals(originalText)) {
                    Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(this)
                        .setTitle("Replace Original?")
                        .setMessage("This will REPLACE the original PDF file:\n\n" + fileName + "\n\nAre you sure?")
                        .setPositiveButton("Replace", (d2, w2) -> {
                            dialog.dismiss();
                            saveCopyWithEdits(editedText, true);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });

        dialog.show();

        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.85));
        }
    }

    private void saveCopyWithEdits(String editedText, boolean replaceOriginal) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                // Parse edited text into page map
                java.util.Map<Integer, String> pageEdits = PdfCopyEditor.parseEditedText(editedText);

                // Generate output file name
                String baseName = fileName;
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                String outputFileName = baseName + "_edited.pdf";

                // Create edited copy using image-based method
                File outputFile = PdfCopyEditor.createEditedCopy(
                        this,
                        filePath,
                        getCacheDir(),
                        outputFileName,
                        pageEdits
                );

                if (replaceOriginal) {
                    // Copy to original location
                    File originalFile = new File(filePath);
                    try (java.io.FileInputStream in = new java.io.FileInputStream(outputFile);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(originalFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    outputFile.delete();

                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Original PDF updated!", Toast.LENGTH_SHORT).show();
                        // Reload the PDF
                        reloadPdf();
                    });
                } else {
                    // Save to Downloads
                    String finalPath = outputFile.getAbsolutePath();
                    String savedLocation = "Cache/" + outputFileName;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, outputFileName);
                        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
                        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_DOWNLOADS);

                        Uri uri = getContentResolver().insert(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                                 java.io.FileInputStream in = new java.io.FileInputStream(outputFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                            savedLocation = "Downloads/" + outputFileName;
                        }
                    }

                    String pathToOpen = finalPath;
                    String fileNameToOpen = outputFileName;
                    String locationToShow = savedLocation;

                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);

                        new AlertDialog.Builder(this)
                                .setTitle("PDF Saved!")
                                .setMessage("Your edited PDF has been saved to:\n\n" + locationToShow +
                                        "\n\nThe original PDF is unchanged.")
                                .setPositiveButton("Open Edited PDF", (d, w) -> {
                                    filePath = pathToOpen;
                                    fileName = fileNameToOpen;
                                    if (getSupportActionBar() != null) {
                                        getSupportActionBar().setTitle(fileName);
                                    }
                                    reloadPdf();
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    });
                }

            } catch (Exception e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==================== ACTIVITY CALLBACKS ====================

    // Note: onActivityResult removed - using ActivityResultLauncher pattern instead
    // (pickImageLauncher and pickPdfMergeLauncher handle the callbacks)

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performSave();
            } else {
                Toast.makeText(this, "Permission required to save PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            exitEditMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_undo) {
            performUndo();
            return true;
        } else if (id == R.id.action_save) {
            showSaveOptionsDialog();
            return true;
        } else if (id == R.id.action_edit) {
            showPdfEditorBottomSheet();
            return true;
        } else if (id == R.id.action_draw) {
            toggleEditMode();
            return true;
        } else if (id == R.id.action_visual_edit) {
            toggleVisualEditMode();
            return true;
        } else if (id == R.id.action_edit_text) {
            toggleEditTextMode();
            return true;
        } else if (id == R.id.action_add_note) {
            showPageNotesDialog();
            return true;
        } else if (id == R.id.action_edit_copy) {
            editAsCopy();
            return true;
        } else if (id == R.id.action_merge_pdf) {
            showMergePdfDialog();
            return true;
        } else if (id == R.id.action_split_pdf) {
            showSplitPdfDialog();
            return true;
        } else if (id == R.id.action_add_page_numbers) {
            showAddPageNumbersDialog();
            return true;
        } else if (id == R.id.action_pdf_to_images) {
            showPdfToImagesDialog();
            return true;
        } else if (id == R.id.action_add_watermark) {
            showAddWatermarkDialog();
            return true;
        } else if (id == R.id.action_delete_pages) {
            showDeletePagesDialog();
            return true;
        } else if (id == R.id.action_rotate_pages) {
            showRotatePagesDialog();
            return true;
        } else if (id == R.id.action_copy_pdf) {
            showCopyPdfDialog();
            return true;
        } else if (id == R.id.action_pdf_info) {
            showPdfInfo();
            return true;
        } else if (id == R.id.action_share) {
            shareDocument();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ==================== PDF TOOLS ====================

    private void showMergePdfDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Merge PDFs")
                .setMessage("Select multiple PDF files to merge with the current PDF.\n\nCurrent PDF will be first.")
                .setPositiveButton("Select PDFs", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("application/pdf");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    pickPdfMergeLauncher.launch(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSplitPdfDialog() {
        int pageCount = totalPages;

        String[] options = {"Split into single pages", "Extract specific pages"};

        new AlertDialog.Builder(this)
                .setTitle("Split PDF (" + pageCount + " pages)")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        splitIntoSinglePages();
                    } else {
                        showExtractPagesToolDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void splitIntoSinglePages() {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            PdfTools.PdfResult result = PdfTools.splitPdf(filePath, getCacheDir());

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                showToolResultDialog(result, "Split PDF");
            });
        });
    }

    private void showExtractPagesToolDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.TextView info = new android.widget.TextView(this);
        info.setText("Enter page numbers to extract (e.g., 1,3,5-8):");
        layout.addView(info);

        EditText input = new EditText(this);
        input.setHint("1,2,3 or 1-5");
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Extract Pages")
                .setView(layout)
                .setPositiveButton("Extract", (d, w) -> {
                    String pagesStr = input.getText().toString();
                    List<Integer> pages = parsePageNumbers(pagesStr, totalPages);
                    if (pages.isEmpty()) {
                        Toast.makeText(this, "Invalid page numbers", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    extractPagesWithTool(pages);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<Integer> parsePageNumbers(String input, int maxPage) {
        List<Integer> pages = new ArrayList<>();
        String[] parts = input.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end && i <= maxPage; i++) {
                            if (i >= 1 && !pages.contains(i)) pages.add(i);
                        }
                    } catch (NumberFormatException e) {
                        AppLogger.w("Invalid page range format: " + part);
                    }
                }
            } else {
                try {
                    int page = Integer.parseInt(part);
                    if (page >= 1 && page <= maxPage && !pages.contains(page)) {
                        pages.add(page);
                    }
                } catch (NumberFormatException e) {
                    AppLogger.w("Invalid page number: " + part);
                }
            }
        }
        return pages;
    }

    private void extractPagesWithTool(List<Integer> pages) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String outputName = baseName + "_extracted_" + timestamp + ".pdf";

            PdfTools.PdfResult result = PdfTools.extractPages(filePath, getCacheDir(), outputName, pages);

            if (result.success && result.filePath != null) {
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    addToRecentFiles(savedPath, outputName);
                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "Pages extracted! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving PDF", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddPageNumbersDialog() {
        String[] positions = {"Bottom Center", "Bottom Left", "Bottom Right", "Top Center", "Top Left", "Top Right"};
        final String[] positionValues = {"bottom-center", "bottom-left", "bottom-right", "top-center", "top-left", "top-right"};
        final int[] selectedPosition = {0};

        new AlertDialog.Builder(this)
                .setTitle("Add Page Numbers")
                .setSingleChoiceItems(positions, 0, (d, which) -> selectedPosition[0] = which)
                .setPositiveButton("Add", (d, w) -> addPageNumbers(positionValues[selectedPosition[0]]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addPageNumbers(String position) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String outputName = baseName + "_numbered_" + timestamp + ".pdf";

            PdfTools.PdfResult result = PdfTools.addPageNumbers(filePath, getCacheDir(), outputName, position, 1);

            if (result.success && result.filePath != null) {
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    addToRecentFiles(savedPath, outputName);
                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "Page numbers added! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving PDF", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showPdfToImagesDialog() {
        String[] formats = {"JPG (Smaller size)", "PNG (Better quality)"};
        final String[] formatValues = {"jpg", "png"};
        final int[] selectedFormat = {0};

        new AlertDialog.Builder(this)
                .setTitle("PDF to Images")
                .setMessage("Convert all " + totalPages + " pages to images")
                .setSingleChoiceItems(formats, 0, (d, which) -> selectedFormat[0] = which)
                .setPositiveButton("Convert", (d, w) -> pdfToImages(formatValues[selectedFormat[0]]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pdfToImages(String format) {
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Converting to images...", Toast.LENGTH_SHORT).show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File outputDir = new File(getCacheDir(), baseName + "_images_" + timestamp);

                PdfTools.PdfResult result = PdfTools.pdfToImages(this, filePath, outputDir, format, 150);

                if (result.success && outputDir.exists()) {
                    // Save all images to Downloads
                    File[] imageFiles = outputDir.listFiles();
                    int savedCount = 0;

                    if (imageFiles != null) {
                        for (File imageFile : imageFiles) {
                            String savedPath = saveFileToDownloads(imageFile, imageFile.getName());
                            if (savedPath != null) {
                                savedCount++;
                            }
                            imageFile.delete();
                        }
                    }

                    // Clean up cache folder
                    outputDir.delete();

                    final int finalSavedCount = savedCount;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        new AlertDialog.Builder(this)
                                .setTitle("PDF to Images - Success!")
                                .setMessage("Converted " + finalSavedCount + " page(s) to " + format.toUpperCase() + " images.\n\nSaved to: Downloads folder")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Conversion failed: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddWatermarkDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("Watermark text:");
        layout.addView(label);

        EditText input = new EditText(this);
        input.setHint("CONFIDENTIAL");
        input.setText("CONFIDENTIAL");
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Add Watermark")
                .setView(layout)
                .setPositiveButton("Add", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        addWatermarkWithTool(text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addWatermarkWithTool(String text) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String outputName = baseName + "_watermarked_" + timestamp + ".pdf";

            // Gray color with 30% opacity, 45 degree rotation, larger font
            PdfTools.PdfResult result = PdfTools.addWatermark(
                    filePath, getCacheDir(), outputName,
                    text, 0.4f, 72f, 45, Color.GRAY);

            if (result.success && result.filePath != null) {
                // Save to Downloads
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    // Add to recent files
                    addToRecentFiles(savedPath, outputName);

                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "Watermark added! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving watermarked PDF", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showDeletePagesDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.TextView info = new android.widget.TextView(this);
        info.setText("Total pages: " + totalPages + "\n\nEnter pages to delete (e.g., 1,3,5-8):");
        layout.addView(info);

        EditText input = new EditText(this);
        input.setHint("1,2,3 or 1-5");
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Delete Pages")
                .setView(layout)
                .setPositiveButton("Delete", (d, w) -> {
                    String pagesStr = input.getText().toString();
                    List<Integer> pages = parsePageNumbers(pagesStr, totalPages);
                    if (pages.isEmpty()) {
                        Toast.makeText(this, "Invalid page numbers", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (pages.size() >= totalPages) {
                        Toast.makeText(this, "Cannot delete all pages", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deletePages(pages);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePages(List<Integer> pages) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String outputName = baseName + "_modified_" + timestamp + ".pdf";

            PdfTools.PdfResult result = PdfTools.deletePages(filePath, getCacheDir(), outputName, pages);

            if (result.success && result.filePath != null) {
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    addToRecentFiles(savedPath, outputName);
                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "Pages deleted! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving PDF", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showRotatePagesDialog() {
        String[] options = {"Rotate all pages 90° clockwise", "Rotate all pages 90° counter-clockwise",
                "Rotate all pages 180°", "Rotate specific pages"};

        new AlertDialog.Builder(this)
                .setTitle("Rotate Pages")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: rotateAllPages(90); break;
                        case 1: rotateAllPages(270); break;
                        case 2: rotateAllPages(180); break;
                        case 3: showRotateSpecificPagesDialog(); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rotateAllPages(int degrees) {
        List<Integer> allPages = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) allPages.add(i);
        rotatePagesAction(allPages, degrees);
    }

    private void showRotateSpecificPagesDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.TextView info = new android.widget.TextView(this);
        info.setText("Enter pages to rotate (e.g., 1,3,5-8):");
        layout.addView(info);

        EditText input = new EditText(this);
        input.setHint("1,2,3 or 1-5");
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Rotate Specific Pages")
                .setView(layout)
                .setPositiveButton("Rotate 90°", (d, w) -> {
                    List<Integer> pages = parsePageNumbers(input.getText().toString(), totalPages);
                    if (!pages.isEmpty()) rotatePagesAction(pages, 90);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rotatePagesAction(List<Integer> pages, int degrees) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String outputName = baseName + "_rotated_" + timestamp + ".pdf";

            PdfTools.PdfResult result = PdfTools.rotatePages(filePath, getCacheDir(), outputName, pages, degrees);

            if (result.success && result.filePath != null) {
                // Save to Downloads
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    // Add to recent files
                    addToRecentFiles(savedPath, outputName);

                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "Pages rotated! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving rotated PDF", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showCopyPdfDialog() {
        EditText input = new EditText(this);
        String baseName = fileName.replace(".pdf", "").replace(".PDF", "");
        input.setText(baseName + "_copy");

        new AlertDialog.Builder(this)
                .setTitle("Copy PDF")
                .setMessage("Enter name for the copy:")
                .setView(input)
                .setPositiveButton("Copy", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        copyPdf(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyPdf(String name) {
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            String outputName = name.endsWith(".pdf") ? name : name + ".pdf";
            PdfTools.PdfResult result = PdfTools.copyPdf(filePath, getCacheDir(), outputName);

            if (result.success && result.filePath != null) {
                File tempFile = new File(result.filePath);
                String savedPath = saveFileToDownloads(tempFile, outputName);
                tempFile.delete();

                if (savedPath != null) {
                    addToRecentFiles(savedPath, outputName);
                    final String finalPath = savedPath;
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSavedFileDialogWithOpen(finalPath, outputName, "PDF copied! Saved to Downloads:");
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error saving PDF copy", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + (result != null ? result.message : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showPdfInfo() {
        String info = PdfTools.getPdfInfo(filePath);
        new AlertDialog.Builder(this)
                .setTitle("PDF Information")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Show result dialog for all PDF tools - opens file after save
     */
    private void showToolResultDialog(PdfTools.PdfResult result, String toolName) {
        if (result == null) {
            Toast.makeText(this, toolName + " failed: Unknown error", Toast.LENGTH_SHORT).show();
            return;
        }

        if (result.success) {
            String fileInfo = result.fileName != null ? result.fileName : "output file";
            String message = result.message + "\n\nFile: " + fileInfo +
                    "\nSize: " + result.getFileSizeFormatted() +
                    "\nLocation: Cache";

            new AlertDialog.Builder(this)
                    .setTitle(toolName + " - Success!")
                    .setMessage(message)
                    .setPositiveButton("Open", (d, w) -> {
                        // Open the created PDF
                        if (result.filePath != null && result.filePath.endsWith(".pdf")) {
                            openCreatedPdf(result.filePath, result.fileName);
                        } else if (result.filePath != null) {
                            // It's a folder (for split/images)
                            Toast.makeText(this, "Files saved to: " + result.filePath, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNeutralButton("Save to Downloads", (d, w) -> {
                        saveToDownloads(result.filePath, result.fileName);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(toolName + " - Failed")
                    .setMessage(result.message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /**
     * Open a PDF file in this viewer
     */
    private void openCreatedPdf(String path, String name) {
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (IOException e) {
            AppLogger.e("PdfViewerActivity", "Error", e);
        }

        filePath = path;
        fileName = name;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        loadPdf();
        Toast.makeText(this, "Opened: " + fileName, Toast.LENGTH_SHORT).show();
    }

    /**
     * Save file to Downloads folder
     */
    private void saveToDownloads(String sourcePath, String fileName) {
        if (sourcePath == null) {
            Toast.makeText(this, "No file to save", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure fileName is not null
        if (fileName == null) {
            fileName = new File(sourcePath).getName();
        }

        final String finalFileName = fileName;
        binding.progressBar.setVisibility(View.VISIBLE);

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                File sourceFile = new File(sourcePath);
                String savedPath;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, finalFileName);

                    String mimeType = "application/pdf";
                    if (finalFileName.endsWith(".jpg") || finalFileName.endsWith(".jpeg")) {
                        mimeType = "image/jpeg";
                    } else if (finalFileName.endsWith(".png")) {
                        mimeType = "image/png";
                    }
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                    if (uri != null) {
                        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                             java.io.FileInputStream in = new java.io.FileInputStream(sourceFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        savedPath = "Downloads/" + finalFileName;
                    } else {
                        throw new Exception("Failed to create file");
                    }
                } else {
                    File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS);
                    File destFile = new File(downloadsDir, finalFileName);

                    try (java.io.FileInputStream in = new java.io.FileInputStream(sourceFile);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    savedPath = destFile.getAbsolutePath();
                }

                String finalPath = savedPath;
                String fullPath = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + finalFileName;
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    // Add to recent files if it's a PDF
                    if (finalFileName.toLowerCase().endsWith(".pdf")) {
                        addToRecentFiles(fullPath, finalFileName);
                    }

                    Toast.makeText(this, "Saved to: " + finalPath, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pdfRenderer != null) {
            pdfRenderer.close();
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException e) {
                AppLogger.e("PdfViewerActivity", "Error", e);
            }
        }

        for (ImageView imageView : pageViews) {
            imageView.setImageBitmap(null);
        }
        pageViews.clear();
    }
}
