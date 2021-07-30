// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 *  Each ResponseTimeMeasurements instance maintains a log of the
 *  Configuration.ResponseTimeMeasurements() most recently logged
 *  response times.  This log is used to calculate P50, P90, P99,
 *  P99.9, P99.99, P99.999, and P100 response times for this
 *  quantity. 
 */
class ResponseTimeMeasurements extends ExtrememObject {

  private final int total_entries;

  private int logged_entries;
  private int first_entry;

  private long max_logged;
  private long min_logged;

  // Each log entry is typically represented in microsecond units.
  private final long[] log;

  // If more than max_entries are logged, some (arbitrarily selected)
  // entries will be overwritten and will not contribute to final results.
  public ResponseTimeMeasurements(ExtrememThread t, LifeSpan ls, int max_entries) {
    super(t, ls);
    this.total_entries = max_entries;

    log = new long[total_entries];
    first_entry = 0;
    logged_entries = 0;

    MemoryLog log = t.memoryLog();

    // Account for 1 reference fields: log
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 1);

    // Account for 3 int fields: total_entries, logged_entries, first_entry
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Polarity.Expand, 3 * Util.SizeOfInt);

    // Account for log array
    log.accumulate(ls, MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    log.accumulate(ls, MemoryFlavor.ArrayRSB, Polarity.Expand,
                   total_entries * Util.SizeOfLong);
  }

  void addToLog(ResponseTimeMeasurements other) {
    if (total_entries > 0) {
      other.prep_for_reporting();
      for (int i = 0; i < other.logged_entries; i++) {
        addToLog(other.log[(other.first_entry + i) % total_entries]);
      }
    }
  }

  // value may be negative due to imprecision in behavior of sleep()
  void addToLog(long microseconds) {
    if (total_entries > 0) {
      if (logged_entries == 0) {
        max_logged = min_logged = microseconds;
      } else {
        if (microseconds < min_logged)
          min_logged = microseconds;
        if (microseconds > max_logged)
          max_logged = microseconds;
      }
      if (logged_entries < total_entries) {
        log[logged_entries++] = microseconds;
      } else {
        log[first_entry++] = microseconds;
        if (first_entry >= total_entries)
          first_entry = 0;
      }
    }
  }

  int count() {
    return logged_entries;
  }

  private void prep_for_reporting() {
    if (logged_entries > 0) {
      if (logged_entries < total_entries) {
        java.util.Arrays.sort(log, 0, logged_entries);
      } else {
        java.util.Arrays.sort(log);
      }
      log[0] = min_logged;
      log[logged_entries - 1] = max_logged;
    }
  }

  void report(ExtrememThread t, boolean reportCSV) {
    String s;
    int l;

    if (logged_entries > 0) {
      prep_for_reporting();
      long p100     = log[logged_entries - 1];
      long  p50     = (logged_entries > 1)? log[logged_entries / 2 - 1]: -1;
      long  p95     = (logged_entries >= 100)? log[(int)(logged_entries * 0.95)]: -1;
      long  p99     = (logged_entries >= 100)? log[(int)(logged_entries * 0.99)]: -1;
      long  p99_9   = (logged_entries >= 1000)? log[(int)(logged_entries * 0.999)]: -1;
      long  p99_99  = (logged_entries >= 10000)? log[(int)(logged_entries * 0.9999)]: -1;
      long  p99_999 = (logged_entries >= 100000)? log[(int)(logged_entries * 0.99999)]: -1;
    
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine("[");
      }
      s = Integer.toString(logged_entries);
      l = s.length();
      Util.ephemeralString(t, l);
      Report.outputNoLine(s);
      Util.abandonEphemeralString(t, l);
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine("]: P50(");
      }
      if (p50 >= 0) {
        s = Long.toString(p50);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P95(");
      }
      if (p95 >= 0) {
        s = Long.toString(p95);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P99(");
      }
      if (p99 >= 0) {
        s = Long.toString(p99);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P99.9(");
      }
      if (p99_9 >= 0) {
        s = Long.toString(p99_9);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P99.99(");
      }
      if (p99_99 >= 0) {
        s = Long.toString(p99_99);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P99.999(");
      }
      if (p99_999 >= 0) {
        s = Long.toString(p99_999);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.outputNoLine(", ");
      } else {
        Report.outputNoLine(") P100(");
      }
      if (p100 >= 0) {
        s = Long.toString(p100);
        l = s.length();
        Util.ephemeralString(t, l);
        Report.outputNoLine(s);
        Util.abandonEphemeralString(t, l);
      } else {
        Report.outputNoLine("*");
      }
      if (reportCSV) {
        Report.output("");
      } else {
        Report.output(")");
      }
    } else if (reportCSV) {
      Report.output("0, *, *, *, *, *, *, *");
    } else {
      Report.output("[0]: P50(*) P95(*) P99(*) P99.9(*) P99.99(*) P99.999(*) P100(*)");
    }
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for 1 reference field: log
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);

    // Account for 3 int fields: total_entries, logged_entries, first_entry
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 3 * Util.SizeOfInt);

    // Account for log array
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ArrayRSB, p, total_entries * Util.SizeOfLong);
  }
}
