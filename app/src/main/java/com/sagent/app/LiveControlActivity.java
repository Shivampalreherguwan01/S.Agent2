package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;

public class LiveControlActivity extends AppCompatActivity {
    private Socket mSocket;
    private String roomId;
    private ImageView imageView;
    private TextView statusText;
    private float hostWidth = 540f, hostHeight = 960f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        statusText = new TextView(this);
        statusText.setText("Waking up server...\nPlease wait up to 60 seconds.");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setPadding(30, 30, 30, 30);

        layout.addView(imageView);
        layout.addView(statusText);
        setContentView(layout);

        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.connect();
            
            // Agar pehle se connected hai
            if (mSocket.connected()) {
                mSocket.emit("request-connect", roomId);
                runOnUiThread(() -> statusText.setText("Requesting Host to connect..."));
            }

            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("request-connect", roomId);
                runOnUiThread(() -> statusText.setText("Requesting Host to connect..."));
            });

            mSocket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                runOnUiThread(() -> statusText.setText("Server Error. Please restart App."));
            });

            mSocket.on("connection-accepted", args -> {
                runOnUiThread(() -> statusText.setText("Connected! Waiting for screen data..."));
            });

            mSocket.on("receive-screen", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String base64 = data.getString("image");
                    hostWidth = (float) data.getDouble("width");
                    hostHeight = (float) data.getDouble("height");
                    
                    byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    
                    runOnUiThread(() -> {
                        imageView.setImageBitmap(decodedByte);
                        statusText.setVisibility(TextView.GONE); // Screen aate hi text hata do
                    });
                } catch (Exception e) { e.printStackTrace(); }
            });
        } catch (URISyntaxException e) { e.printStackTrace(); }

        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
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
