package com.flyme.fscrn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import com.flyme.fscrn.SearchableMultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;
import com.flyme.fscrn.service.ForegroundOverlayService;
import androidx.preference.Preference;
import android.content.pm.PackageInfo;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        Intent serviceIntent = new Intent(this, ForegroundOverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.buttons_preferences, rootKey);
            populateAppsList("quick_launch_apps");
            populateAppsList("fullscreen_apps");
            setupAppInfo();

            Preference usageStatsPref = findPreference("request_usage_stats");
            if (usageStatsPref != null) {
                usageStatsPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(intent);
                    return true;
                });
            }

            Preference accessStatsPref = findPreference("request_accessibility");
            if (accessStatsPref != null) {
                accessStatsPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    return true;
                });
            }
        }

        private void setupAppInfo() {
            Preference appInfoPref = findPreference("app_info");
            if (appInfoPref != null) {
                try {
                    PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
                    String version = pInfo.versionName;
                    appInfoPref.setSummary("Версия: " + version);
                } catch (PackageManager.NameNotFoundException e) {
                    appInfoPref.setSummary("Версия: Неизвестно");
                }
            }
        }

        private void populateAppsList(String key) {
            SearchableMultiSelectListPreference listPreference = findPreference(key);
            if (listPreference != null) {
                PackageManager pm = requireContext().getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

                List<CharSequence> entries = new ArrayList<>();
                List<CharSequence> entryValues = new ArrayList<>();

                for (ResolveInfo info : activities) {
                    entries.add(info.loadLabel(pm).toString());
                    entryValues.add(info.activityInfo.packageName);
                }

                listPreference.setEntries(entries.toArray(new CharSequence[0]));
                listPreference.setEntryValues(entryValues.toArray(new CharSequence[0]));
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("quick_launch_enabled".equals(key) || "ql_position_x".equals(key) || "ql_button_size".equals(key)) {
                ForegroundOverlayService service = ForegroundOverlayService.getInstance();
                if (service != null) {
                    service.updateQuickLaunchButton();
                }
            } else if ("fullscreen_overlay_enabled".equals(key) || "fullscreen_apps".equals(key) || "fs_position_x".equals(key) || "fs_button_size".equals(key)) {
                ForegroundOverlayService service = ForegroundOverlayService.getInstance();
                if (service != null) {
                    service.updateFullscreenState();
                }
            }
        }
    }
}
