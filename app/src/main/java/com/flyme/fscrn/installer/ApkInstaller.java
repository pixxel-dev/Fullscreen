package com.flyme.fscrn.installer;

import com.flyme.fscrn.R;
import com.flyme.fscrn.adb.LocalAdbHelper;
import com.flyme.fscrn.adb.NativeAdbHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class ApkInstaller {

    private static final String TAG = "ApkInstaller";

    public interface InstallCallback {
        void onResult(boolean success, String message);
    }

    public static void installApk(Context context, File file) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_install_progress, null);

        TextView tvFileName = dialogView.findViewById(R.id.tv_install_file_name);
        TextView tvShizuku = dialogView.findViewById(R.id.tv_step_shizuku);
        TextView tvNativeAdb = dialogView.findViewById(R.id.tv_step_native_adb);
        TextView tvLocalAdb = dialogView.findViewById(R.id.tv_step_local_adb);
        TextView tvPackageInstaller = dialogView.findViewById(R.id.tv_step_package_installer);
        ProgressBar progressBar = dialogView.findViewById(R.id.pb_install_progress);

        tvFileName.setText("Файл: " + file.getName());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Диагностика и установка APK")
                .setView(dialogView)
                .setNegativeButton("Закрыть", null)
                .show();

        new Thread(() -> {
            // STEP 1: SHIZUKU
            if (ShizukuManager.isAvailable() && ShizukuManager.hasPermission()) {
                runOnMain(context, () -> tvShizuku.setText("✅ 1. Shizuku API: Запуск установки..."));
                executeShizukuInstall(context, file, dialog, tvShizuku, tvNativeAdb, tvLocalAdb, tvPackageInstaller, progressBar);
                return;
            } else {
                runOnMain(context, () -> tvShizuku.setText("❌ 1. Shizuku API: Служба не запущена или нет прав"));
            }

            // STEP 2: NATIVE ADB TLS
            if (NativeAdbHelper.isConnected()) {
                runOnMain(context, () -> tvNativeAdb.setText("✅ 2. Native ADB TLS: Запуск установки..."));
                executeNativeAdbInstall(context, file, dialog, tvNativeAdb, tvLocalAdb, tvPackageInstaller, progressBar);
                return;
            } else {
                runOnMain(context, () -> tvNativeAdb.setText("❌ 2. Native ADB TLS: Нет сопряжения"));
            }

            // STEP 3: LOCAL ADB 5555
            runOnMain(context, () -> tvLocalAdb.setText("⏳ 3. Local ADB: Проверка порта 5555..."));
            LocalAdbHelper.installApk(context, file, (success, message) -> {
                if (success) {
                    runOnMain(context, () -> {
                        tvLocalAdb.setText("✅ 3. Local ADB: Успешно установлено!");
                        progressBar.setVisibility(View.GONE);
                    });
                } else {
                    runOnMain(context, () -> {
                        tvLocalAdb.setText("❌ 3. Local ADB: Порт 5555 недоступен");
                        // STEP 4: PACKAGE INSTALLER
                        tvPackageInstaller.setText("✅ 4. Стандартный PackageInstaller: Открытие инсталлера...");
                        progressBar.setVisibility(View.GONE);
                        installApkWithPackageInstaller(context, file);
                    });
                }
            });

        }).start();
    }

    private static void executeShizukuInstall(Context context, File file, AlertDialog dialog,
                                               TextView tvShizuku, TextView tvNativeAdb,
                                               TextView tvLocalAdb, TextView tvPackageInstaller,
                                               ProgressBar progressBar) {
        new Thread(() -> {
            try {
                int userId = 0;
                try {
                    int handleId = Process.myUserHandle().hashCode();
                    if (handleId >= 0) {
                        userId = handleId;
                    }
                } catch (Throwable ignored) {}

                String userIdStr = String.valueOf(userId);
                java.lang.Process process = rikka.shizuku.Shizuku.newProcess(
                        new String[]{"pm", "install", "-r", "--user", userIdStr, file.getAbsolutePath()}, null, null);

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();

                final String result = output.toString().trim();
                if (result.contains("Success")) {
                    runOnMain(context, () -> {
                        tvShizuku.setText("✅ 1. Shizuku API: Успешно установлено без Root!");
                        progressBar.setVisibility(View.GONE);
                    });
                } else {
                    runOnMain(context, () -> {
                        tvShizuku.setText("❌ 1. Shizuku API: Ошибка: " + (result.isEmpty() ? "Сбой команды" : result));
                        // Fallback to Native ADB
                        if (NativeAdbHelper.isConnected()) {
                            tvNativeAdb.setText("✅ 2. Native ADB TLS: Запуск установки...");
                            executeNativeAdbInstall(context, file, dialog, tvNativeAdb, tvLocalAdb, tvPackageInstaller, progressBar);
                        } else {
                            tvNativeAdb.setText("❌ 2. Native ADB TLS: Нет сопряжения");
                            tvLocalAdb.setText("⏳ 3. Local ADB: Проверка порта 5555...");
                            LocalAdbHelper.installApk(context, file, (success, message) -> {
                                if (success) {
                                    runOnMain(context, () -> {
                                        tvLocalAdb.setText("✅ 3. Local ADB: Успешно установлено!");
                                        progressBar.setVisibility(View.GONE);
                                    });
                                } else {
                                    runOnMain(context, () -> {
                                        tvLocalAdb.setText("❌ 3. Local ADB: Порт 5555 недоступен");
                                        tvPackageInstaller.setText("✅ 4. Стандартный PackageInstaller: Открытие...");
                                        progressBar.setVisibility(View.GONE);
                                        installApkWithPackageInstaller(context, file);
                                    });
                                }
                            });
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Shizuku install error", e);
                runOnMain(context, () -> tvShizuku.setText("❌ 1. Shizuku API: Сбой " + e.getMessage()));
            }
        }).start();
    }

    private static void executeNativeAdbInstall(Context context, File file, AlertDialog dialog,
                                                 TextView tvNativeAdb, TextView tvLocalAdb,
                                                 TextView tvPackageInstaller, ProgressBar progressBar) {
        NativeAdbHelper.installApk(context, file, (success, message) -> {
            if (success) {
                runOnMain(context, () -> {
                    tvNativeAdb.setText("✅ 2. Native ADB TLS: Успешно установлено!");
                    progressBar.setVisibility(View.GONE);
                });
            } else {
                runOnMain(context, () -> {
                    tvNativeAdb.setText("❌ 2. Native ADB TLS: " + message);
                    tvLocalAdb.setText("⏳ 3. Local ADB: Проверка порта 5555...");
                    LocalAdbHelper.installApk(context, file, (succ, msg) -> {
                        if (succ) {
                            runOnMain(context, () -> {
                                tvLocalAdb.setText("✅ 3. Local ADB: Успешно установлено!");
                                progressBar.setVisibility(View.GONE);
                            });
                        } else {
                            runOnMain(context, () -> {
                                tvLocalAdb.setText("❌ 3. Local ADB: Порт 5555 недоступен");
                                tvPackageInstaller.setText("✅ 4. Стандартный PackageInstaller: Открытие...");
                                progressBar.setVisibility(View.GONE);
                                installApkWithPackageInstaller(context, file);
                            });
                        }
                    });
                });
            }
        });
    }

    public static void installApkWithShizuku(Context context, File file) {
        installApk(context, file);
    }

    public static void installApkWithPackageInstaller(Context context, File file) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
                Toast.makeText(context, "Разрешите установку неизвестных приложений", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Toast.makeText(context, "Подготовка к установке...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = packageInstaller.createSession(params);
                PackageInstaller.Session session = packageInstaller.openSession(sessionId);

                long sizeBytes = file.length();
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = session.openWrite(file.getName(), 0, sizeBytes)) {
                    byte[] buffer = new byte[65536];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                }

                Intent intent = new Intent("com.flyme.fscrn.ACTION_INSTALL_COMPLETE");
                intent.setPackage(context.getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags);
                session.commit(pendingIntent.getIntentSender());
                session.close();
            } catch (Exception e) {
                Log.e(TAG, "PackageInstaller error", e);
                runOnMain(context, () -> Toast.makeText(context, "Ошибка PackageInstaller: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void runOnMain(Context context, Runnable runnable) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(runnable);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
        }
    }
}
