// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * A SalesTransaction represents a proposal to sell a particular
 * product to a particular customer.  A subset of all proposed sales
 * will be transacted.  The other SalesTransaction objects will be
 * abandoned and garbage collected before the proposed business is
 * transacted.
 */
class SalesTransaction extends ExtrememObject {

  private Customer customer;
  private Product product;
  private String reviewer_info;
  SalesTransaction next;	// For linked list of pending transactions.

  SalesTransaction (ExtrememThread t, LifeSpan ls, Configuration config,
		    Product p, Customer c) {
    super (t, ls);
    this.customer = c;
    this.product = p;

    MemoryLog log = t.memoryLog ();
    final Polarity Grow = Polarity.Expand;
    // Account for customer, product, reviewer_info, next
    log.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 4);
    reviewer_info = Util.randomString(t, config.ProductReviewLength(), config);
    Util.convertEphemeralString(t, ls, reviewer_info.length());
  }

  final long hash(AbsoluteTime t) {
    return (t.seconds() & t.nanoseconds()) + product.id();
  }

  String review() {
    return reviewer_info;
  }

  Customer customer() {
    return customer;
  }

  Product product() {
    return product;
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    // Account for customer, product, reviewer_info, next
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 4);
    Util.tallyString(log, ls, p, reviewer_info.length());
  }
}

