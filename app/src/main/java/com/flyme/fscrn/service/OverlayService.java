package com.flyme.fscrn.service;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.flyme.fscrn.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class OverlayService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String ACTION_UPDATE_OVERLAYS = "com.flyme.fscrn.ACTION_UPDATE_OVERLAYS";
    private static OverlayService instance;
    private WindowManager secondaryWindowManager;
    private Context secondaryContext;

    private SharedPreferences prefs;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        defaultWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);


        prefs = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateQuickLaunchButton();
    }
    
    private void setupSecondaryDisplayContext() {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display secondaryDisplay = displayManager.getDisplay(1003); // ID экрана автомобиля
        if (secondaryDisplay != null) {
            secondaryContext = createDisplayContext(secondaryDisplay);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                secondaryContext = secondaryContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            }
            secondaryWindowManager = (WindowManager) secondaryContext.getSystemService(WINDOW_SERVICE);
        } else {
            // Если экран 1003 не найден (например, тест на телефоне), используем основной
            secondaryContext = this;
            secondaryWindowManager = defaultWindowManager;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String newPackage = event.getPackageName().toString();

                if (!newPackage.equals(activeFullscreenPackage) && !newPackage.equals("com.flyme.fscrn")) {
                    isTargetAppFullscreen = false;
                    activeFullscreenPackage = "";
                }

                currentForegroundPackage = newPackage;
                checkFullscreenCondition();
            }
        }
    }

    public void updateFullscreenState() {
        checkFullscreenCondition();
    }

    @Override
    public void onInterrupt() {
    }

        if (shouldShowFullscreenBtn || isTargetAppFullscreen) {
            showFullscreenToggleButton();
        } else {
            hideFullscreenToggleButton();
        }
    }

    public void updateQuickLaunchButton() {
        boolean isEnabled = prefs.getBoolean("quick_launch_enabled", false);
        if (!isEnabled) {
            if (quickLaunchView != null) {
                defaultWindowManager.removeView(quickLaunchView);
                quickLaunchView = null;
            }
            return;
        }

        int sizeDp = prefs.getInt("button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        if (quickLaunchView == null) {
            quickLaunchView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
            ImageView iv = quickLaunchView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(R.drawable.ic_menu);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            quickLaunchParams = new WindowManager.LayoutParams(
                    sizePx, sizePx, layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            quickLaunchParams.x = 0;
            quickLaunchParams.y = prefs.getInt("ql_position_y", 200);

            setupDragAndClick(quickLaunchView, quickLaunchParams, "ql_position_y", this::showQuickLaunchMenu, defaultWindowManager);
            defaultWindowManager.addView(quickLaunchView, quickLaunchParams);
        } else {

            ImageView iv = quickLaunchView.findViewById(R.id.overlay_image_view);
            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;
            quickLaunchParams.width = sizePx;
            quickLaunchParams.height = sizePx;
        }

        String positionX = prefs.getString("overlay_position_x", "left");
        quickLaunchParams.gravity = Gravity.TOP | ("right".equals(positionX) ? Gravity.END : Gravity.START);
        defaultWindowManager.updateViewLayout(quickLaunchView, quickLaunchParams);
    }

    private void showFullscreenToggleButton() {
        int sizeDp = prefs.getInt("button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        if (fullscreenToggleView != null) {
            ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;
            fullscreenToggleParams.width = sizePx;
            fullscreenToggleParams.height = sizePx;
            secondaryWindowManager.updateViewLayout(fullscreenToggleView, fullscreenToggleParams);
            return;

    private void toggleFullscreen() {
        if (!isTargetAppFullscreen) {
            activeFullscreenPackage = currentForegroundPackage;
        }

        String targetPackage = isTargetAppFullscreen ? currentForegroundPackage : activeFullscreenPackage;
        if (targetPackage == null || targetPackage.isEmpty()) {
            targetPackage = currentForegroundPackage;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                if (isTargetAppFullscreen) {
                    Bundle bundle = ActivityOptions.makeBasic()
                            .setLaunchDisplayId(1003)
                            .toBundle();
                    startActivity(launchIntent, bundle);
                } else {
                    startActivity(launchIntent);

                }
            } catch (Exception e) {
                startActivity(launchIntent);
            }
        }
    }

    private void showQuickLaunchMenu() {
        Set<String> targetPackages = prefs.getStringSet("quick_launch_apps", Collections.emptySet());
        if (targetPackages.isEmpty()) {
            Toast.makeText(this, "Нет приложений для быстрого запуска!", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> packages = new ArrayList<>(targetPackages);
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
                .setTitle("Быстрый запуск")
                .setItems(appNames, (dialogInterface, i) -> {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packages.get(i));
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                    }
                })
                .create();

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        dialog.getWindow().setType(layoutFlag);
        dialog.show();
    }

    private void setupDragAndClick(View view, WindowManager.LayoutParams params, String prefKey, Runnable onClick, WindowManager wm) {
        view.setOnTouchListener(new View.OnTouchListener() {
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
                            wm.updateViewLayout(view, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            onClick.run();
                        } else {
                            prefs.edit().putInt(prefKey, params.y).apply();
                        }
                        return true;
                }
                return true;
            }
        });
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (quickLaunchView != null) defaultWindowManager.removeView(quickLaunchView);
        if (fullscreenToggleView != null) secondaryWindowManager.removeView(fullscreenToggleView);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            updateQuickLaunchButton();
            updateFullscreenState();
        }
    }
}
