// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * The Main class starts up the Extremem workload.
 */
public class Main {
  private static final long UniversalAnswer = 42;

  public static void main(String[] args) {
    Configuration config = new Configuration(args);
    Bootstrap booter = new Bootstrap(config, UniversalAnswer);
    booter.start();
    // This thread goes to sleep until the workload has finished.
    for (boolean joined = false; !joined; ) {
      try {
	booter.join();
	joined = true;
      } catch (InterruptedException x) {
	;			// try it again
      }
    }
  }
}
