package com.sagent.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static final String SERVER_URL = "https://s-agent-relay.onrender.com"; // Apna URL yahan check kar lein

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        String mode = prefs.getString("mode", "none");

        // Agar mode pehle se select hai, toh direct wahan bhej do
        if (mode.equals("host")) {
            startActivity(new Intent(this, HostActivity.class));
            finish();
            return;
        } else if (mode.equals("controller")) {
            startActivity(new Intent(this, ControllerActivity.class));
            finish();
            return;
        }

        // UI Setup (Dark Theme)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Select Mode\n(One Time Selection)");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 100);

        Button btnHost = new Button(this);
        btnHost.setText("HOST MODE\n(Share this phone)");
        btnHost.setTextSize(18);
        btnHost.setPadding(20, 40, 20, 40);
        btnHost.setOnClickListener(v -> {
            prefs.edit().putString("mode", "host").apply();
            startActivity(new Intent(this, HostActivity.class));
            finish();
        });

        Button btnController = new Button(this);
        btnController.setText("CONTROLLER MODE\n(Control other phone)");
        btnController.setTextSize(18);
        btnController.setPadding(20, 40, 20, 40);
        btnController.setOnClickListener(v -> {
            prefs.edit().putString("mode", "controller").apply();
            startActivity(new Intent(this, ControllerActivity.class));
            finish();
        });

        layout.addView(title);
        layout.addView(btnHost);
        layout.addView(btnController);
        setContentView(layout);
    }
}
