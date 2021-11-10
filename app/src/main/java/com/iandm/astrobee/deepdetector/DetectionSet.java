package com.iandm.astrobee.deepdetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.iandm.astrobee.deepdetector.ml.IssModelSmall;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.MessageFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import vision_msgs.Detection2D;
import vision_msgs.Detection2DArray;
import vision_msgs.ObjectHypothesisWithPose;

public class DetectionSet {
    private static final int[] PALETTE = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN,
        Color.MAGENTA, Color.YELLOW};
    private static final Hashtable<String, Integer> paletteLut = new Hashtable<>();

    static {
        paletteLut.put("Laptop", 0);
        paletteLut.put("Camera", 1);
        paletteLut.put("Handrail", 2);
        paletteLut.put("Light", 3);
        paletteLut.put("Window", 4);
        paletteLut.put("Hatch", 5);
        paletteLut.put("Express", 6);
        paletteLut.put("Vent", 7);
    }

    private class Detection {
        public RectF boundingBox;
        public String category;
        public float score;
        public Detection(RectF bb, String c, float s) {
            boundingBox = bb;
            category = c;
            score = s;
        }

        public Detection2D exportROS(MessageFactory messageFactory) {
            Detection2D rosDet = messageFactory.newFromType(Detection2D._TYPE);
            ObjectHypothesisWithPose rosDetPose =
                    messageFactory.newFromType(ObjectHypothesisWithPose._TYPE);
            rosDetPose.setId(paletteLut.get(category));
            rosDetPose.setScore(score);
            rosDet.setResults(Collections.singletonList(rosDetPose));
            rosDet.getBbox().getCenter().setX((boundingBox.left+boundingBox.right)/2.);
            rosDet.getBbox().getCenter().setY((boundingBox.top+boundingBox.bottom)/2.);
            rosDet.getBbox().setSizeX(boundingBox.right - boundingBox.left);
            rosDet.getBbox().setSizeY(boundingBox.bottom - boundingBox.top);

            return rosDet;
        }
    }
    private final List<Detection> detectionsFiltered;
    private final Bitmap annotatedImg;

    public DetectionSet(List<IssModelSmall.DetectionResult> detections, Bitmap img) {
        annotatedImg = img.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotatedImg);
        detectionsFiltered = new ArrayList<>();

        for (IssModelSmall.DetectionResult d : detections) {
            RectF location = d.getLocationAsRectF();
            RectF ros_location = new RectF();
            String category = d.getCategoryAsString();
            float score = d.getScoreAsFloat();

            if (score > 0.3 && (location.bottom-location.top)*(location.right-location.left) > 50) {
                if (paletteLut.get(category) == null) {
                    Log.e(DeepDetector.TAG, "Detector outputted class that doesn't exist.");
                    continue;
                }

                //Rescale
                location.bottom *= img.getHeight()/320.;
                location.top *= img.getHeight()/320.;
                location.left *= img.getWidth()/320.;
                location.right *= img.getWidth()/320.;

                int color = PALETTE[paletteLut.get(category) % PALETTE.length];

                //Center detection for ROS
                ros_location.set(location);
                ros_location.bottom -= img.getHeight()/2.;
                ros_location.top -= img.getHeight()/2.;
                ros_location.left -= img.getWidth()/2.;
                ros_location.right -= img.getWidth()/2.;

                detectionsFiltered.add(new Detection(ros_location, category, score));

                Paint paint = new Paint();
                paint.setColor(color);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setTextSize(20);
                canvas.drawRect(location, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText(category, location.left, location.bottom, paint);
            }
        }
    }

    public Bitmap visualize() {
        return annotatedImg;
    }

    public Detection2DArray exportROS(MessageFactory messageFactory) {
        Detection2DArray rosDetArray =
                messageFactory.newFromType(Detection2DArray._TYPE);
        for (Detection d : detectionsFiltered) {
            Detection2D rosDet = d.exportROS(messageFactory);
            rosDetArray.getDetections().add(rosDet);
        }

        return rosDetArray;
    }

    public sensor_msgs.Image exportVizROS(MessageFactory messageFactory) throws IOException {
        sensor_msgs.Image rosImg = messageFactory.newFromType(sensor_msgs.Image._TYPE);
        rosImg.setEncoding("bgr8");
        int w = annotatedImg.getWidth();
        int h = annotatedImg.getHeight();
        rosImg.setHeight(h);
        rosImg.setWidth(w);
        rosImg.setStep(3*w);

        int[] pixels = new int[h*w];
        annotatedImg.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] imgBytes = new byte[3*h*w];

        for (int ind=0; ind<pixels.length; ind++) {
            imgBytes[3*ind] = (byte)(pixels[ind] & 0xff);
            imgBytes[3*ind+1] = (byte)((pixels[ind] >> 8) & 0xff);
            imgBytes[3*ind+2] = (byte)((pixels[ind] >> 16) & 0xff);
        }

        ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
        stream.write(imgBytes);
        rosImg.setData(stream.buffer().copy());
        stream.buffer().clear();

        return rosImg;
    }
}
