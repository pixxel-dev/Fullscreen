package com.flyme.fscrn.adb;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.File;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.RSAKeyGenParameterSpec;

public class LocalAdbHelper {
    private static final String TAG = "LocalAdbHelper";

    public interface AdbListener {
        void onResult(boolean success, String message);
    }

    public static void installApk(Context context, File apkFile, AdbListener listener) {
        executeShellCommand(context, "pm install -r --user 10 \"" + apkFile.getAbsolutePath() + "\"", listener);
    }

    public static void executeShellCommand(Context context, String command, AdbListener listener) {
        new Thread(() -> {
            Socket socket = null;
            AdbConnection connection = null;
            AdbStream stream = null;
            try {
                // 1. Generate or load keys for authentication
                AdbCrypto crypto = getAdbCrypto(context);
                if (crypto == null) {
                    listener.onResult(false, "Не удалось получить или сгенерировать ключи ADB");
                    return;
                }

                // 2. Connect to local ADB server
                socket = new Socket("127.0.0.1", 5555);
                socket.setTcpNoDelay(true);

                // 3. Initialize ADB Connection
                connection = AdbConnection.create(socket, crypto);
                connection.connect();

                // 4. Open shell stream and execute install command
                stream = connection.open("shell:" + command);

                StringBuilder output = new StringBuilder();

                // Keep reading until the stream is closed
                while (!stream.isClosed()) {
                    try {
                        byte[] buffer = stream.read();
                        if (buffer != null && buffer.length > 0) {
                            output.append(new String(buffer));
                        }
                    } catch (Exception e) {
                        break;
                    }
                }

                String result = output.toString().trim();
                boolean success = result.toLowerCase().contains("success");

                listener.onResult(success, success ? "Успешно установлено (через Local ADB)" : "Ошибка ADB: " + result);

            } catch (java.net.ConnectException ce) {
                listener.onResult(false, "Ошибка подключения: Local ADB (порт 5555) не включен.");
            } catch (Exception e) {
                Log.e(TAG, "ADB Error", e);
                listener.onResult(false, "Ошибка ADB: " + e.getMessage());
            } finally {
                try {
                    if (stream != null) stream.close();
                } catch (Exception ignored) {}
                try {
                    if (connection != null) connection.close();
                } catch (Exception ignored) {}
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static AdbCrypto getAdbCrypto(Context context) {
        File privKey = new File(context.getFilesDir(), "adbkey");
        File pubKey = new File(context.getFilesDir(), "adbkey.pub");

        com.cgutman.adblib.AdbBase64 base64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);

        AdbCrypto crypto = null;
        if (privKey.exists() && pubKey.exists()) {
            try {
                crypto = AdbCrypto.loadAdbKeyPair(base64, privKey, pubKey);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load ADB key pair", e);
            }
        }

        if (crypto == null) {
            try {
                crypto = AdbCrypto.generateAdbKeyPair(base64);
                crypto.saveAdbKeyPair(privKey, pubKey);
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate ADB key pair", e);
            }
        }
        return crypto;
    }
}
