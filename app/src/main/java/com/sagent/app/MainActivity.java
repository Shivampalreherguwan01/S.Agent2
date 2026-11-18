package com.sagent.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private Socket mSocket;
    private String roomId;
    private SharedPreferences prefs;
    public static final String SERVER_URL = "https://s-agent-relay.onrender.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        
        // Auto Generate Room ID if not exists
        roomId = prefs.getString("roomId", null);
        if (roomId == null) {
            roomId = "SA-" + (100000 + new Random().nextInt(900000));
            prefs.edit().putString("roomId", roomId).apply();
        }

        String savedMode = prefs.getString("mode", "none");

        // UI Setup
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundColor(0xFFF0F0F0);

        TextView tvCode = new TextView(this);
        tvCode.setText("Your Pairing Code:\n" + roomId);
        tvCode.setTextSize(24);
        tvCode.setGravity(Gravity.CENTER);
        tvCode.setPadding(0, 0, 0, 80);

        Button btnHost = new Button(this);
        btnHost.setText("HOST MODE (Share this phone)");
        
        Button btnController = new Button(this);
        btnController.setText("CONTROLLER MODE (Control other phone)");

        Button btnReset = new Button(this);
        btnReset.setText("Reset Settings");
        btnReset.setBackgroundColor(0xFFFFCCCC);

        // Auto-Login based on saved mode
        if (savedMode.equals("host")) {
            btnHost.setText("Host Mode (Active) - Click to Re-Start");
        } else if (savedMode.equals("controller")) {
            btnController.setText("Controller Mode (Active) - Click to Re-Connect");
        }

        btnHost.setOnClickListener(v -> {
            prefs.edit().putString("mode", "host").apply();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Enable Accessibility for S.Agent2", Toast.LENGTH_LONG).show();

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        });

        btnController.setOnClickListener(v -> {
            prefs.edit().putString("mode", "controller").apply();
            Intent intent = new Intent(this, ControllerActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });

        btnReset.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Reset Done! Restart App", Toast.LENGTH_SHORT).show();
            finish();
        });

        layout.addView(tvCode);
        layout.addView(btnHost);
        layout.addView(btnController);
        layout.addView(btnReset);
        setContentView(layout);
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
