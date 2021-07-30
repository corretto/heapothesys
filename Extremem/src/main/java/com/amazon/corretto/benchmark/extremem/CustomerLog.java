// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * CustomerLog records performance metrics associated with a
 * CustomerThread.  Each CustomerThread is associated with a single
 * companion CustomerLog to reduce synchronization overhead.
 *
 * Before a CustomerThread terminates its execution, it either prints
 * out its own log, or it accumulates its log into the global log so
 * that it can be printed.
 */
class CustomerLog extends ExtrememObject {
  RelativeTimeMetrics preparer, purchaser, saver, abandoner, loser;

  int engagements;

  int min_any = Integer.MAX_VALUE;
  int max_any;
  int total_any;

  int min_all = Integer.MAX_VALUE;
  int max_all;
  int total_all;

  int min_saved = Integer.MAX_VALUE;
  int max_saved;
  int total_previously_saved;

  // selection is saved + (all > 0?) all: any
  int min_selection = Integer.MAX_VALUE;
  int max_selection;
  int total_selection;

  // Note: engagements equals the sum of
  // total_purchased, total_saved, total_abandoned, total_do_nothings
  int total_purchased;
  int total_saved;
  int total_abandoned;
  int total_do_nothings;

  ResponseTimeMeasurements prepare_response_times;
  ResponseTimeMeasurements purchase_response_times;
  ResponseTimeMeasurements save_for_later_response_times;
  ResponseTimeMeasurements abandonment_response_times;
  ResponseTimeMeasurements do_nothing_response_times;

