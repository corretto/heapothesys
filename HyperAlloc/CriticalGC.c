#include <jni.h>
#include <com_amazon_corretto_benchmark_hyperalloc_CriticalGC.h>

static jbyte* sink;

JNIEXPORT void JNICALL Java_com_amazon_corretto_benchmark_hyperalloc_CriticalGC_acquire(JNIEnv* env, jclass klass, jbyteArray arr) {
   sink = (*env)->GetPrimitiveArrayCritical(env, arr, 0);
}

JNIEXPORT void JNICALL Java_com_amazon_corretto_benchmark_hyperalloc_CriticalGC_release(JNIEnv* env, jclass klass, jbyteArray arr) {
   (*env)->ReleasePrimitiveArrayCritical(env, arr, sink, 0);
}
