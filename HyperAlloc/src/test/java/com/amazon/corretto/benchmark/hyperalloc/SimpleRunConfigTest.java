// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SimpleRunConfigTest {

    // value will change depending on how much memory the test machine has.
    int maxHeap = (int)(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1048576L);
    boolean useCompressedOops;

    {
        HotSpotDiagnosticMXBean mxBeanServer = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        useCompressedOops = Boolean.parseBoolean(mxBeanServer.getVMOption("UseCompressedOops").getValue());
    }

    @Test
    void DefaultStringsTest() {
        final SimpleRunConfig config = new SimpleRunConfig(new String[0]);

        assertThat(config.getNumOfThreads(), is(4));
        assertThat(config.getAllocRateInMbPerSecond(), is(1024L));
        assertThat(config.getDurationInSecond(), is(60));
        assertThat(config.getMaxObjectSize(), is(1024));
        assertThat(config.getMinObjectSize(), is(128));
        assertThat(config.getHeapSizeInMb(), is(maxHeap));
        assertThat(config.getLongLivedInMb(), is(64));
        assertThat(config.getMidAgedInMb(), is(64));
        assertThat(config.getPruneRatio(), is(50));
        assertThat(config.getReshuffleRatio(), is(100));
        assertThat(config.getLogFile(), is("output.csv"));
        assertTrue(config.isUseCompressedOops());
    }

    @Test
    void ConstructorTest() {
        final SimpleRunConfig config = new SimpleRunConfig(16384L, 0.0, 256,
                32, 3000, 16, 256, 512,
                10, 20, "nosuch.csv", null, 0.0);

        assertThat(config.getNumOfThreads(), is(16));
        assertThat(config.getAllocRateInMbPerSecond(), is(16384L));
        assertThat(config.getDurationInSecond(), is(3000));
        assertThat(config.getMaxObjectSize(), is(512));
        assertThat(config.getMinObjectSize(), is(256));
        assertThat(config.getHeapSizeInMb(), is(maxHeap));
        assertThat(config.getLongLivedInMb(), is(256));
        assertThat(config.getMidAgedInMb(), is(32));
        assertThat(config.getPruneRatio(), is(10));
        assertThat(config.getReshuffleRatio(), is(20));
        assertThat(config.getLogFile(), is("nosuch.csv"));
        assertEquals(useCompressedOops,config.isUseCompressedOops());
    }

    @Test
    void StringArgsTest() {
        final SimpleRunConfig config = new SimpleRunConfig(new String[]{"-a", "16384", "-s", "256", "-h", "32768",
                                                                        "-d", "3000", "-m", "32", "-t", "16",
                                                                        "-f", "20", "-r", "10", "-x", "512", "-u", "simple",
                                                                        "-n", "256", "-c", "false", "-l", "nosuch.csv"});
        assertThat(config.getNumOfThreads(), is(16));
        assertThat(config.getAllocRateInMbPerSecond(), is(16384L));
        assertThat(config.getDurationInSecond(), is(3000));
        assertThat(config.getMaxObjectSize(), is(512));
        assertThat(config.getMinObjectSize(), is(256));
        assertThat(config.getHeapSizeInMb(), is(maxHeap));
        assertThat(config.getLongLivedInMb(), is(256));
        assertThat(config.getMidAgedInMb(), is(32));
        assertThat(config.getPruneRatio(), is(10));
        assertThat(config.getReshuffleRatio(), is(20));
        assertThat(config.getLogFile(), is("nosuch.csv"));
        assertEquals(useCompressedOops,config.isUseCompressedOops());
    }

    @Test
    void UnknownParameterShouldExitTest() {
        try {
            new SimpleRunConfig(new String[]{"-w", "who"});
            fail("Expected IllegalArgumentException here");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Unknown"));
        }
    }
}
