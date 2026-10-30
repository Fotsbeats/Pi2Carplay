#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
static void *library,*states[2];
static void *(*create)(unsigned,unsigned,unsigned,int,int *);
static int (*process)(void *,unsigned,const int16_t *,unsigned *,int16_t *,unsigned *);
static void (*destroy)(void *);
static int (*skip)(void *);
JNIEXPORT jboolean JNICALL Java_org_bench_pi2call_CallResampler_reset(JNIEnv *env,jclass type) {
    (void)env;(void)type;
    if(!library) {
        library=dlopen("/system/lib64/libspeexresampler.so",RTLD_NOW|RTLD_LOCAL);
        if(!library)return JNI_FALSE;
        create=dlsym(library,"speex_resampler_init");process=dlsym(library,"speex_resampler_process_int");
        destroy=dlsym(library,"speex_resampler_destroy");skip=dlsym(library,"speex_resampler_skip_zeros");
    }
    if(!create || !process || !destroy || !skip)return JNI_FALSE;
    for(int i=0;i<2;i++) { if(states[i])destroy(states[i]);int error=0;states[i]=create(1,i?16000:8000,i?8000:16000,5,&error);if(error || !states[i])return JNI_FALSE;skip(states[i]); }
    return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_org_bench_pi2call_CallResampler_convert(JNIEnv *env,jclass type,jint direction,jbyteArray input,jint bytes,jbyteArray output) {
    (void)type;
    if(direction<0 || direction>1 || !states[direction] || !input || !output || bytes<0 || bytes>960 || (bytes&1) || bytes>(*env)->GetArrayLength(env,input))return -1;
    int16_t in[480],out[1024];unsigned in_count=(unsigned)bytes/2,out_count=(unsigned)(*env)->GetArrayLength(env,output)/2;
    if(out_count>1024)out_count=1024;
    (*env)->GetByteArrayRegion(env,input,0,bytes,(jbyte *)in);
    if(process(states[direction],0,in,&in_count,out,&out_count) || in_count!=(unsigned)bytes/2)return -1;
    (*env)->SetByteArrayRegion(env,output,0,(jint)out_count*2,(jbyte *)out);return (jint)out_count*2;
}
