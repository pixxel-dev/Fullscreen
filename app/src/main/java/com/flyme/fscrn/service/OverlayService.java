package com.flyme.fscrn.service;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.util.TypedValue;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.view.WindowManager.LayoutParams;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;

import com.flyme.fscrn.R;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private SharedPreferences prefs;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "overlay_channel")
                .setContentTitle("Service running")
                .setContentText("Displaying over other apps")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);

        if (overlayView == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            int sizeDp = prefs.getInt("button_size", 48);
            int sizePx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    sizePx,
                    sizePx,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            String positionX = prefs.getString("overlay_position_x", "left");
            if ("right".equals(positionX)) {
                params.gravity = Gravity.TOP | Gravity.END;
            } else {
                params.gravity = Gravity.TOP | Gravity.START;
            }

            params.x = 0;
            params.y = prefs.getInt("position", 100);

            windowManager.addView(overlayView, params);

            overlayView.setOnClickListener(v -> launchApp());

            // Ensure child ImageView matches parent size
            View imageView = overlayView.findViewById(R.id.overlay_image_view);
            if (imageView != null) {
                imageView.getLayoutParams().width = sizePx;
                imageView.getLayoutParams().height = sizePx;
            }

            overlayView.setOnTouchListener(new View.OnTouchListener() {
                private int initialY;
                private float initialTouchY;
                private boolean isClick;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialY = params.y;
                            initialTouchY = event.getRawY();
                            isClick = true;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int deltaY = (int) (event.getRawY() - initialTouchY);
                            if (Math.abs(deltaY) > ViewConfiguration.get(OverlayService.this).getScaledTouchSlop()) {
                                isClick = false;
                            }
                            if (!isClick) {
                                params.y = initialY + deltaY;
                                windowManager.updateViewLayout(overlayView, params);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (isClick) {
                                v.performClick();
                            }
                            prefs.edit().putInt("position", params.y).apply();
                            return true;
                    }
                    return true;
                }
            });
        }
        return START_STICKY;
    }

    private void launchApp() {
        Set<String> targetPackages = prefs.getStringSet("ihu_package", Collections.emptySet());

        if (targetPackages.isEmpty()) {
            Toast.makeText(this, "No app selected in settings!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetPackages.size() == 1) {
            startPackage(targetPackages.iterator().next());
        } else {
            showAppSelectionDialog(new ArrayList<>(targetPackages));
        }
    }

    private void startPackage(String targetPackage) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                Bundle bundle = ActivityOptions.makeBasic().toBundle();
                startActivity(launchIntent, bundle);
            } catch (Exception e) {
                startActivity(launchIntent);
            }
        } else {
            Toast.makeText(this, "App not found!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppSelectionDialog(ArrayList<String> packages) {
        PackageManager pm = getPackageManager();
        String[] appNames = new String[packages.size()];
        for (int i = 0; i < packages.size(); i++) {
            try {
                appNames[i] = pm.getApplicationLabel(pm.getApplicationInfo(packages.get(i), 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                appNames[i] = packages.get(i);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Выберите приложение")
                .setItems(appNames, (dialogInterface, i) -> {
                    startPackage(packages.get(i));
                })
                .create();

        // Allow the dialog to be displayed from a background service
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        dialog.getWindow().setType(layoutFlag);
        dialog.show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "overlay_channel",
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }
}
