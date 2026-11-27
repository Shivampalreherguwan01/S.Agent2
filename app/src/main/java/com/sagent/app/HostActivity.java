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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;
import rikka.shizuku.Shizuku;

public class HostActivity extends AppCompatActivity {
    private String roomId;
    private static final int REQUEST_CODE_SHIZUKU = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);

        roomId = prefs.getString("roomId", null);
        if (roomId == null) {
            roomId = "SA-" + (100000 + new Random().nextInt(900000));
            prefs.edit().putString("roomId", roomId).apply();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "CRITICAL: Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
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
        btnAccess.setText("1. ENABLE ACCESSIBILITY (For Fallback)");
        btnAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button btnShizuku = new Button(this);
        btnShizuku.setText("2. GRANT SHIZUKU PERMISSION");
        btnShizuku.setOnClickListener(v -> requestShizukuPermission());

        Button btnStart = new Button(this);
        btnStart.setText("3. START BACKGROUND HOST");
        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Enable 'Display over other apps' first!", Toast.LENGTH_LONG).show();
                return;
            }
            Intent serviceIntent = new Intent(this, HostSocketService.class);
            serviceIntent.putExtra("roomId", roomId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);

            Toast.makeText(this, "Host Active! Minimize the app.", Toast.LENGTH_LONG).show();
            finishAffinity();
        });

        layout.addView(title);
        layout.addView(tvCode);
        layout.addView(btnAccess);
        layout.addView(btnShizuku);
        layout.addView(btnStart);
        setContentView(layout);
    }

    private void requestShizukuPermission() {
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "Shizuku version too old", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku Permission Already Granted!", Toast.LENGTH_SHORT).show();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toast.makeText(this, "Please grant permission", Toast.LENGTH_SHORT).show();
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
        } else {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku Permission Granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Shizuku Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
