// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/* The memory accounting for ServerLogAccumulator is the same as for
 * ServerLog.
 */
class ServerLogAccumulator extends ServerLog {

  ServerLogAccumulator(ExtrememThread t, LifeSpan ls, int response_time_measurements) {
    super(t, ls, response_time_measurements);
  }

  // Since there is no synchronization on other, this method should be
  // called by a thread within which other is known to be coherent.
  public synchronized void accumulate(ServerLog other) {
    total_do_nothings += other.total_do_nothings;

    total_xact += other.total_xact;
    xact_batches += other.xact_batches;
    if (other.min_xact_per_batch < this.min_xact_per_batch)
      this.min_xact_per_batch = other.min_xact_per_batch;
    if (other.max_xact_per_batch > this.max_xact_per_batch)
      this.max_xact_per_batch = other.max_xact_per_batch;
    
    total_histories += other.total_histories;
    history_batches += other.history_batches;
    if (other.min_history_per_batch < this.min_history_per_batch)
      this.min_history_per_batch = other.min_history_per_batch;
    if (other.max_history_per_batch > this.max_history_per_batch)
      this.max_history_per_batch = other.max_history_per_batch;

    total_customers += other.total_customers;
    customer_batches += other.customer_batches;
    if (other.min_customer_per_batch < this.min_customer_per_batch)
      this.min_customer_per_batch = other.min_customer_per_batch;
    if (other.max_customer_per_batch > this.max_customer_per_batch)
      this.max_customer_per_batch = other.max_customer_per_batch;

    total_products += other.total_products;
    product_batches += other.product_batches;
    if (other.min_product_per_batch < this.min_product_per_batch)
      this.min_product_per_batch = other.min_product_per_batch;
    if (other.max_product_per_batch > this.max_product_per_batch)
      this.max_product_per_batch = other.max_product_per_batch;

    sales_xact.addToLog(other.sales_xact);
    expire_history.addToLog(other.expire_history);
    replace_customers.addToLog(other.replace_customers);
    replace_products.addToLog(other.replace_products);
    do_nothing.addToLog(other.do_nothing);

    xact_response_times.addToLog(other.xact_response_times);
    history_response_times.addToLog(other.history_response_times);
    customer_response_times.addToLog(other.customer_response_times);
    product_response_times.addToLog(other.product_response_times);
    do_nothing_response_times.addToLog(other.do_nothing_response_times);
  }

  public synchronized void report(ExtrememThread t,
                                  String label, boolean reportCSV) {
    super.report(t, label, reportCSV);
  }

  public synchronized void reportPercentiles(ExtrememThread t, boolean reportCSV) {
    Report.output("");
    Report.output("Server Response-Time Percentiles (in microseconds)");
    if (reportCSV) {
      Report.output("Label, P50, P95, P99, P99.9, P99.99, P99.999, P100");
      Report.outputNoLine("Sales Transaction Processing, ");
      xact_response_times.report(t, true);
      Report.outputNoLine("Browsing History Processing, ");
      history_response_times.report(t, true);
      Report.outputNoLine("Customer Replacement Processing, ");
      customer_response_times.report(t, true);
      Report.outputNoLine("Product Replacement Processing, ");
      product_response_times.report(t, true);
      Report.outputNoLine("Do Nothing Processing, ");
      do_nothing_response_times.report(t, true);
      Report.output("* indicates insufficient data to report this percentile");
    } else {
      Report.outputNoLine("Sales Transaction Processing: ");
      xact_response_times.report(t, false);
      Report.outputNoLine("Browsing History Processing: ");
      history_response_times.report(t, false);
      Report.outputNoLine("Customer Replacement Processing: ");
      customer_response_times.report(t, false);
      Report.outputNoLine("Product Replacement Processing: ");
      product_response_times.report(t, false);
      Report.outputNoLine("Do Nothing Processing: ");
      do_nothing_response_times.report(t, false);
      Report.output("* indicates insufficient data to report this percentile");
    }
  }
}
