package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    private String roomId;
    private long lastTouchTime = 0;
    private Path swipePath;
    private long swipeStartTime = 0;

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
                        DisplayMetrics metrics = new DisplayMetrics();
                        wm.getDefaultDisplay().getMetrics(metrics);
                        
                        float actualX = xRatio * metrics.widthPixels;
                        float actualY = yRatio * metrics.heightPixels;

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

    // --- यहाँ से ऑटो-क्लिकर का जादू शुरू होता है ---
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                // Android ke "Start Now" ya "Cast" button ko dhoondhna
                findAndClickButton(rootNode, "Start now");
                findAndClickButton(rootNode, "Start Now");
                findAndClickButton(rootNode, "Cast");
                findAndClickButton(rootNode, "Share");
                findAndClickButton(rootNode, "Allow");
            }
        }
    }

    private void findAndClickButton(AccessibilityNodeInfo node, String textToFind) {
        if (node == null) return;
        
        if (node.getText() != null && node.getText().toString().equalsIgnoreCase(textToFind)) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Toast.makeText(this, "Auto-Clicked: " + textToFind, Toast.LENGTH_SHORT).show();
                return;
            } else {
                // Agar button ke andar ka text clickable nahi hai, to parent par click karo
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null && parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Toast.makeText(this, "Auto-Clicked Parent: " + textToFind, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findAndClickButton(node.getChild(i), textToFind);
        }
    }
    // --- ऑटो-क्लिकर खत्म ---

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
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        Path path = new Path();
        path.moveTo(metrics.widthPixels / 2f, metrics.heightPixels / 2f);
        performGesture(path, 50);
    }

    private void performSwipe(float startXRatio, float startYRatio, float endXRatio, float endYRatio) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        Path path = new Path();
        path.moveTo(startXRatio * metrics.widthPixels, startYRatio * metrics.heightPixels);
        path.lineTo(endXRatio * metrics.widthPixels, endYRatio * metrics.heightPixels);
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
