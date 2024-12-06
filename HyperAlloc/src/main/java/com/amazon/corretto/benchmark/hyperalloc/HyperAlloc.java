// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

public final class HyperAlloc {
    private static final String DEFAULT_RUN_TYPE = "simple";

    private HyperAlloc() {}

    public static void main(String[] args) {

        try {
            runner(args);
        } catch (Throwable t) {
            System.out.println(t.getMessage());
            System.exit(1);
        }
    }

    static void runner(String[] args) {
        try {
            String runType = findRunType(args);

            switch (runType) {
                case "simple":
                    new SimpleRunner(new SimpleRunConfig(args)).start();
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unknown run type (-u %s), supported type is -u simple.", runType));
            }
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Throwable t) {
            throw new RuntimeException(t);
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


