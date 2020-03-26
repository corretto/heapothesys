package com.amazon.corretto.benchmark.heapothesys;

public final class Heapothesys {
    private static final String DEFAULT_RUN_TYPE = "simple";

    private Heapothesys() {}

    public static void main(String[] args) {
        switch (findRunType(args)) {
            case "simple" :
                new SimpleRunner(new SimpleRunConfig(args)).start();
                break;
            default:
                System.out.println("Current supported run type (-u): simple.");
                System.exit(1);
        }
    }

    private static String findRunType(final String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-u")) {
                return args[i + 1];
            }
        }
        return DEFAULT_RUN_TYPE;
    }
}


