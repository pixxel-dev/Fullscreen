package com.flyme.fscrn.adb;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NativeAdbHelper {
    private static final String TAG = "NativeAdbHelper";

    // Store connection port globally to use for subsequent commands
    private static String currentConnectionPort = null;

    /**
     * Gets the path to the native adb binary extracted by the Android Package Manager.
     */
    private static String getAdbPath(Context context) {
        String path = context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
        File f = new File(path);
        if (f.exists()) {
            return path;
        }
        Log.e(TAG, "adb binary not found at " + path);
        return null;
    }

    /**
     * Выполняет сопряжение (pairing) для Android 11+
     */
    public static boolean pair(Context context, String port, String pairingCode) {
        String adbPath = getAdbPath(context);
        if (adbPath == null) return false;

        try {
            // Сначала запустим сервер
            ProcessBuilder serverPb = new ProcessBuilder(Arrays.asList(adbPath, "start-server"));
            serverPb.environment().put("HOME", context.getFilesDir().getPath());
            serverPb.environment().put("TMPDIR", context.getCacheDir().getPath());
            serverPb.start().waitFor();

            List<String> command = Arrays.asList(adbPath, "pair", "localhost:" + port);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.environment().put("HOME", context.getFilesDir().getPath());
            pb.environment().put("TMPDIR", context.getCacheDir().getPath());

            Process process = pb.start();

            // Пишем код сопряжения в stdin
            java.io.PrintStream ps = new java.io.PrintStream(process.getOutputStream());
            ps.println(pairingCode);
            ps.flush();

            boolean finished = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                finished = process.waitFor(10, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }

            if (!finished) {
                process.destroy();
                return false;
            }

            // Процесс pair от LADB обычно возвращает 0 при успехе.
            // При использовании stdin он может не выдавать "successfully paired" в stdout,
            // поэтому мы просто полагаемся на код возврата.
            return process.exitValue() == 0;

        } catch (Exception e) {
            Log.e(TAG, "Pairing failed", e);
            return false;
        }
    }

    /**
     * Выполняет подключение к основному порту ADB (TLS connection)
     */
    public static boolean connect(Context context, String connectionPort) {
        String adbPath = getAdbPath(context);
        if (adbPath == null) return false;

        try {
            List<String> command = Arrays.asList(adbPath, "connect", "localhost:" + connectionPort);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.environment().put("HOME", context.getFilesDir().getPath());
            pb.environment().put("TMPDIR", context.getCacheDir().getPath());

            Process process = pb.start();
            process.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean success = false;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("connected to")) {
                    success = true;
                }
            }

            if (success) {
                currentConnectionPort = connectionPort;
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
            return false;
        }
    }

    /**
     * Выполняет установку APK через нативный ADB.
     */
    public static void installApk(Context context, File apkFile, LocalAdbHelper.AdbListener listener) {
        new Thread(() -> {
            String adbPath = getAdbPath(context);
            if (adbPath == null || currentConnectionPort == null) {
                listener.onResult(false, "ADB не инициализирован или не подключен (Android 11+)");
                return;
            }

            try {
                // Если у нас несколько устройств (например 5555 и TLS порт), указываем порт
                List<String> command = Arrays.asList(adbPath, "-s", "localhost:" + currentConnectionPort, "install", "-r", "--user", "10", apkFile.getAbsolutePath());
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(context.getFilesDir());
                pb.environment().put("HOME", context.getFilesDir().getPath());
                pb.environment().put("TMPDIR", context.getCacheDir().getPath());

                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                process.waitFor();

                String result = output.toString().trim();
                boolean success = result.toLowerCase().contains("success");

                listener.onResult(success, success ? "Успешно установлено (через Native ADB)" : "Ошибка Native ADB: " + result);

            } catch (Exception e) {
                Log.e(TAG, "Install failed", e);
                listener.onResult(false, "Ошибка Native ADB: " + e.getMessage());
            }
        }).start();
    }

    public static boolean isConnected() {
        return currentConnectionPort != null;
    }

    /**
     * Executes a raw ADB shell command.
     */
    public static boolean executeCommand(Context context, String commandLine) {
        String adbPath = getAdbPath(context);
        if (adbPath == null || currentConnectionPort == null) {
            return false;
        }

        try {
            String[] shellCmd = commandLine.split(" ");
            List<String> command = new java.util.ArrayList<>();
            command.add(adbPath);
            command.add("-s");
            command.add("localhost:" + currentConnectionPort);
            command.add("shell");
            command.addAll(Arrays.asList(shellCmd));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.environment().put("HOME", context.getFilesDir().getPath());
            pb.environment().put("TMPDIR", context.getCacheDir().getPath());

            Process process = pb.start();
            process.waitFor();

            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
            return false;
        }
    }
}
