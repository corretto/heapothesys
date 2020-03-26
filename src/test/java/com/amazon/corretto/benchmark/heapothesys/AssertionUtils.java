package com.amazon.corretto.benchmark.heapothesys;

public class AssertionUtils {
    public static void assertNoThrow(final Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            throw new AssertionError("It throws exception!", ex);
        }
    }

    public static void assertNoThrow(final Runnable runnable, final Class<?> expectedException) {
        try {
            runnable.run();
        } catch (Throwable ex) {
            if (ex.getClass().equals(expectedException)) {
                throw new AssertionError("It throws " + expectedException.getName() + " exception!", ex);
            }
            throw ex;
        }
    }

    public static void assertThrow(final Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            return;
        }
        throw new AssertionError("It does not throw!");
    }

    public static void assertThrow(final Runnable runnable, final Class<?> expectedException) {
        try {
            runnable.run();
        } catch (Throwable ex) {
            if (ex.getClass().equals(expectedException)) {
                return;
            }
            throw new AssertionError("It throws exception other than " + expectedException.getName() + "!", ex);
        }
        throw new AssertionError("It does not throw " + expectedException.getName() + " exception!");
    }
}
