package com.iandm.astrobee.deepdetector;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

public class Undistorter {
    private boolean haveCalib = false;
    private boolean haveCv = false;
    private double lastD = -1;
    private Mat mapX, mapY;

    public Undistorter() {
        if (OpenCVLoader.initDebug()) {
            Log.i(DeepDetector.TAG, "OpenCV Loaded");
            haveCv = true;
        } else {
            Log.e(DeepDetector.TAG, "OpenCV Loaded Unsuccessfully, Cannot Start");
        }
    }

    private double[] matMul(double[] mat, double[] vec) {
        double[] retval = new double[3];
        retval[0] = mat[0]*vec[0] + mat[1]*vec[1] + mat[2]*vec[2];
        retval[1] = mat[3]*vec[0] + mat[4]*vec[1] + mat[5]*vec[2];
        retval[2] = mat[6]*vec[0] + mat[7]*vec[1] + mat[8]*vec[2];
        return retval;
    }

    public void setCalib(int w, int h, double[] K, String dist_type, double[] D) {
        if (!dist_type.equals("fov") || D.length != 1) {
            Log.e(DeepDetector.TAG, "Undistorter currently only supports FOV model");
            return;
        }
        if (D[0] == lastD) {
            return; // Already loaded calib, don't need to do so again
        }
        lastD = D[0];
        Log.i(DeepDetector.TAG, "Received Camera Calib, generating undistortion maps");

        mapX = Mat.zeros(new Size(w, h), CvType.CV_32F);
        mapY = Mat.zeros(new Size(w, h), CvType.CV_32F);

        // Matrix for camera we want to undistort to
        double[] K_undist = K.clone();
        K_undist[2] = w/2.;
        K_undist[5] = h/2.;

        // Convert to mat so we can invert
        Mat K_undist_mat = Mat.eye(new Size(3, 3), CvType.CV_64F);
        K_undist_mat.put(0, 0, K_undist);
        double[] K_undist_inv = new double[9];
        K_undist_mat.inv().get(0, 0, K_undist_inv);

        // Intermediate constants
        double coeff1 = 1/D[0];
        double coeff2 = 2 * Math.tan(D[0]/2);
        // Buffers
        float[] rowX = new float[w];
        float[] rowY = new float[w];
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                double[] pt_arr = {x, y, 1};
                // Just inv(K_mat) @ pt
                pt_arr = matMul(K_undist_inv, pt_arr);
                // norm(norm_coord[:2])
                double ru = Math.sqrt(pt_arr[0]*pt_arr[0] + pt_arr[1]*pt_arr[1]);
                double rd = Math.atan(ru * coeff2) * coeff1;
                double conv = 1;
                if (ru > 1e-5) {
                    conv = rd / ru;
                }
                // scaled_coord = norm_coord * conv
                pt_arr[0] *= conv;
                pt_arr[1] *= conv;
                pt_arr = matMul(K, pt_arr);
                // store data
                rowX[x] = (float)pt_arr[0];
                rowY[x] = (float)pt_arr[1];
            }
            // Put is slow, so buffer up each line
            mapX.put(y, 0, rowX);
            mapY.put(y, 0, rowY);
        }
        Log.i(DeepDetector.TAG, "Undistortion maps built");

        haveCalib = true;
    }

    public boolean isHaveCalib() {
        return haveCalib && haveCv;
    }

    public Bitmap undistort(Bitmap dist_img, boolean debayer) {
        Mat mat = new Mat();
        Utils.bitmapToMat(dist_img, mat);

        if (debayer) {
            // Make single-channel
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
            // Debayer
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BayerGB2RGB);
        }

        // Actually do the undistortion
        Imgproc.remap(mat, mat, mapX, mapY, Imgproc.INTER_LINEAR);

        Bitmap undist_img = Bitmap.createBitmap(mat.cols(),
                mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, undist_img);
        mat.release();
        return undist_img;
    }
}
