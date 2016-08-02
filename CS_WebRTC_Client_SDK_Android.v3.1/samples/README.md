# Intel Collaboration Suite for WebRTC Android Samples.

## Run samples with Android Studio IDE

    1.  Open Android Studio
    2.  Import sample project by "Open an existing Android Studio project"
    3.  Build and run samples

## Run samples with Gradle in the console

    > cd /path/to/dist/samples/folder
    > ./gradlew assembleRelease

You will get the apk file in build/outputs/apk folder.

## Conference Sample

Conference sample sends HTTP post requests to basic example server to fetch the token, then connects to Conference Server. After connecting to the Conference Server, it will be able to publish streams captured from a camera or subscribe streams from remote sides. By default, the sample subscribes mixed stream from conference server.

### SSL/TLS
Basic example server can also accept HTTPS requests, to do this, please don't forget to replace the conference server's certificate with a trusted one. Or if you wouldn't like to use HTTPS/SSL, you should disable the ssl at the server side by changing ```config.erizoController.ssl``` in ```<conference server folder>/etc/woogeen_config.js``` to ```false```.

## P2P Sample

P2P sample connects to PeerServer and then it can start a session with other clients connected to the PeerServer with Intel CS for WebRTC client SDK.
