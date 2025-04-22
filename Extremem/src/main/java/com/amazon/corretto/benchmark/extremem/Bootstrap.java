// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

public class Bootstrap extends ExtrememThread {
  private static final long NanosPerSecond = 1000000000L;
  private Configuration _config;
  private UpdateThread _update_thread;
  private CustomerThread[] _customer_threads;
  private ServerThread[] _server_threads;
  private Products _all_products;
  private Customers _all_customers;
  private CustomerLogAccumulator _customer_accumulator;
  private ServerLogAccumulator _server_accumulator;
  private MemoryLog _customer_alloc_accumulator, _customer_garbage_accumulator;
  private MemoryLog _server_alloc_accumulator, _server_garbage_accumulator;
  private BrowsingHistoryQueue[] _browsing_queues;
  private SalesTransactionQueue[] _sales_queues;
  private MemoryLog _memory;
  private MemoryLog _garbage;
  private MemoryLog _all_threads_accumulator;
  private AbsoluteTime _start_time;
  private AbsoluteTime _end_time;
  private RelativeTime _customer_period;
  private RelativeTime _customer_think_time;

  Bootstrap(Configuration config, long random_seed) {
    super(config, random_seed);
    this.setLabel("Bootstrap");
    _config = config;
    
  }

  public void runExtreme() {

    // config.initialize() replaces the random number generation seed
    // of this before generating the dictionary.
    _config.initialize(this);
    if (_config.ReportCSV()) {
      Report.output("All times reported in microseconds");
      _config.dumpCSV(this);
    }
    else {
      _config.dump(this);
    }  
    _all_products = new Products(this, LifeSpan.NearlyForever, _config);
    Trace.msg(4, "all_products established");

    _all_customers = new Customers(this, LifeSpan.NearlyForever, _config);
    Trace.msg(4, "all_customers established");

    _customer_period = _config.CustomerPeriod();
    _customer_think_time = _config.CustomerThinkTime();

    if (_config.SearchForMaximumTransactionRate()) {
      final int MaxRetries = 5;
      boolean searching_for_greater_success = false;
      boolean searching_for_first_success = true;
      boolean found_top_failure = false;
      boolean first_try = true;
      int allowed_retries_for_initial_success = MaxRetries;;
      int search_backward_increments = 0;
      while (true) {
	configure_simulation_threads();
	boolean success = run_one_experiment(true);
	if (searching_for_first_success) {
	  if (success) {
	    double thread_rate = 1000_000.0 / _customer_period.microseconds();
	    double transaction_rate = _config.CustomerThreads() * thread_rate;
	    String s = String.valueOf(transaction_rate);
	    Report.output("Found first success at ", s, " TPS, searching for higher transaction rate");
	    searching_for_first_success = false;
	    searching_for_greater_success = true;

	    // increase transaction rate by 10%.
	    _customer_period = _customer_period.multiplyBy(this, 0.9);
	    _customer_think_time = _customer_think_time.multiplyBy(this, 0.9);
	    first_try = true;
	  } else if (first_try) {
	    first_try = false;
	    // try it again
	  } else {
	    double thread_rate = 1000_000.0 / _customer_period.microseconds();
	    double transaction_rate = _config.CustomerThreads() * thread_rate;
	    String s = String.valueOf(transaction_rate);
	    if (allowed_retries_for_initial_success-- >= 0) {
	      Report.output("Failed twice at ", s, " TPS, searching for lower transaction rate");
	      // Decrease transaction rate by 10%
	      _customer_period = _customer_period.multiplyBy(this, 1.1);
	      _customer_think_time = _customer_think_time.multiplyBy(this, 0.9);
	      first_try = true;
	    } else {
	      Report.output("Failed to find initial success after ", Integer.toString(MaxRetries), ".  Giving up.");
	      return;
	    }
	  }
	} else {
	  // We've already found a first success
	  if (success) {
	    double thread_rate = 1000_000.0 / _customer_period.microseconds();
	    double transaction_rate = _config.CustomerThreads() * thread_rate;
	    String s = String.valueOf(transaction_rate);
	    if (searching_for_greater_success) {
	      Report.output("Found greater success at ", s, " TPS, search for even higher transaction rate");

	      // increase transaction rate by 10%.
	      _customer_period = _customer_period.multiplyBy(this, 0.9);
	      _customer_think_time = _customer_think_time.multiplyBy(this, 0.9);
	      searching_for_first_success = false;
	      searching_for_greater_success = true;
	      first_try = true;
	    } else {
	      Report.output("Found greater success at ", s, " TPS.  This is maximum successful rate");
	      return;
	    }
	  } else if (first_try) {
	    first_try = false;
	    // try it again
	  } else if (searching_for_greater_success) {
	    double thread_rate = 1000_000.0 / _customer_period.microseconds();
	    double transaction_rate = _config.CustomerThreads() * thread_rate;
	    String s = String.valueOf(transaction_rate);
	    Report.output("Failed twice at ", s, " TPS while searching for higher transaction rate.  Take smaller steps backward");

	    // Decrease transaction rate by 2.5%
	    _customer_period = _customer_period.multiplyBy(this, 1.025);
	    _customer_think_time = _customer_think_time.multiplyBy(this, 1.025);
	    first_try = true;

	    found_top_failure = true;
	    searching_for_greater_success = false;
	    search_backward_increments = 0;
	  } else if (search_backward_increments >= 3) {
	    Report.output("Search for greater success terminated.  Most recent success is best available.");
	    return;
	  } else {
	    // Decrease transaction rate by 2.5%
	    _customer_period = _customer_period.multiplyBy(this, 1.025);
	    _customer_think_time = _customer_think_time.multiplyBy(this, 1.025);
	    search_backward_increments++;
	    first_try = true;
	  }
	}
      }
    } else {
      configure_simulation_threads();
      boolean success = run_one_experiment(false);
      if (success) {
        Report.output("Simulation was successful");
      } else {
        Report.output("Simulation failed");
      }
    }
  }

