// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class RelativeTime extends HighResolutionTime {

  /**
   * Construct a RelativeTime object representing no passage of time.
   *
   * The code that invokes this constructor is expected to account for
   * the returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime(ExtrememThread t) {
    this(t, 0, 0);
  }

  /**
   * Construct an Ephemeral RelativeTime object representing s seconds
   * plus ns nanoseconds from the moment in time at which the current JVM
   * began to execute.
   *
   * The code that invokes this constructor is expected to account for
   * the returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime(ExtrememThread t, long s, int ns) {
    super(t, s, ns);
    // Memory behavior logged in superclass.
  }

  // I have confirmed through instrumentation that Thread.sleep()
  // sometimes delays less that the intended amount of time.  This can
  // result in certain latency measurements being reported as
  // negative.  For efficiency, would prefer not to check the current
  // time after Thread.sleep() returns, and would prefer not to issue
  // multiple Thread.sleep() requests.  Since fixing Thread.sleep() is
  // beyond the scope of the extremem testbed effort, the current
  // workaround is to round any negative latency measurements up to
  // zero within RelativeTimeMetrics.addToLog().

  /**
   * Sleep until this AbsoluteTime has been reached, returning an
   * Ephemeral approximation of the wakeup time.
   *
   * The code that invokes this service is expected to account for the
   * returned AbsoluteTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  AbsoluteTime sleep(ExtrememThread t) {
    boolean sufficient_delay = false;
    AbsoluteTime now = AbsoluteTime.now(t);
    AbsoluteTime awake = now.addRelative(t, this);
    while (true) {
      RelativeTime delay = awake.difference(t, now);
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
          Thread.sleep (delay.s * MillisPerSecond + delay.ns / NanosPerMilli,
                        delay.ns % NanosPerMilli);
          now.garbageFootprint(t);
          return awake;
        } catch (InterruptedException x) {
          now.garbageFootprint(t);
          now = AbsoluteTime.now (t);           // try it again
        }
      }
    }
  }

  /**
   * Return an Ephemeral RelativeTime instance representing the time
   * span between this and smaller. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime delta(ExtrememThread t, RelativeTime smaller) {
    long s = this.s - smaller.s;
    int ns = this.ns - smaller.ns;
    return new RelativeTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the
   * result of subtracting delta from this. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime subtractRelative(ExtrememThread t, RelativeTime delta) {
    long s = this.s - delta.s;
    int ns = this.ns - delta.ns;
    return new RelativeTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and delta. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addRelative(ExtrememThread t, RelativeTime delta) {
    long s = this.s + delta.s;
    int ns = this.ns + delta.ns;
    return new RelativeTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and day days.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addDay(ExtrememThread t, long day) {
    long s = this.s + day * 24 * 3600;
    return new RelativeTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and h hours.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addHours(ExtrememThread t, long h) {
    long s = this.s + h * 3600;
    return new RelativeTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and m minutes. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addMinutes(ExtrememThread t, long m) {
    long s = this.s + m * 60;
    return new RelativeTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and s seconds. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addSeconds(ExtrememThread t, long delta_s) {
    long s = this.s + delta_s;
    return new RelativeTime(t, s, this.ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and ms milliseconds. 
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addMillis(ExtrememThread t, int ms) {
    long s = this.s + ms / 1000;
    int ns = this.ns + (ms % 1000) * HighResolutionTime.NanosPerMilli;
    return new RelativeTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and us microseconds.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addMicros(ExtrememThread t, int us) {
    int ns = (
      this.ns + ((us % HighResolutionTime.MicrosPerSecond)
                 * HighResolutionTime.NanosPerMicro));
    long s = this.s + us / HighResolutionTime.MicrosPerSecond;
    return new RelativeTime(t, s, ns);
  }
  
  /**
   * Return a new Ephemeral RelativeTime instance representing the sum
   * of this and ns nanoseconds.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime addNanos(ExtrememThread t, int delta_ns) {
    int ns = this.ns + delta_ns % HighResolutionTime.NanosPerSecond;
    long s = this.s + delta_ns / HighResolutionTime.NanosPerSecond;
    return new RelativeTime(t, s, ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the
   * product of this and factor.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime multiplyBy(ExtrememThread t, int factor) {
    long ns = this.ns * (long) factor;
    long s = this.s * factor;
    s += ns / NanosPerSecond;
    ns = ns % NanosPerSecond;
    return new RelativeTime(t, s, (int) ns);
  }

  /**
   * Return a new Ephemeral RelativeTime instance representing the
   * quotient of dividing this by divisor.
   *
   * The code that invokes this service is expected to account for the
   * returned RelativeTime object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  RelativeTime divideBy(ExtrememThread t, int divisor) {
    long ns = this.ns / (long) divisor;
    long s = this.s / divisor;
    return new RelativeTime(t, s, (int) ns);
  }

  /**
   * Return a primitive double representing the quotient of dividing this by divisor.
   */
  double divideBy(ExtrememThread t, RelativeTime divisor) {
    double my_time = this.s + this.ns / 1_000_000_000.0;
    double divisor_time = divisor.s + divisor.ns / 1_000_000_000.0;
    return my_time / divisor_time;
  }

  /**
   * Return 0 if this and other represent same time, -1 if this is
   * chronologically less than other, 1 if this is chronologically
   * greater than other.
   */
  int compare(RelativeTime other) {
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
    String result, part1, part2;
    part1 = "RelativeTime: ";
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
