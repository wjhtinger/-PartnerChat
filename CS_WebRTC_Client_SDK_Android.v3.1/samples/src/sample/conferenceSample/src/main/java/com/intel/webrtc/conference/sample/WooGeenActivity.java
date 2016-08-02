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

package com.intel.webrtc.conference.sample;

import android.app.AlertDialog;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.Toast;

import com.intel.webrtc.base.ActionCallback;
import com.intel.webrtc.base.ClientContext;
import com.intel.webrtc.base.LocalCameraStream;
import com.intel.webrtc.base.LocalCameraStreamParameters;
import com.intel.webrtc.base.LocalCameraStreamParameters.CameraType;
import com.intel.webrtc.base.MediaCodec.VideoCodec;
import com.intel.webrtc.base.RemoteStream;
import com.intel.webrtc.base.Stream.VideoRendererInterface;
import com.intel.webrtc.base.WoogeenException;
import com.intel.webrtc.base.WoogeenSurfaceRenderer;
import com.intel.webrtc.conference.ConferenceClient;
import com.intel.webrtc.conference.ConferenceClient.ConferenceClientObserver;
import com.intel.webrtc.conference.ConferenceClientConfiguration;
import com.intel.webrtc.conference.PublishOptions;
import com.intel.webrtc.conference.RecordAck;
import com.intel.webrtc.conference.RecordOptions;
import com.intel.webrtc.conference.RemoteMixedStream;
import com.intel.webrtc.conference.SubscribeOptions;
import com.intel.webrtc.conference.User;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.RendererCommon;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