  public void configure_simulation_threads() {
    _sales_queues = new SalesTransactionQueue[_config.SalesTransactionQueueCount()];
    Util.referenceArray(this, LifeSpan.NearlyForever,
                        _config.SalesTransactionQueueCount());
    for (int i = 0; i < _config.SalesTransactionQueueCount(); i++)
      _sales_queues[i] = new SalesTransactionQueue(this,
                                                  LifeSpan.NearlyForever);
    _browsing_queues = new BrowsingHistoryQueue[_config.BrowsingHistoryQueueCount()];
    Util.referenceArray(this, LifeSpan.NearlyForever,
                        _config.BrowsingHistoryQueueCount());
    for (int i = 0; i < _config.BrowsingHistoryQueueCount(); i++)
      _browsing_queues[i] = new BrowsingHistoryQueue(this,
                                                    LifeSpan.NearlyForever);
    Trace.msg(4, "_browsing_queues and _sales_queues established");

    // Memory accounting: I have no more fields than parent class
    // ExtrememThread.  So my memory usage is accounted for during my
    // construction.
    _memory = memoryLog();
    _garbage = garbageLog();
    _all_threads_accumulator = new MemoryLog(LifeSpan.NearlyForever);
    _all_threads_accumulator.memoryFootprint(this);
      
    Trace.msg(1, "@ ",
              Integer.toString(_memory.hashCode()),
              ": Bootstrap.memoryLog()");
    Trace.msg(1, "@ ",
              Integer.toString(_garbage.hashCode()),
              ": Bootstrap.garbageLog()");
    Trace.msg(1, "@ ", Integer.toString(_all_threads_accumulator.hashCode()),
              ": Bootstrap._all_threads_accumulator");
      
    _customer_accumulator = new CustomerLogAccumulator(this, LifeSpan.NearlyForever, _config.ResponseTimeMeasurements());
    _server_accumulator = new ServerLogAccumulator(this, LifeSpan.NearlyForever, _config.ResponseTimeMeasurements());

    if (!_config.ReportIndividualThreads()) {
      _customer_alloc_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      _customer_alloc_accumulator.memoryFootprint(this);

      Trace.msg(1, "@ ",
                Integer.toString(_customer_alloc_accumulator.hashCode()),
                ": Bootstrap._customer_alloc_accumulator");

      _customer_garbage_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      _customer_garbage_accumulator.memoryFootprint(this);
      
      Trace.msg(1, "@ ",
                Integer.toString(_customer_garbage_accumulator.hashCode()),
                ": Bootstrap._customer_garbage_accumulator");

      _server_alloc_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      _server_alloc_accumulator.memoryFootprint(this);

      Trace.msg(1, "@ ",
                Integer.toString(_server_alloc_accumulator.hashCode()),
                ": Bootstrap._server_alloc_accumulator");

      _server_garbage_accumulator = new MemoryLog(LifeSpan.NearlyForever);
      _server_garbage_accumulator.memoryFootprint(this);

      Trace.msg(1, "@ ",
                Integer.toString(_server_garbage_accumulator.hashCode()),
                ": Bootstrap.server_garbage__accumulator");
    } else {
      _customer_alloc_accumulator = null;
      _customer_garbage_accumulator = null;
      _server_alloc_accumulator = null;
      _server_garbage_accumulator = null;
    }
      
    RelativeTime customer_stagger = null;
    RelativeTime server_stagger = null;
    if (_config.CustomerThreads() > 0) {
      // Stagger the Customer threads so they are not all triggered at
      // the same moment in time.
      RelativeTime period = _config.CustomerPeriod();
      long period_ns = (
        period.nanoseconds() +
        (_config.CustomerPeriod().seconds() * NanosPerSecond));
      long stagger = period_ns / _config.CustomerThreads();
      customer_stagger = new RelativeTime(this, stagger / NanosPerSecond,
                                          (int) (stagger % NanosPerSecond));
      Trace.msg(3, "Customer stagger set to: ",
                customer_stagger.toString(this));
    }
      
    RelativeTime customer_replacement_stagger = null;
    RelativeTime product_replacement_stagger = null;
    if (_config.ServerThreads() > 0) {
      // Stagger the Server threads so they are not all triggered at the
      // same moment in time.
      RelativeTime period = _config.ServerPeriod();
      long period_ns = (period.nanoseconds() +
                        (_config.ServerPeriod().seconds() * NanosPerSecond));
      long stagger = period_ns / _config.ServerThreads();
      server_stagger = new RelativeTime(this, stagger / NanosPerSecond,
                                        (int) (stagger % NanosPerSecond));
      customer_replacement_stagger = (
        _config.CustomerReplacementPeriod().divideBy(this,
                                                    _config.ServerThreads()));
      product_replacement_stagger = (
        _config.ProductReplacementPeriod().divideBy(this,
                                                   _config.ServerThreads()));
      Trace.msg(3, "Server stagger set to: ", server_stagger.toString(this));
    }

    Trace.msg(2, "starting up CustomerThreads: ", Integer.toString(_config.CustomerThreads()));
    // Initialize and startup all of the threads as specified in _config.
    _customer_threads = new CustomerThread[_config.CustomerThreads()];
    Util.referenceArray(this, LifeSpan.NearlyForever, _config.CustomerThreads());
      
    int bq_no = _config.BrowsingHistoryQueueCount() - 1;
    int sq_no = _config.SalesTransactionQueueCount() - 1;
    for (int i = 0; i < _config.CustomerThreads(); i++) {
      _customer_threads[i] = new CustomerThread(_config, _customer_period, _customer_think_time,
						randomLong(), i, _all_products, _all_customers, _browsing_queues[bq_no],
						_sales_queues[sq_no], _customer_accumulator, _customer_alloc_accumulator,
						_customer_garbage_accumulator);
      if (bq_no-- == 0) {
        bq_no = _config.BrowsingHistoryQueueCount() - 1;
      }
      if (sq_no-- == 0) {
        sq_no = _config.SalesTransactionQueueCount() - 1;
      }
    }
    if (customer_stagger != null) {
      customer_stagger.garbageFootprint(this);
    }

    Trace.msg(2, "starting up ServerThreads: ", Integer.toString(_config.ServerThreads()));
    _server_threads = new ServerThread[_config.ServerThreads()];
    Util.referenceArray(this, LifeSpan.NearlyForever, _config.ServerThreads());
      
    bq_no = _config.BrowsingHistoryQueueCount() - 1;
    sq_no = _config.SalesTransactionQueueCount() - 1;
    for (int i = 0; i < _config.ServerThreads(); i++) {
      _server_threads[i] = new ServerThread(_config, randomLong(), i, _all_products, _all_customers, _browsing_queues[bq_no],
                                           _sales_queues[sq_no], _server_accumulator, _server_alloc_accumulator,
                                           _server_garbage_accumulator);
      if (bq_no-- == 0)
        bq_no = _config.BrowsingHistoryQueueCount() - 1;
      if (sq_no-- == 0)
        sq_no = _config.SalesTransactionQueueCount() - 1;
    }
    
    if (_config.PhasedUpdates()) {
      _update_thread = new UpdateThread(_config, randomLong(), _all_products, _all_customers);
    } else {
      _update_thread = null;
    }
  
    AbsoluteTime now = AbsoluteTime.now(this);

    // Add 4 ms to conservatively approximate the time required to establish start times and start() each thread
    _start_time = now.addMillis(this, 4 * (_config.CustomerThreads() + _config.ServerThreads()));
     _end_time =  _start_time.addRelative(this, _config.SimulationDuration());

    AbsoluteTime staggered_customer_replacement = new AbsoluteTime(this, _start_time);
    AbsoluteTime staggered_product_replacement = new AbsoluteTime(this, _start_time);

    String s;
    if (_config.ReportCSV()) {
      s = Long.toString(_start_time.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Simulation start time,", s);
    } else {
      s = _start_time.toString(this);
      Report.output("  Simulation starts: ", s);
    }
    Trace.msg(2, "");
    Trace.msg(2, "  Simulation starts: ", s);
    Util.abandonEphemeralString(this, s);
      
    if (_config.ReportCSV()) {
      s = Long.toString(_end_time.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Simulation end time,", s);
    } else {
      s = _end_time.toString(this);
      Report.output("End simulation time: ", s);
    }
    Trace.msg(2, "End simulation time: ", s);
    Trace.msg(2, "");
    Util.abandonEphemeralString(this, s);

    // startup the customer threads
    AbsoluteTime staggered_start = _start_time.addMinutes(this, 0);
    for (int i = 0; i < _config.CustomerThreads(); i++) {
      _customer_threads[i].setStartAndStop(staggered_start, _end_time);
      staggered_start.garbageFootprint(this);
      _customer_threads[i].start(); // will wait for first release
      staggered_start = staggered_start.addRelative(this, customer_stagger);
    }

    // startup the server threads
    staggered_start = _start_time.addMinutes(this, 0);
    for (int i = 0; i < _config.ServerThreads(); i++) {
      _server_threads[i].setStartsAndStop(staggered_start, staggered_customer_replacement, staggered_product_replacement,
                                         _end_time);
      staggered_start.garbageFootprint(this);
      staggered_start = staggered_start.addRelative(this, server_stagger);
      staggered_customer_replacement.garbageFootprint(this);
      staggered_customer_replacement = staggered_customer_replacement.addRelative(this, customer_replacement_stagger);
      staggered_product_replacement.garbageFootprint(this);
      staggered_product_replacement = staggered_product_replacement.addRelative(this, product_replacement_stagger);
      _server_threads[i].start(); // will wait for first release
    }
    staggered_start.garbageFootprint(this);
    staggered_start = null;

    staggered_customer_replacement.garbageFootprint(this);
    staggered_customer_replacement = null;
      
    staggered_product_replacement.garbageFootprint(this);
    staggered_product_replacement = null;

    if (server_stagger != null) {
      server_stagger.garbageFootprint(this);
    }
      
    if (customer_replacement_stagger != null) {
      customer_replacement_stagger.garbageFootprint(this);
    }
    customer_replacement_stagger = null;
      
    if (product_replacement_stagger != null) {
      product_replacement_stagger.garbageFootprint(this);
    }
    product_replacement_stagger = null;

    if (_config.PhasedUpdates()) {
      staggered_start = _start_time.addRelative(this, _config.PhasedUpdateInterval());
      _update_thread.setStartAndStop(staggered_start, _end_time);
      _update_thread.start();    // will wait for first release
      staggered_start.garbageFootprint(this);
      staggered_start = null;
    }

    now = AbsoluteTime.now(this);
    if (_config.ReportCSV()) {
      s = Long.toString(now.microseconds());
      Util.ephemeralString(this, s.length());
      Report.output("Initialization completion time,", s);
    } else {
      s = now.toString(this);
      Report.output("");
      Report.output("Initialization completes at time: ", s);
    }
    Util.abandonEphemeralString(this, s);

    if (now.compare(_start_time) > 0) {
      Report.output("Warning!  Consumed more than 4 ms to start each thread.");
      s = _start_time.toString(this);
      Report.output(" Planned to start at: ", s);
      s = now.toString(this);
      Report.output("Actually starting at: ", s);
    }
    _start_time.garbageFootprint(this);
    _start_time = null;
    _end_time.changeLifeSpan(this, LifeSpan.NearlyForever);
    now.garbageFootprint(this);
    now = null;
  }

  // Returns true iff the test was considered successful
  public boolean run_one_experiment(bool preserve_customers_and_products) {
    Trace.msg(2, "Joining with customer threads");
    // Each thread will terminate when the _end_time is reached.
    for (int i = 0; i < _config.CustomerThreads(); i++) {
      try {
        _customer_threads[i].join();
      } catch (InterruptedException x) {
        i--;                    // just try it again
      }
    }
    Trace.msg(2, "Joining with server threads");
    for (int i = 0; i < _config.ServerThreads(); i++) {
      try {
        _server_threads[i].join();
      } catch (InterruptedException x) {
        i--;                    // just try it again
      }
    }
    if (_update_thread != null) {
      Trace.msg(2, "Joining with update thread");
      boolean retry = false;
      do {
        try {
          _update_thread.join();
          retry = false;
        } catch (InterruptedException x) {
          retry = true;
        }
      } while (retry);
    }
    Trace.msg(2, "Program simulation has ended");
    _all_products.report(this);
    _all_customers.report(this);
    if (!_config.ReportIndividualThreads()) {
        
      Report.acquireReportLock();
      _customer_accumulator.report(this, "(all customer threads)",
                                  _config.ReportCSV());
      MemoryLog.report(this, _config.ReportCSV(), _customer_alloc_accumulator,
                       _customer_garbage_accumulator);
        
      Report.output("");
      Report.output("Bootstrap thread after reporting customer accumulator");
      MemoryLog.report(this, _config.ReportCSV(), _memory, _garbage);
        
      _server_accumulator.report(this, "(all server threads)",
                                _config.ReportCSV());
      MemoryLog.report(this, _config.ReportCSV(), _server_alloc_accumulator,
                       _server_garbage_accumulator);
        
      _customer_alloc_accumulator.foldInto(_server_alloc_accumulator);
      _customer_alloc_accumulator.foldOutof(_customer_garbage_accumulator);
      _customer_alloc_accumulator.foldOutof(_server_garbage_accumulator);
        
      _all_threads_accumulator.foldInto(_customer_alloc_accumulator);
        
      Report.output();
      Report.output("Customer/Server thread Net Allocation (expect zero)");
      MemoryLog.reportCumulative(this, _config.ReportCSV(),
                                 _customer_alloc_accumulator);

      Report.releaseReportLock();
    } else {
      // Individual threads have printed their individual reports.
      for (int i = 0; i < _config.CustomerThreads(); i++) {
        _all_threads_accumulator.foldInto(_customer_threads[i].memoryLog());
        _all_threads_accumulator.foldOutof(_customer_threads[i].garbageLog());
      }
      for (int i = 0; i < _config.ServerThreads(); i++) {
        _all_threads_accumulator.foldInto(_server_threads[i].memoryLog());
        _all_threads_accumulator.foldOutof(_server_threads[i].garbageLog());
      }
    }
      
    for (int i = 0; i < _config.ServerThreads(); i++) {
      _server_threads[i].garbageFootprint(this);
    }
    _server_threads = null;
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               _config.ServerThreads());
      
    for (int i = 0; i < _config.CustomerThreads(); i++) {
      _customer_threads[i].garbageFootprint(this);
    }
    _customer_threads = null;
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               _config.CustomerThreads());
      
    _end_time.garbageFootprint(this);
    _end_time = null;
      
    _customer_threads = null;
    _server_threads = null;

    if (!preserve_customers_and_products) {
      _all_customers.garbageFootprint(this);
      _all_customers = null;
      _all_products.garbageFootprint(this);
      _all_products = null;
    }

    for (int i = 0; i < _config.BrowsingHistoryQueueCount(); i++) {
      _browsing_queues[i].garbageFootprint(this);
      _browsing_queues[i] = null;
    }
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               _config.BrowsingHistoryQueueCount());
    _browsing_queues = null;
      
