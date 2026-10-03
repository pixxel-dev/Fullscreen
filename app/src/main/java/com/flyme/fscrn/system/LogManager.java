package com.flyme.fscrn.system;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Process;
import android.view.Display;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class LogManager {
    private static final String TAG = "LogManager";
    private static LogManager instance;

    private boolean isRecording = false;
    private java.lang.Process logcatProcess = null;
    private final StringBuilder recordedLogs = new StringBuilder();
    private Thread logReaderThread = null;

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public synchronized boolean isRecording() {
        return isRecording;
    }

    public synchronized void startRecording() {
        if (isRecording) return;

        isRecording = true;
        recordedLogs.setLength(0);
        recordedLogs.append("=== СТАРТ ЗАПИСИ ЛОГА ===\n");

        try {
            // Clear logcat buffer before starting recording
            Runtime.getRuntime().exec("logcat -c").waitFor();
            logcatProcess = Runtime.getRuntime().exec("logcat -v time");

            logReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()))) {
                    String line;
                    while (isRecording && (line = reader.readLine()) != null) {
                        synchronized (recordedLogs) {
                            recordedLogs.append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading logcat", e);
                }
            });
            logReaderThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting logcat recording", e);
            recordedLogs.append("Ошибка запуска logcat: ").append(e.getMessage()).append("\n");
        }
    }

    public synchronized String stopRecording() {
        if (!isRecording) return recordedLogs.toString();

        isRecording = false;
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }

        if (logReaderThread != null) {
            try {
                logReaderThread.join(500);
            } catch (InterruptedException ignored) {}
            logReaderThread = null;
        }

        synchronized (recordedLogs) {
            recordedLogs.append("=== КОНЕЦ ЗАПИСИ ЛОГА ===\n");
            return recordedLogs.toString();
        }
    }

    public String gatherSystemInformation(Context context) {
        StringBuilder info = new StringBuilder();
        info.append("=== ИНФОРМАЦИЯ О СИСТЕМЕ И АВТОМОБИЛЕ ===\n\n");

        // 1. Process & User Info
        info.append("--- Процесс и Пользователь ---\n");
        info.append("App PID: ").append(Process.myPid()).append("\n");
        info.append("App UID: ").append(Process.myUid()).append("\n");
        try {
            int userId = Process.myUid() / 100000;
            info.append("User ID: ").append(userId).append("\n");
        } catch (Exception ignored) {}

        // 2. Build & Hardware Properties
        info.append("\n--- Параметры Устройства ---\n");
        info.append("MANUFACTURER: ").append(Build.MANUFACTURER).append("\n");
        info.append("BRAND: ").append(Build.BRAND).append("\n");
        info.append("MODEL: ").append(Build.MODEL).append("\n");
        info.append("DEVICE: ").append(Build.DEVICE).append("\n");
        info.append("BOARD: ").append(Build.BOARD).append("\n");
        info.append("HARDWARE: ").append(Build.HARDWARE).append("\n");
        info.append("PRODUCT: ").append(Build.PRODUCT).append("\n");
        info.append("FINGERPRINT: ").append(Build.FINGERPRINT).append("\n");
        info.append("ANDROID SDK: ").append(Build.VERSION.SDK_INT).append(" (Release: ").append(Build.VERSION.RELEASE).append(")\n");

        // 3. System Properties (VIN, Model, Car specifics)
        info.append("\n--- Системные Свойства Автомобиля (System Properties) ---\n");
        String[] propKeys = new String[]{
                "ro.vin", "ro.car.vin", "ro.product.model", "ro.build.display.id",
                "ro.flyme.version", "ro.build.version.incremental", "persist.sys.locale",
                "ro.hardware", "ro.boot.hardware"
        };
        for (String key : propKeys) {
            String val = getSystemProperty(key);
            if (val != null && !val.isEmpty()) {
                info.append(key).append(" = ").append(val).append("\n");
            }
        }

        // 4. Display Manager Details (Multidisplay 1003 / Head Unit)
        info.append("\n--- Дисплеи и Мониторы ---\n");
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            Display[] displays = dm.getDisplays();
            info.append("Всего подключено дисплеев: ").append(displays.length).append("\n");
            for (Display d : displays) {
                info.append("▸ Display ID: ").append(d.getDisplayId())
                        .append(" | Name: ").append(d.getName())
                        .append(" | State: ").append(d.getState()).append("\n");

                DisplayMetrics metrics = new DisplayMetrics();
                d.getMetrics(metrics);
                info.append("  Разрешение: ").append(metrics.widthPixels).append("x").append(metrics.heightPixels)
                        .append(" | DensityDpi: ").append(metrics.densityDpi)
                        .append(" | Density: ").append(metrics.density).append("\n");
            }
        }

        info.append("\n=== КОНЕЦ ОТЧЕТА ===");
        return info.toString();
    }

    private String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            return (String) get.invoke(c, key);
        } catch (Exception e) {
            return null;
        }
    }
}
