package com.iandm.astrobee.deepdetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.iandm.astrobee.deepdetector.ml.Detect;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class DetectionSet {
    private static final int[] PALETTE = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN,
        Color.MAGENTA, Color.YELLOW};
    private static int nextPaletteInd = 0;
    private static final Hashtable<String, Integer> paletteLut = new Hashtable<>();

    private final List<Detect.DetectionResult> detections;

    public DetectionSet(List<Detect.DetectionResult> d) {
        detections = d;
    }

    public Bitmap visualize(Bitmap orig_img) {
        Bitmap annotated_img = orig_img.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotated_img);

        for (Detect.DetectionResult d : detections) {
            RectF location = d.getLocationAsRectF();
            String category = d.getCategoryAsString();
            float score = d.getScoreAsFloat();

            if (score > 0.5 && (location.top-location.bottom)*(location.left-location.right) > 50) {
                //Rescale
                location.bottom *= orig_img.getHeight()/300.;
                location.top *= orig_img.getHeight()/300.;
                location.left *= orig_img.getWidth()/300.;
                location.right *= orig_img.getWidth()/300.;

                Integer color = paletteLut.get(category);
                if (color == null) {
                    paletteLut.put(category, PALETTE[nextPaletteInd % PALETTE.length]);
                    color = paletteLut.get(category);
                    nextPaletteInd++;
                }

                Paint paint = new Paint();
                paint.setColor(color);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setTextSize(30);
                canvas.drawRect(location, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText(category, location.left, location.top, paint);
            }
        }

        return annotated_img;
    }
}
