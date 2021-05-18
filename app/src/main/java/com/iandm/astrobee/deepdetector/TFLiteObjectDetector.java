package com.iandm.astrobee.deepdetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import com.iandm.astrobee.deepdetector.ml.Detect;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.IOException;

public class TFLiteObjectDetector {
    Detect model;

    public TFLiteObjectDetector(Context context) {
        Model.Options options;
        CompatibilityList compatibilityList = new CompatibilityList();

        if (compatibilityList.isDelegateSupportedOnThisDevice()) {
            options = new Model.Options.Builder().setDevice(Model.Device.GPU).build();
            Log.i(DeepDetector.TAG, "Found GPU, accelerating inference");
        } else {
            options = new Model.Options.Builder().setNumThreads(4).build();
            Log.i(DeepDetector.TAG, "No GPU found");
        }

        try {
            model = Detect.newInstance(context, options);
        } catch (IOException e) {
            Log.i(DeepDetector.TAG, "Error creating TFLite model");
        }
    }

    public DetectionSet Infer(Bitmap img) {
        Bitmap scaledImg = Bitmap.createScaledBitmap(img, 300, 300, true);
        TensorImage tfImg = TensorImage.fromBitmap(scaledImg);
        Detect.Outputs outputs = model.process(tfImg);

        return new DetectionSet(outputs.getDetectionResultList());
    }
}