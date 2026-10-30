package org.bench.pi2call;
final class CallResampler {
    static { System.load("/data/local/tmp/pi2call/libpi2call_resample.so"); }
    static synchronized native boolean reset();
    static synchronized native int convert(int direction,byte[] input,int bytes,byte[] output);
}