    for (int i = 0; i < _config.SalesTransactionQueueCount(); i++) {
      _sales_queues[i].garbageFootprint(this);
      _sales_queues[i] = null;
    }
    Util.abandonReferenceArray(this, LifeSpan.NearlyForever,
                               _config.SalesTransactionQueueCount());
    _sales_queues = null;

    // While these objects may not be garbage quite yet, treat them as
    // if they were, so that report on net memory allocations balance
    // out to zero.
      
    if (!_config.ReportIndividualThreads()) {
      _server_garbage_accumulator.garbageFootprint(this);
      _server_alloc_accumulator.garbageFootprint(this);
      _customer_garbage_accumulator.garbageFootprint(this);
      _customer_alloc_accumulator.garbageFootprint(this);
    } 
    _server_accumulator.garbageFootprint(this);
    _customer_accumulator.garbageFootprint(this);

    _config.garbageFootprint(this);
    _all_threads_accumulator.garbageFootprint(this);
    this.garbageFootprint(this);
      
    // This should be empty
    Report.output("");
    Report.output("Bootstrap thread after discarding this");
    MemoryLog.report(this, _config.ReportCSV(), _memory, _garbage);
      
    _all_threads_accumulator.foldInto(_memory);
    _all_threads_accumulator.foldOutof(_garbage);
    
