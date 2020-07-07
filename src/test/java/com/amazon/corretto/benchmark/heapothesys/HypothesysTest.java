package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HypothesysTest {

    @Test
    public void SimpleRunTest() {
        Heapothesys.main(new String[]{"-u", "simple", "-d", "5"});
    }

    @Test
    public void DefaultRunTypeTest() {
        Heapothesys.main(new String[]{"-d", "5"});
    }

    @Test
    public void UnknownRunTypeTest() throws Exception {
        int status = catchSystemExit(
                () -> Heapothesys.main(new String[]{"-u", "unknown", "-a", "5"}));
        assertThat(status, is(1));
    }
}
