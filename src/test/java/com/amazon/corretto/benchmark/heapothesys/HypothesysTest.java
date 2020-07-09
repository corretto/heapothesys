package com.amazon.corretto.benchmark.heapothesys;

import org.junit.jupiter.api.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HypothesysTest {

    @Test
    void SimpleRunTest() {
        Heapothesys.main(new String[]{"-u", "simple", "-d", "5"});
    }

    @Test
    void DefaultRunTypeTest() {
        Heapothesys.main(new String[]{"-d", "5"});
    }

    @Test
    void UnknownRunTypeTest() throws Exception {
        int status = catchSystemExit(
                () -> Heapothesys.main(new String[]{"-u", "unknown", "-a", "5"}));
        assertThat(status, is(1));
    }
}
