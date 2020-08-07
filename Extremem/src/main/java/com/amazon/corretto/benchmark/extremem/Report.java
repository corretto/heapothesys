// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.io.PrintStream;

/**
 * Report provides services similar to Trace, with the following
 * contrasting behaviors:
 *
 *  1. Messages are sent to stdout rather than stderr.
 *
 *  2. Locking is not provided to individual lines of output.  Rather,
 *     the client acquires an explicit lock and holds it until a
 *     report comprised of potentially many output lines is completely
 *     produced. 
 */
class Report {
  private static final Object locker = new Object();
  private static final PrintStream out = System.out;
  private static int num_writers;

  // "varargs" formats avoid autoboxing and catenation costs
  static void acquireReportLock() {
    synchronized (locker) {
      while (num_writers > 0) {
	try {
	  locker.wait();
	} catch (InterruptedException x) {
	  ;			// try again
	}
      }
      num_writers++;
    }
  }

  static void releaseReportLock() {
    synchronized (locker) {
      num_writers--;
      locker.notifyAll();
    }
  }

  static void output() {
    out.println("");
  }

  static void output(String s1) {
    out.println(s1);
  }

  static void outputNoLine(String s1) {
    out.print(s1);
  }

  static void outputNoLine(String s1, String s2) {
    out.print(s1);
    out.print(s2);
  }

  static void output(String s1, String s2) {
    out.print(s1);
    out.println(s2);
  }

  static void output(String s1, String s2, String s3) {
    out.print(s1);
    out.print(s2);
    out.println(s3);
  }

  static void output(String s1, String s2, String s3, String s4) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.println(s4);
  }

  static void output(String s1, String s2, String s3, String s4, String s5) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.println(s5);
  }

  static void output(String s1, String s2, String s3,
		     String s4, String s5, String s6) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.println(s6);
  }

  static void output(String s1, String s2, String s3,
		     String s4, String s5, String s6, String s7, String s8) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.println(s8);
  }

  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.println(s9);
  }

  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9, String s10,
		     String s11, String s12, String s13, String s14,
		     String s15) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.print(s9);
    out.print(s10);
    out.print(s11);
    out.print(s12);
    out.print(s13);
    out.print(s14);
    out.println(s15);
  }
  
  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9, String s10,
		     String s11, String s12, String s13, String s14,
		     String s15, String s16) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.print(s9);
    out.print(s10);
    out.print(s11);
    out.print(s12);
    out.print(s13);
    out.print(s14);
    out.print(s15);
    out.println(s16);
  }
  
  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9, String s10,
		     String s11, String s12, String s13, String s14,
		     String s15, String s16, String s17) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.print(s9);
    out.print(s10);
    out.print(s11);
    out.print(s12);
    out.print(s13);
    out.print(s14);
    out.print(s15);
    out.print(s16);
    out.println(s17);
  }
  
  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9, String s10,
		     String s11, String s12, String s13, String s14,
		     String s15, String s16, String s17, String s18,
		     String s19, String s20) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.print(s9);
    out.print(s10);
    out.print(s11);
    out.print(s12);
    out.print(s13);
    out.print(s14);
    out.print(s15);
    out.print(s16);
    out.print(s17);
    out.print(s18);
    out.print(s19);
    out.println(s20);
  }

  static void output(String s1, String s2, String s3, String s4, String s5,
		     String s6, String s7, String s8, String s9, String s10,
		     String s11, String s12, String s13, String s14,
		     String s15, String s16, String s17, String s18,
		     String s19, String s20, String s21) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
    out.print(s8);
    out.print(s9);
    out.print(s10);
    out.print(s11);
    out.print(s12);
    out.print(s13);
    out.print(s14);
    out.print(s15);
    out.print(s16);
    out.print(s17);
    out.print(s18);
    out.print(s19);
    out.print(s20);
    out.println(s21);
  }
  
  static void outputNoLine(String s1, String s2, String s3,
			   String s4, String s5, String s6, String s7) {
    out.print(s1);
    out.print(s2);
    out.print(s3);
    out.print(s4);
    out.print(s5);
    out.print(s6);
    out.print(s7);
  }
}
