package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
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
    private DatabaseReference screenRef, touchRef, requestRef;
    private float hostWidth = 360f, hostHeight = 640f;
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
        statusText.setText("Connecting to Host...\nPlease wait.");
        statusText.setTextColor(Color.CYAN);
        statusText.setTextSize(20);
        statusText.setPadding(30, 30, 30, 30);

        layout.addView(imageView);
        layout.addView(statusText);
        setContentView(layout);

        DatabaseReference roomRef = FirebaseDatabase.getInstance(MainActivity.DB_URL).getReference("rooms").child(roomId);
        screenRef = roomRef.child("screen");
        touchRef = roomRef.child("touch");
        requestRef = roomRef.child("controller_request");

        // Host ko request bhejo
        requestRef.setValue(true);

        // Screen data listen karo
        screenRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        String base64 = snapshot.child("image").getValue(String.class);
                        hostWidth = snapshot.child("width").getValue(Float.class);
                        hostHeight = snapshot.child("height").getValue(Float.class);
                        
                        if (base64 != null) {
                            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            
                            runOnUiThread(() -> {
                                imageView.setImageBitmap(decodedByte);
                                if(isFirstFrame) {
                                    statusText.setVisibility(TextView.GONE);
                                    isFirstFrame = false;
                                }
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Touch events Firebase par bhejo
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float viewWidth = imageView.getWidth();
                float viewHeight = imageView.getHeight();
                if(viewWidth == 0 || viewHeight == 0) return true;

                float xRatio = event.getX() / viewWidth;
                float yRatio = event.getY() / viewHeight;

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
        // Activity band hone par request hatao
        if(requestRef != null) requestRef.setValue(false);
        super.onDestroy();
    }
}
