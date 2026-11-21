package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private ValueEventListener screenListener, hostListener; // Listener references
    private boolean isFirstFrame = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        statusText = new TextView(this);
        statusText.setText("Connecting...\nWaiting for Host.");
        statusText.setTextColor(Color.CYAN);
        statusText.setTextSize(20);
        statusText.setPadding(30, 30, 30, 30);

        layout.addView(imageView);
        layout.addView(statusText);
        setContentView(layout);

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
                    runOnUiThread(() -> statusText.setText("Host is OFFLINE.\nPlease check Host Background Service."));
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
                    } catch (Exception e) { Log.e("Controller", "Decode Error", e); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        screenRef.addValueEventListener(screenListener);

        // PDF Point 11 fix: ACTION_DOWN, ACTION_MOVE aur ACTION_UP sab handle kiye
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                if(imageView.getWidth() == 0 || imageView.getHeight() == 0) return true;
                
                float xRatio = event.getX() / imageView.getWidth();
                float yRatio = event.getY() / imageView.getHeight();
                Map<String, Object> touchData = new HashMap<>();
                touchData.put("xRatio", xRatio);
                touchData.put("yRatio", yRatio);
                touchData.put("timestamp", System.currentTimeMillis());
                touchData.put("action", event.getAction()); // Action bhi bhej rahe hain
                touchRef.setValue(touchData);
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        // PDF Point 14 fix: Listeners clear karna zaroori hai
        if (requestRef != null) requestRef.setValue(false);
        if (hostActiveRef != null && hostListener != null) hostActiveRef.removeEventListener(hostListener);
        if (screenRef != null && screenListener != null) screenRef.removeEventListener(screenListener);
        super.onDestroy();
    }
}
