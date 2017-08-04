package com.cv.hy.picoincamera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.opencv.core.Core.BORDER_ISOLATED;
import static org.opencv.core.Core.BORDER_REFLECT;
import static org.opencv.core.Core.BORDER_REFLECT_101;
import static org.opencv.core.Core.BORDER_TRANSPARENT;
import static org.opencv.core.Core.FLAGS_EXPAND_SAME_NAMES;
import static org.opencv.core.Core.FONT_HERSHEY_COMPLEX;
import static org.opencv.core.Core.FONT_HERSHEY_DUPLEX;
import static org.opencv.core.Core.FONT_HERSHEY_SCRIPT_COMPLEX;
import static org.opencv.core.Core.FONT_HERSHEY_SIMPLEX;
import static org.opencv.core.Core.getTickCount;
import static org.opencv.core.Core.getTickFrequency;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener, View.OnClickListener {

    private String configPath;

    private CameraBridgeViewBase openCVCameraView;
    private Button frontBtn;
    private Button backBtn;
    private Mat grayscaleImage;
    private boolean isFrontCamera=true;
    private Point timeTextPoint = new Point(10, 120);
    private Point sizeTextPoint = new Point(10, 50);
    private Scalar frameScalar = new Scalar(255, 0, 0, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        openCVCameraView = (CameraBridgeViewBase) findViewById(R.id.java_surface_view);
        openCVCameraView.setCvCameraViewListener(this);
        openCVCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);

        frontBtn = (Button) findViewById(R.id.front_btn);
        backBtn = (Button) findViewById(R.id.back_btn);
        frontBtn.setOnClickListener(this);
        backBtn.setOnClickListener(this);
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
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.front_btn:
                openCVCameraView.setVisibility(SurfaceView.GONE);
                openCVCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
                openCVCameraView.setVisibility(SurfaceView.VISIBLE);
                openCVCameraView.enableView();
                isFrontCamera=true;
                break;
            case R.id.back_btn:
                openCVCameraView.setVisibility(SurfaceView.GONE);
                openCVCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
                openCVCameraView.setVisibility(SurfaceView.VISIBLE);
                openCVCameraView.enableView();
                isFrontCamera=false;
                break;

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
        if (isFrontCamera) {
            Core.flip(inputFrame,inputFrame,1);
        }
        Point center = new Point(inputFrame.cols() / 2, inputFrame.rows() / 2);
        double angle= -90;
        Mat rotateMat = Imgproc.getRotationMatrix2D(center, angle, 1.4);//缩放比例为1.4
        Imgproc.warpAffine(inputFrame, inputFrame, rotateMat, inputFrame.size(), 1, 0,frameScalar );

        Imgproc.cvtColor(inputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);
        //Mat imageMat = new Mat();//这里会出现内存泄漏，因为不停的new，并且当作返回变量，导致无法被GC

//        Log.i("Main Mat size:","cols and rows:"+grayscaleImage.cols()+" "+ grayscaleImage.rows());
        PicoHelper.detect(grayscaleImage.nativeObj,configPath,inputFrame.nativeObj);

        long endTime = getTickCount();
//        Log.i("Main detectTime:","              "+ String.valueOf((endTime - startTime) / getTickFrequency()));
        Imgproc.putText(inputFrame, String.valueOf((endTime - startTime) / getTickFrequency()), timeTextPoint, FONT_HERSHEY_COMPLEX, 2, frameScalar);
        Imgproc.putText(inputFrame, String.valueOf(inputFrame.size()), sizeTextPoint, FONT_HERSHEY_DUPLEX, 2, frameScalar);
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
