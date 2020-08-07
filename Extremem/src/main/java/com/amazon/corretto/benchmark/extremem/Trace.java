// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.io.PrintStream;

/**
 * This class provides diagnostic trace reports for purposes of
 * debugging (and confirming proper operation of) the extremem test
 * workload.
 *
 * Methods are overloaded to support different numbers of arguments.
 * This avoids autoboxing and string catenation costs that might
 * otherwise be associated with variable argument lists.  The intent
 * is to avoid implicit allocation and garbage collection.  Within the
 * extremem workload, all allocation and deallocation is to be
 * explicit and accompanied by explicit bookkeeping.
 */
class Trace {
  // Set verbosity to control which trace messages display.  A higher
  // number results in more messages.  In current practice, 3 is the
  // most verbose, 1 is the least verbose.  The value 0 indicates no
  // trace information.
  //
  // verbosity level 1 is reserved for debugging and diagnosing issues
  // in memory accounting.
  //
  // The experimental workload performance reports are only valid if
  // verbosity equals zero.  Various extra memory allocations that are
  // required when trace messages are enabled may not fully accounted for.
  private final static int verbosity = 0;
  private final static PrintStream err = System.err;

  private static synchronized void output(String s1) {
    err.println(s1);
  }

  private static synchronized void output(String s1, String s2) {
    err.print(s1);
    err.println(s2);
  }

  private static synchronized void output(String s1, String s2, String s3) {
    err.print(s1);
    err.print(s2);
    err.println(s3);
  }

  private static synchronized void output(String s1, String s2,
					  String s3, String s4) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.println(s4);
  }

  private static synchronized void output(String s1, String s2, String s3,
					  String s4, String s5) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.println(s5);
  }

  private static synchronized void output(String s1, String s2, String s3,
					  String s4, String s5, String s6) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.println(s6);
  }

  private static synchronized void
  output(String s1,
	 String s2, String s3, String s4, String s5, String s6, String s7) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.println(s7);
  }

  private static synchronized void
  output(String s1, String s2, String s3, String s4, String s5, String s6,
	 String s7, String s8, String s9) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.print(s7);
    err.print(s8);
    err.println(s9);
  }

  private static synchronized void
  output(String s1, String s2, String s3, String s4, String s5, String s6,
	 String s7, String s8, String s9, String s10, String s11, String s12) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.print(s7);
    err.print(s8);
    err.print(s9);
    err.print(s10);
    err.print(s11);
    err.println(s12);
  }

  private static synchronized void
  output(String s1, String s2, String s3, String s4, String s5, String s6,
	 String s7, String s8, String s9, String s10, String s11, String s12,
	 String s13, String s14) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.print(s7);
    err.print(s8);
    err.print(s9);
    err.print(s10);
    err.print(s11);
    err.print(s12);
    err.print(s13);
    err.println(s14);
  }

  private static synchronized void
  output(String s1, String s2, String s3, String s4, String s5, String s6,
	 String s7, String s8, String s9, String s10, String s11,
	 String s12, String s13, String s14, String s15, String s16,
	 String s17, String s18, String s19, String s20, String s21) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.print(s7);
    err.print(s8);
    err.print(s9);
    err.print(s10);
    err.print(s11);
    err.print(s12);
    err.print(s13);
    err.print(s14);
    err.print(s15);
    err.print(s16);
    err.print(s17);
    err.print(s18);
    err.print(s19);
    err.print(s20);
    err.println(s21);
  }

  private static synchronized void
  outputNoLine(String s1, String s2,
	       String s3, String s4, String s5, String s6, String s7) {
    err.print(s1);
    err.print(s2);
    err.print(s3);
    err.print(s4);
    err.print(s5);
    err.print(s6);
    err.print(s7);
  }

  // Return true iff trace messages are enabled at specified level.
  static boolean enabled(int level) {
    return (level <= verbosity);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1) {
    output(s1);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2) {
    output(s1, s2);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3) {
    output(s1, s2, s3);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4) {
    output(s1, s2, s3, s4);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5) {
    output(s1, s2, s3, s4, s5);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5,
		    String s6) {
    output(s1, s2, s3, s4, s5, s6);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5,
		    String s6, String s7) {
    output(s1, s2, s3, s4, s5, s6, s7);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5,
		    String s6, String s7, String s8, String s9) {
    output(s1, s2, s3, s4, s5, s6, s7, s8, s9);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5,
		    String s6, String s7, String s8, String s9, String s10,
		    String s11, String s12) {
    output(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12);
  }

  // debug statements always output, regardless of verbosity value
  static void debug(String s1, String s2, String s3, String s4, String s5,
		    String s6, String s7, String s8, String s9, String s10,
		    String s11, String s12, String s13, String s14,
		    String s15, String s16, String s17, String s18,
		    String s19, String s20, String s21) {
    output(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14,
	   s15, s16, s17, s18, s19, s20, s21);
  }

  static void msg(int level, String s1) {
    if (level <= verbosity)
      output(s1);
  }

  static void msg(int level, String s1, String s2) {
    if (level <= verbosity)
      output(s1, s2);
  }

  static void msg(int level, String s1, String s2, String s3) {
    if (level <= verbosity)
      output(s1, s2, s3);
  }

  static void msg(int level, String s1, String s2, String s3, String s4) {
    if (level <= verbosity)
      output(s1, s2, s3, s4);
  }

  static void msg(int level, String s1, String s2, String s3,
		  String s4, String s5) {
    if (level <= verbosity)
      output(s1, s2, s3, s4, s5);

  }

  static void msg(int level, String s1, String s2, String s3,
		  String s4, String s5, String s6) {
    if (level <= verbosity)
      output(s1, s2, s3, s4, s5, s6);

  }

  static void msg(int level, String s1, String s2, String s3,
		  String s4, String s5, String s6, String s7) {
    if (level <= verbosity)
      output(s1, s2, s3, s4, s5, s6, s7);

  }

  static void msg(int level, String s1, String s2, String s3,
		  String s4, String s5, String s6, String s7,
		  String s8, String s9, String s10, String s11,
		  String s12, String s13, String s14) {
    if (level <= verbosity)
      output(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14);
  }

  static void msgNoLine(int level, String s1, String s2, String s3,
			String s4, String s5, String s6, String s7) {
    if (level <= verbosity)
      outputNoLine(s1, s2, s3, s4, s5, s6, s7);

  }
}
