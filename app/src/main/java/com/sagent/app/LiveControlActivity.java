package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
    private float hostWidth = 270f, hostHeight = 480f;
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private Runnable retryRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        statusText = new TextView(this);
        statusText.setText("Connecting...\nPlease wait for host.");
        statusText.setTextColor(Color.CYAN);
        statusText.setTextSize(20);
        statusText.setPadding(30, 30, 30, 30);

        layout.addView(imageView);
        layout.addView(statusText);
        setContentView(layout);

        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join-as-controller", roomId);
                
                // Retry logic: Har 2 second mein host ko request bhejega
                retryRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (statusText.getVisibility() == TextView.VISIBLE) {
                            mSocket.emit("request-connect", roomId);
                            retryHandler.postDelayed(this, 2000);
                        }
                    }
                };
                retryHandler.post(retryRunnable);
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
                        if(statusText.getVisibility() == TextView.VISIBLE) {
                            statusText.setVisibility(TextView.GONE);
                            // Stop retrying once connected
                            if(retryHandler != null && retryRunnable != null) retryHandler.removeCallbacks(retryRunnable);
                        }
                    });
                } catch (Exception e) { e.printStackTrace(); }
            });

            mSocket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }

        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float viewWidth = imageView.getWidth();
                float viewHeight = imageView.getHeight();
                if(viewWidth == 0 || viewHeight == 0) return true;

                float xRatio = hostWidth / viewWidth;
                float yRatio = hostHeight / viewHeight;
                
                float finalX = event.getX() * xRatio;
                float finalY = event.getY() * yRatio;

                JSONObject data = new JSONObject();
                try {
                    data.put("roomId", roomId);
                    data.put("x", finalX);
                    data.put("y", finalY);
                    if(mSocket != null) mSocket.emit("touch-event", data);
                } catch (JSONException e) { e.printStackTrace(); }
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        if(retryHandler != null && retryRunnable != null) retryHandler.removeCallbacks(retryRunnable);
        if (mSocket != null) mSocket.disconnect();
        super.onDestroy();
    }
}
