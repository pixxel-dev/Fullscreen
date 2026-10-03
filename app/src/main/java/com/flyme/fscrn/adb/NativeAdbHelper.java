package com.flyme.fscrn.adb;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NativeAdbHelper {
    private static final String TAG = "NativeAdbHelper";

    private static String currentConnectionPort = null;

    private static String getAdbPath(Context context) {
        String path = context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
        File f = new File(path);
        if (f.exists()) {
            return path;
        }
        Log.e(TAG, "adb binary not found at " + path);
        return null;
    }

    public static boolean pair(Context context, String port, String pairingCode) {
        String adbPath = getAdbPath(context);
        if (adbPath == null) return false;

        try {
            ProcessBuilder serverPb = new ProcessBuilder(Arrays.asList(adbPath, "start-server"));
            serverPb.environment().put("HOME", context.getFilesDir().getPath());
            serverPb.environment().put("TMPDIR", context.getCacheDir().getPath());
            serverPb.start().waitFor();

            List<String> command = Arrays.asList(adbPath, "pair", "localhost:" + port);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.environment().put("HOME", context.getFilesDir().getPath());
            pb.environment().put("TMPDIR", context.getCacheDir().getPath());

            java.lang.Process process = pb.start();

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

            return process.exitValue() == 0;

        } catch (Exception e) {
            Log.e(TAG, "Pairing failed", e);
            return false;
        }
    }

    public static boolean connect(Context context, String connectionPort) {
        String adbPath = getAdbPath(context);
        if (adbPath == null) return false;

        try {
            List<String> command = Arrays.asList(adbPath, "connect", "localhost:" + connectionPort);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.environment().put("HOME", context.getFilesDir().getPath());
            pb.environment().put("TMPDIR", context.getCacheDir().getPath());

            java.lang.Process process = pb.start();
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

    public static void installApk(Context context, File apkFile, LocalAdbHelper.AdbListener listener) {
        new Thread(() -> {
            String adbPath = getAdbPath(context);
            if (adbPath == null || currentConnectionPort == null) {
                listener.onResult(false, "ADB не инициализирован или не подключен");
                return;
            }

            try {
                int userId = 0;
                try {
                    int handleId = Process.myUserHandle().hashCode();
                    if (handleId >= 0) {
                        userId = handleId;
                    }
                } catch (Throwable ignored) {}

                List<String> command = Arrays.asList(adbPath, "-s", "localhost:" + currentConnectionPort, "install", "-r", "--user", String.valueOf(userId), apkFile.getAbsolutePath());
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(context.getFilesDir());
                pb.environment().put("HOME", context.getFilesDir().getPath());
                pb.environment().put("TMPDIR", context.getCacheDir().getPath());

                java.lang.Process process = pb.start();

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

    public static String getStatusSummary(Context context) {
        if (isConnected()) {
            return "🟢 Беспроводная отладка ADB подключена (порт " + currentConnectionPort + ")";
        }
        return "🔴 Беспроводной ADB не сопряжен (нажмите для настройки)";
    }

    public static void openDeveloperSettings(Context context) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "Cannot open settings", ex);
            }
        }
    }

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

            java.lang.Process process = pb.start();
            process.waitFor();

            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
            return false;
        }
    }
}
