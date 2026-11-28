package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class HostSocketService extends Service {
    private String roomId;
    private DatabaseReference roomRef;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. FOREGROUND SABSE PEHLE (Android 14 Rule)
        startForegroundService();

        if (intent != null && intent.hasExtra("roomId")) {
            roomId = intent.getStringExtra("roomId");
        } else {
            roomId = getSharedPreferences("SAgentPrefs", MODE_PRIVATE).getString("roomId", null);
        }
        
        if (roomId == null) { stopSelf(); return START_NOT_STICKY; }

        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        roomRef.child("host_active").onDisconnect().setValue(false);
        
        startHeartbeat();
        return START_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("hostbg", "Host Background", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "hostbg")
                    .setContentTitle("S.Agent2 Host is Active")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(2, notification);
            }
        }
    }

    private void startHeartbeat() {
        if (heartbeatHandler != null) return;
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (roomRef != null) roomRef.child("host_active").setValue(true);
                heartbeatHandler.postDelayed(this, 5000);
            }
        };
        heartbeatHandler.post(heartbeatRunnable);
    }

    // Recent Apps se swipe karne par bhi ye service zinda rahegi
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (roomRef != null) roomRef.child("host_active").setValue(true);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
        if (roomRef != null) roomRef.child("host_active").setValue(false);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
