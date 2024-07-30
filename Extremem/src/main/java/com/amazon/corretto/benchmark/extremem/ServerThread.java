// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class ServerThread extends ExtrememThread {
  final static int TotalAttentionPoints = 4;

  // Identifies the point of attention for the next release of this
  // server thread.
  private int attention;

  private final Products all_products;
  private final Customers all_customers;
  private final SalesTransactionQueue sales_queue;
  private final BrowsingHistoryQueue browsing_queue;

  private final ServerLog history;
  private final ServerLogAccumulator accumulator;
  private final MemoryLog alloc_accumulator;
  private final MemoryLog garbage_accumulator;

  private AbsoluteTime next_release_time;
  private AbsoluteTime customer_replacement_time;
  private AbsoluteTime product_replacement_time;
  private AbsoluteTime end_simulation_time;

  /* Memory accounting: The memory allocated for each ServerThread
   * instance is accounted in the memoryLog() for the ServerThread.
   * ServerThread is assumed to have NearlyForever life span.
   * The Bootstrap thread accounts for this ServerThread instance's
   * garbage.  */
  ServerThread(Configuration config, long random_seed, int sequence_no, Products all_products, Customers all_customers,
               BrowsingHistoryQueue browsing_queue, SalesTransactionQueue sales_queue, ServerLogAccumulator accumulator,
               MemoryLog alloc_accumulator, MemoryLog garbage_accumulator) {

      super (config, random_seed);
      final Polarity Grow = Polarity.Expand;
      this.attention = sequence_no % TotalAttentionPoints;
      final MemoryLog log = this.memoryLog();
      final MemoryLog garbage = this.garbageLog();
      this.setLabel(Util.i2s(this, sequence_no));

      Trace.msg(1, "@ ",
                Integer.toString(log.hashCode()),
                ": ServerThread[", this.label, "].memoryLog()");
      Trace.msg(1, "@ ",
                Integer.toString(garbage.hashCode()),
                ": ServerThread[", this.label, "].garbageLog()");

      Util.convertEphemeralString(this, LifeSpan.NearlyForever, label.length());
      this.all_customers = all_customers;
      this.all_products = all_products;
      this.browsing_queue = browsing_queue;
      this.sales_queue = sales_queue;

      this.accumulator = accumulator;
      this.alloc_accumulator = alloc_accumulator;
      this.garbage_accumulator = garbage_accumulator;
      this.history = new ServerLog(this, LifeSpan.NearlyForever, config.ResponseTimeMeasurements());

      // Account for reference fields label, all_products,
      // all_customers, sales_queue, browsing_queue,
      // end_simulation_time, history, accumulator, alloc_accumulator,
      // garbage_accumulator, next_release_time,
      // customer_replacement_time, product_replacement_time
      log.accumulate(LifeSpan.NearlyForever,
                     MemoryFlavor.ObjectReference, Grow, 13);
      // Account for int field attention.
      log.accumulate(LifeSpan.NearlyForever,
                     MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }

  public void setStartsAndStop(AbsoluteTime first_release, AbsoluteTime customer_replacement_time,
                               AbsoluteTime product_replacement_time, AbsoluteTime end_simulation) {

    this.next_release_time = new AbsoluteTime(this, first_release);

    // Replaced every period, typically less than 2 minutes for ServerThread.
    this.next_release_time.changeLifeSpan(this, LifeSpan.TransientShort);
    this.customer_replacement_time = new AbsoluteTime(this, customer_replacement_time);
    this.customer_replacement_time.changeLifeSpan(this, LifeSpan.TransientShort);
    this.product_replacement_time = new AbsoluteTime(this, product_replacement_time);
    this.product_replacement_time.changeLifeSpan(this, LifeSpan.TransientShort);
    this.end_simulation_time = end_simulation;
  }

  public void runExtreme() {
    while (true) {
      // If the simulation will have ended before we wake up, don't
      // even bother to sleep.
      if (next_release_time.compare(end_simulation_time) >= 0)
        break;

      AbsoluteTime now = next_release_time.sleep(this);
      now.garbageFootprint(this);

      // In an earlier implementation, termination of the thread was
      // determined by comparing next_release_time against
      // end_simulation_time. In the case that the thread falls
      // hopelessly behind schedule, the thread "never" terminates.
      if (now.compare(end_simulation_time) >= 0)
        break;

      Trace.msg(4, "Server ", label, ": attention: ",
                Integer.toString(attention));

      switch (attention) {
        case 0:
          // Statistically, the number of pending SalesTransaction
          // instances that accumulate on my sales_queue since the
          // last time I serviced this queue should be approximately
          // constant.   The greatest source of variance would be
          // contention with other server threads that might be
          // servicing the same queue.
          int transaction_count = 0;
          while (true) {
            SalesTransaction x = sales_queue.dequeue();
            if (x != null) {
              transaction_count++;
              // Note that either the customer or the product may be
              // decommissioned prior to transaction of the sale.  Our
              // simple implementation of transaction is not harmed by
              // either.  In the case that either of these entities is
              // decommissioned while transaction is pending, the
              // SalesTransaction object will force the entity to
              // remain alive until the transaction is performed.
              // Thereafter, the decommissioned object will be
              // eligible for garbage collection.  To simplify
              // bookkeeping, we account that the object has become
              // garbage at the moment it is decommissioned, even
              // though the garbage collector may have to wait for the
              // sale to be transacted before reclaiming it.
              Customer c = x.customer();
              c.transactSale(this, next_release_time, x);
              x.garbageFootprint(this);
            } else
              break;
          }
          Trace.msg(4, "Server ", label, ": processed sales transactions: ",
                    Integer.toString(transaction_count));
          history.logTransactions(this, next_release_time, transaction_count);
          break;
        case 1:
          // Statistically, the number of BrowsingHistory instances that
          // expire since the last time I serviced this queue should
          // be approximately constant.  The greatest source of
          // variance would be contention with other server threads
          // that might be servicing the same queue.
          int browsing_expirations = 0;
          while (true) {
            BrowsingHistory h;
            h = browsing_queue.pullIfExpired(next_release_time);
            if (h != null) {
              browsing_expirations++;
              Customer c = h.customer();
              c.retireOneSaveForLater(h);
              h.garbageFootprint(this);
            } else
              break;
          }
          Trace.msg(4, "Server ", label, ": browsing_expirations: ",
                    Integer.toString(browsing_expirations));
          history.logHistories(this, next_release_time, browsing_expirations);
          break;
        case 2:
          if (next_release_time.compare(customer_replacement_time) >= 0) {
            if (config.PhasedUpdates()) {
              for (int i = config.CustomerReplacementCount(); i > 0; i--)
                all_customers.replaceRandomCustomerPhasedUpdates(this);
            } else {
              for (int i = config.CustomerReplacementCount(); i > 0; i--)
                all_customers.replaceRandomCustomer(this);
            }

            customer_replacement_time.garbageFootprint(this);
            customer_replacement_time = (
              customer_replacement_time
              .addRelative(this, config.CustomerReplacementPeriod()));
            customer_replacement_time.changeLifeSpan(this,
                                                     LifeSpan.TransientShort);
            Trace.msg(4, "Server ", label, ": replaced ",
                      Integer.toString(config.CustomerReplacementCount()),
                      " customers");
            history.logCustomers(this, next_release_time,
                                 config.CustomerReplacementCount());
          } else {
            Trace.msg(4, "Server ", label, ": too early to replace customers");
            history.logDoNothings(this, next_release_time);
          }
          break;
        case 3:
          assert (3 + 1 == TotalAttentionPoints): "Bad TotalAttentionPoints";
          if (next_release_time.compare(product_replacement_time) >= 0) {
            for (int i = config.ProductReplacementCount(); i > 0; i--)
              all_products.replaceRandomProduct(this);
            product_replacement_time.garbageFootprint(this);
            product_replacement_time = (
              product_replacement_time
              .addRelative(this, config.ProductReplacementPeriod()));
            product_replacement_time.changeLifeSpan(this,
                                                    LifeSpan.TransientShort);
            Trace.msg(4, "Server ", label, ": replaced products: ",
                      Integer.toString(config.ProductReplacementCount()));
            history.logProducts(this, next_release_time,
                                config.ProductReplacementCount());
          } else {
            Trace.msg(4, "Server ", label, ": too early to replace products");
            history.logDoNothings(this, next_release_time);
          }
          break;
        default:
          assert (false): " Unhandled attention point in server thread";
      }
      if (attention-- == 0)
        attention = TotalAttentionPoints - 1;

      next_release_time.garbageFootprint(this);
      next_release_time = (
        next_release_time.addRelative(this, config.ServerPeriod()));
      next_release_time.changeLifeSpan(this, LifeSpan.TransientShort);
    }
    Trace.msg(2, "Server ", label, " terminating.  Time is up.");

    // We accumulate accumulator even if reporting individual threads
    accumulator.accumulate(history);
    if (config.ReportIndividualThreads())
      this.report(this);
    else {
      alloc_accumulator.foldInto(memoryLog());
      garbage_accumulator.foldInto(garbageLog());
    }
  }

  /* Every subclass overrides this method if its size differs from
   * the size of its superclass.  */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    
    // Account for reference fields label, all_products,
    // all_customers, sales_queue, browsing_queue,
    // end_simulation_time, history, accumulator, alloc_accumulator,
    // garbage_accumulator, next_release_time,
    // customer_replacement_time, product_replacement_time
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 13);
    // Account for int field attention.
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, Util.SizeOfInt);
    // Account for label.
    Util.tallyString(log, ls, p, label.length());

    // Account for objects referenced from customer_replacement_time,
    // product_replacement_time, next_release_time, all of which are
    // considered to be TransientShort.
    customer_replacement_time.tallyMemory(log, LifeSpan.TransientShort, p);
    product_replacement_time.tallyMemory(log, LifeSpan.TransientShort, p);
    next_release_time.tallyMemory(log, LifeSpan.TransientShort, p);
  }    

  void report(ExtrememThread t) {
    Report.acquireReportLock();
    history.report(t, label, config.ReportCSV());
    Report.output("Server Thread ", label, " memory behavior");
    MemoryLog.report(t, config.ReportCSV(), memoryLog(), garbageLog());
    Report.releaseReportLock();
  }
}
