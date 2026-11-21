package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
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
    private WindowManager windowManager;
    private View floatingView;
    private DatabaseReference roomRef;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("roomId")) {
            roomId = intent.getStringExtra("roomId");
        } else {
            roomId = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE).getString("roomId", null);
        }
        
        if (roomId == null) { stopSelf(); return START_NOT_STICKY; }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SAgent::HostWakeLock");
        wakeLock.acquire();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        roomRef.child("host_active").setValue(true);
        roomRef.child("controller_request").setValue(false);

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
        
        // Debugging ke liye Toast
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Host Background Service Started Successfully", Toast.LENGTH_SHORT).show());

        roomRef.child("controller_request").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isRequested = snapshot.getValue(Boolean.class);
                if (isRequested != null && isRequested) {
                    if (Settings.canDrawOverlays(HostSocketService.this)) {
                        showFloatingButton();
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Overlay Permission Missing!", Toast.LENGTH_LONG).show());
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        return START_STICKY;
    }

    private void showFloatingButton() {
        if (floatingView != null) return;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 100;

        Button acceptBtn = new Button(this);
        acceptBtn.setText("CONTROLLER CONNECTED!\nTap to Share Screen");
        acceptBtn.setBackgroundColor(Color.parseColor("#00E676"));
        acceptBtn.setTextColor(Color.BLACK);
        acceptBtn.setPadding(30, 30, 30, 30);
        acceptBtn.setTextSize(16);

        acceptBtn.setOnClickListener(v -> {
            if (floatingView != null) {
                windowManager.removeView(floatingView);
                floatingView = null;
            }
            roomRef.child("controller_request").setValue(false);
            
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
        if (roomRef != null) roomRef.child("host_active").setValue(false);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
