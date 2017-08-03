package com.cv.hy.picoincamera;

import android.content.res.AssetManager;

/**
 * Created by gzs10692 on 2017/8/3.
 */

public class PicoHelper {
    static {
        System.loadLibrary("OpenCV");
    }

    public static native void detect(long originObj, String configPath,long outputObj);
}
