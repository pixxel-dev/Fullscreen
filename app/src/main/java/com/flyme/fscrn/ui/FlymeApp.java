package com.flyme.fscrn.ui;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import android.content.Context;
import android.content.res.Configuration;

public class FlymeApp extends Application {
    public static final String PREF_NIGHT_MODE = "pref_night_mode";


    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme();
    }

    private void applyTheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isNightMode = prefs.getBoolean(PREF_NIGHT_MODE, false); // Default to light mode (false)
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}
