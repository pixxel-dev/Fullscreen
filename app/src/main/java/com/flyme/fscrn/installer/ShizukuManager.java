package com.flyme.fscrn.installer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import rikka.shizuku.Shizuku;

public class ShizukuManager {

    private static final String TAG = "ShizukuManager";
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

    public static void checkStatus(Context context, StatusCallback callback) {
        boolean available = isAvailable();
        boolean granted = available && hasPermission();

        String statusMessage;
        if (!available) {
            statusMessage = "Shizuku не запущен или недоступен.";
        } else if (!granted) {
            statusMessage = "Shizuku запущен, но разрешение для приложения не предоставлено.";
        } else {
            statusMessage = "Shizuku запущен и доступен для работы!";
        }

        if (callback != null) {
            callback.onStatusChecked(available, granted, statusMessage);
        }
    }
}
