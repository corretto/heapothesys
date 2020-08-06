// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * HighResolutionTime is an abstract class that represents measurement
 * of time to the nearest ns, depending on the precision of underlying
 * timing measurement libraries.
 *
 * Two subclasses of HighResolutionTime are:
 *   AbsoluteTime measures passage of time from VM startup
 *   RelativeTime measures spans of time measure between moments in
 *     AbsoluteTime 
 */
abstract class HighResolutionTime extends ExtrememObject {
  final static int MillisPerSecond = 1000;
  final static int NanosPerSecond = 1000000000;
  final static int NanosPerMilli = 1000000;
  final static int NanosPerMicro = 1000;
  final static int MicrosPerSecond = 1000000;

  /* In canonical form, 0 <= ns < 1,000,000,000. */
  final long s;
  final int ns;

  /**
   * The constructed object is assumed to have Ephemeral.  The code
   * that invokes the constructor is expected to account for this
   * object's memory eventually becoming garbage, possibly adjusting
   * the accounting of its LifeSpan along the way to becoming garbage.
   */
  HighResolutionTime (ExtrememThread t, long s, int ns) {
    super (t, LifeSpan.Ephemeral);
    if (ns < 0) {
      int negative_seconds = ns / NanosPerSecond;

      // decrease s and increase ns by same amount
      s += negative_seconds;
      ns -= negative_seconds * NanosPerSecond;

      if (ns < 0) {
	// Due to round-down in division above, ns might still be < 0,
	//  but will not be less than -NanosPerSecond.
	s--;
	ns += NanosPerSecond;
      }
    } else if (ns > NanosPerSecond) {
      s += ns / NanosPerSecond;
      ns %= NanosPerSecond;
    }
    this.s = s;
    this.ns = ns;

    MemoryLog log = t.memoryLog ();
    log.accumulate (LifeSpan.Ephemeral, MemoryFlavor.ObjectRSB,
		    Polarity.Expand, Util.SizeOfLong + Util.SizeOfInt);
  }

  long seconds() {
    return s;
  }

  int nanoseconds() {
    return ns;
  }

  // truncate and ignore overflow
  long microseconds() {
    return s * 1000000 + ns / 1000;
  }

  boolean isNegative () {
    return (s < 0);
  }

  void tallyMemory (MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory (log, ls, p);

    // Account for s and ns fields.
    log.accumulate (ls, MemoryFlavor.ObjectRSB, p,
		    Util.SizeOfInt + Util.SizeOfLong);
  }

  /**
   * This method, used primarily for reporting, returns an Ephemeral
   * String representation of this.
   *
   * The code that invokes toString is expected to account for the
   * returned String object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  public String toString(ExtrememThread t) {
    final int NanosPerSecond = 1000000000;
    final long SecondsPerHour = 60 * 60;
    final long SecondsPerMinute = 60;
    final int NanosPerMilliseconds = 1000000;
    final int NanosPerMicroseconds = 1000;
    
    long seconds = this.s;
    int nanos = this.ns;
    String result;
    int sb_length;

    if (seconds < 0) {
      result = "-";
      seconds = -seconds;
      nanos = NanosPerSecond - nanos;
      sb_length = 1;
    } else {
      result = "";
      sb_length = 0;
    }
    int sb_capacity = Util.ephemeralStringBuilder(t, sb_length);

    if (seconds >= SecondsPerHour) {
      long hours = seconds / SecondsPerHour;
      seconds %= SecondsPerHour;
      int digits = Util.decimalDigits(hours);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      result = result + hours + "h ";
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if (seconds >= SecondsPerMinute) {
      long minutes = seconds / SecondsPerMinute;
      int digits = Util.decimalDigits(minutes);
      result = result + minutes + "m ";
      seconds %= SecondsPerMinute;
      
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if (seconds > 0) {
      int digits = Util.decimalDigits(seconds);
      result = result + seconds + "s ";

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }

    if (nanos >= NanosPerMilliseconds) {
      int millis = nanos / NanosPerMilliseconds;
      int digits = Util.decimalDigits(millis);

      result = result + millis + "ms ";
      nanos %= NanosPerMilliseconds;
      
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 3);
      sb_length += 3;
    }
    if (nanos >= NanosPerMicroseconds) {
      int us = nanos / NanosPerMicroseconds;
      int digits = Util.decimalDigits(us);
      result = result + us + "us ";
      nanos %= NanosPerMicroseconds;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 3);
      sb_length += 3;
    }
    if (nanos > 0) {
      int digits = Util.decimalDigits(nanos);
      result = result + nanos + "ns ";

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 3);
      sb_length += 3;
    }

    if (result.length() == 0) {
      Util.abandonEphemeralStringBuilder(t, sb_length, sb_capacity);

      // Allocate String so that it can be consistently garbage collected.
      Util.ephemeralString(t, 2);
      return new String("0s");
    }
    else {
      // Assume compiler optimizes this (so there's not a String
      // followed by a substring operation).
      Util.ephemeralStringBuilderToString(t, sb_length - 1, sb_capacity);
      return result.substring(0, result.length() - 1);
    }
  }
}