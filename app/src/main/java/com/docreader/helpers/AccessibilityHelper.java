package com.docreader.helpers;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.docreader.R;

/**
 * Helper class for accessibility-related functionality.
 * Provides methods for announcements, content descriptions, and accessibility state updates.
 */
public class AccessibilityHelper {

    private AccessibilityHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Announces a message for accessibility services (e.g., TalkBack).
     * Use this for dynamic state changes that should be announced to screen reader users.
     *
     * @param view The view to use for the announcement
     * @param message The message to announce
     */
    public static void announce(@Nullable View view, @NonNull String message) {
        if (view != null) {
            view.announceForAccessibility(message);
        }
    }

    /**
     * Announces a string resource message for accessibility services.
     */
    public static void announce(@Nullable View view, @NonNull Context context, @StringRes int messageRes) {
        if (view != null) {
            view.announceForAccessibility(context.getString(messageRes));
        }
    }

    /**
     * Announces a formatted string resource message for accessibility services.
     */
    public static void announceFormatted(@Nullable View view, @NonNull Context context,
                                          @StringRes int messageRes, Object... formatArgs) {
        if (view != null) {
            view.announceForAccessibility(context.getString(messageRes, formatArgs));
        }
    }

    /**
     * Announces page change for PDF viewer.
     */
    public static void announcePageChange(@Nullable View view, @NonNull Context context,
                                          int currentPage, int totalPages) {
        announceFormatted(view, context, R.string.announce_page_changed, currentPage, totalPages);
    }

    /**
     * Announces zoom level change.
     */
    public static void announceZoomChange(@Nullable View view, @NonNull Context context, int zoomPercent) {
        announceFormatted(view, context, R.string.announce_zoom_changed, zoomPercent);
    }

    /**
     * Announces tool selection change.
     */
    public static void announceToolSelected(@Nullable View view, @NonNull Context context, String toolName) {
        announceFormatted(view, context, R.string.announce_tool_selected, toolName);
    }

    /**
     * Announces color selection change.
     */
    public static void announceColorSelected(@Nullable View view, @NonNull Context context, String colorName) {
        announceFormatted(view, context, R.string.announce_color_selected, colorName);
    }

    /**
     * Announces file loaded.
     */
    public static void announceFileLoaded(@Nullable View view, @NonNull Context context, String fileName) {
        announceFormatted(view, context, R.string.announce_file_loaded, fileName);
    }

    /**
     * Announces edit mode entered.
     */
    public static void announceEditModeEntered(@Nullable View view, @NonNull Context context) {
        announce(view, context, R.string.announce_edit_mode_entered);
    }

    /**
     * Announces edit mode exited.
     */
    public static void announceEditModeExited(@Nullable View view, @NonNull Context context) {
        announce(view, context, R.string.announce_edit_mode_exited);
    }

    /**
     * Announces operation complete.
     */
    public static void announceOperationComplete(@Nullable View view, @NonNull Context context,
                                                  String operationName) {
        announceFormatted(view, context, R.string.announce_operation_complete, operationName);
    }

    /**
     * Sets the content description of a view.
     */
    public static void setContentDescription(@Nullable View view, @NonNull String description) {
        if (view != null) {
            view.setContentDescription(description);
        }
    }

    /**
     * Sets the content description of a view from a string resource.
     */
    public static void setContentDescription(@Nullable View view, @NonNull Context context,
                                             @StringRes int descriptionRes) {
        if (view != null) {
            view.setContentDescription(context.getString(descriptionRes));
        }
    }

    /**
     * Sets a formatted content description on a view.
     */
    public static void setFormattedContentDescription(@Nullable View view, @NonNull Context context,
                                                       @StringRes int descriptionRes, Object... formatArgs) {
        if (view != null) {
            view.setContentDescription(context.getString(descriptionRes, formatArgs));
        }
    }

    /**
     * Updates the page info accessibility description.
     */
    public static void updatePageInfoDescription(@Nullable View view, @NonNull Context context,
                                                  int currentPage, int totalPages) {
        setFormattedContentDescription(view, context, R.string.cd_page_info, currentPage, totalPages);
    }

    /**
     * Updates the zoom level accessibility description.
     */
    public static void updateZoomDescription(@Nullable View view, @NonNull Context context, int zoomPercent) {
        setFormattedContentDescription(view, context, R.string.cd_zoom_level, zoomPercent);
    }

    /**
     * Sets a view as a button for accessibility purposes.
     * Useful for custom views that act as buttons.
     */
    public static void setAsButton(@NonNull View view) {
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
    }

    /**
     * Sets a custom role description for accessibility.
     */
    public static void setRoleDescription(@NonNull View view, @NonNull String roleDescription) {
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setRoleDescription(roleDescription);
            }
        });
    }

    /**
     * Marks a view as not important for accessibility (decorative elements).
     */
    public static void setNotImportantForAccessibility(@Nullable View view) {
        if (view != null) {
            ViewCompat.setImportantForAccessibility(view,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /**
     * Marks a view as important for accessibility.
     */
    public static void setImportantForAccessibility(@Nullable View view) {
        if (view != null) {
            ViewCompat.setImportantForAccessibility(view,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    /**
     * Checks if accessibility services are enabled.
     */
    public static boolean isAccessibilityEnabled(@NonNull Context context) {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled();
    }

    /**
     * Checks if touch exploration (TalkBack) is enabled.
     */
    public static boolean isTouchExplorationEnabled(@NonNull Context context) {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isTouchExplorationEnabled();
    }

    /**
     * Sends an accessibility event from a view.
     */
    public static void sendAccessibilityEvent(@Nullable View view, int eventType) {
        if (view != null) {
            view.sendAccessibilityEvent(eventType);
        }
    }

    /**
     * Requests focus for accessibility on a view.
     */
    public static void requestAccessibilityFocus(@Nullable View view) {
        if (view != null) {
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    /**
     * Updates a color button's content description to indicate selected state.
     */
    public static void updateColorButtonState(@Nullable View view, @NonNull Context context,
                                              String colorName, boolean isSelected) {
        if (view != null) {
            String description = isSelected
                    ? context.getString(R.string.cd_color_selected, colorName)
                    : context.getString(getColorDescriptionRes(colorName));
            view.setContentDescription(description);
        }
    }

    /**
     * Gets the content description resource for a color name.
     */
    @StringRes
    private static int getColorDescriptionRes(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red":
                return R.string.cd_color_red;
            case "blue":
                return R.string.cd_color_blue;
            case "green":
                return R.string.cd_color_green;
            case "yellow":
                return R.string.cd_color_yellow;
            default:
                return R.string.cd_color_red;
        }
    }
}