    Report.acquireReportLock();
    Report.output();
    Report.output("Net allocation for all threads (should be zero)");
    MemoryLog.reportCumulative(this, _config.ReportCSV(),
                               _all_threads_accumulator);
    _all_threads_accumulator = null;
    Report.releaseReportLock();

    _server_accumulator.reportPercentiles(this, _config.ReportCSV());
    _customer_accumulator.reportPercentiles(this, _config.ReportCSV());

    int customer_thread_count = _config.CustomerThreads();
    RelativeTime customer_period = _customer_period;;
    RelativeTime simulation_duration = _config.SimulationDuration();
    int activations_per_thread = (int) simulation_duration.divideBy(customer_period);
    int expected_activations = customer_thread_count * activations_per_thread;
    int actual_activations = _customer_accumulator.engagements();
    boolean result = actual_activations + customer_thread_count >= expected_activations;

    Report.acquireReportLock();
    Report.output();
    String judgement = result? "Looks good: ": "PROBLEM: ";
    if (_config.ReportCSV()) {
      Report.output(judgement);
      Report.output("Observed Customer Transactions, Expected Customer Transactions");
      Report.output(String.valueOf(actual_activations), ", ", String.valueOf(expected_activations));
    } else {
      Report.output(judgement, "observed ", String.valueOf(actual_activations),
		      " customer interactions out of at least ", String.valueOf(expected_activations), " expected transactions");
    }
    Report.output();
    Report.releaseReportLock();

