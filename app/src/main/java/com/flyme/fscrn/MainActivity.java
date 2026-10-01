package com.flyme.fscrn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.flyme.fscrn.SearchableMultiSelectListPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;
import com.flyme.fscrn.service.ForegroundOverlayService;
import androidx.preference.Preference;
import android.content.pm.PackageInfo;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;
import androidx.preference.SeekBarPreference;
import android.widget.Toast;

import android.content.res.Configuration;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences sharedPreferences;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(newBase);
        int scalePercent = prefs.getInt("local_app_scale_percent", 100);

        if (scalePercent != 100) {
            Configuration config = new Configuration(newBase.getResources().getConfiguration());
            float scale = scalePercent / 100.0f;
            config.fontScale = scale;
            config.densityDpi = (int) (newBase.getResources().getDisplayMetrics().densityDpi * scale);
            super.attachBaseContext(newBase.createConfigurationContext(config));
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

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

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String tag = null;
            if (item.getItemId() == R.id.nav_tweaks) {
                selectedFragment = new SettingsFragment();
                tag = "FRAG_TWEAKS";
            } else if (item.getItemId() == R.id.nav_files) {
                selectedFragment = new FileManagerFragment();
                tag = "FRAG_FILES";
            }
            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment, tag)
                        .commit();
            }
            return true;
        });

        // Load default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_tweaks);
        } else {
            // Ensure the correct fragment is showing after theme toggle
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof SettingsFragment) {
                bottomNav.getMenu().findItem(R.id.nav_tweaks).setChecked(true);
            } else if (f instanceof FileManagerFragment) {
                bottomNav.getMenu().findItem(R.id.nav_files).setChecked(true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.findItem(R.id.action_theme_toggle);
        if (themeItem != null) {
            boolean isNightMode = sharedPreferences.getBoolean(FlymeApp.PREF_NIGHT_MODE, false);
            if (isNightMode) {
                themeItem.setIcon(R.drawable.ic_moon);
                themeItem.setTitle("Светлая тема");
            } else {
                themeItem.setIcon(R.drawable.ic_sun);
                themeItem.setTitle("Темная тема");
            }
        }

        MenuItem pasteItem = menu.findItem(R.id.action_paste);
        if (pasteItem != null) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof FileManagerFragment) {
                pasteItem.setVisible(((FileManagerFragment) f).hasFileInClipboard());
            } else {
                pasteItem.setVisible(false);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_theme_toggle) {
            toggleTheme();
            return true;
        } else if (item.getItemId() == R.id.action_paste) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof FileManagerFragment) {
                ((FileManagerFragment) f).pasteFile();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleTheme() {
        boolean isNightMode = sharedPreferences.getBoolean(FlymeApp.PREF_NIGHT_MODE, false);
        boolean newNightMode = !isNightMode;

        sharedPreferences.edit().putBoolean(FlymeApp.PREF_NIGHT_MODE, newNightMode).apply();

        if (newNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // This will force the menu to be redrawn with the new icon
        invalidateOptionsMenu();
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

            Preference adbPairingPref = findPreference("adb_pairing");
            if (adbPairingPref != null) {
                adbPairingPref.setOnPreferenceClickListener(preference -> {
                    showAdbPairingDialog();
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

            SeekBarPreference localScalePref = findPreference("local_app_scale_percent");
            if (localScalePref != null) {
                localScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    Toast.makeText(getContext(), "Требуется перезапуск приложения для применения масштаба", Toast.LENGTH_LONG).show();
                    return true;
                });
            }
        }

        private void showAdbPairingDialog() {
            android.view.View view = getLayoutInflater().inflate(R.layout.dialog_adb_pairing, null);
            com.google.android.material.textfield.TextInputEditText editPairingPort = view.findViewById(R.id.edit_pairing_port);
            com.google.android.material.textfield.TextInputEditText editCode = view.findViewById(R.id.edit_code);
            com.google.android.material.textfield.TextInputEditText editConnectionPort = view.findViewById(R.id.edit_connection_port);

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Сопряжение ADB (Android 11+)")
                    .setView(view)
                    .setPositiveButton("Соединить", (dialog, which) -> {
                        String pairingPort = editPairingPort.getText() != null ? editPairingPort.getText().toString() : "";
                        String code = editCode.getText() != null ? editCode.getText().toString() : "";
                        String connectionPort = editConnectionPort.getText() != null ? editConnectionPort.getText().toString() : "";

                        if (!pairingPort.isEmpty() && !code.isEmpty() && !connectionPort.isEmpty()) {
                            new Thread(() -> {
                                boolean pairSuccess = NativeAdbHelper.pair(requireContext(), pairingPort, code);
                                if (pairSuccess) {
                                    boolean connectSuccess = NativeAdbHelper.connect(requireContext(), connectionPort);
                                    if (getActivity() != null) {
                                        requireActivity().runOnUiThread(() -> {
                                            android.widget.Toast.makeText(requireContext(),
                                                connectSuccess ? "ADB успешно сопряжен и подключен!" : "Сопряжение прошло, но ошибка подключения.",
                                                android.widget.Toast.LENGTH_LONG).show();
                                        });
                                    }
                                } else {
                                    if (getActivity() != null) {
                                        requireActivity().runOnUiThread(() -> {
                                            android.widget.Toast.makeText(requireContext(),
                                                "Ошибка сопряжения.",
                                                android.widget.Toast.LENGTH_LONG).show();
                                        });
                                    }
                                }
                            }).start();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
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
