package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Arrays;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    private String roomId;
    private Path swipePath;
    private long swipeStartTime = 0;
    private long lastTouchTime = 0;
    private long lastClickTime = 0;

    private final List<String> allowKeywords = Arrays.asList(
            "[sagent] controller connected!",
            "start now",
            "start now",
            "अभी शुरू करें",
            "cast",
            "साझा करें"
    );

    // BLACKLIST: In apps me kabhi click nahi karega
    private boolean isBlockedPackage(String pkg) {
        if (pkg == null) return true;
        return pkg.contains("facebook") || pkg.contains("instagram") || 
               pkg.contains("whatsapp") || pkg.contains("chrome") ||
               pkg.contains("youtube") || pkg.contains("twitter") ||
               pkg.contains("snapchat") || pkg.contains("telegram");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        roomId = getSharedPreferences("SAgentPrefs", MODE_PRIVATE).getString("roomId", "SA-123456");
        
        DatabaseReference touchRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId).child("touch");
        touchRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        long timestamp = snapshot.child("timestamp").getValue(Long.class);
                        if (timestamp <= lastTouchTime) return;
                        lastTouchTime = timestamp;

                        if (snapshot.hasChild("command")) {
                            String cmd = snapshot.child("command").getValue(String.class);
                            performGlobalActionFromCommand(cmd);
                            return;
                        }

                        float xRatio = snapshot.child("xRatio").getValue(Float.class);
                        float yRatio = snapshot.child("yRatio").getValue(Float.class);
                        int action = snapshot.child("action").getValue(Integer.class);

                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        int screenWidth, screenHeight;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                            screenWidth = bounds.width();
                            screenHeight = bounds.height();
                        } else {
                            DisplayMetrics metrics = new DisplayMetrics();
                            wm.getDefaultDisplay().getRealMetrics(metrics);
                            screenWidth = metrics.widthPixels;
                            screenHeight = metrics.heightPixels;
                        }
                        
                        float actualX = xRatio * screenWidth;
                        float actualY = yRatio * screenHeight;

                        if (action == android.view.MotionEvent.ACTION_DOWN) {
                            swipePath = new Path();
                            swipePath.moveTo(actualX, actualY);
                            swipeStartTime = System.currentTimeMillis();
                        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                            if (swipePath != null) swipePath.lineTo(actualX, actualY);
                        } else if (action == android.view.MotionEvent.ACTION_UP) {
                            if (swipePath != null) {
                                long duration = System.currentTimeMillis() - swipeStartTime;
                                if (duration < 50) duration = 50;
                                performGesture(swipePath, duration);
                                swipePath = null;
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        
        String pkg = event.getPackageName().toString();
        // Agar blocked app hai (Facebook etc) to wapas jao
        if (isBlockedPackage(pkg)) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 1500) return; // 1.5 second delay

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                if (searchAndClick(rootNode, 0)) {
                    lastClickTime = currentTime;
                }
            }
        }
    }

    private boolean searchAndClick(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 10) return false;

        if (node.getText() != null) {
            String text = node.getText().toString().toLowerCase().trim();
            for (String keyword : allowKeywords) {
                if (text.contains(keyword)) {
                    if (clickNodeOrParent(node)) {
                        return true;
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (searchAndClick(node.getChild(i), depth + 1)) return true;
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node.isClickable()) {
            if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        AccessibilityNodeInfo parent = node.getParent();
        int depth = 0;
        while (parent != null && depth < 3) { 
            if (parent.isClickable()) {
                if(parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
            parent = parent.getParent();
            depth++;
        }
        return false;
    }

    private void performGlobalActionFromCommand(String cmd) {
        if (cmd == null) return;
        switch (cmd) {
            case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break;
            case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break;
            case "RECENT": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case "POWER": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
            case "ENTER": performTapCenter(); break;
            case "UP": performSwipe(0.5f, 0.7f, 0.5f, 0.3f); break; 
            case "DOWN": performSwipe(0.5f, 0.3f, 0.5f, 0.7f); break; 
            case "LEFT": performSwipe(0.7f, 0.5f, 0.3f, 0.5f); break; 
            case "RIGHT": performSwipe(0.3f, 0.5f, 0.7f, 0.5f); break; 
        }
    }
    
    private void performTapCenter() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int screenWidth, screenHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        Path path = new Path();
        path.moveTo(screenWidth / 2f, screenHeight / 2f);
        performGesture(path, 50);
    }

    private void performSwipe(float startXRatio, float startYRatio, float endXRatio, float endYRatio) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int screenWidth, screenHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        Path path = new Path();
        path.moveTo(startXRatio * screenWidth, startYRatio * screenHeight);
        path.lineTo(endXRatio * screenWidth, endYRatio * screenHeight);
        performGesture(path, 200);
    }

    private void performGesture(Path path, long duration) {
        if (path == null) return;
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    @Override public void onInterrupt() {}
}
