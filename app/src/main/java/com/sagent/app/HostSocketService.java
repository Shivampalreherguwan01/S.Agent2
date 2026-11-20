package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;

public class HostSocketService extends Service {
    private Socket mSocket;
    private String roomId;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        roomId = intent.getStringExtra("roomId");
        
        // Notification banayein taaki service na mare
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

        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.on(Socket.EVENT_CONNECT, args -> mSocket.emit("join-as-host", roomId));
            
            // Jab koi controller connect karega
            mSocket.on("controller-joined", args -> {
                // Turant Screen Share ki permission maangne ke liye Activity open karein
                Intent autoShareIntent = new Intent(this, AutoShareActivity.class);
                autoShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                autoShareIntent.putExtra("roomId", roomId);
                startActivity(autoShareIntent);
            });
            mSocket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
