package com.iandm.astrobee.deepdetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import org.ros.address.InetAddressFactory;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.net.URI;

interface DeepDetectorCallback {
    void callback(Bitmap img);
}

public class DeepDetector extends AppCompatActivity {
    private NodeMainExecutor nodeMainExecutor = null;
    private NodeConfiguration nodeConfiguration = null;
    public ROSInterface rosInterface = null;

    private TFLiteObjectDetector detector;

    private ImageView imageView;
    private Bitmap lastImg;

    public static final String TAG =
            "deep_det";

    //Commands from user
    public static final String TURN_ON_DETECTOR =
            "com.iandm.astrobee.deepdetector.TURN_ON_DETECTOR";
    public static final String TURN_OFF_DETECTOR =
            "com.iandm.astrobee.deepdetector.TURN_OFF_DETECTOR";

    public static final String IMAGE_RECEIVED =
            "com.iandm.astrobee.deepdetector.IMAGE_RECEIVED";

    private DeepDetectorCallback imageReadyCb = new DeepDetectorCallback() {
        @Override
        public void callback(Bitmap img) {
            lastImg = img;

            //Sent Intent to tell main thread that we got new image
            Intent intent = new Intent();
            intent.setAction(IMAGE_RECEIVED);
            sendBroadcast(intent);
        }
    };

    private BroadcastReceiver processNewImage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DetectionSet detectionSet = detector.Infer(lastImg);
            rosInterface.publishDetections(detectionSet);

            //In-app visualization
            Bitmap annotatedImg = detectionSet.visualize(lastImg);
            imageView.setImageBitmap(annotatedImg);
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);

        //Initialize model
        detector = new TFLiteObjectDetector(this);

        //GUI stuff
        registerReceiver(processNewImage, new IntentFilter(IMAGE_RECEIVED));
        setContentView(R.layout.image_preview);
        imageView = (ImageView)findViewById(R.id.imageView);

        startROS();
    }

    @Override
    protected void onDestroy() {
        stopROS();
        super.onDestroy();
    }

    void startROS() {
        try {
            Log.i(TAG, "Trying to start ROS");

            String uri_str = "http://llp:11311";
            URI masterURI = new URI(uri_str);

            Log.i(TAG, "Host is " + masterURI.getHost());
            Log.i(TAG, "Port is " + masterURI.getPort());

            nodeConfiguration = NodeConfiguration
                    .newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration.setMasterUri(masterURI);

            nodeMainExecutor = DefaultNodeMainExecutor.newDefault();

            Log.i(TAG, "Create ROSInterface");
            rosInterface = new ROSInterface(imageReadyCb);

            nodeMainExecutor.execute(rosInterface, nodeConfiguration);
            Log.i(TAG, "Started ROS");

        } catch (Exception e) {
            Log.i(TAG, "Failed to start ROS: " + e.getMessage());
            // Socket problem
        }
    }

    void stopROS() {
        try {
            Log.i(TAG, "Trying to stop ROS");

            nodeConfiguration = null;
            nodeMainExecutor = null;
            rosInterface = null;

        } catch (Exception e) {
            Log.i(TAG, "Failed to stop ROS: " + e.getMessage());
            // Socket problem
        }
        Log.i(TAG, "Stopped ROS");
    }
}
