package com.flyme.fscrn.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class OverlayService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String newPackage = event.getPackageName().toString();
                // If ForegroundOverlayService is running, notify it for immediate UI update
                ForegroundOverlayService foregroundService = ForegroundOverlayService.getInstance();
                if (foregroundService != null) {
                    foregroundService.onAccessibilityWindowChanged(newPackage);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
