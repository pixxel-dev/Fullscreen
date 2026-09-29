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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

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
        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = prefs.getInt("position", 100);

            windowManager.addView(overlayView, params);

            overlayView.setOnClickListener(v -> launchApp());

            overlayView.setOnTouchListener(new View.OnTouchListener() {
                private int initialY;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialY = params.y;
                            initialTouchY = event.getRawY();
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(overlayView, params);
                            return false;
                        case MotionEvent.ACTION_UP:
                            prefs.edit().putInt("position", params.y).apply();
                            return false;
                    }
                    return false;
                }
            });
        }
        return START_STICKY;
    }

    private void launchApp() {
        String targetPackage = prefs.getString("ihu_package", null);
        if (targetPackage != null && !targetPackage.isEmpty()) {
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
        } else {
            Toast.makeText(this, "No app selected in settings!", Toast.LENGTH_SHORT).show();
        }
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
