// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * Nearly all objects instantiated by the Extremem workload are
 * subclasses of ExtrememObject.  This class provides instance data
 * and methods to facilitate accounting for allocation and
 * deallocation of objects.
 */
abstract class ExtrememObject {
  private LifeSpan ls;		// Default lifespan

  /**
   * Allocate a new ExtrememObject, charging the memory used for its
   * representation to ExtrememThread t, expecting the allocated
   * object to have life span represented by LifeSpan ls.
   */
  ExtrememObject(ExtrememThread t, LifeSpan ls) {
    this.ls = ls;
    MemoryLog log = t.memoryLog();
    // Account for this object
    log.accumulate(ls, MemoryFlavor.PlainObject, Polarity.Expand, 1);
    // Account for ls field
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 1);
  }

  final LifeSpan intendedLifeSpan() {
    return ls;
  }

  /**
   * tallyMemory accounts for the memory of this object and the
   * objects it instantiates.  It does not necessarily account for all
   * of the objects this object references, as objects instantiated
   * elsewhere and simply referenced from this object are typically
   * accounted for at their instantiation point.
   *
   * Every subclass overrides this method if the amount of memory
   * required to represent the object may differ from the amount of
   * memory consumed by its superclass. 
   *
   * In general, constructors of non-final classes should not invoke
   * tallyMemory because subclasses haven't yet been fully
   * constructed, and their implementations of tallyMemory may 
   * depend on fields not yet initialized.
   */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    // Account for this object.
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 1);
    // And account for its ls field.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);
  }

  // Tally memory for count instances of ExtrememObject.
  static void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p, int count) {
    // Account for this object.
    log.accumulate(ls, MemoryFlavor.PlainObject, p, count);
    // And account for its ls field.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, count);
  }

  /**
   * Tally for thread t the amount of memory consumed by
   * representation of this object.  If instantiation of this object
   * triggers allocation of additional objects, the memory to
   * represent these allocated objects is also accumulated into the
   * tallies.
   *
   * In the case that this object references other objects that were
   * allocated before this object and passed in as arguments to its
   * constructor, these referenced objects are generally not included
   * as part of the memory footprint.
   */
  final void memoryFootprint(ExtrememThread t) {
    this.tallyMemory(t.memoryLog(), ls, Polarity.Expand);
   }

  /**
   * When this object becomes garbage, tally that this thread has
   * opportunity to reclaim this much more memory.
   */
  final void garbageFootprint(ExtrememThread t) {
    this.tallyMemory(t.garbageLog(), ls, Polarity.Expand);
  }

  /**
   * Change the LifeSpan of this object.  In typical usage, a newly
   * allocated object was presumed to have Ephemeral life span when it
   * was first allocated, but was subsequently determined have longer
   * life.  Adjust tallies appropriately.
   */
  final void changeLifeSpan (ExtrememThread t, LifeSpan ls) {
    this.tallyMemory(t.memoryLog(), this.ls, Polarity.Shrink);
    this.ls = ls;
    this.tallyMemory(t.memoryLog(), this.ls, Polarity.Expand);
  }
}