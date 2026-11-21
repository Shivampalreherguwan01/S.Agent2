package com.sagent.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ControllerActivity extends AppCompatActivity {
    private LinearLayout deviceList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#121212"));
        mainLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("My Devices");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 30);

        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);

        Button btnAdd = new Button(this);
        btnAdd.setText("+ Add Device (Enter Code)");
        btnAdd.setBackgroundColor(Color.parseColor("#6200EE"));
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setOnClickListener(v -> showAddDeviceDialog());

        mainLayout.addView(title);
        mainLayout.addView(deviceList);
        mainLayout.addView(btnAdd);
        setContentView(mainLayout);

        loadSavedDevices();
    }

    private void showAddDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Pairing Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g. SA-123456");
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (!code.isEmpty()) {
                saveDevice(code);
                Intent intent = new Intent(this, LiveControlActivity.class);
                intent.putExtra("roomId", code);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveDevice(String code) {
        String saved = prefs.getString("savedDevices", "");
        if (!saved.contains(code)) {
            saved = saved.isEmpty() ? code : saved + "," + code;
            prefs.edit().putString("savedDevices", saved).apply();
            loadSavedDevices();
        }
    }

    private void loadSavedDevices() {
        deviceList.removeAllViews();
        String saved = prefs.getString("savedDevices", "");
        if (saved.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No devices added yet.");
            empty.setTextColor(Color.GRAY);
            deviceList.addView(empty);
            return;
        }

        String[] devices = saved.split(",");
        for (String code : devices) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundColor(Color.parseColor("#1E1E1E"));
            card.setPadding(30, 30, 30, 30);
            card.setGravity(Gravity.CENTER_VERTICAL);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 20);
            card.setLayoutParams(params);

            TextView tvName = new TextView(this);
            tvName.setText("Device: " + code);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(18);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button btnControl = new Button(this);
            btnControl.setText("Control");
            btnControl.setOnClickListener(v -> {
                Intent intent = new Intent(this, LiveControlActivity.class);
                intent.putExtra("roomId", code);
                startActivity(intent);
            });

            card.addView(tvName);
            card.addView(btnControl);
            deviceList.addView(card);
        }
    }
}
