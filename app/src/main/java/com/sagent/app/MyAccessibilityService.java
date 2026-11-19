package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class MyAccessibilityService extends AccessibilityService {
    private Socket mSocket;
    private String roomId;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        roomId = getSharedPreferences("SAgentPrefs", MODE_PRIVATE).getString("roomId", "SA-123456");
        
        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.on(Socket.EVENT_CONNECT, args -> mSocket.emit("join", roomId));
            
            mSocket.on("perform-touch", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    // Normalized coordinates receive karein
                    float xRatio = (float) data.getDouble("xRatio");
                    float yRatio = (float) data.getDouble("yRatio");
                    
                    // Actual screen size nikalein
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    DisplayMetrics metrics = new DisplayMetrics();
                    wm.getDefaultDisplay().getMetrics(metrics);
                    
                    float actualX = xRatio * metrics.widthPixels;
                    float actualY = yRatio * metrics.heightPixels;
                    
                    performClick(actualX, actualY);
                } catch (Exception e) { e.printStackTrace(); }
            });
            mSocket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }
}
