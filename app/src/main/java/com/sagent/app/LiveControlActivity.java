package com.sagent.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import io.socket.client.IO;
import io.socket.client.Socket;

public class LiveControlActivity extends AppCompatActivity {
    private Socket mSocket;
    private String roomId;
    private TextView statusText;
    private SurfaceViewRenderer surfaceViewRenderer;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private final List<IceCandidate> iceCandidates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getStringExtra("roomId");

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);

        surfaceViewRenderer = new SurfaceViewRenderer(this);
        statusText = new TextView(this);
        statusText.setText("Connecting WebRTC...\nPlease wait.");
        statusText.setTextColor(Color.CYAN);
        statusText.setTextSize(18);
        statusText.setPadding(30, 30, 30, 30);

        layout.addView(surfaceViewRenderer);
        layout.addView(statusText);
        setContentView(layout);

        initWebRTC();
        initSocket();

        surfaceViewRenderer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float viewWidth = surfaceViewRenderer.getWidth();
                float viewHeight = surfaceViewRenderer.getHeight();
                if(viewWidth == 0 || viewHeight == 0) return true;

                float normalizedX = event.getX() / viewWidth;
                float normalizedY = event.getY() / viewHeight;

                JSONObject data = new JSONObject();
                try {
                    data.put("roomId", roomId);
                    data.put("xRatio", normalizedX);
                    data.put("yRatio", normalizedY);
                    mSocket.emit("touch-event", data);
                } catch (JSONException e) { e.printStackTrace(); }
            }
            return true;
        });
    }

    private void initWebRTC() {
        PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        eglBase = EglBase.create();
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        
        surfaceViewRenderer.init(eglBase.getEglBaseContext(), null);

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                runOnUiThread(() -> {
                    if(iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        statusText.setVisibility(TextView.GONE);
                    }
                });
            }
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
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                if (mediaStreams.length > 0 && mediaStreams[0].videoTracks.size() > 0) {
                    VideoTrack track = mediaStreams[0].videoTracks.get(0);
                    runOnUiThread(() -> track.addSink(surfaceViewRenderer));
                }
            }
        });
    }

    private void initSocket() {
        try {
            mSocket = IO.socket(MainActivity.SERVER_URL);
            
            // Connect hote hi Server ko batayenge ki Controller aa gaya
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                mSocket.emit("join", roomId);
                mSocket.emit("request-connect", roomId);
            });

            mSocket.on("offer", args -> {
                String sdp = (String) args[0];
                SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                
                peerConnection.setRemoteDescription(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                    @Override public void onSetSuccess() {}
                    @Override public void onCreateFailure(String s) {}
                    @Override public void onSetFailure(String s) { s.toString(); }
                }, offer);
                
                for (IceCandidate candidate : iceCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                iceCandidates.clear();

                peerConnection.createAnswer(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new SdpObserver() {
                            @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                            @Override public void onSetSuccess() {}
                            @Override public void onCreateFailure(String s) {}
                            @Override public void onSetFailure(String s) { s.toString(); }
                        }, sessionDescription);
                        
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("roomId", roomId);
                            obj.put("sdp", sessionDescription.description);
                            mSocket.emit("answer", obj);
                        } catch (JSONException e) { e.printStackTrace(); }
                    }
                    @Override public void onSetSuccess() {}
                    @Override public void onCreateFailure(String s) {}
                    @Override public void onSetFailure(String s) {}
                }, new MediaConstraints());
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
    protected void onDestroy() {
        if (peerConnection != null) peerConnection.close();
        if (mSocket != null) mSocket.disconnect();
        if (eglBase != null) eglBase.release();
        super.onDestroy();
    }
}
