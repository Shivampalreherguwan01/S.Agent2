package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;

public class ControllerActivity extends AppCompatActivity {
    private Socket mSocket;
    private String roomId;
    private ImageView imageView;
    private float hostWidth = 540f, hostHeight = 960f; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setContentView(imageView);

        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.connect();
            
            // Auto Reconnect Room Join
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join-as-controller", roomId);
                runOnUiThread(() -> Toast.makeText(this, "Connected to Server!", Toast.LENGTH_SHORT).show());
            });

            // Receive Live Screen
            mSocket.on("receive-screen", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String base64 = data.getString("image");
                    hostWidth = (float) data.getDouble("width");
                    hostHeight = (float) data.getDouble("height");
                    
                    byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    
                    runOnUiThread(() -> imageView.setImageBitmap(decodedByte));
                } catch (Exception e) { e.printStackTrace(); }
            });

        } catch (URISyntaxException e) { e.printStackTrace(); }

        // Touch Handling
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Calculate Actual Touch Coordinates based on ImageView Size
                float viewWidth = imageView.getWidth();
                float viewHeight = imageView.getHeight();
                
                float xRatio = hostWidth / viewWidth;
                float yRatio = hostHeight / viewHeight;
                
                float finalX = event.getX() * xRatio;
                float finalY = event.getY() * yRatio;

                JSONObject data = new JSONObject();
                try {
                    data.put("roomId", roomId);
                    data.put("x", finalX);
                    data.put("y", finalY);
                    mSocket.emit("touch-event", data);
                } catch (JSONException e) { e.printStackTrace(); }
            }
            return true;
        });
    }
}
