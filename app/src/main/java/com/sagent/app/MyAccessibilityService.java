package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Yahan aap server se aane wale commands ko listen karenge
        // Example: Agar server se "TAP X Y" aaya, toh performClick(x, y) call hoga
    }

    @Override
    public void onInterrupt() {}

    // Ye method dusre phone se aaye hue touch ko execute karega
    public void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }
}
