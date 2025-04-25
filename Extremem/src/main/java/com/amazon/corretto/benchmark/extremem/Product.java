// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

class Product extends ExtrememObject {
  final private String name;
  final private String description;

  final private long id;
  private boolean available;

  // On input, name and description are treated as Ephemeral, but
  // their memory is not reclaimed by the caller.  The Product
  // constructor converts these two strings so they have LifeSpan ls.
  Product(ExtrememThread t, LifeSpan ls,
          long id, String name, String description) {
    super(t, ls);
    MemoryLog log = t.memoryLog();
    final Polarity Grow = Polarity.Expand;

    // Account for name, description fields
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 2);
    // Account for id, available fields
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow,
                   Util.SizeOfLong + Util.SizeOfBoolean);

    Util.convertEphemeralString(t, ls, name.length());
    Util.convertEphemeralString(t, ls, description.length());

    this.id = id;
    this.name = name;
    this.description = description;
    this.available = true;
  }

  /* Very rarely, a server thread will deactivate a Product before
   * removing it from the catalogue.  Once deactivated, this Product
   * will be removed from indexes and will be garbage collected after
   * all SalesTransaction and BrowsingHistory ojects that refer to
   * this Product have been processed and discarded.
   */
  synchronized void deactivate() {
    this.available = false;
  }

  synchronized boolean available() {
    return this.available;
  }

  final long id() {
    return id;
  }

  synchronized String name() {
    return name;
  }

  synchronized String description() {
    return description;
  }

  // Account for count instances of Product, excluding the
  // variable-size representations of Product names and descriptions.
  static void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p, int count) {
    ExtrememObject.tallyMemory(log, ls, p, count);
    
    // Account for name, description fields
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, count * 2);
    // Account for id, available
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
                   ((long) count) * (Util.SizeOfLong + Util.SizeOfBoolean));

  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    
    // Account for name, description
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
    // Account for id, available
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
                   Util.SizeOfLong + Util.SizeOfBoolean);

    // Account for Strings referenced from name and description
    Util.tallyString(log, ls, p, name.length());
    Util.tallyString(log, ls, p, description.length());
  }
}
