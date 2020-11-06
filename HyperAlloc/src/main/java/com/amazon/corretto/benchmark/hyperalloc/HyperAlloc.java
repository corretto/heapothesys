// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

public final class HyperAlloc {
    private static final String DEFAULT_RUN_TYPE = "simple";

    private HyperAlloc() {}

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


