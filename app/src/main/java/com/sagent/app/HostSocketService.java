package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import rikka.shizuku.Shizuku;
import java.lang.reflect.Method;

public class HostSocketService extends Service {
    private String roomId;
    private DatabaseReference roomRef;
    private ValueEventListener requestListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("roomId")) {
            roomId = intent.getStringExtra("roomId");
        } else {
            roomId = getSharedPreferences("SAgentPrefs", Context.MODE_PRIVATE).getString("roomId", null);
        }
        
        if (roomId == null) { stopSelf(); return START_NOT_STICKY; }

        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        
        // Heartbeat System (Offline Bug Fix)
        roomRef.child("host_active").setValue(true);
        startHeartbeat();

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

        requestListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isRequested = snapshot.getValue(Boolean.class);
                if (isRequested != null && isRequested) {
                    // 1. MediaProjection ka popup kholo
                    Intent autoShareIntent = new Intent(getApplicationContext(), AutoShareActivity.class);
                    autoShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    autoShareIntent.putExtra("roomId", roomId);
                    startActivity(autoShareIntent);

                    // 2. Shizuku se "Start Now" button par click karo
                    if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                // Screen ke bottom-center par tap karne ka command
                                String[] cmd = {"sh", "-c", "input tap 540 1500"}; 
                                
                                // Reflection ka use karke private method ko call karna
                                Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                                Method newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                                newProcessMethod.setAccessible(true);
                                newProcessMethod.invoke(null, cmd, null, null);
                                
                            } catch (Exception e) { e.printStackTrace(); }
                        }, 1500); // 1.5 second wait karke click karega
                    }
                    roomRef.child("controller_request").setValue(false);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.child("controller_request").addValueEventListener(requestListener);

        return START_STICKY;
    }

    private void startHeartbeat() {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                roomRef.child("host_active").setValue(true); // Har 5 second me update
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(runnable);
    }

    @Override
    public void onDestroy() {
        if (roomRef != null && requestListener != null) {
            roomRef.child("controller_request").removeEventListener(requestListener);
            roomRef.child("host_active").setValue(false);
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
