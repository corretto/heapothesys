package com.amazon.corretto.benchmark.hyperalloc;

import org.junit.jupiter.api.Test;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HyperAllocTest {

    @Test
    void SimpleRunTest() {
        HyperAlloc.main(new String[]{"-u", "simple", "-d", "5"});
    }

    @Test
    void DefaultRunTypeTest() {
        HyperAlloc.main(new String[]{"-d", "5"});
    }

    @Test
    void UnknownRunTypeTest() throws Exception {
        int status = catchSystemExit(
                () -> HyperAlloc.main(new String[]{"-u", "unknown", "-a", "5"}));
        assertThat(status, is(1));
    }
}
