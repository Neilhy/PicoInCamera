package com.cv.hy.picoincamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.opencv.core.Core.getTickCount;
import static org.opencv.core.Core.getTickFrequency;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener
{

    private String configPath;

    private CameraBridgeViewBase openCVCameraView;
    private Mat grayscaleImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        openCVCameraView = new JavaCameraView(this, -1);
        setContentView(openCVCameraView);
        openCVCameraView.setCvCameraViewListener(this);

    }
    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initDebug();
        init();
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (openCVCameraView != null) {
            openCVCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        grayscaleImage = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        grayscaleImage.release();
    }

    @Override
    public Mat onCameraFrame(Mat inputFrame) {
        long startTime = getTickCount();
        Imgproc.cvtColor(inputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);
        //Mat imageMat = new Mat();//这里会出现内存泄漏，因为不停的new，并且当作返回变量，导致无法被GC

        Log.i("Main grayscale addr:", String.valueOf(grayscaleImage.nativeObj)+"  dims:"+grayscaleImage.dims()+" cols and rows:"+grayscaleImage.cols()+" "+ grayscaleImage.rows());
        PicoHelper.detect(grayscaleImage.nativeObj,configPath,inputFrame.nativeObj);
        Log.i("Main output after obj","addr:"+ String.valueOf(inputFrame.nativeObj)+"  dims:"+inputFrame.dims()+" cols and rows:"+inputFrame.cols()+" "+ inputFrame.rows());

        long endTime = getTickCount();
        Log.i("Main detectTime:", String.valueOf((endTime - startTime) / getTickFrequency()));
        return inputFrame;
    }
    private void init() {
        try {
            InputStream is = getResources().openRawResource(R.raw.facefinder);
            File finderDir = getDir("finder", Context.MODE_PRIVATE);
            File finderFile = new File(finderDir, "facefinder");
            FileOutputStream os = new FileOutputStream(finderFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            configPath = finderFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        openCVCameraView.enableView();

    }
    private Bitmap getSmallBitmap(String filePath) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, 600, 800);
//        options.inSampleSize = calculateInSampleSize(options, 200, 266);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(filePath, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }
}
