package com.flyme.fscrn.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.flyme.fscrn.service.OverlayService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {

            boolean isEnabled = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                                       .getBoolean("hud_enabled", false);
            if (isEnabled) {
                Intent serviceIntent = new Intent(context, OverlayService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
