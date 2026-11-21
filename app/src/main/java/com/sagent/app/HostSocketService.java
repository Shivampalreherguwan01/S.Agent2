package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HostSocketService extends Service {
    private String roomId;
    private PowerManager.WakeLock wakeLock;
    private boolean isPopupShown = false;
    private WindowManager windowManager;
    private View floatingView;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        roomId = intent.getStringExtra("roomId");

        // CPU ko jagaye rakhne ke liye WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SAgent::HostWakeLock");
        wakeLock.acquire();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("hostbg", "Host Background", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "hostbg")
                    .setContentTitle("S.Agent2 Host is Active")
                    .setContentText("Waiting for controller...")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build();
            startForeground(2, notification);
        }

        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId).child("controller_request");

        // Firebase par request listen karo
        requestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isRequested = snapshot.getValue(Boolean.class);
                if (isRequested != null && isRequested && !isPopupShown) {
                    isPopupShown = true;
                    showFloatingButton(); // Popup ki jagah Floating Button dikhao
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        return START_STICKY;
    }

    private void showFloatingButton() {
        // Floating Window banane ka code
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 100; // Screen ke top se thoda neeche

        // Button Design
        Button acceptBtn = new Button(this);
        acceptBtn.setText("CONTROLLER CONNECTED!\nTap to Share Screen");
        acceptBtn.setBackgroundColor(Color.parseColor("#00E676")); // Green
        acceptBtn.setTextColor(Color.BLACK);
        acceptBtn.setPadding(30, 30, 30, 30);
        acceptBtn.setTextSize(16);

        acceptBtn.setOnClickListener(v -> {
            // Button hatado
            if (floatingView != null) {
                windowManager.removeView(floatingView);
                floatingView = null;
            }
            // Permission Activity kholo
            Intent autoShareIntent = new Intent(getApplicationContext(), AutoShareActivity.class);
            autoShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            autoShareIntent.putExtra("roomId", roomId);
            startActivity(autoShareIntent);
        });

        floatingView = acceptBtn;
        
        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) { e.printStackTrace(); }
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
