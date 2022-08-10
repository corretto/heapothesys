// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class UpdateThread extends ExtrememThread {
  // There are two attention points: (0) Rebuild Customers, (1) Rebuild Products
  final static int TotalAttentionPoints = 2;

  // Identifies the point of attention for the next release of this server thread.
  private int attention;

  private final Configuration config;
  private final Products all_products;
  private final Customers all_customers;
  private AbsoluteTime next_release_time;
  private final AbsoluteTime end_simulation_time;

  private long customers_rebuild_count = 0;
  private long replaced_customers_min = 0;
  private long replaced_customers_max = 0;
  private long replaced_customers_total = 0;
  private long replaced_customers_micros_min = 0;
  private long replaced_customers_micros_max = 0;
  private long replaced_customers_micros_total = 0;

  private long products_rebuild_count = 0;
  private long replaced_products_min = 0;
  private long replaced_products_max = 0;
  private long replaced_products_total = 0;
  private long replaced_products_micros_min = 0;
  private long replaced_products_micros_max = 0;
  private long replaced_products_micros_total = 0;

  // private final MemoryLog alloc_accumulator;
  // private final MemoryLog garbage_accumulator;

  UpdateThread(Configuration config, long random_seed, Products all_products, Customers all_customers,
               AbsoluteTime first_release, AbsoluteTime end_simulation) {
      super (config, random_seed);
      final Polarity Grow = Polarity.Expand;
      final MemoryLog log = this.memoryLog();
      final MemoryLog garbage = this.garbageLog();

      this.attention = 0;
      this.config = config;

      this.setLabel("PhasedUpdaterThread");
      // Util.convertEphemeralString(this, LifeSpan.NearlyForever, label.length());

      this.all_customers = all_customers;
      this.all_products = all_products;

      // Replaced every period, typically less than 2 minutes for ServerThread.
      this.next_release_time = new AbsoluteTime(this, first_release);
      this.next_release_time.changeLifeSpan(this, LifeSpan.TransientShort);

      this.end_simulation_time = end_simulation;

      // this.accumulator = accumulator;
      // this.alloc_accumulator = alloc_accumulator;
      // this.garbage_accumulator = garbage_accumulator;

      // Account for reference fields label, all_products,
      // all_customers, sales_queue, browsing_queue,
      // end_simulation_time, history, accumulator, alloc_accumulator,
      // garbage_accumulator, next_release_time,
      // customer_replacement_time, product_replacement_time
      // log.accumulate(LifeSpan.NearlyForever,
      //                MemoryFlavor.ObjectReference, Grow, 13);
      // Account for int field attention.
      // log.accumulate(LifeSpan.NearlyForever,
      //                MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }
  
  public void runExtreme() {
    long customers_rebuild_count = 0;
    long replaced_customers_min = 0;
    long replaced_customers_max = 0;
    long replaced_customers_total = 0;
    long replaced_customers_micros_min = 0;
    long replaced_customers_micros_max = 0;
    long replaced_customers_micros_total = 0;

    long products_rebuild_count = 0;
    long replaced_products_min = 0;
    long replaced_products_max = 0;
    long replaced_products_total = 0;
    long replaced_products_micros_min = 0;
    long replaced_products_micros_max = 0;
    long replaced_products_micros_total = 0;

    while (true) {
      // If the simulation will have ended before we wake up, don't
      // even bother to sleep.
      if (next_release_time.compare(end_simulation_time) >= 0)
        break;

      AbsoluteTime now = next_release_time.sleep(this);
      AbsoluteTime after = now;

      RelativeTime delta;
      long duration;            // microseconds

      // In an earlier implementation, termination of the thread was
      // determined by comparing next_release_time against
      // end_simulation_time. In the case that the thread falls
      // hopelessly behind schedule, the thread "never" terminates.
      if (now.compare(end_simulation_time) >= 0)
        break;

      Trace.msg(4, "PhasedUpdateThread processing with attention: ", Integer.toString(attention));

      switch (attention) {
        case 0:
          // Update the Customers data base
          long customers_replaced = all_customers.rebuildCustomersPhasedUpdates(this);
          after = AbsoluteTime.now(this);
          delta = after.difference(this, now);
          duration = delta.microseconds();
          // now.garbageFootprint();
          // delta.garbageFootprint();
          if (customers_rebuild_count++ == 0) {
            replaced_customers_min = replaced_customers_max = replaced_customers_total = customers_replaced;
            replaced_customers_micros_min = replaced_customers_micros_max = replaced_customers_micros_total = duration;
          } else {
            replaced_customers_total += customers_replaced;
            if (customers_replaced < replaced_customers_min) {
              replaced_customers_min = customers_replaced;
            } else if (customers_replaced > replaced_customers_max) {
              replaced_customers_max = customers_replaced;
            }
            replaced_customers_micros_total += duration;
            if (duration < replaced_customers_micros_min) {
              replaced_customers_micros_min = duration;
            } else if (duration > replaced_customers_micros_max) {
              replaced_customers_micros_max = duration;
            }
          }
          break;
        case 1:
          // Update the Products data base
          long products_replaced = all_products.rebuildProductsPhasedUpdates(this);
          after = AbsoluteTime.now(this);
          delta = after.difference(this, now);
          duration = delta.microseconds();

          // now.garbageFootprint();
          // delta.garbageFootprint();
          if (products_rebuild_count++ == 0) {
            replaced_products_min = replaced_products_max = replaced_products_total = products_replaced;
            replaced_products_micros_min = replaced_products_micros_max = replaced_products_micros_total = duration;
          } else {
            replaced_products_total += products_replaced;
            if (products_replaced < replaced_products_min) {
              replaced_products_min = products_replaced;
            } else if (products_replaced > replaced_products_max) {
              replaced_products_max = products_replaced;
            }
            replaced_products_micros_total += duration;
            if (duration < replaced_products_micros_min) {
              replaced_products_micros_min = duration;
            } else if (duration > replaced_products_micros_max) {
              replaced_products_micros_max = duration;
            }
          }
          break;
        default:
          assert (false): " Unhandled attention point in PhasedUpdaterThread";
      }
      if (attention-- == 0)
        attention = TotalAttentionPoints - 1;

      // next_release_time.garbageFootprint(this);
      next_release_time = after.addRelative(this, config.PhasedUpdateInterval());
      // after.garbageFootprint(this);
      next_release_time.changeLifeSpan(this, LifeSpan.TransientShort);
    }
    Trace.msg(2, "Server ", label, " terminating.  Time is up.");

    updateReport(customers_rebuild_count, replaced_customers_min, replaced_customers_max, replaced_customers_total,
                 replaced_customers_micros_min, replaced_customers_micros_max, replaced_customers_micros_total,
                 products_rebuild_count, replaced_products_min, replaced_products_max, replaced_products_total,
                 replaced_products_micros_min, replaced_products_micros_max, replaced_products_micros_total);

    this.report(this);
  }

  synchronized void updateReport(long customers_rebuild_count, long replaced_customers_min, long replaced_customers_max,
                                 long replaced_customers_total, long replaced_customers_micros_min,
                                 long replaced_customers_micros_max, long replaced_customers_micros_total,
                                 long products_rebuild_count, long replaced_products_min, long replaced_products_max,
                                 long replaced_products_total, long replaced_products_micros_min,
                                 long replaced_products_micros_max, long replaced_products_micros_total) {

    this.customers_rebuild_count = customers_rebuild_count;
    this.replaced_customers_min = replaced_customers_min;
    this.replaced_customers_max = replaced_customers_max;
    this.replaced_customers_total = replaced_customers_total;
    this.replaced_customers_micros_min = replaced_customers_micros_min;
    this.replaced_customers_micros_max = replaced_customers_micros_max;
    this.replaced_customers_micros_total = replaced_customers_micros_total;
    this.products_rebuild_count = products_rebuild_count;
    this.replaced_products_min = replaced_products_min;
    this.replaced_products_max = replaced_products_max;
    this.replaced_products_total = replaced_products_total;
    this.replaced_products_micros_min = replaced_products_micros_min;
    this.replaced_products_micros_max = replaced_products_micros_max;
    this.replaced_products_micros_total = replaced_products_micros_total;
  }

  /* Every subclass overrides this method if its size differs from the size of its superclass.  */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Memory accounting not implemented
  }    

  void report(ExtrememThread t) {
    Report.acquireReportLock();
    Report.output();
    if (config.ReportCSV()) {
      Report.output("PhasedUpdater Thread report");
      String s = Long.toString(customers_rebuild_count);
      Report.output("Customer rebuild executions ,", s);
      s = Long.toString(replaced_customers_total);
      Report.output("Total replaced customers, ", s);
      s = Long.toString(replaced_customers_min);
      Report.output("Minimum replacements per execution, ", s);
      s = Long.toString(replaced_customers_max);
      Report.output("Maximum replacements per execution, ", s);
      double average = ((double) replaced_customers_total) / customers_rebuild_count;
      s = Double.toString(average);
      Report.output("Average replacements per execution, ", s);
      s = Long.toString(replaced_customers_micros_min);
      Report.output("Minimum execution time (us), ", s);
      s = Long.toString(replaced_customers_micros_max);
      Report.output("Maximum execution time (us), ", s);
      average = ((double) replaced_customers_micros_total) / customers_rebuild_count;
      s = Double.toString(average);
      Report.output("Average execution time (us), ", s);
      
      s = Long.toString(products_rebuild_count);
      Report.output("Products rebuild executions, ", s);
      s = Long.toString(replaced_products_total);
      Report.output("Total replaced products, ", s);
      s = Long.toString(replaced_products_min);
      Report.output("Minimum replacements per execution, ", s);
      s = Long.toString(replaced_products_max);
      Report.output("Maximum replacements per execution, ", s);
      average = ((double) replaced_products_total) / products_rebuild_count;
      s = Double.toString(average);
      Report.output("Average replacements per execution, ", s);
      s = Long.toString(replaced_products_micros_min);
      Report.output("Minimum execution time (us), ", s);
      s = Long.toString(replaced_products_micros_max);
      Report.output("Maximum execution time (us), ", s);
      average = ((double) replaced_products_micros_total) / products_rebuild_count;
      s = Double.toString(average);
      Report.output("Average execution time (us), ", s);
    } else {
      Report.output("PhasedUpdater Thread report");
      String s = Long.toString(customers_rebuild_count);
      Report.output("        Customer rebuild executions: ", s);
      s = Long.toString(replaced_customers_total);
      Report.output("           Total replaced customers: ", s);
      s = Long.toString(replaced_customers_min);
      Report.output(" Minimum replacements per execution: ", s);
      s = Long.toString(replaced_customers_max);
      Report.output(" Maximum replacements per execution: ", s);
      double average = ((double) replaced_customers_total) / customers_rebuild_count;
      s = Double.toString(average);
      Report.output(" Average replacements per execution: ", s);
      s = Long.toString(replaced_customers_micros_min);
      Report.output("       Minimum execution time (us): ", s);
      s = Long.toString(replaced_customers_micros_max);
      Report.output("       Maximum execution time (us): ", s);
      average = ((double) replaced_customers_micros_total) / customers_rebuild_count;
      s = Double.toString(average);
      Report.output("       Average execution time (us): ", s);
      
      s = Long.toString(products_rebuild_count);
      Report.output("        Products rebuild executions: ", s);
      s = Long.toString(replaced_products_total);
      Report.output("            Total replaced products: ", s);
      s = Long.toString(replaced_products_min);
      Report.output(" Minimum replacements per execution: ", s);
      s = Long.toString(replaced_products_max);
      Report.output(" Maximum replacements per execution: ", s);
      average = ((double) replaced_products_total) / products_rebuild_count;
      s = Double.toString(average);
      Report.output(" Average replacements per execution: ", s);
      s = Long.toString(replaced_products_micros_min);
      Report.output("        Minimum execution time (us): ", s);
      s = Long.toString(replaced_products_micros_max);
      Report.output("        Maximum execution time (us): ", s);
      average = ((double) replaced_products_micros_total) / products_rebuild_count;
      s = Double.toString(average);
      Report.output("        Average execution time (us): ", s);
    }
    Report.output();
    Report.releaseReportLock();
  }
}
