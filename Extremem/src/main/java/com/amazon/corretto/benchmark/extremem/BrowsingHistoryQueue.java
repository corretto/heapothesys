// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * A BrowsingHistoryQueue object represents a list of BrowsingHistory
 * objects that need to be expired at some future time.  In general,
 * there may be multiple BrowsingHistoryQueue objects to reduce global
 * synchronization contention that might occur if all threads used the
 * same queue.
 *
 * Each CustomerThread is associated with only one
 * BrowsingHistoryQueue.  For the purpose of deactivating BrowsingHistory
 * objects when their lifetime has expired, each BrowsingHistoryQueue
 * is associated with only ServerThread.  When a ServerThread decides
 * to deactivate a Customer instance, all of the BrowsingHistory
 * objects associated with that Customer instance are removed from
 * their corresponding BrowsingHistoryQueue instances and discarded.
 * In this effort, the ServerThread that deactivates the customer will
 * need to figure out which BrowsingHistoryQueue holds each of the
 * BrowsingHistory objects that must also be decommissioned.  The
 * solution to this challenge is to record in each BrowsingHistory
 * object the BrowsingHistoryQueue on which it is waiting.
 *
 * If a Customer decision causes instantiation of a BrowsingHistory
 * object, that BrowsingHistory object will be enqueued on the
 * BrowsingHistoryQueue that is associated with the acting
 * CustomerThread instance.  If, at a later time, a particular
 * Customer instance is deactivated, all of the BrowsingHistory
 * objects that are associated with that Customer are removed from the
 * BrowsingHistoryQueue on which they are waiting to be deactivated,
 * and the BrowsingHistory objects are discarded.
 *
 * Since Customer objects are randomly associated with different
 * CustomerThreads at different times, the BrowsingHistory objects
 * associated with a particular Customer may be scattered between
 * multiple distinct BrowsingHistoryQueues.  To enable the
 * decommissioning of Customer objects and their associated
 * BrowsingHistory information, each BrowsingHistory object maintains
 * a pointer to its affiliated BrowsingHistoryQueue.
 */
class BrowsingHistoryQueue extends ExtrememObject {
  // Points to the first entry on the queue, the entry that will
  // expire first.
  private BrowsingHistory expiration_queue;

  BrowsingHistoryQueue(ExtrememThread t, LifeSpan ls) {
    super(t, ls);
    MemoryLog log = t.memoryLog();
    // account for expiration_queue
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 1);
  }

  /**
   * Place node onto the queue of pending BrowsingHistory objects at
   * its end.
   */
  synchronized void enqueue(BrowsingHistory history) {
    if (expiration_queue == null) {
      history.next = history;
      history.prev = history;
      expiration_queue = history;
    } else {
      history.prev = expiration_queue.prev;
      history.next = expiration_queue;
      history.prev.next = history;
      history.next.prev = history;
    }
  }

  synchronized BrowsingHistory pullIfExpired (AbsoluteTime now) {
    if (expiration_queue == null)
      return null;
    else {
      BrowsingHistory candidate = expiration_queue;
      if (candidate.expirationTime().compare(now) > 0)
        return null;
      else if (candidate.next == candidate) {
        expiration_queue = null;
        candidate.next = candidate.prev = null;
      } else {
        expiration_queue = expiration_queue.next;
        expiration_queue.prev = candidate.prev;
        expiration_queue.prev.next = expiration_queue;
        expiration_queue.next.prev = expiration_queue;
      }
      return candidate;
    }
  }

  synchronized void dequeue (BrowsingHistory remove_me) {
    if (remove_me.next == remove_me) {
      expiration_queue = null;
      remove_me.next = remove_me.prev = null;
    } else {
      remove_me.next.prev = remove_me.prev;
      remove_me.prev.next = remove_me.next;
      if (expiration_queue == remove_me)
        expiration_queue = remove_me.next;
      remove_me.next = remove_me.prev = null;
    }
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    // account for expiration_queue
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);
  }
}