  CustomerLog(ExtrememThread t, LifeSpan ls, int response_time_measurements) {
    super(t, ls);
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    // Account for preparer, purchaser, saver, abandoner, loser,
    // prepare_response_times, purchase_response_times, save_for_later_response_times,
    // abandonment_response_times, do_nothing_response_times
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 10);
    // Account for 17 int fields: engagements, min_any, max_any, total_any,
    // min_all, max_all, total_all, min_saved, max_saved,
    // total_previously_saved, min_selection, max_selection, total_selection,
    // total_purchased, total_saved, total_abandoned, total_do_nothings
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, 17 * Util.SizeOfInt);

    preparer = new RelativeTimeMetrics(t, ls);
    purchaser = new RelativeTimeMetrics(t, ls);
    saver = new RelativeTimeMetrics(t, ls);
    abandoner = new RelativeTimeMetrics(t, ls);
    loser = new RelativeTimeMetrics(t, ls);

    prepare_response_times = new ResponseTimeMeasurements(t, ls, response_time_measurements);
    purchase_response_times = new ResponseTimeMeasurements(t, ls, response_time_measurements);
    save_for_later_response_times = new ResponseTimeMeasurements(t, ls, response_time_measurements);
    abandonment_response_times = new ResponseTimeMeasurements(t, ls, response_time_measurements);
    do_nothing_response_times = new ResponseTimeMeasurements(t, ls, response_time_measurements);
  }

  // This information is redundant with purchaser.  I'll gather it
  // here, and ignore it when logging purchases.
  void logPrepareToThink(CustomerThread t, AbsoluteTime release,
                         int matched_all, int matched_any, int saved4now) {
    // size of selection set is saved4now +
    //  (matched_all > 0)? matched_all: matched_any
    engagements++;

    int selection_size = (saved4now +
                          ((matched_all > 0)? matched_all: matched_any));
    total_all += matched_all;
    total_any += matched_any;
    total_previously_saved += saved4now;
    total_selection += selection_size;

    if (matched_all < min_all)
      min_all = matched_all;
    if (matched_all > max_all)
      max_all = matched_all;
    if (matched_any < min_any)
      min_any = matched_any;
    if (matched_any > max_any)
      max_any = matched_any;
    if (saved4now < min_saved)
      min_saved = saved4now;
    if (saved4now > max_saved)
      max_saved = saved4now;
    if (selection_size < min_selection)
      min_selection = selection_size;
    if (selection_size > max_selection)
      max_selection = selection_size;

    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    preparer.addToLog(delta_microseconds);
    prepare_response_times.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logPurchase(CustomerThread t, AbsoluteTime release) {
    total_purchased++;

    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    purchaser.addToLog(delta_microseconds);
    purchase_response_times.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void log4Later(CustomerThread t, AbsoluteTime release) {
    total_saved++;

    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    saver.addToLog(delta_microseconds);
    save_for_later_response_times.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logAbandonment(CustomerThread t, AbsoluteTime release) {
    total_abandoned++;

    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    abandoner.addToLog(delta_microseconds);
    abandonment_response_times.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logNoChoice(CustomerThread t, AbsoluteTime release) {
    total_do_nothings++;

    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    loser.addToLog(delta_microseconds);
    do_nothing_response_times.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void report(ExtrememThread t, String label, boolean reportCSV) {
    String s;
    int l;

    Report.output("");
    Report.output("Customer Thread ", label, " summary");
    Report.output("");

    s = Integer.toString(engagements);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("Total engagements,", s);
    else
      Report.output(" Total engagements: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(total_purchased);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("purchases,", s);
    else
      Report.output("         purchases: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Integer.toString(total_saved);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("saves for later,", s);
    else
      Report.output("   saves for later: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Integer.toString(total_abandoned);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("abandonments,", s);
    else
      Report.output("      abandonments: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Integer.toString(total_do_nothings);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("do-nothings,", s);
    else
      Report.output("       do-nothings: ", s);
    Util.abandonEphemeralString(t, l);
    
    Report.output("");
    Report.output("Products matching all criteria:");

    s = Integer.toString(max_all);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max,", s);
    else
      Report.output("               max: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(min_all);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min,", s);
    else
      Report.output("               min: ", s);
    Util.abandonEphemeralString(t, l);

    if (engagements > 0)
      s = Float.toString(((float) total_all / engagements));
    else
      s = new String("0");
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average,", s);
    else
      Report.output("           average: ", s);
    Util.abandonEphemeralString(t, l);
                  
    Report.output("");
    Report.output("Products matching any criteria:");

    s = Integer.toString(max_any);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max,", s);
    else
      Report.output("               max: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(min_any);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min,", s);
    else
      Report.output("               min: ", s);
    Util.abandonEphemeralString(t, l);

    if (engagements > 0)
      s = Float.toString(((float) total_any / engagements));
    else
      s = new String("0");
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average,", s);
    else
      Report.output("           average: ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Products previously saved for now:");

    s = Integer.toString(max_saved);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max,", s);
    else
      Report.output("               max: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(min_saved);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min,", s);
    else
      Report.output("               min: ", s);
    Util.abandonEphemeralString(t, l);

    if (engagements > 0)
      s = Float.toString(((float) total_previously_saved / engagements));
    else
      s = new String("0");
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average,", s);
    else
      Report.output("           average: ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Products available for selection:");
    
    s = Integer.toString(max_selection);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max,", s);
    else
      Report.output("               max: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(min_selection);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min,", s);
    else
      Report.output("               min: ", s);
    Util.abandonEphemeralString(t, l);

    if (engagements > 0)
      s = Float.toString(((float) total_selection / engagements));
    else
      s = new String("0");
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average,", s);
    else
      Report.output("           average: ", s);
    Util.abandonEphemeralString(t, l);
    
    Report.output("");
    Report.output("Timeliness of preparation");
    preparer.report(t, reportCSV);
    Report.output("");
    Report.output("Timeliness of purchases");
    purchaser.report(t, reportCSV);
    Report.output("");
    Report.output("Timeliness of saves for later");
    saver.report(t, reportCSV);
    Report.output("");
    Report.output("Timeliness of abandonment");
    abandoner.report(t, reportCSV);
    Report.output("");
    Report.output("Timeliness of loss");
    loser.report(t, reportCSV);
    Report.output("");
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for preparer, purchaser, saver, abandoner, loser,
    // prepare_response_times, purchase_response_times, save_for_later_response_times,
    // abandonment_response_times, do_nothing_response_times
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 10);
    // Account for 17 int fields:  engagements, min_any, max_any,
    // total_any, min_all, max_all, total_all, min_saved, max_saved,
    // total_previously_saved, min_selection, max_selection, total_selection,
    //  total_purchaesd, total_saved, total_abandoned, total_do_nothings
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 17 * Util.SizeOfInt);
    preparer.tallyMemory(log, ls, p);
    purchaser.tallyMemory(log, ls, p);
    saver.tallyMemory(log, ls, p);
    abandoner.tallyMemory(log, ls, p);
    loser.tallyMemory(log, ls, p);

    prepare_response_times.tallyMemory(log, ls, p);
    purchase_response_times.tallyMemory(log, ls, p);
    save_for_later_response_times.tallyMemory(log, ls, p);
    abandonment_response_times.tallyMemory(log, ls, p);
    do_nothing_response_times.tallyMemory(log, ls, p);
  }
}
