package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class LiveControlActivity extends AppCompatActivity {
    private String roomId;
    private ImageView imageView;
    private TextView statusText;
    private DatabaseReference screenRef, touchRef, requestRef, hostActiveRef;
    private ValueEventListener screenListener, hostListener;
    private boolean isFirstFrame = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        // --- UI SETUP (Dark Theme + D-Pad) ---
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        // 1. Live Screen View
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        rootLayout.addView(imageView, imgParams);

        // 2. Status Text
        statusText = new TextView(this);
        statusText.setText("Connecting to Host...\nWaiting for Screen.");
        statusText.setTextColor(Color.CYAN);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        rootLayout.addView(statusText);

        // 3. Bottom Control Panel (D-Pad + Buttons)
        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.HORIZONTAL);
        controlPanel.setGravity(Gravity.CENTER | Gravity.BOTTOM);
        controlPanel.setPadding(20, 20, 20, 40);
        controlPanel.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent black

        // Back Button
        Button btnBack = createControlButton("Back");
        btnBack.setOnClickListener(v -> sendCommand("BACK"));
        
        // Home Button
        Button btnHome = createControlButton("Home");
        btnHome.setOnClickListener(v -> sendCommand("HOME"));

        // Recent Button
        Button btnRecent = createControlButton("Recent");
        btnRecent.setOnClickListener(v -> sendCommand("RECENT"));

        // D-Pad (Up, Down, Left, Right)
        Button btnUp = createControlButton("↑");
        btnUp.setOnClickListener(v -> sendCommand("UP"));
        Button btnDown = createControlButton("↓");
        btnDown.setOnClickListener(v -> sendCommand("DOWN"));
        Button btnLeft = createControlButton("←");
        btnLeft.setOnClickListener(v -> sendCommand("LEFT"));
        Button btnRight = createControlButton("→");
        btnRight.setOnClickListener(v -> sendCommand("RIGHT"));

        LinearLayout dpadLayout = new LinearLayout(this);
        dpadLayout.setOrientation(LinearLayout.VERTICAL);
        dpadLayout.setGravity(Gravity.CENTER);
        
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER);
        topRow.addView(btnUp);
        
        LinearLayout midRow = new LinearLayout(this);
        midRow.setGravity(Gravity.CENTER);
        midRow.addView(btnLeft);
        midRow.addView(btnHome);
        midRow.addView(btnRight);
        
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setGravity(Gravity.CENTER);
        bottomRow.addView(btnDown);

        dpadLayout.addView(topRow);
        dpadLayout.addView(midRow);
        dpadLayout.addView(bottomRow);

        controlPanel.addView(btnBack);
        controlPanel.addView(dpadLayout);
        controlPanel.addView(btnRecent);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;
        rootLayout.addView(controlPanel, panelParams);

        setContentView(rootLayout);
        // --- UI SETUP END ---

        DatabaseReference roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        screenRef = roomRef.child("screen");
        touchRef = roomRef.child("touch");
        requestRef = roomRef.child("controller_request");
        hostActiveRef = roomRef.child("host_active");

        hostListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isOnline = snapshot.getValue(Boolean.class);
                if (isOnline == null || !isOnline) {
                    runOnUiThread(() -> statusText.setText("Host is OFFLINE."));
                } else {
                    requestRef.setValue(true);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        hostActiveRef.addValueEventListener(hostListener);

        screenListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        String base64 = snapshot.child("image").getValue(String.class);
                        if (base64 != null) {
                            byte[] decodedString = Base64.decode(base64, Base64.NO_WRAP);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            if (decodedByte != null) {
                                runOnUiThread(() -> {
                                    imageView.setImageBitmap(decodedByte);
                                    if(isFirstFrame) { statusText.setVisibility(TextView.GONE); isFirstFrame = false; }
                                });
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        screenRef.addValueEventListener(screenListener);

        // Touch Logic with Letterboxing Fix
        imageView.setOnTouchListener((v, event) -> {
            if (imageView.getDrawable() == null) return true;

            float drawableWidth = imageView.getDrawable().getIntrinsicWidth();
            float drawableHeight = imageView.getDrawable().getIntrinsicHeight();
            float viewWidth = imageView.getWidth();
            float viewHeight = imageView.getHeight();

            float scale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
            float displayWidth = drawableWidth * scale;
            float displayHeight = drawableHeight * scale;

            float deltaX = (viewWidth - displayWidth) / 2;
            float deltaY = (viewHeight - displayHeight) / 2;

            float xRatio = (event.getX() - deltaX) / displayWidth;
            float yRatio = (event.getY() - deltaY) / displayHeight;

            if (xRatio < 0 || xRatio > 1 || yRatio < 0 || yRatio > 1) return true;

            Map<String, Object> touchData = new HashMap<>();
            touchData.put("xRatio", xRatio);
            touchData.put("yRatio", yRatio);
            touchData.put("action", event.getAction()); // Send DOWN, MOVE, UP
            touchData.put("timestamp", System.currentTimeMillis());

            touchRef.setValue(touchData);
            return true;
        });
    }

    private Button createControlButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#333333"));
        btn.setPadding(20, 10, 20, 10);
        return btn;
    }

    private void sendCommand(String cmd) {
        Map<String, Object> cmdData = new HashMap<>();
        cmdData.put("command", cmd);
        cmdData.put("timestamp", System.currentTimeMillis());
        touchRef.setValue(cmdData);
    }

    @Override
    protected void onDestroy() {
        if (requestRef != null) requestRef.setValue(false);
        if (hostActiveRef != null && hostListener != null) hostActiveRef.removeEventListener(hostListener);
        if (screenRef != null && screenListener != null) screenRef.removeEventListener(screenListener);
        super.onDestroy();
    }
}
