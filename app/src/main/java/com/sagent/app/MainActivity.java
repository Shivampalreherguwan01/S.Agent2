package com.sagent.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        Button btnHost = new Button(this);
        btnHost.setText("HOST MODE (Monitor this phone)");
        btnHost.setOnClickListener(v -> {
            startActivity(new Intent(this, HostActivity.class));
        });

        EditText etCode = new EditText(this);
        etCode.setHint("Enter Host Code");
        etCode.setTextColor(Color.WHITE);
        etCode.setInputType(InputType.TYPE_CLASS_TEXT);

        Button btnController = new Button(this);
        btnController.setText("CONTROLLER MODE (Watch Host)");
        btnController.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter Code First!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, ControllerActivity.class);
            intent.putExtra("roomId", code);
            startActivity(intent);
        });

        layout.addView(btnHost);
        layout.addView(etCode);
        layout.addView(btnController);
        setContentView(layout);
    }
}
