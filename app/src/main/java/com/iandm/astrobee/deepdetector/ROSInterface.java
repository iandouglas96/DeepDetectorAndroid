package com.iandm.astrobee.deepdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.util.TimingLogger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;
import org.ros.message.MessageListener;

import java.io.IOException;

import vision_msgs.Detection2DArray;

public class ROSInterface implements NodeMain {
    private ConnectedNode connectedNode;
    private Subscriber<sensor_msgs.Image> imgSub;
    private Publisher<vision_msgs.Detection2DArray> detectionPub;
    private Publisher<sensor_msgs.Image> detectionVizPub;
    private DeepDetectorCallback imgReadyCb;

    private MessageListener<sensor_msgs.Image> imageMessageListener =
            new MessageListener<sensor_msgs.Image>() {
        @Override
        public void onNewMessage(sensor_msgs.Image img_msg) {
            Log.i(DeepDetector.TAG, "Image Received");
            Bitmap img = Bitmap.createBitmap(img_msg.getWidth(),
                    img_msg.getHeight(), Bitmap.Config.ARGB_8888);
            ChannelBuffer img_data = img_msg.getData();
            try {
                long startTime = System.nanoTime();
                //Copy data from image message buffer to Bitmap object for java
                int[] pixels = new int[img.getHeight()*img.getWidth()];
                int w = img.getWidth();
                int h = img.getHeight();

                if (img_msg.getEncoding().equals("bgr8")) {
                    byte[] byte_buf = new byte[pixels.length*3];
                    img_data.getBytes(0, byte_buf, 0, byte_buf.length);
                    for (int ind=0; ind<pixels.length; ind++) {
                        int byte_ind = ind*3;
                        pixels[ind] = Color.rgb(
                                byte_buf[byte_ind+2] & 0xff, byte_buf[byte_ind+1] & 0xff, byte_buf[byte_ind] & 0xff);
                    }
                } else if (img_msg.getEncoding().equals("mono8")) {
                    byte[] byte_buf = new byte[pixels.length];
                    img_data.getBytes(0, byte_buf, 0, byte_buf.length);
                    for (int ind=0; ind<pixels.length; ind++) {
                        pixels[ind] = Color.rgb(
                                byte_buf[ind] & 0xff, byte_buf[ind] & 0xff, byte_buf[ind] & 0xff);
                    }
                } else {
                    Log.i(DeepDetector.TAG, "Unsupported image format: " + img_msg.getEncoding());
                    return;
                }
                img.setPixels(pixels, 0, w, 0, 0, w, h);
                long endTime = System.nanoTime();
                Log.i(DeepDetector.TAG, String.format("Image input proc: %f sec", (endTime-startTime)/1.e9));

                long timeNs = img_msg.getHeader().getStamp().totalNsecs();
                imgReadyCb.callback(img, timeNs);
            }
            catch (IndexOutOfBoundsException e) {
                Log.i(DeepDetector.TAG, "Mismatch in expected image data");
            }
        }
    };

    public ROSInterface(DeepDetectorCallback cb) {
        imgReadyCb = cb;
    }

    public void publishDetections(DetectionSet detections, long timeNs) {
        Time stamp = Time.fromNano(timeNs);
        vision_msgs.Detection2DArray detMsg =
                detections.exportROS(connectedNode.getTopicMessageFactory());
        detMsg.getHeader().setStamp(stamp);
        detectionPub.publish(detMsg);

        try {
            long startTime = System.nanoTime();
            sensor_msgs.Image detVizMsg =
                    detections.exportVizROS(connectedNode.getTopicMessageFactory());
            long endTime = System.nanoTime();
            Log.i(DeepDetector.TAG, String.format("Output viz proc: %f sec", (endTime-startTime)/1.e9));
            detVizMsg.getHeader().setStamp(stamp);
            detectionVizPub.publish(detVizMsg);
        } catch (IOException e) {
            Log.i(DeepDetector.TAG, "Failed to export visualization image");
        }
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
        detectionPub = connectedNode.newPublisher(resolver.resolve("loc/sm/features"),
                vision_msgs.Detection2DArray._TYPE);
        detectionVizPub = connectedNode.newPublisher(resolver.resolve("detections_viz"),
                sensor_msgs.Image._TYPE);
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
