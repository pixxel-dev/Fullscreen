package com.flyme.fscrn.overlay;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.flyme.fscrn.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class ForegroundOverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String CHANNEL_ID = "OverlayServiceChannel";

    private WindowManager defaultWindowManager;
    private WindowManager secondaryWindowManager;
    private Context secondaryContext;

    private SharedPreferences prefs;

    private View quickLaunchView;
    private WindowManager.LayoutParams quickLaunchParams;

    private View quickLaunchViewSecondary;
    private WindowManager.LayoutParams quickLaunchParamsSecondary;

    private View fullscreenToggleView;
    private WindowManager.LayoutParams fullscreenToggleParams;

    private View fullscreenToggleViewSecondary;
    private WindowManager.LayoutParams fullscreenToggleParamsSecondary;

    private android.widget.LinearLayout combinedView;
    private WindowManager.LayoutParams combinedParams;

    private android.widget.LinearLayout combinedViewSecondary;
    private WindowManager.LayoutParams combinedParamsSecondary;

    private boolean isTargetAppFullscreen = false;
    private String currentForegroundPackage = "";
    private String activeFullscreenPackage = "";

    private Handler handler;
    private Runnable packageCheckerRunnable;
    private UsageStatsManager usageStatsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        currentForegroundPackage = getPackageName(); // Set to own package initially
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Flyme Tweak")
                .setContentText("Служба управления окнами активна")
                .setSmallIcon(R.drawable.ic_menu)
                .build();
        startForeground(1, notification);

        defaultWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupSecondaryDisplayContext();

        prefs = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        setupPackageChecker();

        updateOverlayButtons();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
            secondaryContext = this;
            secondaryWindowManager = defaultWindowManager;
        }
    }

    private void setupPackageChecker() {
        packageCheckerRunnable = new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                handler.postDelayed(this, 1000); // Check every 1 second
            }
        };
        handler.post(packageCheckerRunnable);
    }

    private void checkForegroundApp() {
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - 2000;

        UsageEvents usageEvents = usageStatsManager.queryEvents(beginTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();
        String currentApp = currentForegroundPackage;

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.getPackageName();
            }
        }

        if (currentApp != null && !currentApp.equals(currentForegroundPackage)) {
            handlePackageChange(currentApp);
        }
    }

    private void handlePackageChange(String newPackage) {
        if (isTargetAppFullscreen
                && !newPackage.equals(activeFullscreenPackage)
                && !newPackage.equals(getPackageName())
                && !newPackage.equals("com.android.launcher3")
                && !newPackage.equals("com.android.systemui")) {
            isTargetAppFullscreen = false;
            activeFullscreenPackage = "";
        }

        currentForegroundPackage = newPackage;
        updateOverlayButtons();
    }

    public void updateOverlayButtons() {
        boolean separateEnabled = prefs.getBoolean("separate_buttons_enabled", false);

        if (separateEnabled) {
            hideCombinedOverlay();
            updateQuickLaunchButton();
            checkFullscreenCondition();
        } else {
            hideSeparateButtons();
            updateCombinedOverlay();
        }
    }

    private void hideSeparateButtons() {
        if (quickLaunchView != null) {
            defaultWindowManager.removeView(quickLaunchView);
            quickLaunchView = null;
        }
        if (quickLaunchViewSecondary != null) {
            secondaryWindowManager.removeView(quickLaunchViewSecondary);
            quickLaunchViewSecondary = null;
        }
        if (fullscreenToggleView != null) {
            defaultWindowManager.removeView(fullscreenToggleView);
            fullscreenToggleView = null;
        }
        if (fullscreenToggleViewSecondary != null) {
            secondaryWindowManager.removeView(fullscreenToggleViewSecondary);
            fullscreenToggleViewSecondary = null;
        }
    }

    private void hideCombinedOverlay() {
        if (combinedView != null) {
            defaultWindowManager.removeView(combinedView);
            combinedView = null;
        }
        if (combinedViewSecondary != null) {
            secondaryWindowManager.removeView(combinedViewSecondary);
            combinedViewSecondary = null;
        }
    }

    private void updateCombinedOverlay() {
        boolean showQuickLaunch = prefs.getBoolean("quick_launch_enabled", false);

        Set<String> fullscreenApps = prefs.getStringSet("fullscreen_apps", Collections.emptySet());
        boolean isCurrentAppTarget = fullscreenApps.contains(currentForegroundPackage);
        boolean showFullscreen = (prefs.getBoolean("fullscreen_overlay_enabled", false) && isCurrentAppTarget) || isTargetAppFullscreen;

        if (!showQuickLaunch && !showFullscreen) {
            hideCombinedOverlay();
            return;
        }

        int sizeDp = prefs.getInt("combined_button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        String position = prefs.getString("combined_position_x", "left");
        boolean isHorizontalMode = "top".equals(position) || "bottom".equals(position);

        int gravity = Gravity.NO_GRAVITY;
        int oppositeGravity = Gravity.NO_GRAVITY;

        if ("top".equals(position)) {
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else if ("bottom".equals(position)) {
            gravity = Gravity.BOTTOM | Gravity.START;
            oppositeGravity = Gravity.BOTTOM | Gravity.START;
        } else if ("right".equals(position)) {
            gravity = Gravity.TOP | Gravity.END;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else { // left
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.END;
        }

        if (combinedView != null) {
            defaultWindowManager.removeView(combinedView);
            combinedView = null;
        }

        combinedView = createCombinedLinearLayout(this, showQuickLaunch, showFullscreen, sizePx, isHorizontalMode, false, defaultWindowManager);
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        combinedParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        combinedParams.gravity = gravity;
        if (isHorizontalMode) {
            combinedParams.x = prefs.getInt("combined_position_x_offset", 200);
            combinedParams.y = 0;
        } else {
            combinedParams.x = 0;
            combinedParams.y = prefs.getInt("combined_position_y", 200);
        }

        defaultWindowManager.addView(combinedView, combinedParams);

        if (secondaryWindowManager != defaultWindowManager && secondaryContext != null) {
            if (combinedViewSecondary != null) {
                secondaryWindowManager.removeView(combinedViewSecondary);
                combinedViewSecondary = null;
            }

            // Do not duplicate on secondary display for top/bottom (horizontal) modes
            if (!isHorizontalMode) {
                combinedViewSecondary = createCombinedLinearLayout(secondaryContext, showQuickLaunch, showFullscreen, sizePx, false, true, secondaryWindowManager);
                combinedParamsSecondary = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        layoutFlag,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);

                combinedParamsSecondary.gravity = oppositeGravity;
                combinedParamsSecondary.x = 0;
                combinedParamsSecondary.y = prefs.getInt("combined_position_y", 200);

                secondaryWindowManager.addView(combinedViewSecondary, combinedParamsSecondary);
            }
        }
    }

    private android.widget.LinearLayout createCombinedLinearLayout(Context context, boolean showQuickLaunch, boolean showFullscreen, int sizePx, boolean isHorizontalMode, boolean isSecondary, WindowManager wm) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(isHorizontalMode ? android.widget.LinearLayout.HORIZONTAL : android.widget.LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);

        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());

        if (showQuickLaunch) {
            View qlItem = LayoutInflater.from(context).inflate(R.layout.overlay_layout, layout, false);
            ImageView iv = qlItem.findViewById(R.id.overlay_image_view);
            iv.setImageResource(R.drawable.ic_menu);
            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;

            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) qlItem.getLayoutParams();
            if (isHorizontalMode) {
                lp.setMargins(marginPx, 0, marginPx, 0);
            } else {
                lp.setMargins(0, marginPx, 0, marginPx);
            }

            setupCombinedButtonTouch(qlItem, isHorizontalMode, this::showQuickLaunchMenu, wm, isSecondary);
            layout.addView(qlItem);
        }

        if (showFullscreen) {
            View fsItem = LayoutInflater.from(context).inflate(R.layout.overlay_layout, layout, false);
            ImageView iv = fsItem.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);
            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;

            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) fsItem.getLayoutParams();
            if (isHorizontalMode) {
                lp.setMargins(marginPx, 0, marginPx, 0);
            } else {
                lp.setMargins(0, marginPx, 0, marginPx);
            }

            setupCombinedButtonTouch(fsItem, isHorizontalMode, this::toggleFullscreen, wm, isSecondary);
            layout.addView(fsItem);
        }

        return layout;
    }

    private void setupCombinedButtonTouch(View buttonView, boolean isHorizontalMode, Runnable onClick, WindowManager wm, boolean isSecondary) {
        buttonView.setOnTouchListener(new View.OnTouchListener() {
            private int initialPos;
            private float initialTouchPos;
            private boolean isClick;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams params = isSecondary ? combinedParamsSecondary : combinedParams;
                View container = isSecondary ? combinedViewSecondary : combinedView;
                if (params == null || container == null) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialPos = isHorizontalMode ? params.x : params.y;
                        initialTouchPos = isHorizontalMode ? event.getRawX() : event.getRawY();
                        isClick = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int delta = (int) ((isHorizontalMode ? event.getRawX() : event.getRawY()) - initialTouchPos);
                        if (Math.abs(delta) > ViewConfiguration.get(ForegroundOverlayService.this).getScaledTouchSlop()) {
                            isClick = false;
                        }
                        if (!isClick) {
                            if (isHorizontalMode) {
                                params.x = initialPos + delta;
                            } else {
                                params.y = initialPos + delta;
                            }
                            wm.updateViewLayout(container, params);
                            if (!isSecondary && combinedParamsSecondary != null && combinedViewSecondary != null) {
                                if (isHorizontalMode) {
                                    combinedParamsSecondary.x = params.x;
                                } else {
                                    combinedParamsSecondary.y = params.y;
                                }
                                secondaryWindowManager.updateViewLayout(combinedViewSecondary, combinedParamsSecondary);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            onClick.run();
                        } else {
                            if (isHorizontalMode) {
                                prefs.edit().putInt("combined_position_x_offset", params.x).apply();
                            } else {
                                prefs.edit().putInt("combined_position_y", params.y).apply();
                            }
                        }
                        return true;
                }
                return true;
            }
        });
    }

    private void checkFullscreenCondition() {
        Set<String> fullscreenApps = prefs.getStringSet("fullscreen_apps", Collections.emptySet());
        boolean isCurrentAppTarget = fullscreenApps.contains(currentForegroundPackage);
        boolean shouldShowFullscreenBtn = prefs.getBoolean("fullscreen_overlay_enabled", false) && isCurrentAppTarget;

        if (shouldShowFullscreenBtn || isTargetAppFullscreen) {
            showFullscreenToggleButton();
        } else {
            hideFullscreenToggleButton();
        }
    }

    public void updateFullscreenState() {
        updateOverlayButtons();
    }

    public void updateQuickLaunchButton() {
        boolean isEnabled = prefs.getBoolean("quick_launch_enabled", false);
        if (!isEnabled) {
            if (quickLaunchView != null) {
                defaultWindowManager.removeView(quickLaunchView);
                quickLaunchView = null;
            }
            if (quickLaunchViewSecondary != null) {
                secondaryWindowManager.removeView(quickLaunchViewSecondary);
                quickLaunchViewSecondary = null;
            }
            return;
        }

        int sizeDp = prefs.getInt("ql_button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        String position = prefs.getString("ql_position_x", "left");
        boolean isHorizontalMode = "top".equals(position) || "bottom".equals(position);

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

            if (isHorizontalMode) {
                quickLaunchParams.x = prefs.getInt("ql_position_x_offset", 200);
                quickLaunchParams.y = 0;
            } else {
                quickLaunchParams.x = 0;
                quickLaunchParams.y = prefs.getInt("ql_position_y", 200);
            }

            setupDragAndClick(quickLaunchView, quickLaunchParams, isHorizontalMode, "ql_position_x_offset", "ql_position_y", this::showQuickLaunchMenu, defaultWindowManager);
            defaultWindowManager.addView(quickLaunchView, quickLaunchParams);
        } else {
            ImageView iv = quickLaunchView.findViewById(R.id.overlay_image_view);
            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;
            quickLaunchParams.width = sizePx;
            quickLaunchParams.height = sizePx;
        }

        int gravity = Gravity.NO_GRAVITY;
        int oppositeGravity = Gravity.NO_GRAVITY;

        if ("top".equals(position)) {
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else if ("bottom".equals(position)) {
            gravity = Gravity.BOTTOM | Gravity.START;
            oppositeGravity = Gravity.BOTTOM | Gravity.START;
        } else if ("right".equals(position)) {
            gravity = Gravity.TOP | Gravity.END;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else { // left
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.END;
        }

        if (isHorizontalMode) {
            quickLaunchParams.x = prefs.getInt("ql_position_x_offset", 200);
            quickLaunchParams.y = 0;
        } else {
            quickLaunchParams.x = 0;
            quickLaunchParams.y = prefs.getInt("ql_position_y", 200);
        }

        quickLaunchParams.gravity = gravity;
        setupDragAndClick(quickLaunchView, quickLaunchParams, isHorizontalMode, "ql_position_x_offset", "ql_position_y", this::showQuickLaunchMenu, defaultWindowManager);
        defaultWindowManager.updateViewLayout(quickLaunchView, quickLaunchParams);

        if (secondaryWindowManager != defaultWindowManager && secondaryContext != null) {
            if (isHorizontalMode) {
                if (quickLaunchViewSecondary != null) {
                    secondaryWindowManager.removeView(quickLaunchViewSecondary);
                    quickLaunchViewSecondary = null;
                }
            } else {
                if (quickLaunchViewSecondary == null) {
                    quickLaunchViewSecondary = LayoutInflater.from(secondaryContext).inflate(R.layout.overlay_layout, null);
                    ImageView ivSecondary = quickLaunchViewSecondary.findViewById(R.id.overlay_image_view);
                    ivSecondary.setImageResource(R.drawable.ic_menu);

                    int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE;

                    quickLaunchParamsSecondary = new WindowManager.LayoutParams(
                            sizePx, sizePx, layoutFlag,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);

                    quickLaunchParamsSecondary.x = 0;
                    quickLaunchParamsSecondary.y = prefs.getInt("ql_position_y", 200);
                    quickLaunchParamsSecondary.gravity = oppositeGravity;

                    setupDragAndClick(quickLaunchViewSecondary, quickLaunchParamsSecondary, false, "ql_position_x_offset", "ql_position_y", this::showQuickLaunchMenu, secondaryWindowManager);
                    secondaryWindowManager.addView(quickLaunchViewSecondary, quickLaunchParamsSecondary);
                } else {
                    ImageView ivSecondary = quickLaunchViewSecondary.findViewById(R.id.overlay_image_view);
                    ivSecondary.getLayoutParams().width = sizePx;
                    ivSecondary.getLayoutParams().height = sizePx;
                    quickLaunchParamsSecondary.width = sizePx;
                    quickLaunchParamsSecondary.height = sizePx;
                    quickLaunchParamsSecondary.gravity = oppositeGravity;
                    secondaryWindowManager.updateViewLayout(quickLaunchViewSecondary, quickLaunchParamsSecondary);
                }
            }
        }
    }

    private void showFullscreenToggleButton() {
        int sizeDp = prefs.getInt("fs_button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        String position = prefs.getString("fs_position_x", "left");
        boolean isHorizontalMode = "top".equals(position) || "bottom".equals(position);

        int gravity = Gravity.NO_GRAVITY;
        int oppositeGravity = Gravity.NO_GRAVITY;

        if ("top".equals(position)) {
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else if ("bottom".equals(position)) {
            gravity = Gravity.BOTTOM | Gravity.START;
            oppositeGravity = Gravity.BOTTOM | Gravity.START;
        } else if ("right".equals(position)) {
            gravity = Gravity.TOP | Gravity.END;
            oppositeGravity = Gravity.TOP | Gravity.START;
        } else { // left
            gravity = Gravity.TOP | Gravity.START;
            oppositeGravity = Gravity.TOP | Gravity.END;
        }

        if (fullscreenToggleView != null) {
            ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;
            fullscreenToggleParams.width = sizePx;
            fullscreenToggleParams.height = sizePx;
            fullscreenToggleParams.gravity = gravity;

            if (isHorizontalMode) {
                fullscreenToggleParams.x = prefs.getInt("fs_position_x_offset", 100);
                fullscreenToggleParams.y = 0;
            } else {
                fullscreenToggleParams.x = 0;
                fullscreenToggleParams.y = prefs.getInt("fs_position_y", 100);
            }

            setupDragAndClick(fullscreenToggleView, fullscreenToggleParams, isHorizontalMode, "fs_position_x_offset", "fs_position_y", this::toggleFullscreen, defaultWindowManager);
            defaultWindowManager.updateViewLayout(fullscreenToggleView, fullscreenToggleParams);
        } else {
            fullscreenToggleView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
            ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            fullscreenToggleParams = new WindowManager.LayoutParams(
                    sizePx, sizePx, layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            fullscreenToggleParams.gravity = gravity;
            if (isHorizontalMode) {
                fullscreenToggleParams.x = prefs.getInt("fs_position_x_offset", 100);
                fullscreenToggleParams.y = 0;
            } else {
                fullscreenToggleParams.x = 0;
                fullscreenToggleParams.y = prefs.getInt("fs_position_y", 100);
            }

            setupDragAndClick(fullscreenToggleView, fullscreenToggleParams, isHorizontalMode, "fs_position_x_offset", "fs_position_y", this::toggleFullscreen, defaultWindowManager);
            defaultWindowManager.addView(fullscreenToggleView, fullscreenToggleParams);
        }

        if (secondaryWindowManager != defaultWindowManager && secondaryContext != null) {
            if (isHorizontalMode) {
                if (fullscreenToggleViewSecondary != null) {
                    secondaryWindowManager.removeView(fullscreenToggleViewSecondary);
                    fullscreenToggleViewSecondary = null;
                }
            } else {
                if (fullscreenToggleViewSecondary != null) {
                    ImageView ivSecondary = fullscreenToggleViewSecondary.findViewById(R.id.overlay_image_view);
                    ivSecondary.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

                    ivSecondary.getLayoutParams().width = sizePx;
                    ivSecondary.getLayoutParams().height = sizePx;
                    fullscreenToggleParamsSecondary.width = sizePx;
                    fullscreenToggleParamsSecondary.height = sizePx;
                    fullscreenToggleParamsSecondary.gravity = oppositeGravity;

                    secondaryWindowManager.updateViewLayout(fullscreenToggleViewSecondary, fullscreenToggleParamsSecondary);
                } else {
                    fullscreenToggleViewSecondary = LayoutInflater.from(secondaryContext).inflate(R.layout.overlay_layout, null);
                    ImageView ivSecondary = fullscreenToggleViewSecondary.findViewById(R.id.overlay_image_view);
                    ivSecondary.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

                    int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE;

                    fullscreenToggleParamsSecondary = new WindowManager.LayoutParams(
                            sizePx, sizePx, layoutFlag,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);

                    fullscreenToggleParamsSecondary.gravity = oppositeGravity;
                    fullscreenToggleParamsSecondary.y = prefs.getInt("fs_position_y", 100);

                    setupDragAndClick(fullscreenToggleViewSecondary, fullscreenToggleParamsSecondary, false, "fs_position_x_offset", "fs_position_y", this::toggleFullscreen, secondaryWindowManager);
                    secondaryWindowManager.addView(fullscreenToggleViewSecondary, fullscreenToggleParamsSecondary);
                }
            }
        }
    }

    private void hideFullscreenToggleButton() {
        if (fullscreenToggleView != null) {
            defaultWindowManager.removeView(fullscreenToggleView);
            fullscreenToggleView = null;
        }
        if (fullscreenToggleViewSecondary != null) {
            secondaryWindowManager.removeView(fullscreenToggleViewSecondary);
            fullscreenToggleViewSecondary = null;
        }
    }

    private void toggleFullscreen() {
        if (!isTargetAppFullscreen) {
            activeFullscreenPackage = currentForegroundPackage;
        }

        isTargetAppFullscreen = !isTargetAppFullscreen;

        if (fullscreenToggleView != null) {
            ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);
        }
        if (fullscreenToggleViewSecondary != null) {
            ImageView ivSecondary = fullscreenToggleViewSecondary.findViewById(R.id.overlay_image_view);
            ivSecondary.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);
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
                    activeFullscreenPackage = ""; // reset when exiting
                }
            } catch (Exception e) {
                startActivity(launchIntent);
            }
        }

        updateOverlayButtons(); // force re-evaluation of visibility
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

    private void setupDragAndClick(View view, WindowManager.LayoutParams params, boolean isHorizontalMode, String xPrefKey, String yPrefKey, Runnable onClick, WindowManager wm) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialPos;
            private float initialTouchPos;
            private boolean isClick;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialPos = isHorizontalMode ? params.x : params.y;
                        initialTouchPos = isHorizontalMode ? event.getRawX() : event.getRawY();
                        isClick = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int delta = (int) ((isHorizontalMode ? event.getRawX() : event.getRawY()) - initialTouchPos);
                        if (Math.abs(delta) > ViewConfiguration.get(ForegroundOverlayService.this).getScaledTouchSlop()) {
                            isClick = false;
                        }
                        if (!isClick) {
                            if (isHorizontalMode) {
                                params.x = initialPos + delta;
                            } else {
                                params.y = initialPos + delta;
                            }
                            wm.updateViewLayout(view, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            onClick.run();
                        } else {
                            if (isHorizontalMode) {
                                prefs.edit().putInt(xPrefKey, params.x).apply();
                            } else {
                                prefs.edit().putInt(yPrefKey, params.y).apply();
                            }
                        }
                        return true;
                }
                return true;
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Оверлей Служба",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && packageCheckerRunnable != null) {
            handler.removeCallbacks(packageCheckerRunnable);
        }
        hideSeparateButtons();
        hideCombinedOverlay();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            updateOverlayButtons();
        }
    }
}