@SuppressWarnings("deprecation")
public class WooGeenActivity extends TabActivity implements
        ConferenceClientObserver, View.OnClickListener, RemoteMixedStream.RemoteMixedStreamObserver {
    private static final String TAG = "WooGeen-Activity";

    private ConferenceClient mRoom;
    private WoogeenSampleView localView;
    private WoogeenSampleView remoteView;
    private WoogeenSurfaceRenderer localSurfaceRenderer;
    private WoogeenSurfaceRenderer remoteSurfaceRenderer;
    private VideoRendererInterface localStreamRenderer;
    private VideoRendererInterface remoteStreamRenderer;

    private LinearLayout localViewContainer;
    private LinearLayout remoteViewContainer;
    private EglBase rootEglBase;

    private String basicServerString = "http://61.152.239.56:3001/";
    private static final String stunAddr = "stun:61.152.239.60";
    private static final String turnAddrUDP = "turn:61.152.239.60:4478?transport=udp";
    private static final String turnAddrTCP = "turn:61.152.239.60:4478?transport=tcp";
    private LocalCameraStream localStream;
    private RemoteStream currentRemoteStream;

    private TabHost mTabHost;
    private TabSpec serverSpec, mainSpec, chatSpec, streamSpec;

    private int resolutionSelected = -1;

    private Button callButton;
    private Button connectButton;
    private Button localAudioButton;
    private Button localVideoButton;
    private Button remoteAudioButton;
    private Button remoteVideoButton;
    private Button startRecorderButton;
    private Button stopRecorderButton;
    private EditText remoteMessageEditText, localMessageEditText;
    private EditText basicServerEditText;
    private EditText superIdEditText;
    private EditText superKeyEditText;
    private RadioGroup serverGroup;

    private String tokenString = "";

    public static final int MSG_ROOM_DISCONNECTED = 98;
    public static final int MSG_PUBLISH = 99;
    public static final int MSG_LOGIN = 100;
    public static final int MSG_SUBSCRIBE = 101;
    public static final int MSG_UNSUBSCRIBE = 102;
    public static final int MSG_UNPUBLISH = 103;
    public static final int MSG_PAUSEVIDEO = 104;
    public static final int MSG_PLAYVIDEO = 105;
    public static final int MSG_PAUSEAUDIO = 106;
    public static final int MSG_PLAYAUDIO = 107;

    private HandlerThread roomThread;
    private RoomHandler roomHandler;

    private String recorderId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "uncaughtexception");
                e.printStackTrace();
                System.exit(-1);
            }
        });
        AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        audioManager.setSpeakerphoneOn(true);

        ConferenceClientConfiguration config=new ConferenceClientConfiguration();
        List<IceServer> iceServers=new ArrayList<IceServer>();
        iceServers.add(new IceServer(stunAddr));
        //iceServers.add(new IceServer(turnAddrTCP, "woogeen", "master"));
        //iceServers.add(new IceServer(turnAddrUDP, "woogeen", "master"));
        try {
            config.setIceServers(iceServers);
        } catch (WoogeenException e1) {
            e1.printStackTrace();
        }
        mRoom = new ConferenceClient(config);
        mRoom.addObserver(this);

        setContentView(R.layout.tabhost);
        mTabHost = getTabHost();
        // Main tab
        mainSpec = mTabHost.newTabSpec("tab_video");
        mainSpec.setIndicator("Main");
        mainSpec.setContent(R.id.tab_video);
        mTabHost.addTab(mainSpec);
        // chat tab
        chatSpec = mTabHost.newTabSpec("tab_chat");
        chatSpec.setIndicator("Chat");
        chatSpec.setContent(R.id.tab_chat);
        mTabHost.addTab(chatSpec);
        // server tab
        serverSpec = mTabHost.newTabSpec("tab_server");
        serverSpec.setIndicator("Configuration");
        serverSpec.setContent(R.id.tab_server);
        mTabHost.addTab(serverSpec);
        // stream tab
        localStream = null;
        streamSpec = mTabHost.newTabSpec("tab_stream");
        streamSpec.setIndicator("StreamSetting");
        streamSpec.setContent(R.id.tab_stream);
        mTabHost.addTab(streamSpec);

        int childCount = mTabHost.getTabWidget().getChildCount();
        for (int i = 0; i < childCount; i++) {
            mTabHost.getTabWidget().getChildAt(i).getLayoutParams().height = 80;
        }

        remoteMessageEditText = (EditText) findViewById(R.id.remoteMsgEdTx);
        localMessageEditText = (EditText) findViewById(R.id.localMsgEdTx);
        basicServerEditText = (EditText) findViewById(R.id.basicServerEdTx);
        basicServerEditText.setText(basicServerString);

        callButton = (Button) findViewById(R.id.btStartStopCall);
        connectButton = (Button) findViewById(R.id.btConnect);
        localAudioButton = (Button) findViewById(R.id.btSetLocalAudio);
        localVideoButton = (Button) findViewById(R.id.btSetLocalVideo);
        remoteAudioButton = (Button) findViewById(R.id.btSetRemoteAudio);
        remoteVideoButton = (Button) findViewById(R.id.btSetRemoteVideo);
        startRecorderButton = (Button) findViewById(R.id.btStartRecorder);
        stopRecorderButton = (Button) findViewById(R.id.btStopRecorder);
        localViewContainer = (LinearLayout) findViewById(R.id.llLocalView);
        remoteViewContainer = (LinearLayout) findViewById(R.id.llRemoteView);

        serverGroup = (RadioGroup) findViewById(R.id.ServerRadioGp);

        initVideoStreamsViews();
        initAudioControl();

        roomThread = new HandlerThread("Room Thread");
        roomThread.start();
        roomHandler = new RoomHandler(roomThread.getLooper());
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

    public void onClick(View arg0) {
        Message msg = new Message();
        switch (arg0.getId()) {
        case R.id.btConnect:
            if (connectButton.getText().toString().equals("Connect")) {
                msg.what = MSG_LOGIN;
            } else
                msg.what = MSG_ROOM_DISCONNECTED;
            roomHandler.sendMessage(msg);
            break;
        case R.id.btStartStopCall:
            if (callButton.getText().toString().equals("Start Video"))
                msg.what = MSG_PUBLISH;
            else
                msg.what = MSG_UNPUBLISH;
            callButton.setEnabled(false);
            roomHandler.sendMessage(msg);
            break;
        case R.id.btSetLocalAudio:
            try {
                if (localStream != null) {
                    if (localAudioButton.getText().toString()
                            .equals("Pause Local Audio")) {
                        localStream.disableAudio();
                        localAudioButton.setText("Play Local Audio");
                    } else {
                        localStream.enableAudio();
                        localAudioButton.setText("Pause Local Audio");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        case R.id.btSetLocalVideo:
            try {
                if (localStream != null) {
                    if (localVideoButton.getText().toString()
                            .equals("Pause Local Video")) {
                        localStream.disableVideo();
                        localVideoButton.setText("Play Local Video");
                    } else {
                        localStream.enableVideo();
                        localVideoButton.setText("Pause Local Video");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        case R.id.btSetRemoteAudio:
            if (remoteAudioButton.getText().toString().equals("Pause Remote Audio"))
                msg.what = MSG_PAUSEAUDIO;
            else
                msg.what = MSG_PLAYAUDIO;
            remoteAudioButton.setEnabled(false);
            roomHandler.sendMessage(msg);
            break;
        case R.id.btSetRemoteVideo:
            if (remoteVideoButton.getText().toString().equals("Pause Remote Video"))
                msg.what = MSG_PAUSEVIDEO;
            else
                msg.what = MSG_PLAYVIDEO;
            remoteVideoButton.setEnabled(false);
            roomHandler.sendMessage(msg);
            break;
        case R.id.sendBt:
            String messageString = localMessageEditText.getText().toString();
            mRoom.send(messageString, new ActionCallback<Void>(){
                @Override
                public void onFailure(WoogeenException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WooGeenActivity.this, "Sent message failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WooGeenActivity.this, "Sent message.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            localMessageEditText.setText("");
            break;
        case R.id.btStartRecorder:
           if (currentRemoteStream == null) {
               runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WooGeenActivity.this, "No remote stream to be recorded",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
              RecordOptions startOpt = new RecordOptions();
              startOpt.setVideoStreamId(this.currentRemoteStream.getId());
              startOpt.setAudioStreamId(this.currentRemoteStream.getId());
              mRoom.startRecorder(startOpt, new ActionCallback<RecordAck>(){

                @Override
                public void onSuccess(final RecordAck ack) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            WooGeenActivity.this.recorderId = ack.getRecorderId();
                            Toast.makeText(WooGeenActivity.this, "Started recorder",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    Log.d(TAG, "Started recorder, location: http://" + ack.getHost() + ack.getPath());
                }

                @Override
                public void onFailure(WoogeenException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startRecorderButton.setEnabled(true);
                            Toast.makeText(WooGeenActivity.this, "Start recorder failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    Log.w(TAG, "Started record failed, exception: "+e.getLocalizedMessage());
                }

              });
            }
            break;
        case R.id.btStopRecorder:
            RecordOptions stopOpt = new RecordOptions();
            stopOpt.setRecorderId(WooGeenActivity.this.recorderId);
            mRoom.stopRecorder(stopOpt, new ActionCallback<RecordAck>(){

                @Override
                public void onSuccess(RecordAck ack) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WooGeenActivity.this, "Stopped recorder",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    Log.d(TAG, "Stopped recorder, location: http://" + ack.getHost() + "/" + ack.getRecorderId());
                }

                @Override
                public void onFailure(WoogeenException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WooGeenActivity.this, "Stop recorder failed",
                                    Toast.LENGTH_SHORT).show();

                        }
                    });
                    Log.w(TAG, "Stopped record failed, exception: "+e.getLocalizedMessage());
                }

            });
            break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
            android.os.Process.killProcess(android.os.Process.myPid());
        return super.onKeyDown(keyCode, event);
    }

    void showResolutionSelect(){
        for(final RemoteStream stream : mRoom.getRemoteStreams()){
            if(stream instanceof RemoteMixedStream){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run(){
                        List<Hashtable<String, Integer>> list = ((RemoteMixedStream) stream).getSupportedResolutions();
                        if(list.size() != 0){
                            String[] itemList = new String[list.size()];
                            for(int i = 0; i < list.size(); i++){
                                itemList[i] = list.get(i).get("width").toString() + " x " + list.get(i).get("height").toString();
                            }
                            AlertDialog.Builder resoSelect = new AlertDialog.Builder(WooGeenActivity.this);
                            resoSelect.setTitle("Please select the resolution of mixed stream");
                            resoSelect.setSingleChoiceItems(itemList, 0, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which){
                                    resolutionSelected = which;
                                }
                            });
                            resoSelect.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which){
                                    subscribeMixed((RemoteMixedStream) stream);
                                }
                            });
                            resoSelect.create().show();
                        }else{
                            subscribeMixed((RemoteMixedStream) stream);
                        }
                    }
                });
                break;
            }
        }

    }

    void subscribeMixed(RemoteMixedStream stream){
        Message msg = new Message();
        msg.what = MSG_SUBSCRIBE;
        msg.obj = stream;
        roomHandler.sendMessage(msg);
    }

    @Override
    public void onServerDisconnected() {
        Log.d(TAG, "onRoomDisconnected");
        runOnUiThread(new Runnable() {
            @Override
            public void run(){
                callButton.setText("Start Video");
                callButton.setEnabled(false);
                connectButton.setText("Connect");
                localAudioButton.setText("rmLocalAudio");
                localAudioButton.setEnabled(false);
                localVideoButton.setText("rmLocalVideo");
                localVideoButton.setEnabled(false);
                remoteAudioButton.setText("rmRemoteAudio");
                remoteAudioButton.setEnabled(false);
                remoteVideoButton.setText("rmRemoteVideo");
                remoteVideoButton.setEnabled(false);
                Toast.makeText(WooGeenActivity.this, "Room DisConnected",
                        Toast.LENGTH_SHORT).show();
            }
        });
        currentRemoteStream = null;
        localStreamRenderer.cleanFrame();
        remoteStreamRenderer.cleanFrame();
    }

    @Override
    public void onStreamAdded(RemoteStream remoteStream) {
        Log.d(TAG, "onStreamAdded: streamId = " + remoteStream.getId()
                + ", from " + remoteStream.getRemoteUserId());
        /*
         * we only subscribe the mix stream in default.
         */
        if (remoteStream instanceof RemoteMixedStream) {
            showResolutionSelect();
        }
    }

    @Override
    public void onStreamRemoved(RemoteStream remoteStream) {
        Log.d(TAG, "onStreamRemoved: streamId = " + remoteStream.getId());
        // If there is another remote stream subscribed, render it.
        if (currentRemoteStream != null
                && currentRemoteStream.getId().equals(remoteStream.getId())
                && mRoom.getRemoteStreams().size() > 0) {
            currentRemoteStream = mRoom.getRemoteStreams().get(0);
            try {
                if (currentRemoteStream != null)
                    currentRemoteStream.attach(remoteStreamRenderer);
            } catch (WoogeenException e) {
                e.printStackTrace();
            }
        } else {
            remoteStream.detach();
            remoteStreamRenderer.cleanFrame();
        }
        Message msg = new Message();
        msg.what = MSG_UNSUBSCRIBE;
        msg.obj = remoteStream;
        roomHandler.sendMessage(msg);
    }

    @Override
    public void onUserJoined(final User user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run(){
        Toast.makeText(WooGeenActivity.this,
                "A client named " + user.getName() + " has joined this room.",
                Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onUserLeft(final User user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run(){
                Toast.makeText(WooGeenActivity.this,
                        "A client named " + user.getName() + " has left this room.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMessageReceived(final String sender, final String message, final boolean broadcast){
        runOnUiThread(new Runnable() {
            public void run(){
                String userName = sender;
                for(User user : mRoom.getUsers()){
                    if(user.getId().equals(sender)){
                        userName = user.getName();
                        break;
                    }
                }
                remoteMessageEditText.setText(remoteMessageEditText.getText()
                        .toString() + "\n" + (broadcast ? "[Broadcast message]" : "[Private message]")
                        + userName + ":" + message);
            }
        });
    }

    @Override
    public void onRecorderAdded(String recorderId) {
        Log.d(TAG, "onRecorderAdded " + recorderId);
    }

    @Override
    public void onRecorderRemoved(String recorderId) {
        Log.d(TAG, "onRecorderRemoved" + recorderId);
    }

    @Override
    public void onRecorderContinued(String recorderId) {
        Log.d(TAG, "onRecorderReused" + recorderId);
    }

    private void initVideoStreamsViews() {
        rootEglBase = new EglBase();
        ClientContext.setApplicationContext(this, rootEglBase.getContext());

        localView = new WoogeenSampleView(this);
        localSurfaceRenderer = new WoogeenSurfaceRenderer(localView);
        localViewContainer.addView(localView);
        localStreamRenderer = localSurfaceRenderer.createVideoRenderer(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, true);

        remoteView = new WoogeenSampleView(this);
        remoteSurfaceRenderer = new WoogeenSurfaceRenderer(remoteView);
        remoteViewContainer.addView(remoteView);
        remoteStreamRenderer = remoteSurfaceRenderer.createVideoRenderer(0, 0, 100, 100, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);
    }

    private void initAudioControl(){
        try {
            Properties p = new Properties();
            InputStream s = this.getAssets().open("audio_control.properties");
            p.load(s);

            ClientContext.setAudioControlEnabled(Boolean.parseBoolean(p.getProperty("enable_audio_control")));
            ClientContext.setAudioLevelOverloud(Integer.parseInt(p.getProperty("audio_level_overloud")));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onVideoLayoutChanged(){
        Log.d(TAG, "onVideoLayoutChanged");
    }

    String getToken(String basicServer, String roomId){
        StringBuilder token = new StringBuilder("");
        URL url;
        HttpURLConnection httpURLConnection = null;
        try{
            url = new URL(basicServer + "createToken/");
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setRequestMethod("POST");

            DataOutputStream out = new DataOutputStream(httpURLConnection.getOutputStream());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", "presenter");
            jsonObject.put("username", "user");
            jsonObject.put("room", roomId.equals("") ? "" : roomId);
            out.writeBytes(jsonObject.toString());
            out.flush();
            out.close();

            if(httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK){
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                String lines;
                while((lines = reader.readLine()) != null){
                    lines = new String(lines.getBytes(), "utf-8");
                    token.append(lines);
                }
                reader.close();
            }

        }catch(MalformedURLException e){
            e.printStackTrace();
        }catch(ProtocolException e){
            e.printStackTrace();
        }catch(UnsupportedEncodingException e){
            e.printStackTrace();
        }catch(JSONException e){
            e.printStackTrace();
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            if(httpURLConnection != null){
                httpURLConnection.disconnect();
            }
        }

        return token.toString();
    }

    class RoomHandler extends Handler {
        public RoomHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_LOGIN:
                runOnUiThread(new Runnable() {
                    public void run() {
                        connectButton.setEnabled(false);
                    }
                });
                basicServerString = basicServerEditText.getText().toString();
                tokenString = getToken(basicServerString, "");
                Log.d(TAG, "token is " + tokenString);
                mRoom.join(tokenString, new ActionCallback<User>() {

                    @Override
                    public void onSuccess(User myself) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callButton.setEnabled(true);
                                callButton.setText("Start Video");
                                connectButton.setEnabled(true);
                                connectButton.setText("disconnect");
                                Toast.makeText(WooGeenActivity.this,
                                        "Room Connected",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        Log.d(TAG, "My client Id: " + myself.getId());
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                        e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                                connectButton.setEnabled(true);
                            }
                        });
                    }
                });
                break;
            case MSG_PUBLISH:
                try {
                    LocalCameraStreamParameters msp = new LocalCameraStreamParameters(true,
                            true);
                    msp.setCamera(CameraType.FRONT);
                    msp.setResolution(320, 240);
                    localStream = new LocalCameraStream(msp);
                    localStream.attach(localStreamRenderer);
                    PublishOptions option = new PublishOptions();
                    option.setMaximumVideoBandwidth(300);
                    option.setMaximumAudioBandwidth(50);
                    option.setVideoCodec(VideoCodec.VP8);
                    mRoom.publish(localStream, option, new ActionCallback<Void>(){

                        @Override
                        public void onSuccess(final Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    localAudioButton.setEnabled(true);
                                    localVideoButton.setEnabled(true);
                                    callButton.setText("Stop Video");
                                    callButton.setEnabled(true);
                                }
                            });
                         }

                         @Override
                         public void onFailure(final WoogeenException e) {
                             if (localStream != null) {
                                 localStream.close();
                                 localStreamRenderer.cleanFrame();
                                 runOnUiThread(new Runnable() {
                                     public void run() {
                                                localAudioButton.setEnabled(false);
                                                localVideoButton.setEnabled(false);
                                                callButton.setEnabled(true);
                                            }
                                  });
                                  localStream = null;
                             }
                                    e.printStackTrace();
                          }

                    });
                } catch (Exception e) {
                    if (localStream != null) {
                        localStream.close();
                        localStreamRenderer.cleanFrame();
                        runOnUiThread(new Runnable() {
                            public void run() {
                                localAudioButton.setEnabled(false);
                                localVideoButton.setEnabled(false);
                                callButton.setEnabled(true);
                            }
                        });
                        localStream = null;
                    }
                    e.printStackTrace();
                }
                break;
            case MSG_UNPUBLISH:
                if (localStream != null) {
                    mRoom.unpublish(localStream, new ActionCallback<Void>(){

                        @Override
                        public void onSuccess(Void result) {
                            localStream.close();
                            localStream = null;
                            localStreamRenderer.cleanFrame();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    localAudioButton.setEnabled(false);
                                    localVideoButton.setEnabled(false);
                                    callButton.setText("Start Video");
                                    callButton.setEnabled(true);
                                    remoteVideoButton.setEnabled(false);
                                    remoteVideoButton.setEnabled(false);
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(WoogeenException e) {
                                    Log.d(TAG, e.getMessage());
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            localAudioButton.setEnabled(false);
                                            localVideoButton.setEnabled(false);
                                            callButton.setText("Start Video");
                                            callButton.setEnabled(true);
                                        }
                                    });
                                }
                            });
                }
                break;
            case MSG_SUBSCRIBE:
                SubscribeOptions option = new SubscribeOptions();
                option.setVideoCodec(VideoCodec.VP8);
                //In our demo, we only subscribe mixed stream.
                RemoteMixedStream remoteStream = (RemoteMixedStream)msg.obj;
                if(resolutionSelected != -1 && remoteStream.getSupportedResolutions().size() >= resolutionSelected + 1){
                    option.setResolution(remoteStream.getSupportedResolutions().get(resolutionSelected).get("width"),
                                         remoteStream.getSupportedResolutions().get(resolutionSelected).get("height"));
                }
                mRoom.subscribe(remoteStream, option,
                        new ActionCallback<RemoteStream>() {

                            @Override
                            public void onSuccess(
                                    final RemoteStream remoteStream) {
                                Log.d(TAG, "onStreamSubscribed");
                                try {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            remoteAudioButton.setEnabled(true);
                                            remoteVideoButton.setEnabled(true);
                                            startRecorderButton.setEnabled(true);
                                            stopRecorderButton.setEnabled(true);
                                            Log.d(TAG, "Subscribed stream: "
                                                    + remoteStream.getId());
                                        }
                                    });
                                    if (localStream != null
                                            && remoteStream.getId().equals(
                                                    localStream.getId()))
                                        return;
                                    if(currentRemoteStream != null){
                                        currentRemoteStream.detach(remoteStreamRenderer);
                                    }
                                    currentRemoteStream = remoteStream;
                                    remoteStream.attach(remoteStreamRenderer);
                                    Log.d(TAG,
                                            "Remote stream is attached to a view.");
                                } catch (WoogeenException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                e.printStackTrace();
                            }

                        });
                break;
            case MSG_UNSUBSCRIBE:
                mRoom.unsubscribe((RemoteStream) msg.obj, new ActionCallback<Void>(){

                    @Override
                    public void onSuccess(Void result) {
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                e.printStackTrace();
                            }

                        });
                break;
            case MSG_ROOM_DISCONNECTED:
                mRoom.leave(new ActionCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                callButton.setText("Start Video");
                                callButton.setEnabled(false);
                                connectButton.setText("Connect");
                                startRecorderButton.setEnabled(false);
                                stopRecorderButton.setEnabled(false);
                                remoteAudioButton.setEnabled(false);
                                remoteVideoButton.setEnabled(false);
                            }
                        });
                    }

                    @Override
                    public void onFailure(WoogeenException e) {
                        e.printStackTrace();
                    }

                });
                break;
            case MSG_PAUSEVIDEO:
                //we only pause and play the mix and screen sharing stream in default.
                mRoom.pauseVideo(currentRemoteStream, new ActionCallback<Void>(){

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                remoteVideoButton.setText("Play Remote Video");
                                remoteVideoButton.setEnabled(true);
                                Toast.makeText(WooGeenActivity.this,
                                        "pause video success", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                        "pause video failed " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                });
                break;
            case MSG_PLAYVIDEO:
              //we only pause and play the mix stream in default.
                mRoom.playVideo(currentRemoteStream, new ActionCallback<Void>(){

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                remoteVideoButton.setText("Pause Remote Video");
                                remoteVideoButton.setEnabled(true);
                                Toast.makeText(WooGeenActivity.this,
                                        "play video success", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                        "play video failed " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                });
                break;
            case MSG_PAUSEAUDIO:
                //we only pause and play the mix stream in default.
                mRoom.pauseAudio(currentRemoteStream, new ActionCallback<Void>(){

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                remoteAudioButton.setText("Play Remote Audio");
                                remoteAudioButton.setEnabled(true);
                                Toast.makeText(WooGeenActivity.this,
                                        "pause audio success", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                        "pause audio failed " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                });
                break;
            case MSG_PLAYAUDIO:
              //we only pause and play the mix stream in default.
                mRoom.playAudio(currentRemoteStream, new ActionCallback<Void>(){

                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                remoteAudioButton.setText("Pause Remote Audio");
                                remoteAudioButton.setEnabled(true);
                                Toast.makeText(WooGeenActivity.this,
                                        "play audio success", Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final WoogeenException e) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                        "play audio failed " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }

                });
                break;
            }
            super.handleMessage(msg);
        }
    }
}
