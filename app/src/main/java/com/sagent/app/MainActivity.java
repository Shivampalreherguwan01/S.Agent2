package com.sagent.app;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private Socket mSocket;
    private String roomId = "Room_SAgent2"; // Default room

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        EditText etRoomId = new EditText(this);
        etRoomId.setHint("Enter Room ID (e.g. 1234)");
        etRoomId.setText(roomId);
        
        // 1. HOST MODE
        Button btnHost = new Button(this);
        btnHost.setText("HOST MODE (Let others control this phone)");
        btnHost.setOnClickListener(v -> {
            roomId = etRoomId.getText().toString();
            
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Enable Accessibility for S.Agent2", Toast.LENGTH_LONG).show();

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
            
            connectToServer("host");
        });

        // 2. CONTROLLER MODE
        Button btnController = new Button(this);
        btnController.setText("CONTROLLER MODE (Control another phone)");
        btnController.setOnClickListener(v -> {
            roomId = etRoomId.getText().toString();
            connectToServer("controller");
            
            Intent intent = new Intent(this, ControllerActivity.class);
            intent.putExtra("roomId", roomId);
            startActivity(intent);
        });

        layout.addView(etRoomId);
        layout.addView(btnHost);
        layout.addView(btnController);
        setContentView(layout);
    }

    private void connectToServer(String role) {
        try {
            // Render ka Live URL yahan daal diya hai
            mSocket = IO.socket("https://s-agent-relay.onrender.com"); 
            mSocket.connect();
            
            if (role.equals("host")) {
                mSocket.emit("join-as-host", roomId);
            } else {
                mSocket.emit("join-as-controller", roomId);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            startForegroundService(serviceIntent);
        }
    }
}
