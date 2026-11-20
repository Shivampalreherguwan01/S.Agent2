package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

public class ScreenCaptureService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Socket mSocket;
    private String roomId;
    private long lastFrameTime = 0;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        
        // CPU ko jagaye rakhega taaki background mein app na mare
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SAgent::CaptureWakeLock");
        wakeLock.acquire();

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        roomId = intent.getStringExtra("roomId");

        backgroundThread = new HandlerThread("ScreenCaptureThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.connect();
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join-as-host", roomId);
                updateNotification("Connected. Waiting for Controller...");
            });
        } catch (URISyntaxException e) { e.printStackTrace(); }

        if (resultCode != -1 && data != null) {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = mpm.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() { super.onStop(); stopSelf(); }
                }, backgroundHandler);
                
                startScreenCapture();
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("sagent", "S.Agent2", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "sagent")
                    .setContentTitle("S.Agent2 Running")
                    .setContentText("Screen streaming is active")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }
        }
    }

    private void updateNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new Notification.Builder(this, "sagent")
                    .setContentTitle("S.Agent2 Running")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            getSystemService(NotificationManager.class).notify(1, notification);
        }
    }

    private void startScreenCapture() {
        backgroundHandler.post(() -> {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            
            // Ultra low resolution for super fast streaming over socket
            int width = 270; 
            int height = 480;
            int density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("S.Agent2",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, backgroundHandler);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        long currentTime = System.currentTimeMillis();
                        // 200ms = 5 FPS. Good for stability and speed.
                        if (currentTime - lastFrameTime < 200) {
                            image.close();
                            return;
                        }
                        lastFrameTime = currentTime;

                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * width;
                        
                        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 15, baos); // 15% quality, very small size
                        byte[] byteArray = baos.toByteArray();
                        String base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT);

                        JSONObject data = new JSONObject();
                        data.put("roomId", roomId);
                        data.put("image", base64Image);
                        data.put("width", width);
                        data.put("height", height);
                        
                        if (mSocket != null && mSocket.connected()) {
                            mSocket.emit("screen-frame", data);
                        }
                        bitmap.recycle();
                    }
                } catch (Exception e) { e.printStackTrace(); } 
                finally { if (image != null) image.close(); }
            }, backgroundHandler);
        });
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (mSocket != null) mSocket.disconnect();
        if (backgroundThread != null) backgroundThread.quitSafely();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
