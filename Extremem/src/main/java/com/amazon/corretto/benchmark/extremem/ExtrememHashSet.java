// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.util.HashSet;

/**
 * Performs same services as HashSet while maintaining memory accounting.
 *
 * An ExtrememHashSet instance has long field (simulated_capacity),
 * float field (simulated_loadfactor), and reference field (ls).
 *
 * Though different versions of Java may implement HashSet
 * differently, this code assumes the following memory organization:
 *
 *  HashSet has a single reference field which is a pointer to an
 *  associated HashMap object.
 *
 *  Each HashMap instance consists of:
 *     an array of HashNodes (initial size 16 or as specified with
 *                            constructor argument)
 *     a reference to a set of keys that is consulted by iterators
 *        (For purposes of memory accounting, Assume this value is
 *         always null.  The additional memory required to implement
 *         HashSet iteration is approximated by Util.tallyHashSetIterator.)
 *     an int representing the number of elements in the HashSet.
 *     an int that counts the number of times this HashSet has been modified.
 *     an int that represents the next size at which the HashNode array
 *         will need to be expanded.
 *     a float that represents the load factor specified at
 *        construction time (or the default value)
 *
 * Each element of a HashSet is represented by a HashNode.  See
 * Util.java for the implementations of createHashNode() and
 * abandonHashNode(). 
 */
class ExtrememHashSet<E> extends HashSet<E> {
  static private final float DefaultLoadFactor = 0.75f;
  static private final int InitialHashSetArraySize = 16;

  private long simulated_capacity;
  private float simulated_loadfactor;
  private LifeSpan ls;

  ExtrememHashSet(ExtrememThread t, LifeSpan ls) {
    super();
    simulated_loadfactor = DefaultLoadFactor;
    simulated_capacity = InitialHashSetArraySize;

    this.ls = ls;
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;

    // Account for this object and for referenced HashMap object.
    log.accumulate(ls, MemoryFlavor.PlainObject, Grow, 2);
    // Account for this.ls, plus reference to HashMap from this, plus
    // reference to array of HashNodes and to set of keys from within
    // the associated HashMap..
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 4);
    // Account for simulated_capacity (long) and simulated_loadfactor
    // (float) plus associated HashMap object's 3 int fields and 1
    // float field.
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong
		   + 3 * Util.SizeOfInt + 2 * Util.SizeOfFloat);

    // Account for the referenced array.
    log.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference,
		   Grow, simulated_capacity);

    // Initial size known to equal 0, so no need to account for Hash nodes.
  }

  public boolean add(E e) {
    throw new IllegalArgumentException(
      "ExtrememHashSet.add expects ExtrememThread argument");
  }

  boolean add(ExtrememThread t, E e) {
    if (super.add(e)) {
      Util.createHashNode(t, ls);
      if (size() > simulated_capacity * simulated_loadfactor) {
	Util.abandonReferenceArray(t, ls, simulated_capacity);
	simulated_capacity *= 2;
	Util.referenceArray(t, ls, simulated_capacity);
      }
      return true;
    } else
      return  false;
  }

  long capacity() {
    return simulated_capacity;
  }

  public boolean remove(Object e) {
    throw new IllegalArgumentException(
      "ExtrememHashSet.remove expects ExtrememThread argument");
  }

  boolean remove(ExtrememThread t, Object e) {
    if (super.remove(e)) {
      Util.abandonHashNode(t, ls);
      return true;
    } else
      return false;
  }

  // Account for num_sets instances of HashSet with combined size as
  // denoted by cumulative_entries and cumulative_array_lengths.
  static void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p,
			  int num_sets, int cumulative_entries,
			  long cumulative_array_lengths) {

    // Account for this object and for referenced HashMap object.
    log.accumulate (ls, MemoryFlavor.PlainObject, p, num_sets * 2);
    // Account for this.ls, plus reference to HashMap from this, plus
    // reference to array of HashNodes and to set of keys from within
    // the associated HashMap.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, num_sets * 4);
    // Account for simulated_capacity (long) and simulated_loadfactor
    // (float) plus associated HashMap object's 3 int fields and 1
    // float field.
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, num_sets *
		   (Util.SizeOfLong + 3 * Util.SizeOfInt
		    + 2 * Util.SizeOfFloat));

    // Account for the referenced arrays.
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, num_sets);
    log.accumulate(ls, MemoryFlavor.ArrayReference,
		   p, cumulative_array_lengths);

    // Each value stored into the HashMap is represented by a Node
    // object.  Each Node object consists of a key, content, and next
    //  reference fields and an int hash_code field.
    //
    // Account for the Node entries.
    Util.tallyHashNodes(log, ls, p, cumulative_entries);
  }

  // When set shrinks, assume the simulated_capacity is not affected,
  // but we still have to account for garbage collection of the
  // abandoned Hash Node.

  // No need to override size(), or iterator() methods.
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {

    // Account for this object and for referenced HashMap object.
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 2);
    // Account for this.ls, plus reference to HashMap from this, plus
    // reference to array of HashNodes and to set of keys from within
    // the associated HashMap.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 4);
    // Account for simulated_capacity (long) and simulated_loadfactor
    // (float) plus associated HashMap object's 3 int fields and 1
    // float field.
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, Util.SizeOfLong
		   + 3 * Util.SizeOfInt + 2 * Util.SizeOfFloat);

    // Account for the referenced array.
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, p, simulated_capacity);

    // Each value stored into the HashMap is represented by a Node
    // object.  Each Node object consists of a key, content, and next
    //  reference fields and an int hash_code field.
    //
    // Account for the Node entries.
    Util.tallyHashNodes(log, ls, p, size());
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
}