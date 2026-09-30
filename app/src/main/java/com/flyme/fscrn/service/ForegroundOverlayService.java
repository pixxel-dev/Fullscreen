package com.flyme.fscrn.service;

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
    private static ForegroundOverlayService instance;

    private WindowManager defaultWindowManager;
    private WindowManager secondaryWindowManager;
    private Context secondaryContext;

    private SharedPreferences prefs;

    private View quickLaunchView;
    private WindowManager.LayoutParams quickLaunchParams;

    private View fullscreenToggleView;
    private WindowManager.LayoutParams fullscreenToggleParams;

    private boolean isTargetAppFullscreen = false;
    private String currentForegroundPackage = "";
    private String activeFullscreenPackage = "";

    private Handler handler;
    private Runnable packageCheckerRunnable;
    private UsageStatsManager usageStatsManager;

    public static ForegroundOverlayService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
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

        updateQuickLaunchButton();
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

    // Method exposed for AccessibilityService to call and inject immediate updates
    public void onAccessibilityWindowChanged(String packageName) {
        handlePackageChange(packageName);
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
        if (!newPackage.equals(activeFullscreenPackage) && !newPackage.equals(getPackageName())) {
            isTargetAppFullscreen = false;
            activeFullscreenPackage = "";
        }

        currentForegroundPackage = newPackage;
        checkFullscreenCondition();
    }

    private void checkFullscreenCondition() {
        Set<String> fullscreenApps = prefs.getStringSet("fullscreen_apps", Collections.emptySet());
        boolean isCurrentAppTarget = fullscreenApps.contains(currentForegroundPackage);
        boolean shouldShowFullscreenBtn = prefs.getBoolean("fullscreen_overlay_enabled", false)
                && (isCurrentAppTarget || (isTargetAppFullscreen && currentForegroundPackage.equals(getPackageName())));

        if (shouldShowFullscreenBtn || isTargetAppFullscreen) {
            showFullscreenToggleButton();
        } else {
            hideFullscreenToggleButton();
        }
    }

    public void updateFullscreenState() {
        checkFullscreenCondition();
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

        int sizeDp = prefs.getInt("ql_button_size", 48);
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

        String positionX = prefs.getString("ql_position_x", "left");
        int gravity = Gravity.TOP;
        if ("right".equals(positionX)) {
            gravity |= Gravity.END;
        } else if ("center".equals(positionX)) {
            gravity |= Gravity.CENTER_HORIZONTAL;
        } else {
            gravity |= Gravity.START;
        }
        quickLaunchParams.gravity = gravity;
        defaultWindowManager.updateViewLayout(quickLaunchView, quickLaunchParams);
    }

    private void showFullscreenToggleButton() {
        int sizeDp = prefs.getInt("fs_button_size", 48);
        int sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sizeDp, getResources().getDisplayMetrics());

        if (fullscreenToggleView != null) {
            ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
            iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

            iv.getLayoutParams().width = sizePx;
            iv.getLayoutParams().height = sizePx;
            fullscreenToggleParams.width = sizePx;
            fullscreenToggleParams.height = sizePx;

            String positionX = prefs.getString("fs_position_x", "left");
            int gravity = Gravity.TOP;
            if ("right".equals(positionX)) {
                gravity |= Gravity.END;
            } else if ("center".equals(positionX)) {
                gravity |= Gravity.CENTER_HORIZONTAL;
            } else {
                gravity |= Gravity.START;
            }
            fullscreenToggleParams.gravity = gravity;

            secondaryWindowManager.updateViewLayout(fullscreenToggleView, fullscreenToggleParams);
            return;
        }

        fullscreenToggleView = LayoutInflater.from(secondaryContext).inflate(R.layout.overlay_layout, null);
        ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
        iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        fullscreenToggleParams = new WindowManager.LayoutParams(
                sizePx, sizePx, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        String positionX = prefs.getString("fs_position_x", "left");
        int gravity = Gravity.TOP;
        if ("right".equals(positionX)) {
            gravity |= Gravity.END;
        } else if ("center".equals(positionX)) {
            gravity |= Gravity.CENTER_HORIZONTAL;
        } else {
            gravity |= Gravity.START;
        }
        fullscreenToggleParams.gravity = gravity;

        fullscreenToggleParams.y = prefs.getInt("fs_position_y", 100);

        setupDragAndClick(fullscreenToggleView, fullscreenToggleParams, "fs_position_y", this::toggleFullscreen, secondaryWindowManager);
        secondaryWindowManager.addView(fullscreenToggleView, fullscreenToggleParams);
    }

    private void hideFullscreenToggleButton() {
        if (fullscreenToggleView != null) {
            secondaryWindowManager.removeView(fullscreenToggleView);
            fullscreenToggleView = null;
        }
    }

    private void toggleFullscreen() {
        if (!isTargetAppFullscreen) {
            activeFullscreenPackage = currentForegroundPackage;
        }

        isTargetAppFullscreen = !isTargetAppFullscreen;

        ImageView iv = fullscreenToggleView.findViewById(R.id.overlay_image_view);
        iv.setImageResource(isTargetAppFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);

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
                        if (Math.abs(deltaY) > ViewConfiguration.get(ForegroundOverlayService.this).getScaledTouchSlop()) {
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
        if (quickLaunchView != null) defaultWindowManager.removeView(quickLaunchView);
        if (fullscreenToggleView != null) secondaryWindowManager.removeView(fullscreenToggleView);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        instance = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            updateQuickLaunchButton();
            updateFullscreenState();
        }
    }
}
