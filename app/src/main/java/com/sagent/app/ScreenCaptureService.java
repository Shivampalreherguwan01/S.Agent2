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
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ScreenCaptureService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private String roomId;
    private DatabaseReference screenRef;
    private long lastFrameTime = 0;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean isUploading = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra("roomId") || !intent.hasExtra("data")) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("sagent", "S.Agent2", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "sagent")
                    .setContentTitle("S.Agent2 Streaming Active")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }
        }

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        roomId = intent.getStringExtra("roomId");
        
        screenRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId).child("screen");

        backgroundThread = new HandlerThread("CaptureThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        
        if (mediaProjection != null) {
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() { super.onStop(); stopSelf(); }
            }, backgroundHandler);
            startScreenCapture();
        }
        
        return START_NOT_STICKY; // PDF FIX
    }

    private void startScreenCapture() {
        backgroundHandler.post(() -> {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);
            
            int height = 600; 
            int width = (int) (height * ((float) metrics.widthPixels / metrics.heightPixels));
            int density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("S.Agent2", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, backgroundHandler);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastFrameTime > 2000) isUploading = false; // Timeout

                        if (currentTime - lastFrameTime < 150 || isUploading) { 
                            image.close(); return;
                        }
                        lastFrameTime = currentTime;
                        isUploading = true;

                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * width;
                        
                        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);
                        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                        bitmap.recycle();

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos); 
                        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                        Map<String, Object> frameData = new HashMap<>();
                        frameData.put("image", base64Image);
                        frameData.put("width", width);
                        frameData.put("height", height);
                        frameData.put("timestamp", currentTime);
                        
                        screenRef.setValue(frameData).addOnCompleteListener(task -> {
                            isUploading = false;
                            if (!task.isSuccessful()) {
                                Log.e("ScreenCapture", "Upload Failed", task.getException());
                            }
                        });
                        croppedBitmap.recycle();
                    }
                } catch (Exception e) { 
                    isUploading = false; 
                    Log.e("ScreenCapture", "Error", e);
                } 
                finally { if (image != null) image.close(); }
            }, backgroundHandler);
        });
    }

    @Override
    public void onDestroy() {
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (backgroundThread != null) backgroundThread.quitSafely();
        super.onDestroy();
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
