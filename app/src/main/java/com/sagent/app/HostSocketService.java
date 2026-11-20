package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        roomId = intent.getStringExtra("roomId");

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SAgent::HostWakeLock");
        wakeLock.acquire();

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

        requestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isRequested = snapshot.getValue(Boolean.class);
                if (isRequested != null && isRequested && !isPopupShown) {
                    isPopupShown = true;
                    Intent autoShareIntent = new Intent(getApplicationContext(), AutoShareActivity.class);
                    autoShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    autoShareIntent.putExtra("roomId", roomId);
                    startActivity(autoShareIntent);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
