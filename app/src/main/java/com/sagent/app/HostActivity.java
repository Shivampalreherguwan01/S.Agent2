package com.sagent.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.net.URISyntaxException;
import java.util.Random;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class HostActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private Socket mSocket;
    private String roomId;
    private SharedPreferences prefs;
    private TextView statusText;
    private Button btnShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        
        roomId = prefs.getString("roomId", null);
        if (roomId == null) {
            roomId = "SA-" + (100000 + new Random().nextInt(900000));
            prefs.edit().putString("roomId", roomId).apply();
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Host Mode Active");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        
        TextView codeLabel = new TextView(this);
        codeLabel.setText("Your Pairing Code:");
        codeLabel.setTextColor(Color.LTGRAY);
        codeLabel.setTextSize(18);
        codeLabel.setPadding(0, 50, 0, 10);

        TextView tvCode = new TextView(this);
        tvCode.setText(roomId);
        tvCode.setTextColor(Color.parseColor("#00E676"));
        tvCode.setTextSize(36);
        tvCode.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Connecting to Server... (Please wait 30s if sleeping)");
        statusText.setTextColor(Color.YELLOW);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 50, 0, 50);

        Button btnAccess = new Button(this);
        btnAccess.setText("1. Enable Accessibility");
        btnAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnShare = new Button(this);
        btnShare.setText("2. Share Screen & Connect");
        btnShare.setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        });

        layout.addView(title);
        layout.addView(codeLabel);
        layout.addView(tvCode);
        layout.addView(statusText);
        layout.addView(btnAccess);
        layout.addView(btnShare);
        setContentView(layout);

        connectSocket();
    }

    private void connectSocket() {
        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.connect();
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join-as-host", roomId);
                runOnUiThread(() -> {
                    statusText.setText("Server Connected. Waiting for Controller...");
                    statusText.setTextColor(Color.GREEN);
                });
            });
            
            // Jab controller connect request bhejega
            mSocket.on("controller-requested", args -> {
                runOnUiThread(() -> {
                    statusText.setText("Controller Connected! Streaming...");
                    Toast.makeText(HostActivity.this, "A device connected to you!", Toast.LENGTH_SHORT).show();
                    try {
                        // FIX: Data ko JSON Object format mein bhejna tha
                        JSONObject obj = new JSONObject();
                        obj.put("roomId", roomId);
                        mSocket.emit("accept-connect", obj); 
                    } catch (Exception e) { e.printStackTrace(); }
                });
            });

        } catch (URISyntaxException e) { e.printStackTrace(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            serviceIntent.putExtra("roomId", roomId);
            startForegroundService(serviceIntent);
            Toast.makeText(this, "Screen Sharing Started!", Toast.LENGTH_SHORT).show();
        }
    }
}
