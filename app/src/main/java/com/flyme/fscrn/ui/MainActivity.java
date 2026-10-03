package com.flyme.fscrn.ui;

import com.flyme.fscrn.R;
import com.flyme.fscrn.adb.NativeAdbHelper;
import com.flyme.fscrn.filemanager.FileManagerFragment;
import com.flyme.fscrn.installer.ApkInstaller;
import com.flyme.fscrn.overlay.ForegroundOverlayService;

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
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;
import android.content.pm.PackageInfo;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;
import androidx.preference.SeekBarPreference;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.content.res.Configuration;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    private static android.graphics.Bitmap previousScreenSnapshot = null;

    private SharedPreferences sharedPreferences;

    private MaterialCardView updateCard;
    private TextView updateTitle;
    private TextView updateDescription;
    private ProgressBar updateProgress;
    private Button btnUpdateDownload;
    private Button btnUpdateInstall;
    private String downloadUrl;
    private File downloadedApk;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(newBase);
        int scalePercent = prefs.getInt("local_app_scale_percent", 100);

        if (scalePercent != 100) {
            Configuration config = new Configuration(newBase.getResources().getConfiguration());
            float scale = scalePercent / 100.0f;
            config.fontScale = scale;

            // Calculate new density based on the default system density to avoid compounding
            int defaultDensity = android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi;
            config.densityDpi = (int) (defaultDensity * scale);

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

        if (previousScreenSnapshot != null) {
            applyScreenSnapshotCrossfade();
        }

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

        setupUpdatePanel();
        checkForUpdates();
    }

    private void setupUpdatePanel() {
        updateCard = findViewById(R.id.update_card);
        updateTitle = findViewById(R.id.update_title);
        updateDescription = findViewById(R.id.update_description);
        updateProgress = findViewById(R.id.update_progress);
        btnUpdateDownload = findViewById(R.id.btn_update_download);
        btnUpdateInstall = findViewById(R.id.btn_update_install);

        btnUpdateDownload.setOnClickListener(v -> downloadUpdate());
        btnUpdateInstall.setOnClickListener(v -> {
            if (downloadedApk != null && downloadedApk.exists()) {
                ApkInstaller.installApk(this, downloadedApk);
            }
        });
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/pixxel-dev/Fullscreen/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String tagName = json.getString("tag_name");
                    JSONArray assets = json.getJSONArray("assets");

                    String apkUrl = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    if (apkUrl != null && isNewerVersion(tagName)) {
                        final String finalApkUrl = apkUrl;
                        final String versionName = json.optString("name", tagName);
                        runOnUiThread(() -> {
                            downloadUrl = finalApkUrl;
                            updateTitle.setText("Доступно обновление: " + versionName);
                            updateCard.setVisibility(View.VISIBLE);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private boolean isNewerVersion(String tag) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String currentVersion = pInfo.versionName;

            // Extract semantic version using regex (e.g., v1.2.0-build123 -> 1.2.0)
            String cleanTag = tag.replaceAll("^v", "").split("-")[0];
            String cleanCurrent = currentVersion.replaceAll("^v", "").split("-")[0];

            cleanTag = cleanTag.replaceAll("[^0-9\\.]", "");
            cleanCurrent = cleanCurrent.replaceAll("[^0-9\\.]", "");

            String[] tagParts = cleanTag.split("\\.");
            String[] currentParts = cleanCurrent.split("\\.");

            int length = Math.max(tagParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int t = i < tagParts.length && !tagParts[i].isEmpty() ? Integer.parseInt(tagParts[i]) : 0;
                int c = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
                if (t > c) return true;
                if (t < c) return false;
            }

            // If semantic version is same, check build numbers if present
            if (tag.contains("build") && currentVersion.contains("build")) {
                int tagBuild = Integer.parseInt(tag.substring(tag.lastIndexOf("build") + 5));
                int currentBuild = Integer.parseInt(currentVersion.substring(currentVersion.lastIndexOf("build") + 5));
                return tagBuild > currentBuild;
            }

            // Or check versionCode if available
            long currentCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode;
            // Usually we can't extract versionCode directly from tag, so rely on string comp

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadUpdate() {
        if (downloadUrl == null) return;

        btnUpdateDownload.setVisibility(View.GONE);
        updateProgress.setVisibility(View.VISIBLE);
        updateProgress.setIndeterminate(false);
        updateProgress.setProgress(0);

        new Thread(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();

                downloadedApk = new File(getExternalCacheDir(), "update.apk");
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(downloadedApk);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> updateProgress.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                runOnUiThread(() -> {
                    updateProgress.setVisibility(View.GONE);
                    updateDescription.setText("Готово к установке");
                    btnUpdateInstall.setVisibility(View.VISIBLE);

                    // Automatically trigger installation on download complete as requested
                    ApkInstaller.installApk(MainActivity.this, downloadedApk);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnUpdateDownload.setVisibility(View.VISIBLE);
                    updateProgress.setVisibility(View.GONE);
                });
            }
        }).start();
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

    private void applyScreenSnapshotCrossfade() {
        android.view.ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView != null && previousScreenSnapshot != null) {
            android.widget.ImageView overlayView = new android.widget.ImageView(this);
            overlayView.setImageBitmap(previousScreenSnapshot);
            overlayView.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);

            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            rootView.addView(overlayView, params);

            overlayView.animate()
                    .alpha(0.0f)
                    .setDuration(400)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            rootView.removeView(overlayView);
                            previousScreenSnapshot = null;
                        }
                    });
        } else {
            previousScreenSnapshot = null;
        }
    }

    private void captureScreenSnapshotAndRestart() {
        View decorView = getWindow().getDecorView();
        if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    decorView.getWidth(), decorView.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            decorView.draw(canvas);
            previousScreenSnapshot = bitmap;
        }

        finish();
        startActivity(getIntent());
        overridePendingTransition(0, 0);
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

        captureScreenSnapshotAndRestart();
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, androidx.preference.PreferenceScreen pref) {
        Fragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        fragment.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, pref.getKey())
                .addToBackStack(pref.getKey())
                .commit();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.buttons_preferences, rootKey);
            populateAppsList("quick_launch_apps");
            populateAppsList("fullscreen_apps");
            setupAppInfo();
            updatePreferencesVisibility();

            setupPermissionsSubscreen();

            Preference adbPairingPref = findPreference("adb_pairing");
            if (adbPairingPref != null) {
                adbPairingPref.setOnPreferenceClickListener(preference -> {
                    showAdbPairingDialog();
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

            Preference restartAppPref = findPreference("restart_app_scale");
            if (restartAppPref != null) {
                restartAppPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Runtime.getRuntime().exit(0);
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
                    long versionCode;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        versionCode = pInfo.getLongVersionCode();
                    } else {
                        versionCode = pInfo.versionCode;
                    }
                    appInfoPref.setSummary("Версия: " + version + " (Build " + versionCode + ")");
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
            updatePermissionsStates();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        private void setupPermissionsSubscreen() {
            androidx.preference.SwitchPreferenceCompat permUsageStats = findPreference("perm_usage_stats");
            if (permUsageStats != null) {
                permUsageStats.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    return true;
                });
            }

            androidx.preference.SwitchPreferenceCompat permOverlay = findPreference("perm_overlay");
            if (permOverlay != null) {
                permOverlay.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                    return true;
                });
            }

            androidx.preference.SwitchPreferenceCompat permStorage = findPreference("perm_manage_storage");
            if (permStorage != null) {
                permStorage.setOnPreferenceClickListener(preference -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
                    }
                    return true;
                });
            }

            androidx.preference.SwitchPreferenceCompat permInstall = findPreference("perm_install_packages");
            if (permInstall != null) {
                permInstall.setOnPreferenceClickListener(preference -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                    }
                    return true;
                });
            }

            androidx.preference.SwitchPreferenceCompat permNotifications = findPreference("perm_notifications");
            if (permNotifications != null) {
                permNotifications.setOnPreferenceClickListener(preference -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                        startActivity(intent);
                    }
                    return true;
                });
            }

            androidx.preference.SwitchPreferenceCompat permShizuku = findPreference("perm_shizuku");
            if (permShizuku != null) {
                permShizuku.setOnPreferenceClickListener(preference -> {
                    if (rikka.shizuku.Shizuku.pingBinder()) {
                        if (rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            rikka.shizuku.Shizuku.requestPermission(1001);
                        } else {
                            Toast.makeText(getContext(), "Разрешение Shizuku уже предоставлено", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Служба Shizuku не запущена", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }
        }

        private void updatePermissionsStates() {
            Context context = getContext();
            if (context == null) return;

            // Usage stats
            androidx.preference.SwitchPreferenceCompat permUsageStats = findPreference("perm_usage_stats");
            if (permUsageStats != null) {
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                List<android.app.usage.UsageStats> stats = usm != null ? usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now) : null;
                boolean hasUsageStats = stats != null && !stats.isEmpty();
                permUsageStats.setChecked(hasUsageStats);
            }

            // Overlay
            androidx.preference.SwitchPreferenceCompat permOverlay = findPreference("perm_overlay");
            if (permOverlay != null) {
                permOverlay.setChecked(Settings.canDrawOverlays(context));
            }

            // Manage Storage
            androidx.preference.SwitchPreferenceCompat permStorage = findPreference("perm_manage_storage");
            if (permStorage != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    permStorage.setChecked(android.os.Environment.isExternalStorageManager());
                } else {
                    int read = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    permStorage.setChecked(read == PackageManager.PERMISSION_GRANTED);
                }
            }

            // Install packages
            androidx.preference.SwitchPreferenceCompat permInstall = findPreference("perm_install_packages");
            if (permInstall != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    permInstall.setChecked(context.getPackageManager().canRequestPackageInstalls());
                } else {
                    permInstall.setChecked(true);
                }
            }

            // Notifications
            androidx.preference.SwitchPreferenceCompat permNotifications = findPreference("perm_notifications");
            if (permNotifications != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    int notif = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS);
                    permNotifications.setChecked(notif == PackageManager.PERMISSION_GRANTED);
                } else {
                    permNotifications.setChecked(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled());
                }
            }

            // Shizuku
            androidx.preference.SwitchPreferenceCompat permShizuku = findPreference("perm_shizuku");
            if (permShizuku != null) {
                boolean shizukuGranted = rikka.shizuku.Shizuku.pingBinder() && rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                permShizuku.setChecked(shizukuGranted);
            }
        }

        private void updatePreferencesVisibility() {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            boolean separateEnabled = prefs.getBoolean("separate_buttons_enabled", false);

            Preference qlPosX = findPreference("ql_position_x");
            Preference qlSize = findPreference("ql_button_size");
            Preference fsPosX = findPreference("fs_position_x");
            Preference fsSize = findPreference("fs_button_size");

            Preference combinedPosX = findPreference("combined_position_x");
            Preference combinedSize = findPreference("combined_button_size");

            if (qlPosX != null) qlPosX.setVisible(separateEnabled);
            if (qlSize != null) qlSize.setVisible(separateEnabled);
            if (fsPosX != null) fsPosX.setVisible(separateEnabled);
            if (fsSize != null) fsSize.setVisible(separateEnabled);

            if (combinedPosX != null) combinedPosX.setVisible(!separateEnabled);
            if (combinedSize != null) combinedSize.setVisible(!separateEnabled);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("separate_buttons_enabled".equals(key)) {
                updatePreferencesVisibility();
            }

            ForegroundOverlayService service = ForegroundOverlayService.getInstance();
            if (service != null) {
                service.updateOverlayButtons();
            }
        }
    }
}
