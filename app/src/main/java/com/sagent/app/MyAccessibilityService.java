package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class MyAccessibilityService extends AccessibilityService {
    private String roomId;
    private DatabaseReference roomRef, screenRef;
    private Path swipePath;
    private long swipeStartTime = 0;
    private long lastTouchTime = 0;
    
    private boolean isTakingScreenshot = false;
    private boolean isUploading = false;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        roomId = getSharedPreferences("SAgentPrefs", MODE_PRIVATE).getString("roomId", "SA-123456");
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        screenRef = roomRef.child("screen");

        startScreenshotLoop();
        listenForTouches();
    }

    private void startScreenshotLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isTakingScreenshot && !isUploading && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    isTakingScreenshot = true;
                    takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
                            HardwareBuffer buffer = screenshotResult.getHardwareBuffer();
                            Bitmap bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.getColorSpace());
                            if (bitmap != null) {
                                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 270, 480, false);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                scaled.compress(Bitmap.CompressFormat.JPEG, 20, baos);
                                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                                Map<String, Object> frameData = new HashMap<>();
                                frameData.put("image", base64);
                                frameData.put("width", 270);
                                frameData.put("height", 480);
                                frameData.put("timestamp", System.currentTimeMillis());
                                
                                isUploading = true;
                                // Upload timeout (agar net slow hai to 1.5 sec me reset ho jaye)
                                handler.postDelayed(() -> isUploading = false, 1500);

                                screenRef.setValue(frameData).addOnCompleteListener(task -> {
                                    isUploading = false;
                                });

                                bitmap.recycle();
                                scaled.recycle();
                            }
                            isTakingScreenshot = false;
                        }
                        @Override public void onFailure(int errorCode) { 
                            isTakingScreenshot = false; 
                        }
                    });
                }
                handler.postDelayed(this, 300); 
            }
        });
    }

    private void listenForTouches() {
        roomRef.child("touch").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        long timestamp = snapshot.child("timestamp").getValue(Long.class);
                        if (timestamp <= lastTouchTime) return;
                        lastTouchTime = timestamp;

                        if (snapshot.hasChild("command")) {
                            performGlobalActionFromCommand(snapshot.child("command").getValue(String.class));
                            return;
                        }

                        float xRatio = snapshot.child("xRatio").getValue(Float.class);
                        float yRatio = snapshot.child("yRatio").getValue(Float.class);
                        int action = snapshot.child("action").getValue(Integer.class);

                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        int screenWidth, screenHeight;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                            screenWidth = bounds.width(); screenHeight = bounds.height();
                        } else {
                            DisplayMetrics metrics = new DisplayMetrics();
                            wm.getDefaultDisplay().getRealMetrics(metrics);
                            screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels;
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
                        } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
                            // PDF FIX: ACTION_CANCEL par path clear karo
                            swipePath = null;
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void performGlobalActionFromCommand(String cmd) {
        if (cmd == null) return;
        switch (cmd) {
            case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break;
            case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break;
            case "RECENT": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case "POWER": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
            case "UP": performSwipe(0.5f, 0.7f, 0.5f, 0.3f); break; 
            case "DOWN": performSwipe(0.5f, 0.3f, 0.5f, 0.7f); break; 
            case "LEFT": performSwipe(0.7f, 0.5f, 0.3f, 0.5f); break; 
            case "RIGHT": performSwipe(0.3f, 0.5f, 0.7f, 0.5f); break; 
        }
    }

    private void performSwipe(float startXRatio, float startYRatio, float endXRatio, float endYRatio) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int screenWidth = 1080, screenHeight = 1920;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width(); screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels;
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
