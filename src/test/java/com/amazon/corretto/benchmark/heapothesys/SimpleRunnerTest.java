package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Test;

public class SimpleRunnerTest {

    @Test
    public void DefaultRunTest() {
        new SimpleRunner(new SimpleRunConfig(new String[]{"-d", "5"})).start();
    }
}
