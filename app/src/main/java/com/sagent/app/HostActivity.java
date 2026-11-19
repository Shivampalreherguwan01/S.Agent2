package com.sagent.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.net.URISyntaxException;
import java.util.Random;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class HostActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private static final int REQUEST_CODE_NOTIFICATION = 2;
    private Socket mSocket;
    private String roomId;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        
        roomId = prefs.getString("roomId", null);
        if (roomId == null) {
            roomId = "SA-" + (100000 + new Random().nextInt(900000));
            prefs.edit().putString("roomId", roomId).apply();
        }

        // Notification Permission Request (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_NOTIFICATION);
            }
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
        
        TextView tvCode = new TextView(this);
        tvCode.setText("Code:\n" + roomId);
        tvCode.setTextColor(Color.parseColor("#00E676"));
        tvCode.setTextSize(36);
        tvCode.setGravity(Gravity.CENTER);
        tvCode.setPadding(0, 30, 0, 30);

        statusText = new TextView(this);
        statusText.setText("Connecting to Server...");
        statusText.setTextColor(Color.YELLOW);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 20, 0, 40);

        Button btnAccess = new Button(this);
        btnAccess.setText("1. Enable Accessibility");
        btnAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button btnShare = new Button(this);
        btnShare.setText("2. Share Screen & Connect");
        btnShare.setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        });

        layout.addView(title);
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
            
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join-as-host", roomId);
                runOnUiThread(() -> {
                    statusText.setText("✅ SERVER CONNECTED!\nWaiting for Controller...");
                    statusText.setTextColor(Color.GREEN);
                });
            });
            
            mSocket.on("controller-requested", args -> {
                runOnUiThread(() -> {
                    statusText.setText("📱 CONTROLLER CONNECTED!\nScreen streaming started.");
                    statusText.setTextColor(Color.CYAN);
                    Toast.makeText(HostActivity.this, "Device Connected!", Toast.LENGTH_SHORT).show();
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("roomId", roomId);
                        mSocket.emit("accept-connect", obj); 
                    } catch (Exception e) { e.printStackTrace(); }
                });
            });

            mSocket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(this, "Permission Granted! Starting Service...", Toast.LENGTH_SHORT).show();
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            serviceIntent.putExtra("roomId", roomId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            Toast.makeText(this, "Screen Capture Permission Denied!", Toast.LENGTH_LONG).show();
        }
    }
}
