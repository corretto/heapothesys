package com.amazon.corretto.benchmark.hyperalloc;

/**
 * Class for parsing simple run parameters.
 */
public class SimpleRunConfig {
    private long allocRateInMbPerSecond = 1024L;
    private double allocSmoothnessFactor = 0.0;
    private int durationInSecond = 60;
    private int longLivedInMb = 64;
    private int midAgedInMb = 64;
    private int numOfThreads = 4;
    private int minObjectSize = 128;
    private int maxObjectSize = 1024;
    private boolean useCompressedOops = true;
    private int pruneRatio = ObjectStore.DEFAULT_PRUNE_RATIO;
    private int reshuffleRatio = ObjectStore.DEFAULT_RESHUFFLE_RATIO;
    private int heapSizeInMb = 1024;
    private String logFile = "output.csv";
    private String allocationLogFile = null;

    /**
     * Parse input arguments from a string array.
     * @param args The string array of the arguments.
     */
    public SimpleRunConfig(final String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-a")) {
                allocRateInMbPerSecond = Long.parseLong(args[++i]);
            } else if (args[i].equals("-h")) {
                heapSizeInMb = Integer.parseInt(args[++i]);
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
                useCompressedOops = Boolean.parseBoolean(args[++i]);
            } else if (args[i].equals("-z")) {
                allocSmoothnessFactor = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-l")) {
                logFile = args[++i];
            } else if (args[i].equals("-b") || args[i].equals("--allocation-log")) {
                allocationLogFile = args[++i];
            } else if (args[i].equals("-u")) {
                i++;
            } else {
                System.out.println("Usage: java heaputils " +
                        "[-u run type] [-a allocRateInMb] [-h heapSizeInMb] [-s longLivedObjectsInMb] " +
                        "[-m midAgedObjectsInMb] [-d runDurationInSeconds ] [-t numOfThreads] [-n minObjectSize] " +
                        "[-x maxObjectSize] [-r pruneRatio] [-f reshuffleRatio] [-c useCompressedOops] " +
                        "[-l outputFile] [-b|-allocation-log logFile");
                System.exit(1);
            }
        }
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
     */
    public SimpleRunConfig(final long allocRateInMbPerSecond, final double allocSmoothnessFactor,
                           final int heapSizeInMb, final int longLivedInMb,
                           final int midAgedInMb, final int durationInSecond, final int numOfThreads,
                           final int minObjectSize, final int maxObjectSize, final int pruneRatio,
                           final int reshuffleRatio, final boolean useCompressedOops, final String logFile,
                           final String allocationLogFile) {
        this.allocRateInMbPerSecond = allocRateInMbPerSecond;
        this.allocSmoothnessFactor = allocSmoothnessFactor;
        this.heapSizeInMb = heapSizeInMb;
        this.longLivedInMb = longLivedInMb;
        this.midAgedInMb = midAgedInMb;
        this.durationInSecond = durationInSecond;
        this.numOfThreads = numOfThreads;
        this.minObjectSize = minObjectSize;
        this.maxObjectSize = maxObjectSize;
        this.pruneRatio = pruneRatio;
        this.reshuffleRatio = reshuffleRatio;
        this.useCompressedOops = useCompressedOops;
        this.logFile = logFile;
        this.allocationLogFile = allocationLogFile;
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

    public double getAllocationSmoothnessFactor() {
        return allocSmoothnessFactor;
    }

    public String  getAllocationLogFile() {
        return allocationLogFile;
    }
}

