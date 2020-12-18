// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * A SalesTransactionQeueue object represents a list of SalesTransaction
 * objects that need to be Transacted.  There may be multiple
 * SalesTransactionQueue objects to reduce global synchronization
 * contention that might occur if all threads used the same queue.
 *
 * Each SalesTransaction is associated with only one
 * SalesTransactionQueue at a time.
 *
 * Each CustomerThread and each ServerThread is associated with only
 * one SalesTransactionQueue.
 */
class SalesTransactionQueue extends ExtrememObject {
  // Points to the first entry on the queue, the entry that will
  // expire first.
  private SalesTransaction head, tail;

  SalesTransactionQueue (ExtrememThread t, LifeSpan ls) {
    super(t, ls);

    MemoryLog log = t.memoryLog ();
    final Polarity Grow = Polarity.Expand;
    // Account for head, tail
    log.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 2);
  }

  /**
   * Place node onto the queue of pending BrowsingHistory objects at
   * its end.
   */
  synchronized void enqueue(SalesTransaction purchase) {
    purchase.next = null;
    if (head == null)
      head = tail = purchase;
    else {
      tail.next = purchase;
      tail = purchase;
    }
  }

  synchronized SalesTransaction dequeue () {
    if (head == null)
      return null;
    else {
      SalesTransaction result = head;
      head = head.next;
      result.next = null;
      return result;
    }
  }

  /* Every subclass overrides this method if its size differs from
   * the size of its superclass.  */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    // account for head, tail
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
  }
}
