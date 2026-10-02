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
    protected void attachBaseContext(Context base) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(base);
        int scalePercent = prefs.getInt("local_app_scale_percent", 100);

        if (scalePercent != 100) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            float scale = scalePercent / 100.0f;
            config.fontScale = scale;
            config.densityDpi = (int) (base.getResources().getDisplayMetrics().densityDpi * scale);
            Context newContext = base.createConfigurationContext(config);
            super.attachBaseContext(newContext);
        } else {
            super.attachBaseContext(base);
        }
    }

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
