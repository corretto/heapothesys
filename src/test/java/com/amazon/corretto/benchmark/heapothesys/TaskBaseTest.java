package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Test;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

public class TaskBaseTest {
    @Test
    public void SingleTaskTest() throws Exception {
        assertThat(TaskBase.createSingle(new ObjectStore(1), 10, 1000).call(),
                lessThanOrEqualTo(0L));
    }

    @Test
    public void ExerciseLongLivedTest() throws Exception {
        assertThat(TaskBase.createSingle(new ObjectStore(10), 10, 5000, 8192, 65535, 100).call(),
                lessThanOrEqualTo(0L));
    }
}
