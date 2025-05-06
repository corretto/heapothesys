// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class CustomerThread extends ExtrememThread {
  private static final int BuyProduct = 0;
  private static final int SaveProductForLater = 1;
  private static final int AbandonProduct = 2;

  private final RelativeTime period;
  private final RelativeTime think_time;

  private final Customers all_customers;
  private final Products all_products;
  private AbsoluteTime next_release_time;
  private AbsoluteTime end_simulation_time;

  private final BrowsingHistoryQueue browsing_queue;
  private final SalesTransactionQueue sales_queue;

  private final CustomerLog history;
  private final CustomerLogAccumulator accumulator;
  private final MemoryLog alloc_accumulator;
  private final MemoryLog garbage_accumulator;

  /**
   * CustomerThread runs simulations of customer activities.
   *
   * The release times for each customer simulation are staggered so
   * that multiple concurrent and parallel CustomerThread simulations
   * do not typically start at exactly the same time.
   *
   * Memory accounting: The memory allocated for each CustomerThread
   * instance is accounted in the memoryLog() for this CustomerThread.
   * The Bootstrap thread accounts for this CustomerThread instance's
   * garbage.
   */
  CustomerThread (Configuration config, RelativeTime period, RelativeTime think_time, long random_seed, int sequence_no,
                  Products all_products, Customers all_customers, BrowsingHistoryQueue browsing_queue,
                  SalesTransactionQueue sales_queue,  CustomerLogAccumulator accumulator,
                  MemoryLog alloc_accumulator, MemoryLog garbage_accumulator) {
    super (config, random_seed);
    MemoryLog log = this.memoryLog();
    MemoryLog garbage = this.garbageLog();
    this.setLabel(Util.i2s(this, sequence_no));
    
    Trace.msg(1, "@ ",
              Integer.toString(log.hashCode()),
              ": CustomerThread[", label, "].memoryLog()");
    Trace.msg(1, "@ ",
              Integer.toString(garbage.hashCode()),
              ": CustomerThread[", label, "].garbageLog()");
    
    Util.convertEphemeralString(this, LifeSpan.NearlyForever, label.length());

    this.period = period;
    this.think_time = think_time;

    this.all_customers = all_customers;
    this.all_products = all_products;
    this.browsing_queue = browsing_queue;
    this.sales_queue = sales_queue;
    
    this.accumulator = accumulator;
    this.alloc_accumulator = alloc_accumulator;
    this.garbage_accumulator = garbage_accumulator;
    
    history = new CustomerLog(this, LifeSpan.NearlyForever, config.ResponseTimeMeasurements());
    
    // Account for 11 reference fields: label, all_customers,
    // all_products, next_release_time, end_simulation_time,
    // browsing_queue, sales_queue, history, accumulator,
    // alloc_accumulator, garbage_accumulator
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 11);
    
    // Account for object referenced by history.
    history.tallyMemory(log, ls, Polarity.Expand);
  }

  public void setStartAndStop(AbsoluteTime start, AbsoluteTime stop) {
    // We'll count the period of CustomerThread activities as
    // Ephemeral: next_release is discarded and reallocated every period.
    this.next_release_time = new AbsoluteTime(this, start);
    this.end_simulation_time = stop;
  }

  public void runExtreme() {
    while (true) {
      // If the simulation will have ended before we wake up, don't
      // even bother to sleep.
      if (next_release_time.compare(end_simulation_time) >= 0)
        break;

      AbsoluteTime now = next_release_time.sleep(this);
      Customer customer = all_customers.selectRandomCustomer(this);
      now.garbageFootprint(this);

      // In an earlier implementation, termination of the thread was
      // determined by comparing next_release_time against
      // end_simulation_time. In the case that the thread falls
      // hopelessly behind schedule, the thread takes "forever" to
      // terminate.
      if (now.compare(end_simulation_time) >= 0)
        break;

      Trace.msg(4, "CustomerThread ", label,
                ", random customer: ", customer.name(),
                ", id: ", Long.toString(customer.id()));

      // keywords, all, any are all treated as Ephemeral.
      String[] keywords = randomKeywords(config.KeywordSearchCount());
      Product[] all = all_products.lookupProductsMatchingAll(this, keywords);
      Product[] any;
      if (config.AllowAnyMatch()) {
	any = all_products.lookupProductsMatchingAny(this, keywords);
      } else {
	any = new Product[0];
      }
      
      int all_count = all.length;
      int any_count = any.length;

      if (Trace.enabled(3)) {
        for (int i = 0; i < keywords.length; i++)
          Trace.msg(4, "CustomerThread ", label,
                    " looking for keyword: ", keywords[i]);
        for (int i = 0; i < all.length; i++)
          Trace.msg(4, "CustomerThread ", label,
                    " matched all: ", all[i].name());
        for (int i = 0; i < any.length; i++)
          Trace.msg(4, "CustomerThread ", label,
                    " matched any: ", any[i].name());
      }

      // keywords array is now garbage
      Util.abandonEphemeralReferenceArray(this, keywords.length);

      Product[] scrutinize_list, garbage_list;
      if (all_count > 0) {
        scrutinize_list = all;
        garbage_list = any;
      } else {
        scrutinize_list = any;
        garbage_list = all;
      }
      all = any = null;
      // garbage_list is now garbage
      Util.abandonEphemeralReferenceArray(this, garbage_list.length);
      
      int candidate_cnt = scrutinize_list.length;
      // saved4laters is Ephemeral
      Product[] saved4laters = (
        customer.getAllSavedForLater(this, LifeSpan.Ephemeral));
      
      int saved_count = saved4laters.length;
      Product[] candidates;
      if (saved_count > 0) {
        int i;
        candidates = new Product[candidate_cnt + saved_count];
        Util.ephemeralReferenceArray(this, candidate_cnt + saved_count);
        for (i = 0; i < candidate_cnt; i++) 
          candidates[i] = scrutinize_list[i];
        for (int j = 0; j < saved_count; j++)
          candidates[i++] = saved4laters[j];
        candidate_cnt += saved_count;
        // scrutinize_list is now garbage.
        Util.abandonEphemeralReferenceArray(this, scrutinize_list.length);
      } else
        candidates = scrutinize_list;
      // saved4laters is now garbage.
      Util.abandonEphemeralReferenceArray(this, saved_count);
      
      // candidate_cnt represents length of candidates array.
      if (candidate_cnt <= 0) {
        candidates = null;
        // candidates, known to have length 0, is now garbage.
        Util.abandonEphemeralReferenceArray(this, 0);
        
        // else, search came up empty.  wait for next period.
        Trace.msg(4, "Customer Thread ", label, " matched no customers");
        history.logNoChoice(this, next_release_time);

        AbsoluteTime end_think = next_release_time.addRelative(this, this.think_time);
        end_think.changeLifeSpan(this, LifeSpan.TransientShort);
        history.logPrepareToThink(this, next_release_time,
                                  all_count, any_count, saved_count);
        end_think.sleep(this);
        end_think.garbageFootprint(this);
      } else {
        SalesTransaction[] prospects = new SalesTransaction[candidate_cnt];
        Util.referenceArray(this, LifeSpan.TransientShort, candidate_cnt);
        
        for (int i = 0; i < candidate_cnt; i++)
          prospects[i] = new SalesTransaction(this, LifeSpan.TransientShort,
                                              config, candidates[i],
                                              customer);
        // candidates is now garbage.
        Util.abandonEphemeralReferenceArray(this, prospects.length);

        String[] selectors = randomKeywords(config.SelectionCriteriaCount());
        Util.convertStringArray(this, LifeSpan.TransientShort,
                                selectors.length);

        AbsoluteTime end_think = next_release_time.addRelative(this, this.think_time);
        end_think.changeLifeSpan(this, LifeSpan.TransientShort);
        history.logPrepareToThink(this, next_release_time,
                                  all_count, any_count, saved_count);
        end_think.sleep(this);
        end_think.garbageFootprint(this);

        Trace.msg(4, "Customer Thread ", label,
                  ", woke from thinking @ ", end_think.toString(this),
                  ", candidates:");
        if (Trace.enabled(3)) {
          for (int i = 0; i < candidate_cnt; i++) {
            Trace.msg(4, " product id: ",
                      Long.toString(prospects[i].product().id()),
                      ", name: ", prospects[i].product().name());
          }
        }

        // rankings is ephemeral.
        float[] rankings = rankFuzzyMatches(selectors, prospects);
        int best_index = 0;
        float best_match = rankings [0];
        for (int i = 1; i < rankings.length; i++) {
          if (rankings [i] > best_match) {
            best_match = rankings [i];
            best_index = i;
          }
        }
        // Abandon rankings.
        Util.abandonEphemeralRSBArray(this,
                                      rankings.length, Util.SizeOfFloat);

        Trace.msg(4, "Customer Thread ", label, ", selected option ",
                  Integer.toString(best_index));

        SalesTransaction selected = prospects[best_index];
        // Other SalesTransactions and selection_criteria are now all garbage
        // (Some compilers/collectors may be better at discovering
        // this than others.)

        for (int i = 0; i < prospects.length; i++)
          if (i != best_index) prospects[i].garbageFootprint(this);
        // prospects is now garbage.
        Util.abandonReferenceArray(this, LifeSpan.TransientShort,
                                   prospects.length);
        // selectors is now garbage.
        Util.abandonStringArray(this,
                                LifeSpan.TransientShort, selectors.length);

        float coin = this.randomFloat();
        switch (interpretCoinToss(coin)) {

          case BuyProduct:
            Trace.msg(4, "Customer Thread ", label, ", chose buy");
            sales_queue.enqueue(selected);
            selected.changeLifeSpan(this, LifeSpan.TransientIntermediate);
            // Garbage collection of the selected object is the
            // "responsibility" of the ServerThread.  After the
            // ServerThread transacts a sale, it marks the selected
            // SalesTransaction object as garbage.  This is the same
            // process used even if the associated customer or product
            // becomes decommissioned before the sale is transacted.
            history.logPurchase(this, end_think);
            break;

          case SaveProductForLater:
            Trace.msg(4,
                      "Customer Thread ", label, ", chose save for later");
            Customer c = selected.customer();
            Product p = selected.product();
            // selected is now garbage.
            selected.garbageFootprint(this);

            AbsoluteTime expiration = AbsoluteTime.now(this);
            expiration.garbageFootprint(this);
            expiration = expiration.addRelative(this,
                                                config.BrowsingExpiration());
            BrowsingHistory h = new BrowsingHistory(this,
                                                    LifeSpan.
                                                    TransientLingering,
                                                    c, p, expiration,
                                                    browsing_queue);
            expiration.garbageFootprint(this);
            browsing_queue.enqueue(h);
            all_customers.addSaveForLater(c, this, h);
            // Garbage collection of the h BrowsingHistory object is
            // the "responsibility" of the ServerThread.  After the
            // ServerThread expires h because its expiration time has
            // been reached, it object h is marked as eligible for
            // garbage collection.  In the rare case that customer c
            // is decommissioned before BrowsingHistory h's expiration
            // has been reached, the memory belong to h will be marked
            // as eligible for garbage collection by the
            // Customer.prepareForDemise() method that executes when
            // the customer is decommissioned.
            history.log4Later(this, end_think);
            break;
            
          case AbandonProduct:
            // selected is garbage.
            selected.garbageFootprint(this);

            Trace.msg(4, "Customer Thread ", label, ", chose abandon");
            history.logAbandonment(this, end_think);
        }
      }
      next_release_time.garbageFootprint(this);
      next_release_time = next_release_time.addRelative(this, this.period);
    }
    Trace.msg(2, "Customer thread ", label, " terminating.  Time is up.");

    // We accumulate accumulator even if reporting individual threads
    accumulator.accumulate(history);
    if (config.ReportIndividualThreads())
      this.report(this);
    else {
      alloc_accumulator.foldInto(memoryLog());
      garbage_accumulator.foldInto(garbageLog());
    }
  }

  /**
   * Remove this BrowsingHistory object from the browsing queue as
   * the associated Customer is being deactivated.
   */
  void forgetHistory(BrowsingHistory h) {
    browsing_queue.dequeue(h);
  }

  int interpretCoinToss(float c) {
    if (c < config.BuyThreshold())
      return BuyProduct;
    else if (c < (config.SaveForLaterThreshold() + config.BuyThreshold()))
      return SaveProductForLater;
    else
      return AbandonProduct;
  }

  // This goodness is simplistic.  It represents a small amount of
  // computation with a bunch of Ephemeral allocation that is not
  // easily optimized (entirely) away. Note that "more sophisticated"
  // implementations may be able to stack allocate much of the
  // ephemeral memory allocated here.  If some VMs manage to stack
  // allocate this ephemeral memory, more power to them.
  private float goodness (String keyword, String text) {
    float similarity = 1.0f;
    float scale = 0.9375f;
    float penalize = scale;
    boolean found_match = false;
    for (int sub_length = keyword.length();
         !found_match && (sub_length > 0); sub_length--) {
      for (int start = 0;
           !found_match && (start + sub_length <= keyword.length()); start++) {
        String trial = keyword.substring (start, start + sub_length);
        int index = text.indexOf(trial);
        if (text.indexOf(trial) < 0)
          similarity *= penalize;
        else
          found_match = true;
      }
      penalize *= scale;
    }
    return similarity;
  }

  private float rankProspect (String[] criteria, SalesTransaction prospect) {
    String review = prospect.review();
    float ranking = 1.0f;
    for (int i = 0; i < criteria.length; i++)
      ranking *= goodness (criteria[i], review);
    return ranking;
  }

  // Returned array allocated in Ephemeral memory.
  float[] rankFuzzyMatches (String[] criteria,
                            SalesTransaction[] prospects) {
    int len = prospects.length;
    float[] results = new float[len];
    Util.ephemeralRSBArray(this, len, Util.SizeOfFloat);
    for (int i = 0; i < prospects.length; i++)
      results[i] = rankProspect(criteria, prospects[i]);
    return results;
  }

  void report(ExtrememThread t) {
    Report.acquireReportLock();
    history.report(t, label, config.ReportCSV());
    Report.output("Customer Thread ", label, " memory behavior");
    MemoryLog.report(t, config.ReportCSV(), memoryLog(), garbageLog());
    Report.releaseReportLock();
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for 11 reference fields: label, all_customers,
    // all_products, next_release_time, end_simulation_time,
    // browsing_queue, sales_queue, history, accumulator,
    // alloc_accumulator, garbage_accumulator
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 11);

    // Account for String referenced by label.
    Util.tallyString(log, ls, p, label.length());

    // Account for next_release_time
    next_release_time.tallyMemory(log, LifeSpan.Ephemeral, p);

    // Account for object referenced by history.
    history.tallyMemory(log, ls, p);
  }
}
