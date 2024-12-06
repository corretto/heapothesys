// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.corretto.benchmark.hyperalloc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HyperAllocTest {

    @Test
    void SimpleRunTest() {
        HyperAlloc.main(new String[]{"-u", "simple", "-d", "5"});
    }

    @Test
    void DefaultRunTypeTest() {
        HyperAlloc.main(new String[]{"-d", "5"});
    }

    @Test
    void UnknownRunTypeTest() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            HyperAlloc.runner(new String[]{"-u", "unknown", "-a", "5"});
        });
        String expected = "Unknown run type (-u unknown), supported type is -u simple.";
        assertEquals(expected,e.getMessage());
    }
}
