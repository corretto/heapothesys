package com.amazon.corretto.benchmark.hyperalloc;

public class CriticalGC {

  static final int ITERS = Integer.getInteger("iters", 100);
  static final int ARR_SIZE = Integer.getInteger("arrSize", 1_000_000);
  static final int WINDOW = Integer.getInteger("window", 30_000_000);

  static native void acquire(byte[] arr);
  static native void release(byte[] arr);

  static final Object[] window = new Object[WINDOW];

  public static void main(String... args) throws Throwable {
    System.loadLibrary("com.amazon.corretto.benchmark.hyperalloc.CriticalGC");

    byte[] arr = new byte[ARR_SIZE];

    for (int i = 0; i < ITERS; i++) {
      acquire(arr);
      System.out.println("Acquired");
      try {
        for (int c = 0; c < WINDOW; c++) {
          window[c] = new Object();
        }
      } catch (Throwable t) {
        // omit
      } finally {
        System.out.println("Releasing");
        release(arr);
      }
    }
  }
}
