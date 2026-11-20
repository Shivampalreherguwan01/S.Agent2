package com.sagent.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class HostActivity extends AppCompatActivity {
    private String roomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        
        roomId = prefs.getString("roomId", null);
        if (roomId == null) {
            roomId = "SA-" + (100000 + new Random().nextInt(900000));
            prefs.edit().putString("roomId", roomId).apply();
        }

        // Notification & Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Host Mode Ready");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        
        TextView tvCode = new TextView(this);
        tvCode.setText("Your Code:\n" + roomId);
        tvCode.setTextColor(Color.parseColor("#00E676"));
        tvCode.setTextSize(36);
        tvCode.setGravity(Gravity.CENTER);
        tvCode.setPadding(0, 30, 0, 30);

        Button btnAccess = new Button(this);
        btnAccess.setText("1. ENABLE ACCESSIBILITY");
        btnAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button btnStartService = new Button(this);
        btnStartService.setText("2. ACTIVATE HOST BACKGROUND SERVICE");
        btnStartService.setOnClickListener(v -> {
            // Start background socket service
            Intent serviceIntent = new Intent(this, HostSocketService.class);
            serviceIntent.putExtra("roomId", roomId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Host Service Active! You can minimize the app.", Toast.LENGTH_LONG).show();
            finishAffinity(); // App band kar do
        });

        layout.addView(title);
        layout.addView(tvCode);
        layout.addView(btnAccess);
        layout.addView(btnStartService);
        setContentView(layout);
    }
}
