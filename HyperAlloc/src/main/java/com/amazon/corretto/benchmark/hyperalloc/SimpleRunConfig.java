// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;

/**
 * Class for parsing simple run parameters.
 */
public class SimpleRunConfig {
    private long allocRateInMbPerSecond = 1024L;
    private int durationInSecond = 60;
    private int longLivedInMb = 64;
    private int midAgedInMb = 64;
    private int numOfThreads = 4;
    private int minObjectSize = 128;
    private int maxObjectSize = 1024;
    private boolean useCompressedOops;
    private int pruneRatio = ObjectStore.DEFAULT_PRUNE_RATIO;
    private int reshuffleRatio = ObjectStore.DEFAULT_RESHUFFLE_RATIO;
    private int heapSizeInMb = (int)(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1048576L);
    private String logFile = "output.csv";
    private String allocationLogFile = null;
    private Double allocationSmoothnessFactor = null;
    private double rampUpSeconds = 0.0;

    {
        HotSpotDiagnosticMXBean mxBeanServer = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        useCompressedOops = Boolean.parseBoolean(mxBeanServer.getVMOption("UseCompressedOops").getValue());
    }

    /**
     * Parse input arguments from a string array.
     * @param args The string array of the arguments.
     */
    public SimpleRunConfig(final String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-a")) {
                allocRateInMbPerSecond = Long.parseLong(args[++i]);
            } else if (args[i].equals("-h")) {
                ++i;
                // Left in to be compatible with existing scripts
                System.out.println("Use of -h has been deprecated - using value retrieved from MemoryMXBean");
            } else if (args[i].equals("-s")) {
                longLivedInMb = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-m")) {
                midAgedInMb = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-d")) {
                durationInSecond = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-t")) {
                numOfThreads = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-n")) {
                minObjectSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-x")) {
                maxObjectSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-r")) {
                pruneRatio = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-f")) {
                reshuffleRatio = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-c")) {
                ++i;
                System.out.println("Use of -c has been deprecated - using value retrieved from HotSpotDiagnosticMXBean");
            } else if (args[i].equals("-z")) {
                allocationSmoothnessFactor = Double.parseDouble(args[++i]);
                if (allocationSmoothnessFactor < 0 || allocationSmoothnessFactor > 1.0) {
                    throw new IllegalArgumentException(String.format("AllocationSmoothness of %s not in range\n",allocationSmoothnessFactor) + usage());
                }
            } else if (args[i].equals("-l")) {
                logFile = args[++i];
            } else if (args[i].equals("-b") || args[i].equals("--allocation-log")) {
                allocationLogFile = args[++i];
            } else if (args[i].equals("-u")) {
                i++;
            } else if (args[i].equals("-p") || args[i].equals("--ramp-up-seconds")) {
                rampUpSeconds = Double.parseDouble(args[++i]);
            } else {
                throw new IllegalArgumentException(String.format("Unrecognized argument: %s\n",args[i]) + usage());
            }
        }
    }

    private String usage() {
        return "Usage: java -jar HyperAlloc.jar [-u run type] [-a allocRateInMb] [-s longLivedObjectsInMb] [-m midAgedObjectsInMb] [-d runDurationInSeconds ] [-t numOfThreads] [-n minObjectSize] [-x maxObjectSize] [-r pruneRatio] [-f reshuffleRatio] [-l outputFile] [-b|-allocation-log logFile] [-z allocationSmoothness (0 to 1.0)] [-p rampUpSeconds ]";
    }

    /**
     * Creating a simple config from parameters.
     * @param allocRateInMbPerSecond Allocation rate in Mb per second.
     * @param heapSizeInMb Heap size (-Xmx) in Mb.
     * @param longLivedInMb The size of long-lived objects in Mb.
     * @param midAgedInMb The size of mid-aged objects in Mb.
     * @param durationInSecond The run duration in seconds.
     * @param numOfThreads The number of runner threads.
     * @param minObjectSize The minimum object size in byte.
     * @param maxObjectSize The maximum object size in byte.
     * @param pruneRatio The prune ratio per minute.
     * @param reshuffleRatio The reshuffle ratio.
     * @param useCompressedOops Whether compressedOops is enabled.
     * @param logFile The name of the output .csv file.
     * @param allocationLogFile The name of the allocation log file.
     * @param rampUpSeconds Gradually increase allocation rate over this period of time.
     */
    public SimpleRunConfig(final long allocRateInMbPerSecond, final double allocSmoothnessFactor,
                           final int heapSizeInMb, final int longLivedInMb,
                           final int midAgedInMb, final int durationInSecond, final int numOfThreads,
                           final int minObjectSize, final int maxObjectSize, final int pruneRatio,
                           final int reshuffleRatio, final boolean useCompressedOops, final String logFile,
                           final String allocationLogFile, final double rampUpSeconds) {
        this.allocRateInMbPerSecond = allocRateInMbPerSecond;
        this.allocationSmoothnessFactor = allocSmoothnessFactor;
        this.longLivedInMb = longLivedInMb;
        this.midAgedInMb = midAgedInMb;
        this.durationInSecond = durationInSecond;
        this.numOfThreads = numOfThreads;
        this.minObjectSize = minObjectSize;
        this.maxObjectSize = maxObjectSize;
        this.pruneRatio = pruneRatio;
        this.reshuffleRatio = reshuffleRatio;
        this.logFile = logFile;
        this.allocationLogFile = allocationLogFile;
        this.rampUpSeconds = rampUpSeconds;
    }

    public long getAllocRateInMbPerSecond() {
        return allocRateInMbPerSecond;
    }

    public int getHeapSizeInMb() {
        return heapSizeInMb;
    }

    public int getLongLivedInMb() {
        return longLivedInMb;
    }

    public int getMidAgedInMb() {
        return midAgedInMb;
    }

    public int getDurationInSecond() {
        return durationInSecond;
    }

    public int getNumOfThreads() {
        return numOfThreads;
    }

    public int getMinObjectSize() {
        return minObjectSize;
    }

    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    public int getPruneRatio() {
        return pruneRatio;
    }

    public int getReshuffleRatio() {
        return reshuffleRatio;
    }

    public boolean isUseCompressedOops() {
        return useCompressedOops;
    }

    public String getLogFile() {
        return logFile;
    }

    public Double getAllocationSmoothnessFactor() {
        return allocationSmoothnessFactor;
    }

    public String  getAllocationLogFile() {
        return allocationLogFile;
    }

    public double getRampUpSeconds() {
        return rampUpSeconds;
    }
}

