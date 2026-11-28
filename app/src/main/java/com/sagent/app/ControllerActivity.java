package com.sagent.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ControllerActivity extends AppCompatActivity {
    private String roomId;
    private ImageView imageView;
    private Button btnCam, btnMic;
    private DatabaseReference roomRef;
    private boolean isCamOn = false, isMicOn = false;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId"); // From MainActivity

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.BLACK);
        layout.setPadding(30, 30, 30, 30);

        imageView = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        imageView.setLayoutParams(imgParams);

        btnCam = new Button(this);
        btnCam.setText("TURN CAMERA ON");
        btnCam.setOnClickListener(v -> toggleCamera());

        btnMic = new Button(this);
        btnMic.setText("TURN MIC ON");
        btnMic.setOnClickListener(v -> toggleMic());

        layout.addView(imageView);
        layout.addView(btnCam);
        layout.addView(btnMic);
        setContentView(layout);

        setupAudioTrack();
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        // Listen for Camera Frames
        roomRef.child("camera_frame").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String base64 = snapshot.child("image").getValue(String.class);
                    if (base64 != null) {
                        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        runOnUiThread(() -> imageView.setImageBitmap(bmp));
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen for Audio
        roomRef.child("audio_chunk").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String base64 = snapshot.getValue(String.class);
                if (base64 != null && audioTrack != null) {
                    byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                    audioTrack.write(bytes, 0, bytes.length);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void toggleCamera() {
        isCamOn = !isCamOn;
        btnCam.setText(isCamOn ? "TURN CAMERA OFF" : "TURN CAMERA ON");
        roomRef.child("camera_control").setValue(isCamOn);
    }

    private void toggleMic() {
        isMicOn = !isMicOn;
        btnMic.setText(isMicOn ? "TURN MIC OFF" : "TURN MIC ON");
        roomRef.child("mic_control").setValue(isMicOn);
    }

    private void setupAudioTrack() {
        int sampleRate = 16000;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        audioTrack.play();
    }

    @Override
    protected void onDestroy() {
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
        super.onDestroy();
    }
}
