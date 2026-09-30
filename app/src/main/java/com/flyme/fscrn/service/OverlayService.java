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
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;

import com.flyme.fscrn.R;



    private void setupSecondaryDisplayContext() {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display secondaryDisplay = displayManager.getDisplay(1003);
        if (secondaryDisplay != null) {
            secondaryContext = createDisplayContext(secondaryDisplay);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                secondaryContext = secondaryContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            }
            secondaryWindowManager = (WindowManager) secondaryContext.getSystemService(WINDOW_SERVICE);
        } else {
            // Fallback to primary if 1003 is not found
            secondaryContext = this;
            secondaryWindowManager = defaultWindowManager;
        }
    }

    @Override

        } else {
            startForeground(1, notification);
        }
    }



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


        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (isFullscreenModeReady) {
                    Bundle bundle = ActivityOptions.makeBasic()
                            .setLaunchDisplayId(1003)
                            .toBundle();
                    startActivity(launchIntent, bundle);

                    // Reset state
                    isFullscreenModeReady = false;
                    ImageView iv = overlayView.findViewById(R.id.overlay_image_view);
                    if (iv != null) iv.setImageResource(R.mipmap.ic_launcher);
                } else {
                    startActivity(launchIntent);

                }
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

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            updateQuickLaunchButton();
            updateFullscreenState();
        }
    }
}
