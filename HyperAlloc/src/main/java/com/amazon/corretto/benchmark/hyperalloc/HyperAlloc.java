// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

public final class HyperAlloc {
    private HyperAlloc() {}

    public static void main(String[] args) {
        try {
            new SimpleRunner(new SimpleRunConfig(args)).start();
        } catch (IllegalArgumentException e) {
            SimpleRunConfig.usage();
            System.exit(1);
        }
    }
}


