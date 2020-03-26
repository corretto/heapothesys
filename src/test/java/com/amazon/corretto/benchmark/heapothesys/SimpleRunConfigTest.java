package com.amazon.corretto.benchmark.heapothesys;

import org.junit.Rule;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class SimpleRunConfigTest {
    @Test
    public void DefaultStringsTest() {
        final SimpleRunConfig config = new SimpleRunConfig(new String[0]);

        assertThat(config.getNumOfThreads(), is(4));
        assertThat(config.getAllocRateInMbPerSecond(), is(1024L));
        assertThat(config.getDurationInSecond(), is(60));
        assertThat(config.getMaxObjectSize(), is(1024));
        assertThat(config.getMinObjectSize(), is(128));
        assertThat(config.getHeapSizeInMb(), is(1024));
        assertThat(config.getLongLivedInMb(), is(64));
        assertThat(config.getMidAgedInMb(), is(64));
        assertThat(config.getPruneRatio(), is(50));
        assertThat(config.getReshuffleRatio(), is(100));
        assertThat(config.getLogFile(), is("output.csv"));
        assertTrue(config.isUseCompressedOops());
    }

    @Test
    public void ConstructorTest() {
        final SimpleRunConfig config = new SimpleRunConfig(16384L, 32768, 256,
                32, 3000, 16, 256, 512,
                10, 20, false, "nosuch.csv");

        assertThat(config.getNumOfThreads(), is(16));
        assertThat(config.getAllocRateInMbPerSecond(), is(16384L));
        assertThat(config.getDurationInSecond(), is(3000));
        assertThat(config.getMaxObjectSize(), is(512));
        assertThat(config.getMinObjectSize(), is(256));
        assertThat(config.getHeapSizeInMb(), is(32768));
        assertThat(config.getLongLivedInMb(), is(256));
        assertThat(config.getMidAgedInMb(), is(32));
        assertThat(config.getPruneRatio(), is(10));
        assertThat(config.getReshuffleRatio(), is(20));
        assertThat(config.getLogFile(), is("nosuch.csv"));
        assertFalse(config.isUseCompressedOops());
    }

    @Test
    public void StringArgsTest() {
        final SimpleRunConfig config = new SimpleRunConfig(new String[]{"-a", "16384", "-s", "256", "-h", "32768",
                                                                        "-d", "3000", "-m", "32", "-t", "16",
                                                                        "-f", "20", "-r", "10", "-x", "512", "-u", "simple",
                                                                        "-n", "256", "-c", "false", "-l", "nosuch.csv"});

        assertThat(config.getNumOfThreads(), is(16));
        assertThat(config.getAllocRateInMbPerSecond(), is(16384L));
        assertThat(config.getDurationInSecond(), is(3000));
        assertThat(config.getMaxObjectSize(), is(512));
        assertThat(config.getMinObjectSize(), is(256));
        assertThat(config.getHeapSizeInMb(), is(32768));
        assertThat(config.getLongLivedInMb(), is(256));
        assertThat(config.getMidAgedInMb(), is(32));
        assertThat(config.getPruneRatio(), is(10));
        assertThat(config.getReshuffleRatio(), is(20));
        assertThat(config.getLogFile(), is("nosuch.csv"));
        assertFalse(config.isUseCompressedOops());
    }

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void UnknownParameterShouldExitTest() {
        exit.expectSystemExitWithStatus(1);

        final SimpleRunConfig config = new SimpleRunConfig(new String[]{"-w", "who"});
    }
}
