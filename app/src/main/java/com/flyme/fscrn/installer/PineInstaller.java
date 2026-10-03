package com.flyme.fscrn.installer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class PineInstaller {

    private static final String TAG = "PineInstaller";
    private static final String ACTION_PINE_INSTALL_COMPLETE = "com.flyme.fscrn.ACTION_PINE_INSTALL_COMPLETE";
    private static final long TIMEOUT_MS = 15000;

    public interface PineInstallCallback {
        void onResult(boolean success, String message);
    }

    public static void installApk(Context context, File file, PineInstallCallback callback) {
        if (file == null || !file.exists()) {
            if (callback != null) {
                callback.onResult(false, "Файл APK не найден");
            }
            return;
        }

        new Thread(() -> {
            final AtomicBoolean isHandled = new AtomicBoolean(false);
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (!isHandled.compareAndSet(false, true)) {
                        return;
                    }

                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignored) {}

                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

                    Log.d(TAG, "Received Pine install result broadcast. Status: " + status + ", Message: " + message);

                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        if (callback != null) {
                            callback.onResult(true, "Успешно установлено!");
                        }
                    } else {
                        String errMsg = message != null && !message.isEmpty() ? message : "Код ошибки: " + status;
                        if (callback != null) {
                            callback.onResult(false, "Сбой системной установки: " + errMsg);
                        }
                    }
                }
            };

            Runnable timeoutRunnable = () -> {
                if (isHandled.compareAndSet(false, true)) {
                    try {
                        context.unregisterReceiver(receiver);
                    } catch (Exception ignored) {}

                    Log.w(TAG, "Pine installation broadcast timed out");
                    if (callback != null) {
                        callback.onResult(false, "Таймаут ожидания ответа от системы");
                    }
                }
            };

            try {
                // Register BroadcastReceiver for installation completion status
                IntentFilter filter = new IntentFilter(ACTION_PINE_INSTALL_COMPLETE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    context.registerReceiver(receiver, filter);
                }

                // Step 1: Apply Pine ART hooks
                boolean hookSuccess = PineHookManager.applyPackageInstallerHooks(context);
                if (!hookSuccess) {
                    try {
                        context.unregisterReceiver(receiver);
                    } catch (Exception ignored) {}
                    if (callback != null) {
                        callback.onResult(false, "Не удалось применить хуки Pine");
                    }
                    return;
                }

                // Step 2: Attempt PackageInstaller session creation with Pine hooks active
                PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                );

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

                Intent intent = new Intent(ACTION_PINE_INSTALL_COMPLETE);
                intent.setPackage(context.getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, sessionId, intent, flags
                );

                mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

                session.commit(pendingIntent.getIntentSender());
                session.close();

                Log.d(TAG, "Pine session committed successfully, awaiting broadcast result...");

            } catch (Throwable e) {
                if (isHandled.compareAndSet(false, true)) {
                    try {
                        context.unregisterReceiver(receiver);
                    } catch (Exception ignored) {}
                    mainHandler.removeCallbacks(timeoutRunnable);

                    Log.e(TAG, "Pine installation error", e);
                    if (callback != null) {
                        callback.onResult(false, "Сбой Pine: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
}
