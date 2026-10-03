package com.flyme.fscrn.installer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.PineConfig;
import top.canyie.pine.callback.MethodHook;

public class PineHookManager {

    private static final String TAG = "PineHookManager";
    private static boolean isHooked = false;

    public static synchronized boolean applyPackageInstallerHooks(Context context) {
        if (isHooked) {
            Log.d(TAG, "Pine hooks are already applied.");
            return true;
        }

        try {
            boolean isDebug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            PineConfig.debug = isDebug;
            PineConfig.debuggable = isDebug;

            // Hook ApplicationPackageManager.canRequestPackageInstalls()
            try {
                Class<?> appPmClass = Class.forName("android.app.ApplicationPackageManager");
                Method canRequestMethod = appPmClass.getDeclaredMethod("canRequestPackageInstalls");
                Pine.hook(canRequestMethod, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame callFrame) throws Throwable {
                        Log.d(TAG, "Pine hooked ApplicationPackageManager.canRequestPackageInstalls() -> returning true");
                        callFrame.setResult(true);
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Method canRequestPackageInstalls not hooked on ApplicationPackageManager", t);
            }

            // Hook PackageManager.canRequestPackageInstalls() if present
            try {
                Method pmCanRequest = PackageManager.class.getDeclaredMethod("canRequestPackageInstalls");
                Pine.hook(pmCanRequest, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame callFrame) throws Throwable {
                        Log.d(TAG, "Pine hooked PackageManager.canRequestPackageInstalls() -> returning true");
                        callFrame.setResult(true);
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Method canRequestPackageInstalls not hooked on PackageManager interface", t);
            }

            isHooked = true;
            Log.i(TAG, "Pine hooks successfully applied!");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply Pine hooks", t);
            return false;
        }
    }

    public static boolean isHooked() {
        return isHooked;
    }
}
