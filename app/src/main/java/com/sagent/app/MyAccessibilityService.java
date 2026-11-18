package com.sagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class MyAccessibilityService extends AccessibilityService {
    private Socket mSocket;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        try {
            // Render ka Live URL
            mSocket = IO.socket("https://s-agent-relay.onrender.com");
            mSocket.connect();
            
            mSocket.on("perform-touch", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    float x = (float) data.getDouble("x");
                    float y = (float) data.getDouble("y");
                    performClick(x, y);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
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
