// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * This class represents the traditional services of an array, using
 * a fan-out data structure to organize the elements of the array, with
 * no array used in the implementation of this abstraction larger than
 * config.MaxArrayLength().  If config.MaxArrayLength() equals zero,
 * there is no restriction on array lengths.
 */

class ArrayletOflong extends ExtrememObject {
  private final int length;	    // Number of elements in Arraylet
  private final int max_length;     // Max array length
  private final int num_tiers;	    // How may levels in fan-out structure?
  private final int top_entry_span; // Each element of root spans this many
  private final Object[] root;	    // Root of fan-out structure
  private int total_arrays;
  private int total_array_ref_elements;
  private int total_array_long_elements;

  ArrayletOflong (ExtrememThread t, LifeSpan ls, int max_length, int length) {
    super(t, ls);
    Polarity Grow = Polarity.Expand;
    MemoryLog memory = t.memoryLog();
    this.length = length;
    if (max_length == 0) {
      this.max_length = length;
      this.num_tiers = 1;
      this.total_arrays = 2;
      this.total_array_ref_elements = 1;
      this.total_array_long_elements = length;
      long[] array = new long[length];
      root = new Object[1];
      root[0] = array;
      this.top_entry_span = length;
    } else {
      this.max_length = max_length;
      this.total_arrays = 0;
      this.total_array_ref_elements = 0;
      this.total_array_long_elements = 0;
      int num_tiers = 1;
      // At bottom tier of fan-out structure, each entry spans this
      // many ArrayLet elements.
      int span_of_entry = max_length;
      while (span_of_entry * max_length < length) {
	num_tiers++;
	span_of_entry *= max_length;
      }
      this.num_tiers = num_tiers;
      this.top_entry_span = span_of_entry;

      int[] counts = new int[num_tiers];
      Object[][] arrays = new Object[num_tiers][];

      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 2);
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB,
			Grow, num_tiers * Util.SizeOfInt);
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
			Grow, num_tiers);

      for (int i = 0; i < num_tiers; i++) {
	arrays[i] = new Object[max_length];
	this.total_arrays++;
	this.total_array_ref_elements += max_length;
	if (i > 0) {
	  arrays[i-1][0] = arrays[i];
	  counts[i-1] = 1;
	}
      }
      this.root = arrays[0];
      int num_leaf_arrays = (length + max_length - 1) / max_length;
      for (int i = 0; i < num_leaf_arrays; i++) {
	long[] element_array = new long[max_length];
	this.total_arrays++;
	this.total_array_long_elements += max_length;
	adjustForNewLeaf(counts, arrays);
	arrays[num_tiers-1][counts[num_tiers-1]-1] = (Object) element_array;
      }

      MemoryLog garbage = t.garbageLog();
      garbage.accumulate(LifeSpan.Ephemeral,
			 MemoryFlavor.ArrayObject, Grow, 2);
      garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB,
			 Grow, num_tiers * Util.SizeOfInt);
      garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
			 Grow, num_tiers);
    }
    // Account for 7 ints: length, max_length, num_tiers, top_entry_span,
    // total_arrays, total_array_ref_elements, total_array_long_elements
    memory.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, 7 * Util.SizeOfInt);
    // Account for root
    memory.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 1);


    memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, total_arrays);
    memory.accumulate(ls, MemoryFlavor.ArrayReference,
		      Grow, total_array_ref_elements);
    memory.accumulate(ls, MemoryFlavor.ArrayRSB, Grow,
		      this.total_array_long_elements * Util.SizeOfLong);
  }

  private final void adjustForNewLeaf(int[] counts, Object[][] arrays) {
    int focus_level = num_tiers - 1;
    if (counts[focus_level] < max_length) {
      counts[focus_level]++;
    } else {
      while ((focus_level > 0) && (counts[focus_level] >= max_length)) {
	arrays[focus_level] = new Object[max_length];
	this.total_arrays++;
	this.total_array_ref_elements += max_length;
	counts[focus_level] = 1;
	if (focus_level < num_tiers - 1)
	  arrays[focus_level][0] = arrays[focus_level+1];
	focus_level--;
      }
      if (focus_level < num_tiers - 1) {
	arrays[focus_level][counts[focus_level]] = arrays[focus_level+1];
	counts[focus_level]++;
      }
    }
  }
  final long get(int at) {
    if ((at < 0) || (at >= length)) {
      Exception x = new ArrayIndexOutOfBoundsException(at);
      Util.fatalException("Index out of bounds in Arraylet.get", x);
    }
    Object[] fan_out_node = root;
    int entry_span = top_entry_span;
    for (int i = 1; i < num_tiers; i++) {
      fan_out_node = (Object []) fan_out_node[at / entry_span];
      at %= entry_span;
      entry_span /= max_length;
    }
    long[] elements = (long []) fan_out_node[at / max_length];
    return elements[at % max_length];
  }
  
  final void set(int at, long value) {
    if ((at < 0) || (at >= length)) {
      Exception x = new ArrayIndexOutOfBoundsException(at);
      Util.fatalException("Index out of bounds in Arraylet.get", x);
    }
    Object[] fan_out_node = root;
    int entry_span = top_entry_span;
    for (int i = 1; i < num_tiers; i++) {
      fan_out_node = (Object []) fan_out_node[at / entry_span];
      at %= entry_span;
      entry_span /= max_length;
    }
    long[] elements = (long []) fan_out_node[at / max_length];
    elements[at % max_length] = value;
  }

  final int length() {
    return length;
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for 7 ints: length, max_length, num_tiers, top_entry_span,
    // total_arrays, total_array_ref_elements, total_array_long_elements
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 7 * Util.SizeOfInt);
    // Account for root
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);


    log.accumulate(ls, MemoryFlavor.ArrayObject, p, this.total_arrays);
    log.accumulate(ls, MemoryFlavor.ArrayReference,
		   p, this.total_array_ref_elements);
    log.accumulate(ls, MemoryFlavor.ArrayRSB,
		   p, this.total_array_long_elements);
  }

  public static void main(String args[]) {
    Trace.debug("Testing Arraylet with max size 4");

    // Instantiate but do not run the Bootstrap thread.  Just need a
    // a placeholder for memory accounting.
    ExtrememThread t = new Bootstrap(null, 42);
    ArrayletOflong a;

    try {
      a = new ArrayletOflong(t, LifeSpan.Ephemeral, 4, 56);
      for (int i = 0; i < 56; i++)
	a.set(i, -10 * i);
      for (int i = 55; i >= 0; i--) {
	long l = a.get(i);
	String s1 = Integer.toString(i);
	String s2 = Long.toString(l);
	Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during first batch");
      x.printStackTrace();
    }

    try {
      a = new ArrayletOflong(t, LifeSpan.Ephemeral, 7, 61);
      for (int i = 0; i < 61; i++)
	a.set(i, -10 * i);
      for (int i = 60; i >= 0; i--) {
	Long l = a.get(i);
	String s1 = Integer.toString(i);
	String s2 = Long.toString(l);
	Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during second batch");
      x.printStackTrace();
    }

    try {
      a = new ArrayletOflong(t, LifeSpan.Ephemeral, 0, 61);
      for (int i = 0; i < 61; i++)
	a.set(i, -10 * i);
      for (int i = 60; i >= 0; i--) {
	long l = a.get(i);
	String s1 = Integer.toString(i);
	String s2 = Long.toString(l);
	Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during third batch");
      x.printStackTrace();
    }
  }
}