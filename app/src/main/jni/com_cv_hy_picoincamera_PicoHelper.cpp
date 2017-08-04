//
// Created by gzs10692 on 2017/8/2.
//
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <malloc.h>
#include <opencv2\opencv.hpp>
#include <opencv2\highgui\highgui_c.h>
extern "C" {
#include "picornt.h"
}
#include "com_cv_hy_picoincamera_PicoHelper.h"



#include <android/log.h>
//修改日志tag中的值
#define LOG_TAG "JNI Main"
//日志显示的等级
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define GET_ARRAY_LEN(array,len){len = (sizeof(array) / sizeof(array[0]));}
size_t _msize(void *);
#ifdef __GNUC__
#include <time.h>
float getticks()
{
    struct timespec ts;

    if(clock_gettime(CLOCK_MONOTONIC, &ts) < 0)
        return -1.0f;

    return ts.tv_sec + 1e-9f*ts.tv_nsec;
}
#else
#include <windows.h>
float getticks()
{
	static double freq = -1.0;
	LARGE_INTEGER lint;

	if(freq < 0.0)
	{
		if(!QueryPerformanceFrequency(&lint))
			return -1.0f;

		freq = lint.QuadPart;
	}

	if(!QueryPerformanceCounter(&lint))
		return -1.0f;

	return (float)( lint.QuadPart/freq );
}
#endif

void *cascade = 0;
int minsize=60;
int maxsize=1024;

float angle=0.0f;

float scalefactor= 1.1f;
float stridefactor= 0.1f;

float qthreshold= 5.0f;

int usepyr=1;
int noclustering=0;

void process_image(IplImage *frame, int draw);

JNIEXPORT void JNICALL Java_com_cv_hy_picoincamera_PicoHelper_detect
        (JNIEnv * env, jclass tis, jlong originObj,jstring filename,jlong outputObj)
{
    jboolean iscopy;
    const char *mfile = env->GetStringUTFChars( filename, &iscopy);

    /*获取文件大小*/
    FILE * file=fopen(mfile,"rb");
    env->ReleaseStringUTFChars( filename, mfile);
    if(!file)
    {
        LOGI("# cannot read cascade from '%s'\n", mfile);
        return ;
    }
    fseek(file,0L,SEEK_END);
    int size=ftell(file);
    fseek(file,0L,SEEK_SET);
    cascade=(void *)malloc(size);
    if(!cascade || size!=fread(cascade, 1, size, file))
        return ;

    fclose(file);

cv::Mat* imgMat=(cv::Mat*)originObj;

IplImage img;
img =IplImage(*imgMat);

process_image(&img,1);
//const char *outfile = env->GetStringUTFChars( outputFile, &iscopy);//测试的时候用作保存地址
//cvSaveImage(outfile, &img, 0);
//env->ReleaseStringUTFChars( outputFile, outfile);

*(cv::Mat*)outputObj=cv::cvarrToMat(&img, true);//将传过来的形参地址改变成指向检测过后的对象地址

//LOGI("Mat descMat address and dims:%lld and %d and rows %d and cols %d\n",jlong(outputObj),((cv::Mat*)outputObj)->dims,((cv::Mat*)outputObj)->rows,((cv::Mat*)outputObj)->cols);

imgMat->release();

free(cascade);

}

void process_image(IplImage* frame, int draw)
{
//    LOGI("# load image 'width:%d and height:%d'\n", frame->width,frame->height);
    int i, j;
    float t;

    uint8_t* pixels;
    int nrows, ncols, ldim;

    #define MAXNDETECTIONS 2048
    int ndetections;
    float rcsq[4*MAXNDETECTIONS];

    static IplImage* gray = 0;
    static IplImage* pyr[5] = {0, 0, 0, 0, 0};
    if(!pyr[0])
    {
        gray = cvCreateImage(cvSize(frame->width, frame->height), frame->depth, 1);

        pyr[0] = gray;
        pyr[1] = cvCreateImage(cvSize(frame->width/2, frame->height/2), frame->depth, 1);
        pyr[2] = cvCreateImage(cvSize(frame->width/4, frame->height/4), frame->depth, 1);
        pyr[3] = cvCreateImage(cvSize(frame->width/8, frame->height/8), frame->depth, 1);
        pyr[4] = cvCreateImage(cvSize(frame->width/16, frame->height/16), frame->depth, 1);
    }

    // get grayscale image
    if(frame->nChannels == 3){
        cvCvtColor(frame, gray, CV_RGB2GRAY);
    }
    else{
        cvCopy(frame, gray, 0);
    }

    // perform detection with the pico library
//    t = getticks();

    if(usepyr)
    {
        int nd;

        //
        pyr[0] = gray;

        pixels = (uint8_t*)pyr[0]->imageData;
        nrows = pyr[0]->height;
        ncols = pyr[0]->width;
        ldim = pyr[0]->widthStep;

        ndetections = find_objects(rcsq, MAXNDETECTIONS, cascade, angle, pixels, nrows, ncols, ldim, scalefactor, stridefactor, MAX(16, minsize), MIN(128, maxsize));

        for(i=1; i<5; ++i)
        {
            cvResize(pyr[i-1], pyr[i], CV_INTER_LINEAR);

            pixels = (uint8_t*)pyr[i]->imageData;
            nrows = pyr[i]->height;
            ncols = pyr[i]->width;
            ldim = pyr[i]->widthStep;

            nd = find_objects(&rcsq[4*ndetections], MAXNDETECTIONS-ndetections, cascade, angle, pixels, nrows, ncols, ldim, scalefactor, stridefactor, MAX(64, minsize>>i), MIN(128, maxsize>>i));

            for(j=ndetections; j<ndetections+nd; ++j)
            {
                rcsq[4*j+0] = (1<<i)*rcsq[4*j+0];
                rcsq[4*j+1] = (1<<i)*rcsq[4*j+1];
                rcsq[4*j+2] = (1<<i)*rcsq[4*j+2];
            }

            ndetections = ndetections + nd;
        }
    }
    else
    {
        //
        pixels = (uint8_t*)gray->imageData;
        nrows = gray->height;
        ncols = gray->width;
        ldim = gray->widthStep;

        ndetections = find_objects(rcsq, MAXNDETECTIONS, cascade, angle, pixels, nrows, ncols, ldim, scalefactor, stridefactor, minsize, MIN(nrows, ncols));
    }

    if(!noclustering){
        ndetections = cluster_detections(rcsq, ndetections);
    }

//    t = getticks() - t;
//    LOGI("JNI time: %f s",t);
    // if the flag is set, draw each detection
    if(draw)
        for(i=0; i<ndetections; ++i)
            if(rcsq[4*i+3]>=qthreshold) // check the confidence threshold
                cvCircle(frame, cvPoint(rcsq[4*i+1], rcsq[4*i+0]), rcsq[4*i+2]/2, CV_RGB(255, 0, 0), 4, 8, 0); // we draw circles here since height-to-width ratio of the detected face regions is 1.0f

}