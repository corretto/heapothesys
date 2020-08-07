// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.time.Instant;

class AbsoluteTime extends HighResolutionTime {
  static final long epoch_delta;

  static {
    epoch_delta = System.nanoTime();
  }

  /**
   * Construct an AbsoluteTime object representing the time at which
   * the current JVM began to execute, which is presumed to have an
   * Ephemeral life time.
   *
   * The code that invokes this constructor is expected to account for
   * the returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime(ExtrememThread t) {
    super (t, 0, 0);
    // Memory behavior logged in superclass.
  }

  /**
   * Construct an AbsoluteTime object representing the time s seconds
   * plus ns nanosexonds from when the current JVM began to execute,
   * which is presumed to have an Ephemeral life time.
   *
   * The code that invokes this constructor is expected to account for
   * the returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime(ExtrememThread t, long s, int ns) {
    super (t, s, ns);
    // Memory behavior logged in superclass.
  }

  /**
   * Construct an Ephemeral AbsoluteTime object representing the same
   * moment in time as pattern.
   *
   * The code that invokes this constructor is expected to account for
   * the returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime(ExtrememThread t, AbsoluteTime pattern) {
    super (t, pattern.seconds(), pattern.nanoseconds());
    // Memory behavior logged in superclass.
  }

  /**
   * Return an Ephemeral AbsoluteTime instance representing the current
   * time.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage.
   *
   * Aside: Invoking this service consumes approximately 500 ns of CPU
   * time on typical AWS cloud desktop servers.
   */
  static AbsoluteTime now(ExtrememThread t) {

    long ns = System.nanoTime() - epoch_delta;
    return new AbsoluteTime(t, ns / 1000000000L, (int) (ns % 1000000000L));
  }

  /**
   * Suspend execution of the current thread until this time, returning
   * an Ephemeral AbsoluteTime instance representing the current time
   * if this time is in the past, representing a copy of this otherwise.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime sleep(ExtrememThread t) {
    long ms_delay = 0;
    int ns_delay = 0;
    while (true) {
      AbsoluteTime original_now = now(t);
      RelativeTime delay = this.difference(t, original_now);
      delay.garbageFootprint(t);
      if (!delay.isNegative()) {
	try {
	  // I have confirmed through instrumentation that Thread.sleep()
	  // sometimes delays less that the intended amount of time.  This
	  // can result in certain latency measurements being reported as
	  // negative.  Since I can't "FIX" the implementation of
	  // Thread.sleep(), I'll instead round any negative latency
	  // measurements up to zero whenever a negative latency is added to
	  // a RelativeTimeMetrics log.
	  Thread.sleep(delay.s * 1000 + delay.ns / 1000000,
			delay.ns % 1000000);
	  original_now.garbageFootprint(t);
	  return new AbsoluteTime(t, this);
	} catch (InterruptedException x) {
	  original_now.garbageFootprint(t);
	  original_now = AbsoluteTime.now (t);		// try it again
	}
      } else 
	return original_now;
    }
  }

  /**
   * Return an Ephemeral AbsoluteTime instance representing the sum
   * of this plus delta.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addRelative(ExtrememThread t, RelativeTime delta) {
    long s = this.s + delta.s;
    int ns = this.ns + delta.ns;
    // Constructor will normalize representation
    return new AbsoluteTime (t, s, ns);
  }

  /**
   * Return an Ephemeral AbsoluteTime instance representing the result
   * of subtracting delta from this.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime subtractRelative(ExtrememThread t, RelativeTime delta) {
    long s = this.s - delta.s;
    int ns = this.ns - delta.ns;
    // Constructor will normalize representation
    return new AbsoluteTime (t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the
   * difference between this time and that time.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime difference(ExtrememThread t, AbsoluteTime that) {
    long s = this.s - that.s;
    int ns = this.ns - that.ns;
    // Constructor will normalize representation
    return new RelativeTime (t, s, ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and day days.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addDay(ExtrememThread t, long day) {
    long s = this.s + day * 24 * 3600;
    return new AbsoluteTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and h hours.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addHours(ExtrememThread t, long h) {
    long s = this.s + h * 3600;
    return new AbsoluteTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and m minutes.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addMinutes(ExtrememThread t, long m) {
    long s = this.s + m * 60;
    return new AbsoluteTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and s seconds.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addSeconds(ExtrememThread t, long s) {
    long abs_s = this.s + s;
    return new AbsoluteTime(t, abs_s, this.ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and ms milliseconds.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addMillis(ExtrememThread t, int ms) {
    long s = this.s + ms / 1000;
    int ns = this.ns + (ms % 1000) * HighResolutionTime.NanosPerMilli;
    return new AbsoluteTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and us microseconds.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addMicros(ExtrememThread t, int us) {
    int ns = (this.ns +
	       ((us % HighResolutionTime.MicrosPerSecond)
		* HighResolutionTime.NanosPerMicro));
    long s = this.s + us / HighResolutionTime.MicrosPerSecond;
    return new AbsoluteTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral AbsoluteTime instance representing the sum
   * of this and ns nanoseconds.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime addNanos(ExtrememThread t, int delta_nanos) {
    int ns = this.ns + delta_nanos;
    // Constructor will normalize representation
    return new AbsoluteTime(t, this.s, ns);
  }

  /**
   * Return 0 if this and other represent same time, -1 if this is
   * chronologically less than other, 1 if this is chronologically
   * greater than other.
   */
  int compare(AbsoluteTime other) {
    if (this.s == other.s) {
      if (this.ns < other.ns) return -1;
      else if (this.ns == other.ns) return 0;
      else return 1;
    } else if (this.s > other.s) return 1;
    else return -1;
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
    String part1, part2, result;
    part1 = "AbsoluteTime: ";
    part2 = super.toString(t);
    int part1_len = part1.length();
    int part2_len = part2.length();

    int capacity = Util.ephemeralStringBuilder(t, part1_len);
    capacity = Util.ephemeralStringBuilderAppend(t, part1_len,
						 capacity, part2_len);
    Util.ephemeralStringBuilderToString(t, part1_len + part2_len, capacity);
    result = part1 + part2;
    Util.abandonEphemeralString(t, part2_len);
    return result;
  }
}