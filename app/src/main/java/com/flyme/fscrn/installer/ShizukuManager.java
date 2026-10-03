package com.flyme.fscrn.installer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import rikka.shizuku.Shizuku;

public class ShizukuManager {

    private static final String TAG = "ShizukuManager";
    public static final String SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api";
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    public interface StatusCallback {
        void onStatusChecked(boolean isAvailable, boolean hasPermission, String statusMessage);
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            Log.e(TAG, "Error checking Shizuku binder availability", t);
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!isAvailable()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Log.e(TAG, "Error checking Shizuku permission", t);
            return false;
        }
    }

    public static boolean isShizukuAppInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean openShizukuApp(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Shizuku app", e);
        }
        return false;
    }

    public static void requestPermission(Shizuku.OnRequestPermissionResultListener listener) {
        if (!isAvailable()) {
            return;
        }
        try {
            if (listener != null) {
                Shizuku.addRequestPermissionResultListener(listener);
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
        } catch (Throwable t) {
            Log.e(TAG, "Error requesting Shizuku permission", t);
        }
    }

    public static String getStatusSummary(Context context) {
        boolean available = isAvailable();
        boolean granted = available && hasPermission();

        if (!available) {
            return "🔴 Служба не запущена (нажмите для инструкции)";
        } else if (!granted) {
            return "🟡 Служба запущена, нажмите для запроса прав";
        } else {
            return "🟢 Доступ предоставлен (тихая установка активна)";
        }
    }

    public static void checkStatus(Context context, StatusCallback callback) {
        boolean available = isAvailable();
        boolean granted = available && hasPermission();

        String statusMessage;
        if (!available) {
            statusMessage = "Shizuku не запущен или недоступен.";
        } else if (!granted) {
            statusMessage = "Shizuku запущен, но разрешение для приложения не предоставлено.";
        } else {
            statusMessage = "Shizuku запущен и готов к установке приложений!";
        }

        if (callback != null) {
            callback.onStatusChecked(available, granted, statusMessage);
        }
    }

    public static String getInstructionText(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Shizuku — это системная служба для выполнения команд без Root-прав.\n\n");
        sb.append("📌 КАК ЗАПУСТИТЬ SHIZUKU НА МАГНИТОЛЕ:\n\n");
        sb.append("1. Убедитесь, что приложение Shizuku установлено на устройстве.\n");
        sb.append("2. В настройках магнитолы включите «Отладка по Wi-Fi» (Защита / Настройки разработчика).\n");
        sb.append("3. Запустите Shizuku и выберите «Запуск через беспроводную отладку».\n");
        sb.append("4. Воспользуйтесь меню «Сопряжение ADB» в нашем приложении для быстрого подключения без ПК.\n");
        sb.append("5. После запуска службы вернитесь сюда и нажмите «Запросить разрешение».");
        return sb.toString();
    }
}
