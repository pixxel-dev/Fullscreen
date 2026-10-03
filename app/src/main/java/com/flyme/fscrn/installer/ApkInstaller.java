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
import androidx.core.content.FileProvider;

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

    public static int getUserId() {
        return Process.myUid() / 100000;
    }

    public static void installApk(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "Файл APK не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_install_progress, null);

        TextView tvFileName = dialogView.findViewById(R.id.tv_install_file_name);
        TextView tvPine = dialogView.findViewById(R.id.tv_step_pine);
        TextView tvShizuku = dialogView.findViewById(R.id.tv_step_shizuku);
        TextView tvNativeAdb = dialogView.findViewById(R.id.tv_step_native_adb);
        TextView tvLocalAdb = dialogView.findViewById(R.id.tv_step_local_adb);
        TextView tvPackageInstaller = dialogView.findViewById(R.id.tv_step_package_installer);
        TextView tvFileProvider = dialogView.findViewById(R.id.tv_step_file_provider);
        ProgressBar progressBar = dialogView.findViewById(R.id.pb_install_progress);

        tvFileName.setText("Файл: " + file.getName());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Диагностика и установка APK")
                .setView(dialogView)
                .setNegativeButton("Закрыть", null)
                .show();

        runStep0_Pine(context, file, tvPine, tvShizuku, tvNativeAdb, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
    }

    private static void runStep0_Pine(Context context, File file, TextView tvPine, TextView tvShizuku, TextView tvNativeAdb, TextView tvLocalAdb, TextView tvPackageInstaller, TextView tvFileProvider, ProgressBar progressBar) {
        runOnMain(context, () -> tvPine.setText("⏳ 0. Pine Framework Hook: Запуск..."));
        PineInstaller.installApk(context, file, (success, message) -> {
            if (success) {
                runOnMain(context, () -> {
                    tvPine.setText("✅ 0. Pine Framework Hook: " + message);
                    progressBar.setVisibility(View.GONE);
                });
            } else {
                runOnMain(context, () -> {
                    tvPine.setText("❌ 0. Pine Framework Hook: " + message);
                    runStep1_Shizuku(context, file, tvShizuku, tvNativeAdb, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
                });
            }
        });
    }

    private static void runStep1_Shizuku(Context context, File file, TextView tvShizuku, TextView tvNativeAdb, TextView tvLocalAdb, TextView tvPackageInstaller, TextView tvFileProvider, ProgressBar progressBar) {
        runOnMain(context, () -> tvShizuku.setText("⏳ 1. Shizuku API: Проверка..."));
        new Thread(() -> {
            try {
                if (ShizukuManager.isAvailable() && ShizukuManager.hasPermission()) {
                    runOnMain(context, () -> tvShizuku.setText("⏳ 1. Shizuku API: Запуск установки..."));
                    int userId = getUserId();
                    String userIdStr = String.valueOf(userId);

                    java.lang.Process process = rikka.shizuku.Shizuku.newProcess(
                            new String[]{"pm", "install", "-r", "-g", "--user", userIdStr, file.getAbsolutePath()}, null, null);

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
                            tvShizuku.setText("❌ 1. Shizuku API: " + (result.isEmpty() ? "Ошибка выполнения команды" : result));
                            runStep2_NativeAdb(context, file, tvNativeAdb, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
                        });
                    }
                } else {
                    runOnMain(context, () -> {
                        tvShizuku.setText("❌ 1. Shizuku API: Служба не запущена или нет прав");
                        runStep2_NativeAdb(context, file, tvNativeAdb, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Shizuku install error", e);
                runOnMain(context, () -> {
                    tvShizuku.setText("❌ 1. Shizuku API: Сбой " + e.getMessage());
                    runStep2_NativeAdb(context, file, tvNativeAdb, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
                });
            }
        }).start();
    }

    private static void runStep2_NativeAdb(Context context, File file, TextView tvNativeAdb, TextView tvLocalAdb, TextView tvPackageInstaller, TextView tvFileProvider, ProgressBar progressBar) {
        runOnMain(context, () -> tvNativeAdb.setText("⏳ 2. Native ADB TLS: Проверка..."));
        if (NativeAdbHelper.isConnected()) {
            runOnMain(context, () -> tvNativeAdb.setText("⏳ 2. Native ADB TLS: Запуск установки..."));
            NativeAdbHelper.installApk(context, file, (success, message) -> {
                if (success) {
                    runOnMain(context, () -> {
                        tvNativeAdb.setText("✅ 2. Native ADB TLS: Успешно установлено!");
                        progressBar.setVisibility(View.GONE);
                    });
                } else {
                    runOnMain(context, () -> {
                        tvNativeAdb.setText("❌ 2. Native ADB TLS: " + message);
                        runStep3_LocalAdb(context, file, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
                    });
                }
            });
        } else {
            runOnMain(context, () -> {
                tvNativeAdb.setText("❌ 2. Native ADB TLS: Нет сопряжения");
                runStep3_LocalAdb(context, file, tvLocalAdb, tvPackageInstaller, tvFileProvider, progressBar);
            });
        }
    }

    private static void runStep3_LocalAdb(Context context, File file, TextView tvLocalAdb, TextView tvPackageInstaller, TextView tvFileProvider, ProgressBar progressBar) {
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
                    runStep4_FileProvider(context, file, tvPackageInstaller, tvFileProvider, progressBar);
                });
            }
        });
    }

    private static void runStep4_FileProvider(Context context, File file, TextView tvPackageInstaller, TextView tvFileProvider, ProgressBar progressBar) {
        runOnMain(context, () -> tvFileProvider.setText("⏳ 4. Системное окно (FileProvider): Подготовка..."));
        new Thread(() -> {
            try {
                Uri apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                context.startActivity(intent);

                runOnMain(context, () -> {
                    tvFileProvider.setText("✅ 4. Системное окно (FileProvider): Открыто диалоговое окно");
                    tvPackageInstaller.setText("➖ 5. PackageInstaller Session: пропущено");
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                Log.e(TAG, "FileProvider install error", e);
                runOnMain(context, () -> {
                    tvFileProvider.setText("❌ 4. Системное окно (FileProvider): Ошибка " + e.getMessage());
                    runStep5_PackageInstaller(context, file, tvPackageInstaller, progressBar);
                });
            }
        }).start();
    }

    private static void runStep5_PackageInstaller(Context context, File file, TextView tvPackageInstaller, ProgressBar progressBar) {
        runOnMain(context, () -> tvPackageInstaller.setText("⏳ 5. PackageInstaller Session: Подготовка..."));
        new Thread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (!context.getPackageManager().canRequestPackageInstalls()) {
                        runOnMain(context, () -> {
                            tvPackageInstaller.setText("❌ 5. PackageInstaller: Требуется разрешение установки источников");
                            progressBar.setVisibility(View.GONE);
                        });
                        return;
                    }
                }

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

                runOnMain(context, () -> {
                    tvPackageInstaller.setText("✅ 5. PackageInstaller Session: Подтвердите запрос на экране");
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                Log.e(TAG, "PackageInstaller error", e);
                runOnMain(context, () -> {
                    tvPackageInstaller.setText("❌ 5. PackageInstaller: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    public static void installApkWithShizuku(Context context, File file) {
        installApk(context, file);
    }

    public static void installApkWithFileProviderIntent(Context context, File file) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "FileProvider install error", e);
            runOnMain(context, () -> Toast.makeText(context, "Ошибка вызова инсталлятора: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    public static void installApkWithPackageInstaller(Context context, File file) {
        installApk(context, file);
    }

    private static void runOnMain(Context context, Runnable runnable) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(runnable);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
        }
    }
}
