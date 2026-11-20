package com.sagent.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class MainActivity extends AppCompatActivity {
    public static final String DB_URL = "https://fir-agent2-56832-default-rtdb.firebaseio.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firebase ko manually initialize karna (Bina google-services.json ke)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:1234567890") // Dummy ID
                    .setDatabaseUrl(DB_URL)
                    .build();
            FirebaseApp.initializeApp(this, options);
        }

        SharedPreferences prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);
        String mode = prefs.getString("mode", "none");

        if (mode.equals("host")) {
            startActivity(new Intent(this, HostActivity.class));
            finish(); return;
        } else if (mode.equals("controller")) {
            startActivity(new Intent(this, ControllerActivity.class));
            finish(); return;
        }

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
        btnHost.setOnClickListener(v -> {
            prefs.edit().putString("mode", "host").apply();
            startActivity(new Intent(this, HostActivity.class));
            finish();
        });

        Button btnController = new Button(this);
        btnController.setText("CONTROLLER MODE\n(Control other phone)");
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
