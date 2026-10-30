#include <jni.h>
#include <stdint.h>
#include "encoder_core.h"
#include <libyuv.h>
static void fail(JNIEnv *env, const char *message) {
    (*env)->ThrowNew(env,(*env)->FindClass(env,"java/io/IOException"),message);
}
JNIEXPORT jlong JNICALL Java_DisplayCapture_openEncoder(JNIEnv *env,jclass cls,jstring output,jint width,jint height,jint fps) {
    (void)cls;
    if(!output) {fail(env,"Missing encoder output"); return 0;}
    const char *path=(*env)->GetStringUTFChars(env,output,NULL);
    if(!path) return 0;
    Encoder *e=encoder_open(path,width,height,fps);
    (*env)->ReleaseStringUTFChars(env,output,path);
    if(!e) fail(env,"Cannot initialize native AVC encoder/output");
    return (jlong)(intptr_t)e;
}
JNIEXPORT void JNICALL Java_DisplayCapture_encodeFrame(JNIEnv *env,jclass cls,jlong handle,
    jobject buffer,jint stamp,jint force) {
    (void)cls;
    if(!handle || !buffer || (*env)->GetDirectBufferCapacity(env,buffer)!=(jlong)encoder_width((Encoder *)(intptr_t)handle)*encoder_height((Encoder *)(intptr_t)handle)*4 || force<0 || force>1) {
        fail(env,"Invalid native encoder frame"); return;
    }
    const unsigned char *rgba=(*env)->GetDirectBufferAddress(env,buffer);
    if(encoder_frame((Encoder *)(intptr_t)handle,rgba,(uint32_t)stamp,force)) fail(env,"Native AVC frame/output failed");
}
JNIEXPORT void JNICALL Java_DisplayCapture_closeEncoder(JNIEnv *env,jclass cls,jlong handle) {
    (void)env; (void)cls; encoder_close((Encoder *)(intptr_t)handle);
}
JNIEXPORT void JNICALL Java_DisplayCapture_configureEncoder(JNIEnv *env,jclass cls,jlong handle,jint fps,jint bitrate) {
    (void)cls;
    if(encoder_reconfigure((Encoder *)(intptr_t)handle,fps,bitrate))fail(env,"Native AVC configuration failed");
}

JNIEXPORT void JNICALL Java_DisplayCapture_encodeI420(JNIEnv *env,jclass cls,jlong handle,
    jobject buffer,jint stamp,jint force) {
    (void)cls;
    if(!handle || !buffer || (*env)->GetDirectBufferCapacity(env,buffer)<(jlong)encoder_width((Encoder *)(intptr_t)handle)*encoder_height((Encoder *)(intptr_t)handle)*3/2 || force<0 || force>1) {
        fail(env,"Invalid native I420 frame"); return;
    }
    const unsigned char *i420=(*env)->GetDirectBufferAddress(env,buffer);
    if(encoder_frame_i420((Encoder *)(intptr_t)handle,i420,(uint32_t)stamp,force)) fail(env,"Native I420 frame/output failed");
}
JNIEXPORT void JNICALL Java_DisplayCapture_referenceI420(JNIEnv *env,jclass cls,jobject input,jobject output,jint width,jint height) {
    (void)cls;
    if(width<320||height<240||width>2560||height>1440||(width&1)||(height&1)||!input||!output||(*env)->GetDirectBufferCapacity(env,input)!=(jlong)width*height*4||(*env)->GetDirectBufferCapacity(env,output)<(jlong)width*height*3/2) { fail(env,"Invalid conversion check buffer");return; }
    const unsigned char *rgba=(*env)->GetDirectBufferAddress(env,input);
    unsigned char *yuv=(*env)->GetDirectBufferAddress(env,output);
    if(ABGRToI420(rgba,width*4,yuv,width,yuv+width*height,width/2,yuv+width*height*5/4,width/2,width,-height))
        fail(env,"Reference conversion failed");
}
