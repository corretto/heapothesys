// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class ServerLog extends ExtrememObject {
  RelativeTimeMetrics sales_xact, expire_history,
    replace_customers, replace_products, do_nothing;

  // For sales transactions
  int xact_batches;
  int min_xact_per_batch = Integer.MAX_VALUE;
  int max_xact_per_batch;
  int total_xact;

  // For browsing histories
  int history_batches;
  int min_history_per_batch = Integer.MAX_VALUE;
  int max_history_per_batch;
  int total_histories;

  // For customer replacements
  int customer_batches;
  int min_customer_per_batch = Integer.MAX_VALUE;
  int max_customer_per_batch;
  int total_customers;

  // For product replacements
  int product_batches;
  int min_product_per_batch = Integer.MAX_VALUE;
  int max_product_per_batch;
  int total_products;

  int total_do_nothings;

  ServerLog(ExtrememThread t, LifeSpan ls) {
    super(t, ls);

    sales_xact = new RelativeTimeMetrics(t, ls);
    expire_history = new RelativeTimeMetrics(t, ls);
    replace_customers = new RelativeTimeMetrics(t, ls);
    replace_products = new RelativeTimeMetrics(t, ls);
    do_nothing = new RelativeTimeMetrics(t, ls);

    MemoryLog log = t.memoryLog();
    // Account for reference fields sales_xact, expire_history,
    // replace_customers, replace_products, do_nothing;
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 5);
    // Account for int fields xact_batches, min_xact_per_batch,
    // max_xact_per_batch, total_xact, history_batches,
    // min_history_per_batch, max_history_per_batch, total_histories,
    // customer_batches, min_customer_per_batch,
    // max_customer_per_batch, total_customers, product_batches,
    // min_product_per_batch, max_product_per_batch, total_products,
    // total_do_nothings
    log.accumulate(ls, MemoryFlavor.ObjectRSB,
                   Polarity.Expand, 17 * Util.SizeOfInt);
  }

  void logTransactions(ExtrememThread t, AbsoluteTime release, int count) {
    xact_batches++;
    if (count > max_xact_per_batch)
      max_xact_per_batch = count;
    if (count < min_xact_per_batch)
      min_xact_per_batch = count;
    total_xact += count;
    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    sales_xact.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logHistories(ExtrememThread t, AbsoluteTime release, int count) {
    history_batches++;
    if (count > max_history_per_batch)
      max_history_per_batch = count;
    if (count < min_history_per_batch)
      min_history_per_batch = count;
    total_histories += count;
    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    expire_history.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logCustomers(ExtrememThread t, AbsoluteTime release, int count) {
    customer_batches++;
    if (count > max_customer_per_batch)
      max_customer_per_batch = count;
    if (count < min_customer_per_batch)
      min_customer_per_batch = count;
    total_customers += count;
    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    replace_customers.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logProducts(ExtrememThread t, AbsoluteTime release, int count) {
    product_batches++;
    if (count > max_product_per_batch)
      max_product_per_batch = count;
    if (count < min_product_per_batch)
      min_product_per_batch = count;
    total_products += count;
    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    replace_products.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void logDoNothings(ExtrememThread t, AbsoluteTime release) {
    total_do_nothings++;
    AbsoluteTime now = AbsoluteTime.now(t);
    RelativeTime delta = now.difference(t, release);
    long delta_microseconds = (
      delta.seconds() * 1000000 + delta.nanoseconds() / 1000);
    do_nothing.addToLog(delta_microseconds);
    now.garbageFootprint(t);
    delta.garbageFootprint(t);
  }

  void report(ExtrememThread t, String label, boolean reportCSV) {
    int min, max;
    float average;

    Report.output("");
    Report.output("Server ", label, " summary");
    Report.output("");
    Report.output("Sales transaction processing:");

    String s = Integer.toString(xact_batches);
    int l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("batches,", s);
    else
      Report.output("          batches: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(total_xact);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("total,", s);
    else
      Report.output("            total: ", s);
    Util.abandonEphemeralString(t, l);

    if (total_xact > 0) {
      min = min_xact_per_batch;
      max = max_xact_per_batch;
      average = ((float) total_xact) / xact_batches;
    } else {
      min = 0;
      max = 0;
      average = 0.0f;
    }

    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min per batch,", s);
    else
      Report.output("    min per batch: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max per batch,", s);
    else
      Report.output("    max per batch: ", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average per batch,", s);
    else
      Report.output("average per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    Report.output();
    sales_xact.report(t, reportCSV);

    Report.output();
    Report.output("Browsing history processing:");

    s = Integer.toString(history_batches);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("batches,", s);
    else
      Report.output("          batches: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(total_histories);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("total,", s);
    else
      Report.output("            total: ", s);
    Util.abandonEphemeralString(t, l);

    if (total_histories > 0) {
      min = min_history_per_batch;
      max = max_history_per_batch;
      average = ((float) total_histories) / history_batches;
    } else {
      min = 0;
      max = 0;
      average = 0.0f;
    }

    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min per batch,", s);
    else
      Report.output("    min per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max per batch,", s);
    else
      Report.output("    max per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average per batch,", s);
    else
      Report.output("average per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    Report.output();
    expire_history.report(t, reportCSV);
    Report.output();
    Report.output("Customer replacement processing:");

    s = Integer.toString(customer_batches);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("batches,", s);
    else
      Report.output("          batches: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(total_customers);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("total,", s);
    else
      Report.output("            total: ", s);
    Util.abandonEphemeralString(t, l);

    if (total_customers > 0) {
      min = min_customer_per_batch;
      max = max_customer_per_batch;
      average = ((float) total_customers) / customer_batches;
    } else {
      min = 0;
      max = 0;
      average = 0.0f;
    }

    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min per batch,", s);
    else
      Report.output("    min per batch: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max per batch,", s);
    else
      Report.output("    max per batch: ", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average per batch,", s);
    else
      Report.output("average per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    Report.output();
    replace_customers.report(t, reportCSV);

    Report.output();
    Report.output("Product replacement processing:");

    s = Integer.toString(product_batches);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("batches,", s);
    else
      Report.output("          batches: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(total_products);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("total,", s);
    else
      Report.output("            total: ", s);
    Util.abandonEphemeralString(t, l);

    if (total_products > 0) {
      min = min_product_per_batch;
      max = max_product_per_batch;
      average = ((float) total_products) / product_batches;
    } else {
      min = 0;
      max = 0;
      average = 0.0f;
    }

    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("min per batch,", s);
    else
      Report.output("    min per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("max per batch,", s);
    else
      Report.output("    max per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average per batch,", s);
    else
      Report.output("average per batch: ", s);
    Util.abandonEphemeralString(t, l);
                  
    Report.output();
    replace_products.report(t, reportCSV);
    Report.output();
    
    Report.output("Do nothing processing:");
    do_nothing.report(t, reportCSV);
    Report.output();
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    
    // Account for reference fields sales_xact, expire_history,
    // replace_customers, replace_products, do_nothing;
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 5);
    // Account for int fields xact_batches, min_xact_per_batch,
    // max_xact_per_batch, total_xact, history_batches,
    // min_history_per_batch, max_history_per_batch, total_histories,
    // customer_batches, min_customer_per_batch,
    // max_customer_per_batch, total_customers, product_batches,
    // min_product_per_batch, max_product_per_batch, total_products,
    // total_do_nothings
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 17 * Util.SizeOfInt);

    // Account for objects referenced from sales_xact, expire_history,
    // replace_customers, replace_products, do_nothing;
    sales_xact.tallyMemory(log, ls, p);
    expire_history.tallyMemory(log, ls, p);
    replace_customers.tallyMemory(log, ls, p);
    replace_products.tallyMemory(log, ls, p);
    do_nothing.tallyMemory(log, ls, p);
  }
}
