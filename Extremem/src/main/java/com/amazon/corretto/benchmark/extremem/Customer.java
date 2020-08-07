// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class Customer extends ExtrememObject {
  private static final int InitialSaveForLaterQueueSize = 8;

  private final String name;
  private final long id;

  // This field accumulates a hash representing all products purchased
  // by this customer.  
  long purchase_hash;

  private int fsfl;			// first saved for later
  private int csfl;			// count of saved for later
  private BrowsingHistory [] sflq;	// Save for later queue

  Customer (ExtrememThread t, LifeSpan ls, String name, long uniq_id) {
    super(t, ls);
    final Polarity Grow = Polarity.Expand;
    final MemoryLog log = t.memoryLog();
    this.name = name;
    this.id = uniq_id;
    sflq = new BrowsingHistory [InitialSaveForLaterQueueSize];
    fsfl = 0;
    csfl = 0;
    // account for reference name, sflq; long id, purchase_hash;
    // int fsfl, csfl
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 2);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow,
		   2 * Util.SizeOfLong + 2 * Util.SizeOfInt);
    // account for allocated array referenced from sflq
    log.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, Grow,
		   InitialSaveForLaterQueueSize);
  }

  String name () {
    return name;
  }

  long id () {
    return id;
  }

  synchronized int browsingHistorySize() {
    return sflq.length;
  }

  synchronized void transactSale(ExtrememThread t, AbsoluteTime release_time,
				 SalesTransaction sale) {
    purchase_hash += sale.hash(release_time);
  }

  // Add s4l to this Customer's BrowsingHistoryQueue.  Return the
  // number of new slots added to this Customer's
  // BrowsingHistoryQueue.  Typical return value is zero.
  synchronized int addSaveForLater(ExtrememThread t, BrowsingHistory s4l) {
    int bqes = 0;		// browsing queue expansion slots
    if (csfl >= sflq.length) {	// double the size of existing queue
      final Polarity Grow = Polarity.Expand;
      int old_size = sflq.length;
      final MemoryLog log = t.memoryLog();
      final MemoryLog garbage = t.garbageLog();

      log.accumulate(LifeSpan.NearlyForever,
		     MemoryFlavor.ArrayObject, Grow, 1);
      log.accumulate(LifeSpan.NearlyForever,
		     MemoryFlavor.ArrayReference, Grow, old_size);

      bqes = old_size;
      BrowsingHistory[] new_queue = new BrowsingHistory [2 * old_size];
      for (int i = 0; i < csfl; i++) {
	new_queue [i] = sflq [fsfl++];
	if (fsfl == old_size)
	  fsfl = 0;
      }
      sflq = new_queue;		// old queue becomes garbage

      garbage.accumulate(LifeSpan.NearlyForever,
			 MemoryFlavor.ArrayObject, Grow, 1);
      garbage.accumulate(LifeSpan.NearlyForever,
			 MemoryFlavor.ArrayReference, Grow, old_size);
      fsfl = 0;
    }
    int queue_length = sflq.length;
    sflq [(fsfl + csfl++) % queue_length] = s4l;
    return bqes;
  }

  /**
   *  Remove entry h from this customer's save-for-later queue.
   *  Typically, h is the lead entry in the save-for-later queue as h
   *  has been identifid by a background server thread as the "oldest"
   *  expired BrowsingHistory object.  However, due to multiple
   *  possible race conditions, the order in which BrowsingHistory
   *  objects are retired may differ slightly from the order in which
   *  the entries reside in the Customer's save-for-later queue.
   */
  synchronized void retireOneSaveForLater (BrowsingHistory h) {
    // first, find the entry to be retired.
    int index = 0;
    assert (csfl > 0): " internal error in Customer save-for-later queue";
    for (int i = 0; i < csfl; i++) {
      index = (fsfl + i) % sflq.length;
      if (sflq[index] == h)
	break;
    }
    assert (sflq[index] == h): " BrowsingHistory not in queue";

    if (index != fsfl) {
      do {
	int prior_index = index - 1;
	if (prior_index < 0)
	  prior_index = sflq.length - 1;
	sflq[index] = sflq[prior_index];
	index = prior_index;
      } while (index != fsfl);
    } // else: common case: head of saved-for-later is entry to remove
    sflq[fsfl++] = null;
    csfl--;
    if (fsfl == sflq.length)
      fsfl = 0;

    // We'll let the server thread account for garbage after the 
    // BrowsingHistory object is removed from expiration queue.
  }

  /**
   * This provides an array of all saved-for-later requests associated
   * with this Customer for purposes of allowing the Customer to make
   * an informed buying decision.  The anticipated lifespan for the
   * allocated array is the configuration-specified think time.
   */
  synchronized Product [] getAllSavedForLater(ExtrememThread t, LifeSpan ls) {
    int i, index;
    Product [] result = new Product[csfl];
    Util.ephemeralReferenceArray(t, csfl);

    for (i = 0, index = fsfl; i < csfl; i++) {
      result[i] = sflq[index++].product();
      if (index == sflq.length)
	index = 0;
    }
    return result;
  }
  
  // This Customer is about to be decommissioned (and garbage
  // collected).  Break the obsolete links that may refer to this
  // Customer object in order to assure it will be promptly garbage
  // collected:
  //    1. If this customer has one or more remembered save-for-later
  //       requests which are scheduled to be forgotten at a particular
  //       future time, remove the "pending history" request from the
  //       scheduling queue.
  //    2. If this customer has pending sales transactions, they will
  //       be processed at the appropriate future time.  Only after
  //       all pending sales transactions have been processed will
  //       this object be garbage collected.
  // Return the number of BrowsingHistory slots in this Customer
  // instance's save-for-later queue.
  synchronized int prepareForDemise(ExtrememThread t) {
    int index = fsfl;
    for (int i = 0; i < csfl; i++) {
      BrowsingHistory h = sflq[index];
      h.queue().dequeue(h);
      h.garbageFootprint(t);
      sflq[index++] = null;
      if (index >= sflq.length)
	index = 0;
    }
    fsfl = 0;
    csfl = 0;
    return sflq.length;
  }

  // Account for count instances of Customer, excluding the
  // variable-size representations of Customer names.
  static void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p,
			  int count, int cbhs) {
    ExtrememObject.tallyMemory(log, ls, p, count);
    
    // Each Customer has two reference fields: name, sflq
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, count * 2);
    // And two long fields: id, purchase_hash, and two int fields: fsfl, csfl
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
		   count * 2 * (Util.SizeOfLong + Util.SizeOfInt));

    // account for the accumulation of allocated arrays referenced
    // from each Customer's sflq field.
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, count);
    log.accumulate(ls, MemoryFlavor.ArrayReference, p, cbhs);
  }


  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // account for reference name, sflq; long id, purchase_hash;
    // int fsfl, csfl
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
		   2 * Util.SizeOfLong + 2 * Util.SizeOfInt);
    // account for allocated array referenced from sflq
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, p, sflq.length);

    // The name is uniquely constructed for the purpose of identifying
    // this Customer.  When the Customer becomes garbage, memory
    // associated with name is reclaimed.
    Util.tallyString(log, ls, p, name.length());
  }
}