// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

public class Bootstrap extends ExtrememThread {
  private static final long NanosPerSecond = 1000000000L;

  Bootstrap(Configuration config, long random_seed) {
    super(config, random_seed);
  }

  public void runExtreme() {
    CustomerThread[] customer_threads;
    ServerThread[] server_threads;

    RelativeTime customer_stagger = null;
    RelativeTime server_stagger = null;

    // Memory accounting: I have no more fields than parent class
    // ExtrememThread.  So my memory usage is accounted for during my
    // construction.
      
    MemoryLog memory = memoryLog();
    MemoryLog garbage = garbageLog();
    MemoryLog all_threads_accumulator = (
      new MemoryLog(LifeSpan.NearlyForever));
    all_threads_accumulator.memoryFootprint(this);
      
    Trace.msg(1, "@ ",
              Integer.toString(memory.hashCode()),
              ": Bootstrap.memoryLog()");
    Trace.msg(1, "@ ",
              Integer.toString(garbage.hashCode()),
              ": Bootstrap.garbageLog()");
    Trace.msg(1, "@ ", Integer.toString(all_threads_accumulator.hashCode()),
              ": Bootstrap.all_threads_accumulator");
      
    // config.initialize() replaces the random number generation seed
    // of this before generating the dictionary.
    config.initialize(this);
    if (config.ReportCSV()) {
      Report.output("All times reported in microseconds");
      config.dumpCSV(this);
    }
    else
      config.dump(this);
      
    CustomerLogAccumulator customer_accumulator;
    ServerLogAccumulator server_accumulator;
    MemoryLog customer_alloc_accumulator, customer_garbage_accumulator;
    MemoryLog server_alloc_accumulator, server_garbage_accumulator;
    if (!config.ReportIndividualThreads()) {
      customer_accumulator = (
        new CustomerLogAccumulator(this, LifeSpan.NearlyForever));
        
      customer_alloc_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      customer_alloc_accumulator.memoryFootprint(this);
        
      Trace.msg(1, "@ ",
                Integer.toString(customer_alloc_accumulator.hashCode()),
                ": Bootstrap.customer_alloc_accumulator");
        
      customer_garbage_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      customer_garbage_accumulator.memoryFootprint(this);
        
      Trace.msg(1, "@ ",
                Integer.toString(customer_garbage_accumulator.hashCode()),
                ": Bootstrap.customer_garbage_accumulator");
        
      server_accumulator = new ServerLogAccumulator(this,
                                                    LifeSpan.NearlyForever);
        
      server_alloc_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      server_alloc_accumulator.memoryFootprint(this);
        
      Trace.msg(1, "@ ",
                Integer.toString(server_alloc_accumulator.hashCode()),
                ": Bootstrap.server_alloc_accumulator");
        
      server_garbage_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      server_garbage_accumulator.memoryFootprint(this);
        
      Trace.msg(1, "@ ",
                Integer.toString(server_garbage_accumulator.hashCode()),
                ": Bootstrap.server_garbage__accumulator");
    } else {
      customer_accumulator = null;
      customer_alloc_accumulator = null;
      customer_garbage_accumulator = null;
      server_accumulator = null;
      server_alloc_accumulator = null;
      server_garbage_accumulator = null;
    }
      
    SalesTransactionQueue[] sales_queues = (
      new SalesTransactionQueue[config.SalesTransactionQueueCount()]);
    Util.referenceArray(this, LifeSpan.NearlyForever,
                        config.SalesTransactionQueueCount());
    for (int i = 0; i < config.SalesTransactionQueueCount(); i++)
      sales_queues[i] = new SalesTransactionQueue(this,
                                                  LifeSpan.NearlyForever);
    BrowsingHistoryQueue[] browsing_queues = (
      new BrowsingHistoryQueue[config.BrowsingHistoryQueueCount()]);
    Util.referenceArray(this, LifeSpan.NearlyForever,
                        config.BrowsingHistoryQueueCount());
    for (int i = 0; i < config.BrowsingHistoryQueueCount(); i++)
      browsing_queues[i] = new BrowsingHistoryQueue(this,
                                                    LifeSpan.NearlyForever);
    Trace.msg(4, "browsing_queues and sales_queues established");
      
    Products all_products = (
      new Products(this, LifeSpan.NearlyForever, config));
    Trace.msg(4, "all_products established");
    Customers all_customers = new Customers(this, LifeSpan.NearlyForever,
                                            config);
    Trace.msg(4, "all_customers established");
      
    if (config.CustomerThreads() > 0) {
      // Stagger the Customer threads so they are not all triggered at
      // the same moment in time.
      RelativeTime period = config.CustomerPeriod();
      long period_ns = (
        period.nanoseconds() +
        (config.CustomerPeriod().seconds() * NanosPerSecond));
      long stagger = period_ns / config.CustomerThreads();
      customer_stagger = new RelativeTime(this, stagger / NanosPerSecond,
                                          (int) (stagger % NanosPerSecond));
      Trace.msg(3, "Customer stagger set to: ",
                customer_stagger.toString(this));
    }
      
    RelativeTime customer_replacement_stagger = null;
    RelativeTime product_replacement_stagger = null;
      
    if (config.ServerThreads() > 0) {
      // Stagger the Server threads so they are not all triggered at the
      // same moment in time.
      RelativeTime period = config.ServerPeriod();
      long period_ns = (period.nanoseconds() +
                        (config.ServerPeriod().seconds() * NanosPerSecond));
      long stagger = period_ns / config.ServerThreads();
      server_stagger = new RelativeTime(this, stagger / NanosPerSecond,
                                        (int) (stagger % NanosPerSecond));
      customer_replacement_stagger = (
        config.CustomerReplacementPeriod().divideBy(this,
                                                    config.ServerThreads()));
      product_replacement_stagger = (
        config.ProductReplacementPeriod().divideBy(this,
                                                   config.ServerThreads()));
      Trace.msg(3, "Server stagger set to: ", server_stagger.toString(this));
    }
      
    // In addition to config.InitializationDelay(), reserve 1 second for
    // variable costs of initializing every 2000 threads.  
    int start_delay_milliseconds = (
      (config.CustomerThreads() + config.ServerThreads()) / 2);
      
    RelativeTime start_delay = (config.InitializationDelay().
                                addMillis(this, start_delay_milliseconds));
      
    String s = start_delay.toString(this);
    Trace.msg(3, "");
    Trace.msg(3, "Simulation starts in ", s);
    Util.abandonEphemeralString(this, s);
      
    AbsoluteTime now = AbsoluteTime.now(this);
    AbsoluteTime start_time = now.addRelative(this, start_delay);
    start_delay.garbageFootprint(this);
    start_delay = null;
    now.garbageFootprint(this);
    now = null;
    AbsoluteTime end_time = (
      start_time.addRelative(this, config.SimulationDuration()));
    end_time.changeLifeSpan(this, LifeSpan.NearlyForever);
      
    AbsoluteTime staggered_customer_replacement = (
      new AbsoluteTime(this, start_time));
      
    AbsoluteTime staggered_product_replacement = (
      new AbsoluteTime(this, start_time));
      
    if (config.ReportCSV()) {
      s = Long.toString(start_time.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Simulation start time,", s);
    } else {
      s = start_time.toString(this);
      Report.output("  Simulation starts: ", s);
    }
    Trace.msg(2, "");
    Trace.msg(2, "  Simulation starts: ", s);
    Util.abandonEphemeralString(this, s);
      
    if (config.ReportCSV()) {
      s = Long.toString(end_time.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Simulation end time,", s);
    } else {
      s = end_time.toString(this);
      Report.output("End simulation time: ", s);
    }
    Trace.msg(2, "End simulation time: ", s);
    Trace.msg(2, "");
    Util.abandonEphemeralString(this, s);
      
    Trace.msg(2, "starting up CustomerThreads: ",
              Integer.toString(config.CustomerThreads()));
      
    // Initialize and startup all of the threads as specified in
    // config.
    customer_threads = new CustomerThread[config.CustomerThreads()];
    Util.referenceArray(this, LifeSpan.NearlyForever,
                        config.CustomerThreads());
      
    AbsoluteTime staggered_start = start_time.addMinutes(this, 0);
    int bq_no = config.BrowsingHistoryQueueCount() - 1;
    int sq_no = config.SalesTransactionQueueCount() - 1;
    for (int i = 0; i < config.CustomerThreads(); i++) {
      customer_threads[i] = (
        new CustomerThread(config, randomLong(), i, all_products,
                           all_customers, browsing_queues[bq_no],
                           sales_queues[sq_no], customer_accumulator,
                           customer_alloc_accumulator,
                           customer_garbage_accumulator, staggered_start,
                           end_time));
      if (bq_no-- == 0)
        bq_no = config.BrowsingHistoryQueueCount() - 1;
      if (sq_no-- == 0)
        sq_no = config.SalesTransactionQueueCount() - 1;
      staggered_start.garbageFootprint(this);
      staggered_start = staggered_start.addRelative(this, customer_stagger);
      customer_threads[i].start(); // will wait for first release
    }
    staggered_start.garbageFootprint(this);
    if (customer_stagger != null)
      customer_stagger.garbageFootprint(this);
      
    Trace.msg(2, "starting up ServerThreads: ",
              Integer.toString(config.ServerThreads()));
      
    server_threads = new ServerThread[config.ServerThreads()];
    Util.referenceArray(this,
                        LifeSpan.NearlyForever, config.ServerThreads());
      
    staggered_start = start_time.addMinutes(this, 0);
      
    bq_no = config.BrowsingHistoryQueueCount() - 1;
    sq_no = config.SalesTransactionQueueCount() - 1;
    for (int i = 0; i < config.ServerThreads(); i++) {
      server_threads[i] = (
        new ServerThread(config,
                         randomLong(), i, all_products, all_customers,
                         browsing_queues[bq_no], sales_queues[sq_no],
                         server_accumulator, server_alloc_accumulator,
                         server_garbage_accumulator, staggered_start,
                         staggered_customer_replacement,
                         staggered_product_replacement, end_time));
      if (bq_no-- == 0)
        bq_no = config.BrowsingHistoryQueueCount() - 1;
      if (sq_no-- == 0)
        sq_no = config.SalesTransactionQueueCount() - 1;
      staggered_start.garbageFootprint(this);
      staggered_start = staggered_start.addRelative(this, server_stagger);
      staggered_customer_replacement.garbageFootprint(this);
      staggered_customer_replacement = (
        staggered_customer_replacement
        .addRelative(this, customer_replacement_stagger));
      staggered_product_replacement.garbageFootprint(this);
      staggered_product_replacement = (
        staggered_product_replacement
        .addRelative(this, product_replacement_stagger));
      server_threads[i].start(); // will wait for first release
    }
    staggered_start.garbageFootprint(this);
      
    staggered_customer_replacement.garbageFootprint(this);
    staggered_customer_replacement = null;
      
    staggered_product_replacement.garbageFootprint(this);
    staggered_product_replacement = null;
      
    if (server_stagger != null)
      server_stagger.garbageFootprint(this);
      
    if (customer_replacement_stagger != null)
      customer_replacement_stagger.garbageFootprint(this);
    customer_replacement_stagger = null;
      
    if (product_replacement_stagger != null)
      product_replacement_stagger.garbageFootprint(this);
    product_replacement_stagger = null;
      
    now = AbsoluteTime.now(this);
    if (config.ReportCSV()) {
      s = Long.toString(now.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Initialization completion time,", s);
    } else {
      s = now.toString(this);
      Report.output("");
      Report.output("Initialization completes at time: ", s);
    }
    Util.abandonEphemeralString(this, s);

    if (now.compare(start_time) > 0) {
      Configuration.usage("Initialization must complete before start."
                          + "  Increase InitializationDelay.");
      // Does not return.
    }
    start_time.garbageFootprint(this);
    start_time = null;
      
    now.garbageFootprint(this);
    now = null;
      
    Trace.msg(2, "Joining with customer threads");
      
    // Each thread will terminate when the end_time is reached.
    for (int i = 0; i < config.CustomerThreads(); i++) {
      try {
        customer_threads[i].join();
      } catch (InterruptedException x) {
        i--;                    // just try it again
      }
    }
      
    Trace.msg(2, "Joining with server threads");
      
    for (int i = 0; i < config.ServerThreads(); i++) {
      try {
        server_threads[i].join();
      } catch (InterruptedException x) {
        i--;                    // just try it again
      }
    }
      
    Trace.msg(2, "Program simulation has ended");
    all_products.report(this);
    all_customers.report(this);
    if (!config.ReportIndividualThreads()) {
        
      Report.acquireReportLock();
      customer_accumulator.report(this, "(all customer threads)",
                                  config.ReportCSV());
      MemoryLog.report(this, config.ReportCSV(), customer_alloc_accumulator,
                       customer_garbage_accumulator);
        
      Report.output("");
      Report.output("Bootstrap thread after reporting customer accumulator");
      MemoryLog.report(this, config.ReportCSV(), memory, garbage);
        
      server_accumulator.report(this, "(all server threads)",
                                config.ReportCSV());
      MemoryLog.report(this, config.ReportCSV(), server_alloc_accumulator,
                       server_garbage_accumulator);
        
      customer_alloc_accumulator.foldInto(server_alloc_accumulator);
      customer_alloc_accumulator.foldOutof(customer_garbage_accumulator);
      customer_alloc_accumulator.foldOutof(server_garbage_accumulator);
        
      all_threads_accumulator.foldInto(customer_alloc_accumulator);
        
      Report.output();
      Report.output("Customer/Server thread Net Allocation (expect zero)");
      MemoryLog.reportCumulative(this, config.ReportCSV(),
                                 customer_alloc_accumulator);

      Report.releaseReportLock();
    } else {
      // Individual threads have printed their individual reports.
      for (int i = 0; i < config.CustomerThreads(); i++) {
        all_threads_accumulator.foldInto(customer_threads[i].memoryLog());
        all_threads_accumulator.foldOutof(customer_threads[i].garbageLog());
      }
      for (int i = 0; i < config.ServerThreads(); i++) {
        all_threads_accumulator.foldInto(server_threads[i].memoryLog());
        all_threads_accumulator.foldOutof(server_threads[i].garbageLog());
      }
    }
      
    for (int i = 0; i < config.ServerThreads(); i++)
      server_threads[i].garbageFootprint(this);
    server_threads = null;
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               config.ServerThreads());
      
    for (int i = 0; i < config.CustomerThreads(); i++)
      customer_threads[i].garbageFootprint(this);
    customer_threads = null;
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               config.CustomerThreads());
      
    end_time.garbageFootprint(this);
    end_time = null;
      
    customer_threads = null;
    server_threads = null;

    all_customers.garbageFootprint(this);
    all_customers = null;
      
    all_products.garbageFootprint(this);
    all_products = null;
      
    for (int i = 0; i < config.BrowsingHistoryQueueCount(); i++) {
      browsing_queues[i].garbageFootprint(this);
      browsing_queues[i] = null;
    }
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               config.BrowsingHistoryQueueCount());
    browsing_queues = null;
      
    for (int i = 0; i < config.SalesTransactionQueueCount(); i++) {
      sales_queues[i].garbageFootprint(this);
      sales_queues[i] = null;
    }
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               config.SalesTransactionQueueCount());
    sales_queues = null;
      
