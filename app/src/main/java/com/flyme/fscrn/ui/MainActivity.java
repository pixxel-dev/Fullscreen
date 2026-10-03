package com.flyme.fscrn.ui;

import com.flyme.fscrn.R;
import com.flyme.fscrn.adb.NativeAdbHelper;
import com.flyme.fscrn.filemanager.FileManagerFragment;
import com.flyme.fscrn.installer.ApkInstaller;
import com.flyme.fscrn.installer.ShizukuManager;
import com.flyme.fscrn.overlay.ForegroundOverlayService;
import com.flyme.fscrn.system.LogManager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_tweaks);
        } else {
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

    private long updateDownloadId = -1;
    private android.content.BroadcastReceiver updateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == updateDownloadId && updateDownloadId != -1) {
                android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    android.database.Cursor cursor = manager.query(new android.app.DownloadManager.Query().setFilterById(updateDownloadId));
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                        if (statusIndex != -1) {
                            int status = cursor.getInt(statusIndex);
                            if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                if (updateProgress != null) updateProgress.setVisibility(View.GONE);
                                if (btnUpdateInstall != null) btnUpdateInstall.setVisibility(View.VISIBLE);
                                Toast.makeText(context, "Обновление загружено", Toast.LENGTH_SHORT).show();
                                if (downloadedApk != null && downloadedApk.exists()) {
                                    ApkInstaller.installApk(MainActivity.this, downloadedApk);
                                }
                            } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                if (updateProgress != null) updateProgress.setVisibility(View.GONE);
                                if (btnUpdateDownload != null) btnUpdateDownload.setVisibility(View.VISIBLE);
                                Toast.makeText(context, "Ошибка при скачивании обновления", Toast.LENGTH_SHORT).show();
                            }
                        }
                        cursor.close();
                    }
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (IllegalArgumentException ignored) {}
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
            } else {
                Toast.makeText(this, "Файл обновления не найден, попробуйте скачать заново", Toast.LENGTH_SHORT).show();
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(updateReceiver, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

        public void checkForUpdates(boolean manualCheck) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/pixxel-dev/Fullscreen/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "FlymeTweak-App");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String tagName = json.optString("tag_name", "");
                    JSONArray assets = json.optJSONArray("assets");

                    String apkUrl = null;
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            if (asset.optString("name", "").endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    if (apkUrl != null && isNewerVersion(tagName)) {
                        final String finalApkUrl = apkUrl;
                        final String versionName = json.optString("name", tagName);
                        final String body = json.optString("body", "");
                        runOnUiThread(() -> {
                            downloadUrl = finalApkUrl;
                            updateTitle.setText("Доступно обновление: " + versionName);
                            if (!body.isEmpty() && updateDescription != null) {
                                updateDescription.setText(body);
                                updateDescription.setVisibility(View.VISIBLE);
                            }
                            updateCard.setVisibility(View.VISIBLE);
                            if (manualCheck) {
                                Toast.makeText(MainActivity.this, "Найдено обновление!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        if (manualCheck) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "У вас установлена последняя версия", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } else {
                    int responseCode = conn.getResponseCode();
                    Log.w("UpdateCheck", "GitHub API returned code " + responseCode);
                    if (manualCheck) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Ошибка сервера (Код " + responseCode + ")", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e("UpdateCheck", "Error checking updates", e);
                if (manualCheck) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Ошибка проверки: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void checkForUpdates() {
        checkForUpdates(false);
    }

    private boolean isNewerVersion(String tag) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String currentVersion = pInfo.versionName;
            long currentVersionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode;
            
            if (currentVersion == null || tag == null || tag.isEmpty()) return false;

            String cleanTag = tag.replaceAll("^v", "").trim();
            String cleanCurrent = currentVersion.replaceAll("^v", "").trim();

            String tagSemVer = cleanTag.split("-")[0].replaceAll("[^0-9\\.]", "");
            String currentSemVer = cleanCurrent.split("-")[0].replaceAll("[^0-9\\.]", "");

            String[] tagParts = tagSemVer.split("\\.");
            String[] currentParts = currentSemVer.split("\\.");

            int length = Math.max(tagParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int t = i < tagParts.length && !tagParts[i].isEmpty() ? Integer.parseInt(tagParts[i]) : 0;
                int c = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
                if (t > c) return true;
                if (t < c) return false;
            }

            // Semantic versions are equal. Let's compare build numbers.
            if (cleanTag.contains("build")) {
                try {
                    int tagBuild = Integer.parseInt(cleanTag.substring(cleanTag.lastIndexOf("build") + 5).replaceAll("[^0-9]", ""));
                    return tagBuild > currentVersionCode;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e("UpdateCheck", "Error comparing versions", e);
        }
        return false;
    }

    private void downloadUpdate() {
        if (downloadUrl == null) return;

        downloadedApk = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "fullscreen_update.apk");
        if (downloadedApk.exists()) {
            downloadedApk.delete();
        }

        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("Обновление Fullscreen");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "fullscreen_update.apk");

        android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            updateDownloadId = manager.enqueue(request);
            Toast.makeText(this, "Загрузка началась", Toast.LENGTH_SHORT).show();
            if (btnUpdateDownload != null) {
                btnUpdateDownload.setVisibility(View.GONE);
            }
            if (updateProgress != null) {
                updateProgress.setVisibility(View.VISIBLE);
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

        MenuItem logItem = menu.findItem(R.id.action_log_record);
        if (logItem != null) {
            if (LogManager.getInstance().isRecording()) {
                logItem.setTitle("Стоп запись лога");
            } else {
                logItem.setTitle("Запись лога");
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
        } else if (item.getItemId() == R.id.action_log_record) {
            toggleLogRecording();
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

    private void toggleLogRecording() {
        LogManager logManager = LogManager.getInstance();
        if (!logManager.isRecording()) {
            logManager.startRecording();
            Toast.makeText(this, "Запись лога запущенa", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
        } else {
            String logText = logManager.stopRecording();
            invalidateOptionsMenu();

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Записанный лог")
                    .setMessage(logText.length() > 2000 ? logText.substring(0, 2000) + "\n... [Лог слишком длинный, нажмите «Скопировать»]" : logText)
                    .setPositiveButton("Скопировать в буфер обмена", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("App Log", logText);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(this, "Лог скопирован в буфер обмена", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Закрыть", null)
                    .show();
        }
    }

    private void toggleTheme() {
        boolean isNightMode = sharedPreferences.getBoolean(FlymeApp.PREF_NIGHT_MODE, false);
        boolean newNightMode = !isNightMode;

        sharedPreferences.edit().putBoolean(FlymeApp.PREF_NIGHT_MODE, newNightMode).apply();

        AppCompatDelegate.setDefaultNightMode(
                newNightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
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

            Preference checkUpdatesPref = findPreference("check_updates");
            if (checkUpdatesPref != null) {
                checkUpdatesPref.setOnPreferenceClickListener(preference -> {
                    Toast.makeText(requireContext(), "Проверка обновлений...", Toast.LENGTH_SHORT).show();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkForUpdates(true);
                    }
                    return true;
                });
            }

            Preference shizukuPref = findPreference("shizuku_control");
            if (shizukuPref != null) {
                updateShizukuSummary(shizukuPref);
                shizukuPref.setOnPreferenceClickListener(preference -> {
                    ShizukuManager.checkStatus(requireContext(), (isAvailable, hasPermission, statusMessage) -> {
                        if (!isAvailable) {
                            showShizukuInstructionDialog();
                        } else if (!hasPermission) {
                            ShizukuManager.requestPermission((requestCode, grantResult) -> {
                                if (getActivity() != null) {
                                    requireActivity().runOnUiThread(() -> {
                                        updateShizukuSummary(shizukuPref);
                                        updatePermissionsStates();
                                        Toast.makeText(requireContext(),
                                                grantResult == PackageManager.PERMISSION_GRANTED ? "Разрешение Shizuku получено!" : "Разрешение Shizuku отклонено",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                }
                            });
                        } else {
                            Toast.makeText(requireContext(), "🟢 Shizuku работает и готов к установке приложений без Root!", Toast.LENGTH_LONG).show();
                        }
                        updateShizukuSummary(shizukuPref);
                    });
                    return true;
                });
            }

            Preference adbPairingPref = findPreference("adb_pairing");
            if (adbPairingPref != null) {
                updateAdbSummary(adbPairingPref);
                adbPairingPref.setOnPreferenceClickListener(preference -> {
                    showAdbPairingDialog();
                    return true;
                });
            }

            Preference sysInfoPref = findPreference("app_sys_info");
            if (sysInfoPref != null) {
                sysInfoPref.setOnPreferenceClickListener(preference -> {
                    showSystemInfoDialog();
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

        private void showShizukuInstructionDialog() {
            Context context = requireContext();
            String instruction = ShizukuManager.getInstructionText(context);

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Инструкция по настройке Shizuku")
                    .setMessage(instruction);

            if (ShizukuManager.isShizukuAppInstalled(context)) {
                builder.setPositiveButton("Запустить Shizuku", (dialog, which) -> {
                    ShizukuManager.openShizukuApp(context);
                });
            } else {
                builder.setPositiveButton("Скачать Shizuku", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            builder.setNeutralButton("Сопряжение ADB", (dialog, which) -> showAdbPairingDialog());
            builder.setNegativeButton("Закрыть", null);
            builder.show();
        }

        private void updateShizukuSummary(Preference shizukuPref) {
            if (shizukuPref == null) return;
            shizukuPref.setSummary(ShizukuManager.getStatusSummary(requireContext()));
        }

        private void updateAdbSummary(Preference pref) {
            if (pref == null) return;
            pref.setSummary(NativeAdbHelper.getStatusSummary(requireContext()));
        }

        private void showSystemInfoDialog() {
            Context context = requireContext();
            String sysInfoText = LogManager.getInstance().gatherSystemInformation(context);

            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Информация о системе")
                    .setMessage(sysInfoText)
                    .setPositiveButton("Скопировать в буфер обмена", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("System Info", sysInfoText);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(context, "Информация о системе скопирована", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Закрыть", null)
                    .show();
        }

        private void showAdbPairingDialog() {
            android.view.View view = getLayoutInflater().inflate(R.layout.dialog_adb_pairing, null);
            com.google.android.material.button.MaterialButton btnOpenDev = view.findViewById(R.id.btn_open_dev_settings);
            if (btnOpenDev != null) {
                btnOpenDev.setOnClickListener(v -> NativeAdbHelper.openDeveloperSettings(requireContext()));
            }

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
                                            Preference adbPref = findPreference("adb_pairing");
                                            if (adbPref != null) updateAdbSummary(adbPref);
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
            Preference shizukuPref = findPreference("shizuku_control");
            if (shizukuPref != null) {
                updateShizukuSummary(shizukuPref);
            }
            Preference adbPref = findPreference("adb_pairing");
            if (adbPref != null) {
                updateAdbSummary(adbPref);
            }
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
                    if (ShizukuManager.isAvailable()) {
                        if (!ShizukuManager.hasPermission()) {
                            ShizukuManager.requestPermission((requestCode, grantResult) -> {
                                if (getActivity() != null) {
                                    requireActivity().runOnUiThread(this::updatePermissionsStates);
                                }
                            });
                        } else {
                            Toast.makeText(getContext(), "Разрешение Shizuku уже предоставлено", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        showShizukuInstructionDialog();
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
                boolean shizukuGranted = ShizukuManager.isAvailable() && ShizukuManager.hasPermission();
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
        }
    }
}