    long p50_goal = _config.MaxP50CustomerPrepMicroseconds();
    if (p50_goal > 0) {
      long p50_actual = _customer_accumulator.getPreparationResponseTimes().getP50();
      if (p50_actual > p50_goal) {
        Report.output("Failed p50 test: ", String.valueOf(p50_actual), " us > goal: ", String.valueOf(p50_goal), " us");
        result = false;
      }
    }

    long p95_goal = _config.MaxP95CustomerPrepMicroseconds();
    if (p95_goal > 0) {
      long p95_actual = _customer_accumulator.getPreparationResponseTimes().getP95();
      if (p95_actual > p95_goal) {
        Report.output("Failed p95 test: ", String.valueOf(p95_actual), " us > goal: ", String.valueOf(p95_goal), " us");
        result = false;
      }
    }

    long p99_goal = _config.MaxP99CustomerPrepMicroseconds();
    if (p99_goal > 0) {
      long p99_actual = _customer_accumulator.getPreparationResponseTimes().getP99();
      if (p99_actual > p99_goal) {
        Report.output("Failed p99 test: ", String.valueOf(p99_actual), " us > goal: ", String.valueOf(p99_goal), " us");
        result = false;
      }
    }

    long p99_9_goal = _config.MaxP99_9CustomerPrepMicroseconds();
    if (p99_9_goal > 0) {
      long p99_9_actual = _customer_accumulator.getPreparationResponseTimes().getP99_9();
      if (p99_9_actual > p99_9_goal) {
        Report.output("Failed p99_9 test: ", String.valueOf(p99_9_actual), " us > goal: ", String.valueOf(p99_9_goal), " us");
        result = false;
      }
    }

