package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class HypothesysTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void SimpleRunTest() {
        Heapothesys.main(new String[]{"-u", "simple", "-d", "5"});
    }

    @Test
    public void DefaultRunTypeTest() {
        Heapothesys.main(new String[]{"-d", "5"});
    }

    @Test
    public void UnknownRunTypeTest() {
        exit.expectSystemExitWithStatus(1);
        Heapothesys.main(new String[]{"-u", "unknown", "-a", "5"});
    }
}
