package com.amazon.corretto.benchmark.heapothesys;

import org.junit.jupiter.api.Test;

class SimpleRunnerTest {

    @Test
    void DefaultRunTest() {
        new SimpleRunner(new SimpleRunConfig(new String[]{"-d", "5"})).start();
    }
}
