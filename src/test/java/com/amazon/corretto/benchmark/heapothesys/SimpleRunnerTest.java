package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class SimpleRunnerTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void DefaultRunTest() {
        new SimpleRunner(new SimpleRunConfig(new String[]{"-d", "5"})).start();
    }
}
