package com.flyme.fscrn.installer;

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
        // Priority sequence: 1. Shizuku -> 2. Native ADB (TLS) -> 3. Local ADB -> 4. Standard PackageInstaller
        if (ShizukuManager.isAvailable()) {
            installApkWithShizuku(context, file);
            return;
        }

        if (NativeAdbHelper.isConnected()) {
            Toast.makeText(context, "Shizuku не запущен. Пробуем Native ADB (Android 11+)...", Toast.LENGTH_SHORT).show();
            NativeAdbHelper.installApk(context, file, (success, message) -> {
                runOnMain(context, () -> {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    if (!success) {
                        tryLegacyLocalAdb(context, file);
                    }
                });
            });
            return;
        }

        tryLegacyLocalAdb(context, file);
    }

    private static void tryLegacyLocalAdb(Context context, File file) {
        Toast.makeText(context, "Пробуем Local ADB (порт 5555)...", Toast.LENGTH_SHORT).show();
        LocalAdbHelper.installApk(context, file, (success, message) -> {
            runOnMain(context, () -> {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                if (!success && message.contains("порт 5555")) {
                    new AlertDialog.Builder(context)
                        .setTitle("Внимание")
                        .setMessage("Отладка по Wi-Fi отключена или недоступна. Открыть стандартный установщик?")
                        .setPositiveButton("Да", (d, w) -> installApkWithPackageInstaller(context, file))
                        .setNegativeButton("Отмена", null)
                        .show();
                }
            });
        });
    }

    public static void installApkWithShizuku(Context context, File file) {
        if (!ShizukuManager.isAvailable()) {
            Toast.makeText(context, "Shizuku не запущен или недоступен", Toast.LENGTH_LONG).show();
            return;
        }

        if (!ShizukuManager.hasPermission()) {
            ShizukuManager.requestPermission(null);
            Toast.makeText(context, "Запрошено разрешение Shizuku, повторите установку после смены прав", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, "Начинаю установку через Shizuku...", Toast.LENGTH_SHORT).show();
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
                runOnMain(context, () -> {
                    if (result.contains("Success")) {
                        Toast.makeText(context, "Успешно установлено через Shizuku", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "Ошибка установки Shizuku: " + (result.isEmpty() ? "Неизвестная ошибка" : result), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Shizuku install error", e);
                runOnMain(context, () -> Toast.makeText(context, "Сбой установки Shizuku: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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
