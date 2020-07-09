package com.amazon.corretto.benchmark.heapothesys;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class TaskBaseTest {
    @Test
    void SingleTaskTest() throws Exception {
        assertThat(TaskBase.createSingle(new ObjectStore(1), 10, 1000).call(),
                lessThanOrEqualTo(0L));
    }

    @Test
    void ExerciseLongLivedTest() throws Exception {
        assertThat(TaskBase.createSingle(new ObjectStore(10), 10, 5000, 8192, 65535, 100).call(),
                lessThanOrEqualTo(0L));
    }
}
