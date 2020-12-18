// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/* The memory accounting for CustomerLogAccumulator is the same as for
 * CustomerLog.
 */
class CustomerLogAccumulator extends CustomerLog {

  CustomerLogAccumulator(ExtrememThread t, LifeSpan ls) {
    super(t, ls);
  }

  // Since there is no synchronization on other, this method should be
  // called by a thread within which other is known to be coherent.
  public synchronized void accumulate(CustomerLog other) {
    engagements += other.engagements;

    total_any += other.total_any;
    if (other.min_any < this.min_any)
      this.min_any = other.min_any;
    if (other.max_any > this.max_any)
      this.max_any = other.max_any;

    total_all += other.total_all;
    if (other.min_all < this.min_all)
      this.min_all = other.min_all;
    if (other.max_all > this.max_all)
      this.max_all = other.max_all;

    total_previously_saved += other.total_previously_saved;
    if (other.min_saved < this.min_saved)
      this.min_saved = other.min_saved;
    if (other.max_saved > this.max_saved)
      this.max_saved = other.max_saved;

    total_selection += other.total_selection;
    if (other.min_selection < this.min_selection)
      this.min_selection = other.min_selection;
    if (other.max_selection > this.max_selection)
      this.max_selection = other.max_selection;

    total_purchased += other.total_purchased;
    total_saved += other.total_saved;
    total_abandoned += other.total_abandoned;
    total_do_nothings += other.total_do_nothings;
    
    preparer.addToLog(other.preparer);
    purchaser.addToLog(other.purchaser);
    saver.addToLog(other.saver);
    abandoner.addToLog(other.abandoner);
    loser.addToLog(other.loser);
  }

  synchronized void report(ExtrememThread t, String label, boolean reportCSV) {
    super.report(t, label, reportCSV);
  }
}
