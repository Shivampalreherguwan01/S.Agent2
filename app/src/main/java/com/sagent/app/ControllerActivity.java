package com.sagent.app;

import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;

public class ControllerActivity extends AppCompatActivity {
    private Socket mSocket;
    private String roomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        TextView textView = new TextView(this);
        textView.setText("Touch anywhere to control the other phone");
        textView.setTextSize(20);
        textView.setPadding(50, 50, 50, 50);
        
        // Screen par touch detect karna
        textView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                sendTouchCommand(event.getX(), event.getY());
            }
            return true;
        });

        setContentView(textView);

        try {
            mSocket = IO.socket("http://10.0.2.2:3000");
            mSocket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void sendTouchCommand(float x, float y) {
        if (mSocket != null && mSocket.connected()) {
            JSONObject data = new JSONObject();
            try {
                data.put("roomId", roomId);
                data.put("x", x);
                data.put("y", y);
                mSocket.emit("touch-event", data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
