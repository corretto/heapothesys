// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import org.junit.jupiter.api.Test;

class SimpleRunnerTest {

    @Test
    void DefaultRunTest() {
        new SimpleRunner(new SimpleRunConfig(new String[]{"-d", "5"})).start();
    }
}
