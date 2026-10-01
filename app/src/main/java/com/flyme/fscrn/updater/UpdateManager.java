package com.flyme.fscrn.updater;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.flyme.fscrn.installer.ApkInstaller;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private final Activity activity;
    private final MaterialCardView updateCard;
    private final TextView updateTitle;
    private final TextView updateDescription;
    private final ProgressBar updateProgress;
    private final Button btnUpdateDownload;
    private final Button btnUpdateInstall;

    private String downloadUrl;
    private File downloadedApk;

    public UpdateManager(Activity activity, MaterialCardView updateCard, TextView updateTitle,
                         TextView updateDescription, ProgressBar updateProgress,
                         Button btnUpdateDownload, Button btnUpdateInstall) {
        this.activity = activity;
        this.updateCard = updateCard;
        this.updateTitle = updateTitle;
        this.updateDescription = updateDescription;
        this.updateProgress = updateProgress;
        this.btnUpdateDownload = btnUpdateDownload;
        this.btnUpdateInstall = btnUpdateInstall;

        setupListeners();
    }

    private void setupListeners() {
        btnUpdateDownload.setOnClickListener(v -> downloadUpdate());
        btnUpdateInstall.setOnClickListener(v -> {
            if (downloadedApk != null && downloadedApk.exists()) {
                ApkInstaller.installApk(activity, downloadedApk);
            }
        });
    }

    public void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/pixxel-dev/Fullscreen/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String tagName = json.getString("tag_name");
                    JSONArray assets = json.getJSONArray("assets");

                    String apkUrl = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    if (apkUrl != null && isNewerVersion(tagName)) {
                        final String finalApkUrl = apkUrl;
                        final String versionName = json.optString("name", tagName);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            downloadUrl = finalApkUrl;
                            updateTitle.setText("Доступно обновление: " + versionName);
                            updateCard.setVisibility(View.VISIBLE);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private boolean isNewerVersion(String tag) {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            String currentVersion = pInfo.versionName;

            String cleanTag = tag.replaceAll("^v", "").split("-")[0];
            String cleanCurrent = currentVersion.replaceAll("^v", "").split("-")[0];

            cleanTag = cleanTag.replaceAll("[^0-9\\.]", "");
            cleanCurrent = cleanCurrent.replaceAll("[^0-9\\.]", "");

            String[] tagParts = cleanTag.split("\\.");
            String[] currentParts = cleanCurrent.split("\\.");

            int length = Math.max(tagParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int t = i < tagParts.length && !tagParts[i].isEmpty() ? Integer.parseInt(tagParts[i]) : 0;
                int c = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
                if (t > c) return true;
                if (t < c) return false;
            }

            if (tag.contains("build") && currentVersion.contains("build")) {
                int tagBuild = Integer.parseInt(tag.substring(tag.lastIndexOf("build") + 5));
                int currentBuild = Integer.parseInt(currentVersion.substring(currentVersion.lastIndexOf("build") + 5));
                return tagBuild > currentBuild;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void downloadUpdate() {
        if (downloadUrl == null) return;

        btnUpdateDownload.setVisibility(View.GONE);
        updateProgress.setVisibility(View.VISIBLE);
        updateProgress.setIndeterminate(false);
        updateProgress.setProgress(0);

        new Thread(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();

                File cacheDir = activity.getExternalCacheDir();
                if (cacheDir == null) cacheDir = activity.getCacheDir();
                downloadedApk = new File(cacheDir, "update.apk");

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(downloadedApk);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                Handler mainHandler = new Handler(Looper.getMainLooper());

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> updateProgress.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                mainHandler.post(() -> {
                    updateProgress.setVisibility(View.GONE);
                    updateDescription.setText("Готово к установке");
                    btnUpdateInstall.setVisibility(View.VISIBLE);
                    ApkInstaller.installApk(activity, downloadedApk);
                });

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnUpdateDownload.setVisibility(View.VISIBLE);
                    updateProgress.setVisibility(View.GONE);
                    updateDescription.setText("Готово к установке");
                });
            }
        }).start();
    }
}