    long p99_99_goal = _config.MaxP99_99CustomerPrepMicroseconds();
    if (p99_99_goal > 0) {
      long p99_99_actual = _customer_accumulator.getPreparationResponseTimes().getP99_99();
      if (p99_99_actual > p99_99_goal) {
        Report.output("Failed p99_99 test: ", String.valueOf(p99_99_actual), " us > goal: ", String.valueOf(p99_99_goal), " us");
        result = false;
      }
    }

    long p99_999_goal = _config.MaxP99_999CustomerPrepMicroseconds();
    if (p99_999_goal > 0) {
      long p99_999_actual = _customer_accumulator.getPreparationResponseTimes().getP99_999();
      if (p99_999_actual > p99_999_goal) {
        Report.output("Failed p99_999 test: ", String.valueOf(p99_999_actual),
		      " us > goal: ", String.valueOf(p99_999_goal), " us");
        result = false;
      }
    }

    long p100_goal = _config.MaxP100CustomerPrepMicroseconds();
    if (p100_goal > 0) {
      long p100_actual = _customer_accumulator.getPreparationResponseTimes().getP100();
      if (p100_actual > p100_goal) {
        Report.output("Failed p100 test: ", String.valueOf(p100_actual), " us > goal: ", String.valueOf(p100_goal), " us");
        result = false;
      }
    }

    _customer_accumulator = null;
    _customer_alloc_accumulator  = null;
    _customer_garbage_accumulator = null;
    _server_accumulator = null;
    _server_alloc_accumulator = null;
    _server_garbage_accumulator = null;

    return result;
  }

  // No need to override superclass because Bootstrap introduces no
  // instance fields not already present in ExtrememThread.
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
  }
}
