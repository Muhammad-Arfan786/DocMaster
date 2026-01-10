package com.docreader;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import com.docreader.utils.CacheManager;
import com.docreader.utils.PreferencesManager;
import com.google.android.material.color.DynamicColors;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/**
 * Application class for Document Reader (TenDocmas).
 * Initializes app-wide settings and performs maintenance tasks.
 */
public class DocumentReaderApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply Dynamic Colors (Android 12+ Material You)
        // This applies wallpaper-based colors to all activities automatically
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Initialize PDFBox-Android (required before using PDFBox)
        PDFBoxResourceLoader.init(getApplicationContext());

        // Apply saved theme preference
        PreferencesManager prefs = new PreferencesManager(this);
        if (prefs.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // Clean up old cache files asynchronously on startup
        CacheManager.cleanupCacheAsync(this);
    }
}
