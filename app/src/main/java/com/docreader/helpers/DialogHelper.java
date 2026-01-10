package com.docreader.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.docreader.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Centralized helper for creating dialogs following DRY principle.
 * Provides consistent dialog styling and reduces code duplication.
 */
public class DialogHelper {

    private DialogHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Shows a confirmation dialog with Yes/No options.
     */
    public static void showConfirmDialog(
            @NonNull Context context,
            @StringRes int titleRes,
            @StringRes int messageRes,
            @Nullable Runnable onConfirm) {
        showConfirmDialog(context,
            context.getString(titleRes),
            context.getString(messageRes),
            onConfirm);
    }

    /**
     * Shows a confirmation dialog with Yes/No options.
     */
    public static void showConfirmDialog(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @Nullable Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(R.string.btn_no, null)
                .show();
    }

    /**
     * Shows a dialog with custom positive/negative button text.
     */
    public static void showDialog(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String message,
            @NonNull String positiveText,
            @Nullable String negativeText,
            @Nullable Runnable onPositive,
            @Nullable Runnable onNegative) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, (dialog, which) -> {
                    if (onPositive != null) {
                        onPositive.run();
                    }
                });

        if (negativeText != null) {
            builder.setNegativeButton(negativeText, (dialog, which) -> {
                if (onNegative != null) {
                    onNegative.run();
                }
            });
        }

        builder.show();
    }

    /**
     * Shows a dialog with Save/Discard/Cancel options for unsaved changes.
     */
    public static void showUnsavedChangesDialog(
            @NonNull Context context,
            @Nullable Runnable onSave,
            @Nullable Runnable onDiscard) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_save_title)
                .setMessage(R.string.msg_unsaved_changes)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    if (onSave != null) {
                        onSave.run();
                    }
                })
                .setNegativeButton(R.string.btn_discard, (dialog, which) -> {
                    if (onDiscard != null) {
                        onDiscard.run();
                    }
                })
                .setNeutralButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows a single choice selection dialog.
     */
    public static void showSingleChoiceDialog(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String[] options,
            int checkedItem,
            @NonNull OnSingleChoiceListener listener) {
        final int[] selectedIndex = {checkedItem};
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    selectedIndex[0] = which;
                })
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    listener.onItemSelected(selectedIndex[0]);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows a simple list selection dialog.
     */
    public static void showListDialog(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String[] options,
            @NonNull OnSingleChoiceListener listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    listener.onItemSelected(which);
                })
                .show();
    }

    /**
     * Shows an input dialog with a single EditText field.
     */
    public static void showInputDialog(
            @NonNull Context context,
            @NonNull String title,
            @Nullable String hint,
            @Nullable String defaultValue,
            @NonNull OnInputListener listener) {
        showInputDialog(context, title, hint, defaultValue, InputType.TYPE_CLASS_TEXT, listener);
    }

    /**
     * Shows an input dialog with a single EditText field and specified input type.
     */
    public static void showInputDialog(
            @NonNull Context context,
            @NonNull String title,
            @Nullable String hint,
            @Nullable String defaultValue,
            int inputType,
            @NonNull OnInputListener listener) {
        EditText editText = new EditText(context);
        editText.setInputType(inputType);
        if (hint != null) {
            editText.setHint(hint);
        }
        if (defaultValue != null) {
            editText.setText(defaultValue);
            editText.setSelection(defaultValue.length());
        }

        // Add padding to EditText
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    listener.onInput(input);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows a number input dialog.
     */
    public static void showNumberInputDialog(
            @NonNull Context context,
            @NonNull String title,
            @Nullable String hint,
            @Nullable Integer defaultValue,
            @NonNull OnNumberInputListener listener) {
        EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (hint != null) {
            editText.setHint(hint);
        }
        if (defaultValue != null) {
            editText.setText(String.valueOf(defaultValue));
        }

        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    try {
                        int value = Integer.parseInt(input);
                        listener.onInput(value);
                    } catch (NumberFormatException e) {
                        showErrorToast(context, R.string.msg_please_enter_valid_numbers);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Shows a simple error toast message.
     */
    public static void showErrorToast(@NonNull Context context, @StringRes int messageRes) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a simple error toast message.
     */
    public static void showErrorToast(@NonNull Context context, @NonNull String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a success toast message.
     */
    public static void showSuccessToast(@NonNull Context context, @StringRes int messageRes) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a success toast message.
     */
    public static void showSuccessToast(@NonNull Context context, @NonNull String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a formatted error toast with the error message.
     */
    public static void showFormattedErrorToast(@NonNull Context context, @StringRes int formatRes, @NonNull String errorMessage) {
        String message = context.getString(formatRes, errorMessage);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Callback interface for single choice dialog.
     */
    public interface OnSingleChoiceListener {
        void onItemSelected(int index);
    }

    /**
     * Callback interface for text input dialog.
     */
    public interface OnInputListener {
        void onInput(@NonNull String input);
    }

    /**
     * Callback interface for number input dialog.
     */
    public interface OnNumberInputListener {
        void onInput(int value);
    }
}