    // While these objects may not be garbage quite yet, treat them as
    // if they were, so that report on net memory allocations balance
    // out to zero.
      
    if (!config.ReportIndividualThreads()) {
      server_garbage_accumulator.garbageFootprint(this);
      server_alloc_accumulator.garbageFootprint(this);
      server_accumulator.garbageFootprint(this);
      customer_garbage_accumulator.garbageFootprint(this);
      customer_alloc_accumulator.garbageFootprint(this);
      customer_accumulator.garbageFootprint(this);
    } 
    config.garbageFootprint(this);
    all_threads_accumulator.garbageFootprint(this);
    this.garbageFootprint(this);
      
    // This should be empty
    Report.output("");
    Report.output("Bootstrap thread after discarding this");
    MemoryLog.report(this, config.ReportCSV(), memory, garbage);
      
    all_threads_accumulator.foldInto(memory);
    all_threads_accumulator.foldOutof(garbage);
    
    Report.acquireReportLock();
    Report.output();
    Report.output("Net allocation for all threads (should be zero)");
    MemoryLog.reportCumulative(this, config.ReportCSV(),
                               all_threads_accumulator);
    all_threads_accumulator = null;
    Report.releaseReportLock();
      
    customer_accumulator = null;
    customer_alloc_accumulator  = null;
    customer_garbage_accumulator = null;
    server_accumulator = null;
    server_alloc_accumulator = null;
    server_garbage_accumulator = null;
  }

  // No need to override superclass because Bootstrap introduces no
  // instance fields not already present in ExtrememThread.
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
  }
}
