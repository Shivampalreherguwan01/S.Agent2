package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HostSocketService extends Service {
    private String roomId;
    private DatabaseReference requestRef;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        roomId = intent.getStringExtra("roomId");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("hostbg", "Host Background", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "hostbg")
                    .setContentTitle("S.Agent2 Host is Active")
                    .setContentText("Waiting for controller to connect...")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build();
            startForeground(2, notification);
        }

        DatabaseReference db = FirebaseDatabase.getInstance(MainActivity.DB_URL).getReference("rooms").child(roomId);
        requestRef = db.child("controller_request");

        // Jab bhi controller request bhejega, yeh trigger hoga
        requestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isRequested = snapshot.getValue(Boolean.class);
                if (isRequested != null && isRequested) {
                    // Request aayi, ab AutoShareActivity kholo
                    Intent autoShareIntent = new Intent(getApplicationContext(), AutoShareActivity.class);
                    autoShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    autoShareIntent.putExtra("roomId", roomId);
                    startActivity(autoShareIntent);
                    
                    // Wapas false kar do taaki baar baar popup na aaye
                    requestRef.setValue(false);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
