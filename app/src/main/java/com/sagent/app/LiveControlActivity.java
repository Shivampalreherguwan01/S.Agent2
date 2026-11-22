package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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
    private TextView statusText, tvHostName;
    private DatabaseReference screenRef, touchRef, requestRef, hostActiveRef;
    private ValueEventListener screenListener, hostListener;
    private boolean isFirstFrame = true;
    private long lastFrameReceivedTime = 0;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;

    // Colors (From Image)
    private final int bgColor = Color.parseColor("#0B0C10"); // Very Dark Blue
    private final int panelColor = Color.parseColor("#1F2833"); // Dark Greyish Blue
    private final int accentCyan = Color.parseColor("#45A29E"); // Teal/Cyan
    private final int btnColor = Color.parseColor("#2A2D34"); // Button Grey

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        // --- ROOT LAYOUT ---
        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(bgColor);

        // --- TOP BAR ---
        RelativeLayout topBar = new RelativeLayout(this);
        topBar.setId(View.generateViewId());
        topBar.setBackgroundColor(panelColor);
        topBar.setPadding(30, 30, 30, 30);
        
        tvHostName = new TextView(this);
        tvHostName.setText("Device: " + roomId);
        tvHostName.setTextColor(Color.WHITE);
        tvHostName.setTextSize(18);
        tvHostName.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvDisconnect = new TextView(this);
        tvDisconnect.setText("Disconnect");
        tvDisconnect.setTextColor(Color.RED);
        tvDisconnect.setTextSize(16);
        tvDisconnect.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        RelativeLayout.LayoutParams discParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        discParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        discParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tvDisconnect.setLayoutParams(discParams);

        topBar.addView(tvHostName);
        topBar.addView(tvDisconnect);

        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 120);
        topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rootLayout.addView(topBar, topParams);

        // --- MAIN SCREEN VIEW ---
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        RelativeLayout.LayoutParams imgParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        imgParams.addRule(RelativeLayout.BELOW, topBar.getId());
        imgParams.bottomMargin = 400; // Space for bottom controls
        rootLayout.addView(imageView, imgParams);

        // Status Text Overlay
        statusText = new TextView(this);
        statusText.setText("Connecting...\nWaiting for Screen.");
        statusText.setTextColor(accentCyan);
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        statusParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        rootLayout.addView(statusText, statusParams);

        // --- BOTTOM CONTROL PANEL ---
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.HORIZONTAL);
        bottomPanel.setGravity(Gravity.CENTER_VERTICAL);
        bottomPanel.setPadding(30, 30, 30, 30);
        bottomPanel.setBackgroundColor(panelColor);

        // 1. D-Pad (Left Side)
        FrameLayout dpadLayout = new FrameLayout(this);
        LinearLayout.LayoutParams dpadParams = new LinearLayout.LayoutParams(0, 350, 1f);
        dpadLayout.setLayoutParams(dpadParams);

        Button btnUp = createCircleButton("↑");
        Button btnDown = createCircleButton("↓");
        Button btnLeft = createCircleButton("←");
        Button btnRight = createCircleButton("→");
        Button btnCenter = createCircleButton("OK");

        btnUp.setOnClickListener(v -> sendCommand("UP"));
        btnDown.setOnClickListener(v -> sendCommand("DOWN"));
        btnLeft.setOnClickListener(v -> sendCommand("LEFT"));
        btnRight.setOnClickListener(v -> sendCommand("RIGHT"));
        btnCenter.setOnClickListener(v -> sendCommand("ENTER"));

        FrameLayout.LayoutParams paramsUp = new FrameLayout.LayoutParams(100, 100);
        paramsUp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        FrameLayout.LayoutParams paramsDown = new FrameLayout.LayoutParams(100, 100);
        paramsDown.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        FrameLayout.LayoutParams paramsLeft = new FrameLayout.LayoutParams(100, 100);
        paramsLeft.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        FrameLayout.LayoutParams paramsRight = new FrameLayout.LayoutParams(100, 100);
        paramsRight.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        FrameLayout.LayoutParams paramsCenter = new FrameLayout.LayoutParams(120, 120);
        paramsCenter.gravity = Gravity.CENTER;

        dpadLayout.addView(btnUp, paramsUp);
        dpadLayout.addView(btnDown, paramsDown);
        dpadLayout.addView(btnLeft, paramsLeft);
        dpadLayout.addView(btnRight, paramsRight);
        dpadLayout.addView(btnCenter, paramsCenter);

        // 2. Navigation Buttons (Right Side)
        LinearLayout navLayout = new LinearLayout(this);
        navLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0, 350, 1f);
        navParams.setMargins(50, 0, 0, 0);
        navLayout.setLayoutParams(navParams);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        Button btnBack = createNavButton("Back");
        btnBack.setOnClickListener(v -> sendCommand("BACK"));
        Button btnHome = createNavButton("Home");
        btnHome.setOnClickListener(v -> sendCommand("HOME"));
        row1.addView(btnBack);
        row1.addView(btnHome);

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        Button btnRecents = createNavButton("Recent");
        btnRecents.setOnClickListener(v -> sendCommand("RECENT"));
        Button btnPower = createNavButton("Power");
        btnPower.setOnClickListener(v -> sendCommand("POWER"));
        row2.addView(btnRecents);
        row2.addView(btnPower);

        navLayout.addView(row1);
        navLayout.addView(row2);

        bottomPanel.addView(dpadLayout);
        bottomPanel.addView(navLayout);

        RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        rootLayout.addView(bottomPanel, bottomParams);

        setContentView(rootLayout);

        // --- FIREBASE LOGIC & AUTO-RECONNECT ---
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
                    runOnUiThread(() -> {
                        tvHostName.setText("Device: OFFLINE");
                        tvHostName.setTextColor(Color.RED);
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("Host is Offline.\nCheck Host Phone.");
                    });
                } else {
                    tvHostName.setText("Device: Connected");
                    tvHostName.setTextColor(Color.WHITE);
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
                                lastFrameReceivedTime = System.currentTimeMillis();
                                runOnUiThread(() -> {
                                    imageView.setImageBitmap(decodedByte);
                                    if(isFirstFrame) { 
                                        statusText.setVisibility(View.GONE); 
                                        isFirstFrame = false; 
                                    }
                                });
                            }
                        }
                    } catch (Exception e) { Log.e("Controller", "Decode Error", e); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        screenRef.addValueEventListener(screenListener);

        // Auto Reconnect Checker (Har 5 second mein check karega agar screen atki hai)
        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - lastFrameReceivedTime > 5000) {
                    requestRef.setValue(true); // Dobara request bhejega
                }
                reconnectHandler.postDelayed(this, 5000);
            }
        };
        reconnectHandler.post(reconnectRunnable);

        // --- TOUCH LOGIC ---
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
            touchData.put("action", event.getAction());
            touchData.put("timestamp", System.currentTimeMillis());
            touchRef.setValue(touchData);
            return true;
        });
    }

    private Button createCircleButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(20);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(btnColor);
        shape.setStroke(2, accentCyan);
        btn.setBackground(shape);
        return btn;
    }

    private Button createNavButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(20f);
        shape.setColor(btnColor);
        shape.setStroke(2, accentCyan);
        btn.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 120);
        params.setMargins(10, 10, 10, 10);
        btn.setLayoutParams(params);
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
        if (reconnectHandler != null && reconnectRunnable != null) reconnectHandler.removeCallbacks(reconnectRunnable);
        super.onDestroy();
    }
}
