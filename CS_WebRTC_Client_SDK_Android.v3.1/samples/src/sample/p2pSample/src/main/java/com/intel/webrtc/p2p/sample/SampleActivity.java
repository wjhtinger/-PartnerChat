/*
 * Copyright © 2016 Intel Corporation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intel.webrtc.p2p.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.RendererCommon;

import android.app.AlertDialog.Builder;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.Toast;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import com.intel.webrtc.base.ActionCallback;
import com.intel.webrtc.base.ConnectionStats;
import com.intel.webrtc.base.LocalCameraStream;
import com.intel.webrtc.base.LocalCameraStreamParameters;
import com.intel.webrtc.base.MediaCodec.VideoCodec;
import com.intel.webrtc.base.RemoteStream;
import com.intel.webrtc.base.Stream.VideoRendererInterface;
import com.intel.webrtc.base.ClientContext;
import com.intel.webrtc.base.WoogeenException;
import com.intel.webrtc.base.WoogeenSurfaceRenderer;
import com.intel.webrtc.p2p.PeerClient;
import com.intel.webrtc.p2p.PeerClient.PeerClientObserver;
import com.intel.webrtc.p2p.PeerClientConfiguration;
import com.intel.webrtc.p2p.PublishOptions;


@SuppressWarnings("deprecation")
public class SampleActivity extends TabActivity implements OnClickListener {

    private static final String TAG = "WooGeen-Activity";
    private TabHost mTabHost;
    private TabSpec videoSpec, settingSpec, chatSpec;
    private EditText serverEdTx, selfIdEdTx, destIdEdTx, msgEditText,
            receivedMsgEdTx;
    private Button loginBtn, logoutBtn, connectBtn, disconnectBtn, sendMsgBtn,
            startVideoBtn, stopVideoBtn, switchCameraBtn;
    private String selfId = "";
    private String destId = "";
    private String server;
    private PeerClient peerClient;
    private LocalCameraStream localStream;
    private EglBase rootEglBase;
    private LinearLayout remoteViewContainer, localViewContainer;
    private HandlerThread peerThread;
    private PeerHandler peerHandler;
    private Message message;
    private String msgString;
    // to record the local stream publish to which peer.
    private String publishPeerId = "";

    private WoogeenSurfaceRenderer localSurfaceRenderer;
    private WoogeenSurfaceRenderer remoteSurfaceRenderer;
    private VideoRendererInterface localStreamRenderer;
    private VideoRendererInterface remoteStreamRenderer;

    private WoogeenSampleView localView;
    private WoogeenSampleView remoteView;

    private final static int LOGIN = 1;
    private final static int LOGOUT = 2;
    private final static int INVITE = 3;
    private final static int STOP = 4;
    private final static int PUBLISH = 5;
    private final static int UNPUBLISH = 6;
    private final static int SWITCH_CAMERA = 7;
    private final static int SEND_DATA = 8;

    private Timer statsTimer;

    //default camera is the front camera
    private boolean mirror = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        audioManager.setSpeakerphoneOn(true);

        setContentView(R.layout.tabhost);
        mTabHost = getTabHost();
        // Video tab
        videoSpec = mTabHost.newTabSpec("tab_video");
        videoSpec.setIndicator("Video");
        videoSpec.setContent(R.id.tab_video);
        mTabHost.addTab(videoSpec);
        // Setting tab
        settingSpec = mTabHost.newTabSpec("tab_setting");
        settingSpec.setIndicator("Setting");
        settingSpec.setContent(R.id.tab_setting);
        mTabHost.addTab(settingSpec);
        // Chat tab
        chatSpec = mTabHost.newTabSpec("tab_chat");
        chatSpec.setIndicator("Chat");
        chatSpec.setContent(R.id.tab_chat);
        mTabHost.addTab(chatSpec);

        int childCount = mTabHost.getTabWidget().getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mTabHost.getTabWidget().getChildAt(i);
            TextView tv = (TextView)child.findViewById(android.R.id.title);
            RelativeLayout.LayoutParams tvParams = (RelativeLayout.LayoutParams)tv.getLayoutParams();

            tvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            tvParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        }
        serverEdTx = (EditText) findViewById(R.id.txt_server);
        selfIdEdTx = (EditText) findViewById(R.id.txt_name);
        destIdEdTx = (EditText) findViewById(R.id.txt_target);
        msgEditText = (EditText) findViewById(R.id.sendedMsgEdTx);
        receivedMsgEdTx = (EditText) findViewById(R.id.receivedMsgEdTx);
        loginBtn = (Button) findViewById(R.id.btn_login);
        logoutBtn = (Button) findViewById(R.id.btn_logout);
        connectBtn = (Button) findViewById(R.id.btn_connectPeer);
        disconnectBtn = (Button) findViewById(R.id.btn_disconnectPeer);
        startVideoBtn = (Button) findViewById(R.id.btn_startVideo);
        stopVideoBtn = (Button) findViewById(R.id.btn_stopVideo);
        switchCameraBtn = (Button) findViewById(R.id.btn_switchCamera);
        sendMsgBtn = (Button) findViewById(R.id.sendMsgButton);
        localViewContainer = (LinearLayout) findViewById(R.id.local_view_container);
        remoteViewContainer = (LinearLayout) findViewById(R.id.remote_view_container);

        try {
            initVideoStreamsViews();
            initAudioControl();
            initPeerClient();
        } catch (WoogeenException e) {
            e.printStackTrace();
        }


    }

    private void initVideoStreamsViews() throws WoogeenException{
        localView = new WoogeenSampleView(this);
        localSurfaceRenderer = new WoogeenSurfaceRenderer(localView);
        localViewContainer.addView(localView);
        localStreamRenderer = localSurfaceRenderer.createVideoRenderer(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, true);

        remoteView = new WoogeenSampleView(this);
        remoteSurfaceRenderer = new WoogeenSurfaceRenderer(remoteView);
        remoteViewContainer.addView(remoteView);
        remoteStreamRenderer = remoteSurfaceRenderer.createVideoRenderer(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);
    }

    private void initPeerClient(){
        try{
            // Initialization work.
            rootEglBase = new EglBase();

            ClientContext.setApplicationContext(this, rootEglBase.getContext());
            List<IceServer> iceServers = new ArrayList<IceServer>();
            iceServers.add(new IceServer("stun:61.152.239.60"));
            iceServers.add(new IceServer(
                    "turn:61.152.239.60:4478?transport=udp", "woogeen",
                    "master"));
            iceServers.add(new IceServer(
                    "turn:61.152.239.60:4478?transport=tcp", "woogeen",
                    "master"));
            PeerClientConfiguration config = new PeerClientConfiguration();
            config.setIceServers(iceServers);
            config.setVideoCodec(VideoCodec.VP8);
            peerClient = new PeerClient(config, new SocketSignalingChannel());
            peerClient.addObserver(observer);
            peerThread = new HandlerThread("PeerThread");
            peerThread.start();
            peerHandler = new PeerHandler(peerThread.getLooper());
        }catch(WoogeenException e1){
            e1.printStackTrace();
        }
    }

    private void initAudioControl(){
        try {
            Properties p = new Properties();
            InputStream s = this.getAssets().open("audio_control.properties");
            p.load(s);

            ClientContext.setAudioControlEnabled(Boolean.parseBoolean(p.getProperty("enable_audio_control")));
            ClientContext.setAudioLevelOverloud(Integer.parseInt(p.getProperty("audio_level_overloud")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        message = peerHandler.obtainMessage();
        switch (v.getId()) {
        case R.id.btn_login:
            message.what = LOGIN;
            message.sendToTarget();
            break;
        case R.id.btn_logout:
            message.what = LOGOUT;
            message.sendToTarget();
            serverEdTx.setEnabled(true);
            loginBtn.setEnabled(true);
            selfIdEdTx.setEnabled(true);
            logoutBtn.setEnabled(false);
            connectBtn.setEnabled(false);
            disconnectBtn.setEnabled(false);
            startVideoBtn.setEnabled(false);
            stopVideoBtn.setEnabled(false);
            switchCameraBtn.setEnabled(false);
            sendMsgBtn.setEnabled(false);
            break;
        case R.id.btn_connectPeer:
            message.what = INVITE;
            message.sendToTarget();
            break;
        case R.id.btn_disconnectPeer:
            message.what = STOP;
            message.sendToTarget();
            break;
        case R.id.btn_startVideo:
            message.what = PUBLISH;
            message.sendToTarget();
            break;
        case R.id.btn_stopVideo:
            message.what = UNPUBLISH;
            message.sendToTarget();
            break;
        case R.id.btn_switchCamera:
            message.what = SWITCH_CAMERA;
            message.sendToTarget();
            switchCameraBtn.setEnabled(false);
            break;
        case R.id.sendMsgButton:
            message.what = SEND_DATA;
            message.sendToTarget();
            break;
        }

    }

    @Override
    protected void onPause() {
        if (localStream != null) {
            localStream.disableVideo();
            localStream.disableAudio();
            Toast.makeText(this, "Woogeen is running in the background.",
                    Toast.LENGTH_SHORT).show();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (localStream != null) {
            localStream.enableVideo();
            localStream.enableAudio();
            Toast.makeText(this, "Welcome back", Toast.LENGTH_SHORT).show();
        }
        super.onResume();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    PeerClientObserver observer = new PeerClientObserver() {

        @Override
        public void onInvited(final String peerId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    destId = peerId;
                    final Builder builder = new Builder(SampleActivity.this);
                    builder.setTitle("Video Invitation");
                    builder.setMessage("Do you want to connect with " + peerId
                            + "?");
                    builder.setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    peerClient.accept(destId,
                                            new ActionCallback<Void>() {

                                                @Override
                                                public void onSuccess(Void result) {
                                                        runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                                sendMsgBtn.setEnabled(true);
                                                                startVideoBtn.setEnabled(true);
                                                                destIdEdTx.setText(destId);
                                                                disconnectBtn.setEnabled(true);
                                                        }});
                                                }

                                                @Override
                                                public void onFailure(
                                                        WoogeenException e) {
                                                    Log.d(TAG, e.getMessage());
                                                }

                                            });
                                }
                            });
                    builder.setNeutralButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    peerClient.deny(destId,
                                            new ActionCallback<Void>() {

                                                @Override
                                                public void onSuccess(
                                                        Void result) {
                                                    Log.d(TAG,
                                                            "Denied invitation from "
                                                                    + destId);
                                                }

                                                @Override
                                                public void onFailure(
                                                        WoogeenException e) {
                                                    Log.d(TAG, e.getMessage());
                                                }

                                            });
                                }
                            });
                    builder.create().show();
                }
            });
        }

        @Override
        public void onAccepted(final String peerId) {
            Log.d(TAG, "onAccepted:" + peerId);
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(SampleActivity.this,
                            "Receive Accept from " + peerId, Toast.LENGTH_SHORT)
                            .show();
                    sendMsgBtn.setEnabled(true);
                    startVideoBtn.setEnabled(true);
                    disconnectBtn.setEnabled(true);
                }
            });
        }

        @Override
        public void onDenied(final String peerId) {
            Log.d(TAG, "onDenied:" + peerId);
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(SampleActivity.this,
                            "Receive Deny from " + peerId, Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }

        @Override
        public void onStreamAdded(final RemoteStream stream) {
            Log.d(TAG, "onStreamAdded : from " + stream.getRemoteUserId());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        stream.attach(remoteStreamRenderer);
                        Toast.makeText(SampleActivity.this, "Added remote stream "
                                + "from "+stream.getRemoteUserId(),
                                Toast.LENGTH_LONG).show();

                    } catch (WoogeenException e) {
                        Log.d(TAG, e.getMessage());
                    }
                }
            });
        }

        @Override
        public void onDataReceived(final String peerId, final String msg) {
            runOnUiThread(new Runnable() {
                public void run() {
                    receivedMsgEdTx.setText(peerId + ":" + msg);
                }
            });
        }

        @Override
        public void onStreamRemoved(final RemoteStream stream) {
            Log.d(TAG, "onStreamRemoved");
            remoteStreamRenderer.cleanFrame();
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(SampleActivity.this, "onStreamRemoved",
                            Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onChatStopped(final String peerId) {
            Log.d(TAG, "onChatStop:" + peerId);
            runOnUiThread(new Runnable() {
                public void run(){
                    stopVideoBtn.setEnabled(false);
                    startVideoBtn.setEnabled(false);
                    Toast.makeText(SampleActivity.this, "onChatStop:" + peerId,
                            Toast.LENGTH_SHORT).show();
                }
            });
            remoteStreamRenderer.cleanFrame();
            localStreamRenderer.cleanFrame();
            if(statsTimer != null){
                statsTimer.cancel();
            }
        }

        @Override
        public void onChatStarted(final String peerId) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(SampleActivity.this,
                            "onChatStart:" + peerId, Toast.LENGTH_SHORT).show();
                    startVideoBtn.setEnabled(true);
                    sendMsgBtn.setEnabled(true);
                }
            });

            //This is a sample usage of get the statistic data for the peerconnection including all the streams
            //ever been published. If you would like to get the data for a specific stream,  please refer to the
            //sample code in the onSuccess callback of publish.
            //ATTENTION: DO NOT use getConnectionStats(), getConnectionStats(localstream)and getAudioLevels() at the same time.
            /*statsTimer = new Timer();
            statsTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    peerClient.getConnectionStats(destId, new ActionCallback<ConnectionStats>() {
                        @Override
                        public void onSuccess(ConnectionStats result) {
                            Log.d(TAG, "connection stats: " + result.timeStamp
                                      +" available transmit bitrate: " + result.videoBandwidthStats.transmitBitrate
                                      +" retransmit bitrate: " + result.videoBandwidthStats.reTransmitBitrate);
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    peerClient.getAudioLevels(destId, new ActionCallback<AudioLevels>(){

                        @Override
                        public void onSuccess(AudioLevels audioLevels) {
                            Log.d(TAG, "audio input levels: ");
                            for(AudioLevels.AudioLevel al : audioLevels.getInputLevelList())
                                Log.d(TAG, al.ssrcId + ":" + al.level);
                            Log.d(TAG, "audio output levels: ");
                            for(AudioLevels.AudioLevel al : audioLevels.getOutputLevelList())
                                Log.d(TAG, al.ssrcId + ":" + al.level);
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            Log.d(TAG, "Failed to get audio level:" + e.getMessage());
                        }

                    });
                }
            }, 0, 10000);*/
        }

        @Override
        public void onServerDisconnected() {
            remoteStreamRenderer.cleanFrame();
            localStreamRenderer.cleanFrame();
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(SampleActivity.this, "onServerDisconnected",
                            Toast.LENGTH_SHORT).show();
                    loginBtn.setEnabled(true);
                    logoutBtn.setEnabled(false);
                    startVideoBtn.setEnabled(false);
                    stopVideoBtn.setEnabled(false);
                    switchCameraBtn.setEnabled(false);
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(false);
                    sendMsgBtn.setEnabled(false);
                    selfIdEdTx.setEnabled(true);
                    serverEdTx.setEnabled(true);
                }
            });
        }
    };

    class PeerHandler extends Handler {
        public PeerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case LOGIN:
                selfId = selfIdEdTx.getText().toString();
                server = serverEdTx.getText().toString();
                JSONObject loginObject = new JSONObject();
                try {
                    loginObject.put("host", server);
                    loginObject.put("token", selfId);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
                peerClient.connect(loginObject.toString(),
                        new ActionCallback<String>() {

                            @Override
                            public void onSuccess(String result) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(SampleActivity.this,
                                                "onServerConnected",
                                                Toast.LENGTH_SHORT).show();
                                        serverEdTx.setEnabled(false);
                                        loginBtn.setEnabled(false);
                                        selfIdEdTx.setEnabled(false);
                                        logoutBtn.setEnabled(true);
                                        connectBtn.setEnabled(true);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                Log.d(TAG, "Failed to connect server:" + e.getMessage());
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(SampleActivity.this,
                                                "onServerConnectFailed",
                                                Toast.LENGTH_SHORT).show();
                                        loginBtn.setEnabled(true);
                                        logoutBtn.setEnabled(false);
                                        startVideoBtn.setEnabled(false);
                                        stopVideoBtn.setEnabled(false);
                                        switchCameraBtn.setEnabled(false);
                                        connectBtn.setEnabled(false);
                                        disconnectBtn.setEnabled(false);
                                        sendMsgBtn.setEnabled(false);
                                        selfIdEdTx.setEnabled(true);
                                        serverEdTx.setEnabled(true);
                                    }
                                });
                            }
                        });
                break;
            case LOGOUT:
                peerClient.disconnect(new ActionCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        if (localStream != null) {
                            localStream.close();
                            localStream = null;
                            localStreamRenderer.cleanFrame();
                        }
                    }

                    @Override
                    public void onFailure(WoogeenException e) {
                        Log.d(TAG, e.getMessage());
                    }
                });
                break;
            case INVITE:
                destId = destIdEdTx.getText().toString();
                peerClient.invite(destId, new ActionCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                    }

                    @Override
                    public void onFailure(WoogeenException e) {
                        Log.d(TAG, e.getMessage());
                    }

                });
                break;
            case STOP:
                destId = destIdEdTx.getText().toString();
                peerClient.stop(destId, new ActionCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        if (localStream != null) {
                            localStream.close();
                            localStream = null;
                            localStreamRenderer.cleanFrame();
                        }
                    }

                    @Override
                    public void onFailure(WoogeenException e) {
                        Log.d(TAG, e.getMessage());
                    }

                });
                break;
            case PUBLISH:
                if (localStream == null) {
                    LocalCameraStreamParameters msp;
                    try {
                        msp = new LocalCameraStreamParameters(
                                true, true);
                        msp.setResolution(320, 240);
                        localStream = new LocalCameraStream(msp);
                        localStream.attach(localStreamRenderer);
                    } catch (WoogeenException e1) {
                        e1.printStackTrace();
                        if (localStream != null) {
                            localStream.close();
                            localStream = null;
                            localStreamRenderer.cleanFrame();
                        }
                    }
                }
                destId = destIdEdTx.getText().toString();
                PublishOptions option = new PublishOptions();
                option.setMaximumVideoBandwidth(200);
                option.setMaximumAudioBandwidth(30);
                peerClient.publish(localStream, destId, option,
                        new ActionCallback<Void>() {

                            @Override
                            public void onSuccess(Void result) {
                                publishPeerId = destId;
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        startVideoBtn.setEnabled(false);
                                        stopVideoBtn.setEnabled(true);
                                        switchCameraBtn.setEnabled(true);
                                    }
                                });

                                //This is a sample usage of get the statistic data for a specific stream, in this sample, localStream
                                //that has just been published. If you would like to get all the data for the peerconnection, including
                                //the data for the streams had been published before, please refer to the sample code in onChatStarted.
                                //ATTENTION: DO NOT use getConnectionStats(), getConnectionStats(localstream)and getAudioLevels() at the same time.
                                statsTimer = new Timer();
                                statsTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        peerClient.getConnectionStats(destId, localStream, new ActionCallback<ConnectionStats>() {
                                            @Override
                                            public void onSuccess(ConnectionStats result) {
                                                Log.d(TAG, "result:" + result.mediaTracksStatsList.size());
                                                Log.d(TAG, "connection stats: " + result.timeStamp
                                                          +" available transmit bitrate: " + result.videoBandwidthStats.transmitBitrate
                                                          +" retransmit bitrate: " + result.videoBandwidthStats.reTransmitBitrate);
                                            }

                                            @Override
                                            public void onFailure(WoogeenException e) {
                                            }
                                        });

                                    }
                                }, 0, 10000);
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                Log.d(TAG, e.getMessage());
                                if (localStream != null) {
                                    localStream.close();
                                    localStream = null;
                                    localStreamRenderer.cleanFrame();
                                }
                            }
                        });
            break;
            case UNPUBLISH:
                if (localStream != null) {
                    peerClient.unpublish(localStream, publishPeerId,
                            new ActionCallback<Void>() {

                                @Override
                                public void onSuccess(Void result) {
                                    localStream.close();
                                    localStream = null;
                                    localStreamRenderer.cleanFrame();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            stopVideoBtn.setEnabled(false);
                                            startVideoBtn.setEnabled(true);
                                            switchCameraBtn.setEnabled(false);
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(WoogeenException e) {
                                    Log.d(TAG, e.getMessage());
                                }

                            });
                }
                break;
            case SWITCH_CAMERA:
                localStream.switchCamera(new ActionCallback<Boolean>(){

                    @Override
                    public void onSuccess(final Boolean isFrontCamera) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                switchCameraBtn.setEnabled(true);
                                Toast.makeText(SampleActivity.this,
                                        "Switch to " + (isFrontCamera ? "front" : "back") + " camera.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        mirror = !mirror;
                        localSurfaceRenderer.update(localStreamRenderer, 0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, mirror);
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable(){

                            @Override
                            public void run() {
                                switchCameraBtn.setEnabled(true);
                                Toast.makeText(SampleActivity.this,
                                        "Failed to switch camera. " + e.getLocalizedMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }

                        });
                    }

                });
                break;
            case SEND_DATA:
                msgString = msgEditText.getText().toString();
                destId = destIdEdTx.getText().toString();
                Log.d(TAG, "send data:" + msgString + " to " + destId);
                peerClient.send(msgString, destId, new ActionCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(SampleActivity.this,
                                        "Sent successfully.",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(SampleActivity.this, e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        Log.d(TAG, e.getMessage());
                    }

                });
            }
            super.handleMessage(msg);
        }
    }
}
