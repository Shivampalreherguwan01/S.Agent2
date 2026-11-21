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

        hostActiveRef.addValueEventListener(new ValueEventListener() {
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
        });

        screenRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        String base64 = snapshot.child("image").getValue(String.class);
                        if (base64 != null) {
                            // Fix: NO_WRAP decode
                            byte[] decodedString = Base64.decode(base64, Base64.NO_WRAP);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            
                            if (decodedByte != null) {
                                runOnUiThread(() -> {
                                    imageView.setImageBitmap(decodedByte);
                                    if(isFirstFrame) { statusText.setVisibility(TextView.GONE); isFirstFrame = false; }
                                });
                            } else {
                                Log.e("Controller", "Bitmap decode failed!");
                            }
                        }
                    } catch (Exception e) { 
                        Log.e("Controller", "Exception decoding image", e);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if(imageView.getWidth() == 0 || imageView.getHeight() == 0) return true;
                
                float xRatio = event.getX() / imageView.getWidth();
                float yRatio = event.getY() / imageView.getHeight();
                Map<String, Object> touchData = new HashMap<>();
                touchData.put("xRatio", xRatio);
                touchData.put("yRatio", yRatio);
                touchData.put("timestamp", System.currentTimeMillis());
                touchRef.setValue(touchData);
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        if(requestRef != null) requestRef.setValue(false);
        super.onDestroy();
    }
}
