package com.sagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import io.socket.client.IO;
import io.socket.client.Socket;

public class ScreenCaptureService extends Service {
    private Socket mSocket;
    private String roomId;
    private MediaProjection mediaProjection;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private VideoTrack localVideoTrack;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;
    private final List<IceCandidate> iceCandidates = new ArrayList<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("sagent", "S.Agent2", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "sagent")
                    .setContentTitle("S.Agent2 Host Running")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }
        }

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data"); // WebRTC ko yeh Intent chahiye
        roomId = intent.getStringExtra("roomId");

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        
        // MediaProjection callback register karna zaroori hai
        if (mediaProjection != null) {
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    stopSelf();
                }
            }, null);
        }

        initWebRTC(data); // Intent pass kiya
        initSocket();
        
        return START_STICKY;
    }

    private void initWebRTC(Intent data) {
        PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        eglBase = EglBase.create();
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        
        // FIX 1: Yahan 'data' Intent pass hota hai 'mediaProjection' nahi
        videoCapturer = new ScreenCapturerAndroid(data, new MediaProjection.Callback() {});
        
        VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(540, 960, 30);

        localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource);

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override public void onIceCandidate(IceCandidate iceCandidate) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("roomId", roomId);
                    obj.put("candidate", iceCandidate.sdp);
                    mSocket.emit("ice-candidate", obj);
                } catch (JSONException e) { e.printStackTrace(); }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onAddStream(MediaStream mediaStream) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        });

        peerConnection.addTrack(localVideoTrack);

        // Create Offer
        peerConnection.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                    @Override public void onSetSuccess() {}
                    @Override public void onCreateFailure(String s) {}
                    // FIX 2: onSetFailure add kiya
                    @Override public void onSetFailure(String s) { s.toString(); } 
                }, sessionDescription);
                
                JSONObject obj = new JSONObject();
                try {
                    obj.put("roomId", roomId);
                    obj.put("sdp", sessionDescription.description);
                    mSocket.emit("offer", obj);
                } catch (JSONException e) { e.printStackTrace(); }
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) {}
            @Override public void onSetFailure(String s) {}
        }, new MediaConstraints());
    }

    private void initSocket() {
        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            mSocket.on(Socket.EVENT_CONNECT, args -> mSocket.emit("join", roomId));
            
            mSocket.on("answer", args -> {
                String sdp = (String) args[0];
                SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                peerConnection.setRemoteDescription(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                    @Override public void onSetSuccess() {}
                    @Override public void onCreateFailure(String s) {}
                    // FIX 2: onSetFailure add kiya
                    @Override public void onSetFailure(String s) { s.toString(); }
                }, answer);
                
                for (IceCandidate candidate : iceCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                iceCandidates.clear();
            });

            mSocket.on("ice-candidate", args -> {
                String sdp = (String) args[0];
                IceCandidate candidate = new IceCandidate("0", 0, sdp);
                if (peerConnection.getRemoteDescription() != null) {
                    peerConnection.addIceCandidate(candidate);
                } else {
                    iceCandidates.add(candidate);
                }
            });

            mSocket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroy() {
        try {
            if (videoCapturer != null) videoCapturer.stopCapture();
            if (peerConnection != null) peerConnection.close();
            if (mSocket != null) mSocket.disconnect();
            if (eglBase != null) eglBase.release();
        } catch (Exception e) { e.printStackTrace(); }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
