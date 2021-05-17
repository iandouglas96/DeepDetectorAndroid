package com.iandm.astrobee.deepdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.util.TimingLogger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Subscriber;
import org.ros.message.MessageListener;

import sensor_msgs.Image;

public class ROSInterface implements NodeMain {
    private ConnectedNode connectedNode;
    private Subscriber<sensor_msgs.Image> imgSub;
    private DeepDetectorCallback imgReadyCb;

    private MessageListener<sensor_msgs.Image> imageMessageListener =
            new MessageListener<Image>() {
        @Override
        public void onNewMessage(sensor_msgs.Image img_msg) {
            Log.i(DeepDetector.TAG, "Image Received");
            Bitmap img = Bitmap.createBitmap(img_msg.getWidth(),
                    img_msg.getHeight(), Bitmap.Config.ARGB_8888);
            ChannelBuffer img_data = img_msg.getData();
            int ind = 0;
            try {
                //Copy data from image message buffer to Bitmap object for java
                int[] pixels = new int[img.getHeight()*img.getWidth()];
                int w = img.getWidth();
                int h = img.getHeight();
                byte[] bgr = new byte[3];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        img_data.getBytes(ind, bgr, 0, 3);
                        ind += 3;
                        pixels[y*w + x] = Color.argb(255,
                                bgr[2]&0xff, bgr[1]&0xff, bgr[0]&0xff);
                    }
                }
                img.setPixels(pixels, 0, w, 0, 0, w, h);

                imgReadyCb.callback(img);
            } catch (IndexOutOfBoundsException e) {
                Log.i(DeepDetector.TAG, "Mismatch in expected image data");
            }
        }
    };

    public ROSInterface(DeepDetectorCallback cb) {
        imgReadyCb = cb;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("deep_detector_node");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        Log.i(DeepDetector.TAG, "Starting DeepDetector ROS and setting up the pubs and subs.");

        this.connectedNode = connectedNode;
        NameResolver resolver = connectedNode.getResolver();
        imgSub = connectedNode.newSubscriber(resolver.resolve("hw/cam_nav"),
                sensor_msgs.Image._TYPE);
        imgSub.addMessageListener(imageMessageListener);
    }

    @Override
    public void onShutdown(Node node) {
        Log.i(DeepDetector.TAG, "DeepDetector ROS shutdown.");
    }

    @Override
    public void onShutdownComplete(Node node) {
        Log.i(DeepDetector.TAG, "DeepDetector ROS shutdown complete.");
    }

    @Override
    public void onError(Node node, Throwable throwable) {
        Log.i(DeepDetector.TAG, "DeepDetector ROS error.");
    }
}
