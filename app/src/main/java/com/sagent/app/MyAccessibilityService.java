package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MyAccessibilityService extends AccessibilityService {
    private String roomId;
    private long lastTouchTime = 0;

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
                        if (timestamp > lastTouchTime) {
                            lastTouchTime = timestamp;
                            float xRatio = snapshot.child("xRatio").getValue(Float.class);
                            float yRatio = snapshot.child("yRatio").getValue(Float.class);
                            
                            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                            DisplayMetrics metrics = new DisplayMetrics();
                            wm.getDefaultDisplay().getMetrics(metrics);
                            
                            performClick(xRatio * metrics.widthPixels, yRatio * metrics.heightPixels);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    public void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }
}
