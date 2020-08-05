// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * A BrowsingHistory object represents a possible interest in a
 * particular product for a possible future purchase.
 *
 * We maintain multiple BrowsingHistory queues to reduce global
 * contention.  Each thread is associated with one of N queues.
 */
class BrowsingHistory extends ExtrememObject {
  private final Customer customer;
  private final Product product;
  private final AbsoluteTime expiration;

  // Leave this as package access so BrowsingHistoryQueue can manipulate.
  private final BrowsingHistoryQueue my_queue;
  BrowsingHistory next;		// next Product interest for same Customer
  BrowsingHistory prev;		// prev Product interest for same Customer
  
  BrowsingHistory(ExtrememThread t, LifeSpan ls, Customer customer,
		  Product product, AbsoluteTime expiration,
		  BrowsingHistoryQueue bhq) {
    super(t, ls);
    this.customer = customer;
    this.product = product;
    expiration = new AbsoluteTime(t, expiration);
    expiration.changeLifeSpan(t, ls);
    this.expiration = expiration;
    this.my_queue = bhq;
    this.prev = this.next = null;
    // Account for customer, product, expiration, my_queue, next, prev fields.
    t.memoryLog().accumulate(ls,
			     MemoryFlavor.ObjectReference, Polarity.Expand, 6);
  }

  AbsoluteTime expirationTime() {
    return expiration;
  }

  Customer customer() {
    return customer;
  }

  Product product() {
    return product;
  }

  BrowsingHistoryQueue queue() {
    return my_queue;
  }

  /* Every subclass overrides this method if its size differs from
   * the size of its superclass.  */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    // Account for customer, product, expiration, my_queue, next, prev fields.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 6);
    expiration.tallyMemory(log, ls, p);
  }
}