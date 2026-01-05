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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import com.docreader.utils.FileUtils;
import com.docreader.utils.NotesManager;
import com.docreader.utils.PdfEditManager;
import com.docreader.utils.PdfPageManager;
import com.docreader.utils.PdfToWordConverter;
import com.docreader.views.DrawingView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.itextpdf.kernel.geom.PageSize;

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
    private static final int REQUEST_PICK_IMAGE = 101;
    private static final int REQUEST_PICK_PDF_MERGE = 102;
    private static final int REQUEST_SIGNATURE = 103;

    private ActivityPdfViewerBinding binding;
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

        setupToolbar();
        setupControls();
        setupSearch();
        setupEditToolbar();
        loadPdf();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(fileName != null ? fileName : "PDF Viewer");
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupControls() {
        binding.btnZoomIn.setOnClickListener(v -> zoomIn());
        binding.btnZoomOut.setOnClickListener(v -> zoomOut());
        binding.btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        binding.btnNextPage.setOnClickListener(v -> goToNextPage());
        binding.fabSaveEdit.setOnClickListener(v -> saveEditedPdf());
        updateZoomLabel();
    }

    private void setupSearch() {
        binding.btnCloseSearch.setOnClickListener(v -> hideSearch());
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
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

        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);

        int baseWidth = getResources().getDisplayMetrics().widthPixels - 32;
        float aspectRatio = (float) page.getHeight() / page.getWidth();

        int width = (int) (baseWidth * currentZoom);
        int height = (int) (width * aspectRatio);

        int maxSize = 4096;
        if (width > maxSize) {
            width = maxSize;
            height = (int) (width * aspectRatio);
        }
        if (height > maxSize) {
            height = maxSize;
            width = (int) (height / aspectRatio);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        if (pageIndex < pageViews.size()) {
            pageViews.get(pageIndex).setImageBitmap(bitmap);
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

        sheetBinding.btnCompressPdf.setOnClickListener(v -> {
            bottomSheet.dismiss();
            compressPdf();
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

        new Thread(() -> {
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
        }).start();
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

        new Thread(() -> {
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
        }).start();
    }

    private void duplicateCurrentPage() {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
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
        }).start();
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

        new Thread(() -> {
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
        }).start();
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

        new Thread(() -> {
            try {
                String newPath = pdfPageManager.addBlankPage(afterPage, PageSize.A4);

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
        }).start();
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

        new Thread(() -> {
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
        }).start();
    }

    private void splitPdfIntoPages() {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
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
        }).start();
    }

    private void compressPdf() {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String newPath = pdfPageManager.compressPdf();

                File originalFile = new File(filePath);
                File compressedFile = new File(newPath);

                long originalSize = originalFile.length();
                long compressedSize = compressedFile.length();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    String message = String.format("Original: %.2f KB\nCompressed: %.2f KB\n\nSaved to:\n%s",
                            originalSize / 1024.0, compressedSize / 1024.0, newPath);

                    new AlertDialog.Builder(this)
                            .setTitle("PDF Compressed")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Use This", (d, w) -> {
                                filePath = newPath;
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
        }).start();
    }

    // ==================== CONTENT OPERATIONS ====================

    private void pickImageToAdd() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
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

        new Thread(() -> {
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
        }).start();
    }

    private void addImageToCurrentPage(Uri imageUri) {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
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
        }).start();
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

        new Thread(() -> {
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
        }).start();
    }

    private void pickPdfsToMerge() {
        pdfsToMerge.clear();
        pdfsToMerge.add(filePath); // Start with current PDF

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select PDFs to merge"), REQUEST_PICK_PDF_MERGE);
    }

    private void mergePdfs(List<Uri> pdfUris) {
        binding.progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
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
        }).start();
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

    private void performSave() {
        binding.progressBar.setVisibility(View.VISIBLE);

        if (binding.drawingView.hasDrawings()) {
            Bitmap drawingBitmap = binding.drawingView.getDrawingBitmap();
            if (drawingBitmap != null) {
                pdfEditManager.setDrawingOverlay(currentPage, drawingBitmap);
            }
        }

        new Thread(() -> {
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
        }).start();
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
        new Thread(() -> {
            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;
                runOnUiThread(() -> renderPage(pageIndex));
            }
            runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
        }).start();
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

    private void showSearch() {
        binding.searchBar.setVisibility(View.VISIBLE);
        binding.etSearch.requestFocus();
    }

    private void hideSearch() {
        binding.searchBar.setVisibility(View.GONE);
        binding.etSearch.setText("");
    }

    private void performSearch() {
        Toast.makeText(this, "Text search not available in native viewer", Toast.LENGTH_SHORT).show();
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


    private void convertToWord() {
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Converting PDF to Word...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String docxPath = PdfToWordConverter.convertToDocx(filePath, getCacheDir());

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);

                    new AlertDialog.Builder(this)
                            .setTitle("Conversion Complete")
                            .setMessage("PDF converted to Word document.\n\nYou can now edit it with your keyboard.")
                            .setPositiveButton("Open & Edit", (dialog, which) -> {
                                Intent intent = new Intent(this, DocViewerActivity.class);
                                intent.putExtra("file_path", docxPath);
                                intent.putExtra("file_name", new File(docxPath).getName());
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error converting: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    // ==================== ACTIVITY CALLBACKS ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    addImageToPdf(imageUri);
                }
            } else if (requestCode == REQUEST_PICK_PDF_MERGE) {
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
        }
    }

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

        if (id == R.id.action_search) {
            if (binding.searchBar.getVisibility() == View.VISIBLE) hideSearch();
            else showSearch();
            return true;
        } else if (id == R.id.action_edit) {
            showPdfEditorBottomSheet();
            return true;
        } else if (id == R.id.action_draw) {
            toggleEditMode();
            return true;
        } else if (id == R.id.action_add_note) {
            showPageNotesDialog();
            return true;
        } else if (id == R.id.action_convert_to_word) {
            convertToWord();
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
                e.printStackTrace();
            }
        }

        for (ImageView imageView : pageViews) {
            imageView.setImageBitmap(null);
        }
        pageViews.clear();
    }
}
