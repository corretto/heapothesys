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

class Arraylet<BaseType> extends ExtrememObject {
  // We use max_arraylet_length for the typical segment size and the typical index size.  We only truncate the
  // last array segment and the last indexing segment at each indexing level.

  private final int max_arraylet_length;
  private final int length;               // Number of elements in Arraylet
  private final int num_segments;         // Total number of array segments representing the elements of this Arraylet
  private final int indexing_tiers;       // How many levels of indexing arrays, which is at least 1

  private final int[] index_entry_span;
  private final Object[] root_index;      // if max_arraylet_length > 0, points to root index
  private final BaseType[] root_segment;  // if max_arraylet_length == 0, points to contiguous array representaiton

  Arraylet(ExtrememThread t, LifeSpan ls, int max_length, int length) {
    super(t, ls);
    Polarity Grow = Polarity.Expand;
    Polarity Shrink = Polarity.Shrink;
    MemoryLog memory = t.memoryLog();
    if ((max_length != 0) && (max_length < 4)) {
      Exception x = new IllegalArgumentException(Integer.toString(max_length));
      Util.fatalException("Maximum arraylet size must be >= 4)", x);
    }
    this.max_arraylet_length = max_length;
    this.length = length;
    if (max_arraylet_length != 0) {
      // At bottom indexing tier, each index element represents max_arraylet_length number of array elements
      // At second indexing tier, each index element represents max_arraylet_length * max_arraylet_length
      // At level N (with bottom equal to zero), each index element represents max_arraylet_length * max_arraylet_length ^ N
      int index_levels = 1;
      int potential_span = max_arraylet_length * max_arraylet_length;
      while (potential_span < length) {
        index_levels++;
        potential_span *= max_arraylet_length;
      }
      indexing_tiers = index_levels;
      num_segments = (length + max_arraylet_length - 1) / max_arraylet_length;
      index_entry_span = new int[indexing_tiers];

      // index_entry_span
      memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB, Grow, indexing_tiers * Util.SizeOfInt);

      int[] initialized_segments = new int[indexing_tiers];
      int[] initialized_indices = new int[indexing_tiers];
      int[] index_segment_span = new int[indexing_tiers];
      Object[][] initialization_index = new Object[indexing_tiers][];

      // initialization_index, initialized_segments, initialized_indices, index_segment_span 
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 4);

      // initialization_index
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference, Grow, indexing_tiers);

      // initialized_segments, initialized_indices, index_segment_span
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB, Grow, 3 * indexing_tiers * Util.SizeOfInt);

      index_entry_span[0] = max_arraylet_length;
      index_segment_span[0] = max_arraylet_length * max_arraylet_length;
      initialized_segments[0] = 0;
      initialized_indices[0] = 0;

      for (int i = 1; i < indexing_tiers; i++) {
        index_entry_span[i] = index_segment_span[i-1];
        index_segment_span[i] = index_entry_span[i] * max_arraylet_length;
        initialized_segments[i] = 0;
        initialized_indices[i] = 0;
        initialization_index[i] = null;
      }

      int total_segment_span = 0;
      for (int i = 0; i < num_segments; i++) {
        if (total_segment_span >= length) {
          // Do I want an assertion failure here?
          break;
        } else {
          Object[] new_segment;
          if (total_segment_span + max_arraylet_length < length) {
            new_segment = new Object[max_arraylet_length];
            total_segment_span += max_arraylet_length;
          } else {
            new_segment = new Object[length - total_segment_span];
            total_segment_span = length;
          }
          // new_segment
          memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
          memory.accumulate(ls, MemoryFlavor.ArrayReference, Grow, new_segment.length);
      
          if (initialized_indices[0] == 0) {
            // Need to allocate the level-0 index segment
            int unspanned = length - initialized_segments[0] * index_segment_span[0];
            if (unspanned >= index_segment_span[0]) {
              initialization_index[0] = new Object[max_arraylet_length];
            } else {
              int roundup = (unspanned + index_entry_span[0] - 1) / index_entry_span[0];
              initialization_index[0] = new Object[roundup];
            }
            // new level-0 index segment
            memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
            memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference, Grow, initialization_index[0].length);
          }
          initialization_index[0][initialized_indices[0]] = new_segment;
          initialized_indices[0] += 1;
          if (initialized_indices[0] >= max_arraylet_length) {
            initialized_segments[0] += 1;
            initialized_indices[0] = 0;
          }

          for (int update_tier = 1; update_tier < indexing_tiers; update_tier++) {
            if (initialized_indices[update_tier - 1] != 1) {
              // We only need to update parent indices if the lower level was just expanded
              break;
            }
            if (initialized_indices[update_tier] == 0) {
              // Need to allocate a new level-N index segment
              int unspanned = length - initialized_segments[update_tier] * index_segment_span[update_tier];
              if (unspanned >= index_segment_span[update_tier]) {
                initialization_index[update_tier] = new Object[max_arraylet_length];
              } else {
                int roundup = (unspanned + index_entry_span[update_tier] - 1) / index_entry_span[update_tier];
                initialization_index[update_tier] = new Object[roundup];
              }
              // new index segment at level update_tier
              memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
              memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference, Grow, initialization_index[update_tier].length);
            }
            initialization_index[update_tier][initialized_indices[update_tier]] = initialization_index[update_tier - 1];
            initialized_indices[update_tier] += 1;
            if (initialized_indices[update_tier] >= max_arraylet_length) {
              initialized_segments[update_tier] += 1;
              initialized_indices[update_tier] = 0;
            }
          }
        }
      }

      // initialization_index,initialized_segments, initialized_indices, index_segment_span
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Shrink, 4);

      // initialization_index
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference, Shrink, indexing_tiers);

      // initialized_segments, initialized_indices, index_segment_span
      memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB, Shrink, 3 * indexing_tiers * Util.SizeOfInt);
      root_index = initialization_index[indexing_tiers - 1];
      root_segment = null;
    } else {
      num_segments = 1;
      indexing_tiers = 0;
      index_entry_span = null;
      root_index = null;
      root_segment = (BaseType[]) new Object[length];
      // new root_segment
      memory.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
      memory.accumulate(ls, MemoryFlavor.ArrayReference, Grow, length);
    }

    // Account for 4 ints: length, max_arraylet_length, num_segments, indexing_tiers
    memory.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, 4 * Util.SizeOfInt);

    // Account for root_index, root_segment, index_entry_span
    memory.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 3);
  }

  final BaseType get(int at) {
    if ((at < 0) || (at >= length)) {
      Exception x = new ArrayIndexOutOfBoundsException(at);
      Util.fatalException("Index out of bounds in Arraylet.get", x);
      // Not reached
    }
    if (max_arraylet_length == 0) {
      return root_segment[at];
    } else {
      int tier = indexing_tiers - 1;
      int index = at / index_entry_span[tier];
      int remainder = at % index_entry_span[tier];
      Object[] index_segment = root_index;
      while (tier-- > 0) {
        index_segment = (Object[]) index_segment[index];
        index = remainder / index_entry_span[tier];
        remainder = remainder % index_entry_span[tier];
      }
      BaseType[] data_segment = (BaseType[]) index_segment[index];
      return data_segment[remainder];
    }
  }

  final void set(int at, BaseType value) {
    if ((at < 0) || (at >= length)) {
      Exception x = new ArrayIndexOutOfBoundsException(at);
      Util.fatalException("Index out of bounds in Arraylet.get", x);
    } else if (max_arraylet_length == 0) {
      root_segment[at] = value;
    } else {
      int tier = indexing_tiers - 1;
      int index = at / index_entry_span[tier];
      int remainder = at % index_entry_span[tier];
      Object[] index_segment = root_index;
      while (tier-- > 0) {
        index_segment = (Object[]) index_segment[index];
        index = remainder / index_entry_span[tier];
        remainder = remainder % index_entry_span[tier];
      }
      BaseType[] data_segment = (BaseType[]) index_segment[index];
      data_segment[remainder] = value;
    }
  }

  final int length() {
    return length;
  }

  private void helpTallyMemory(MemoryLog log, LifeSpan ls, Polarity p, Object[] index_segment, int tier) {
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, p, index_segment.length);

    if (tier > 1) {
      for (int i = 0; i < index_segment.length; i++) {
        Object[] sub_index_segment = (Object[]) index_segment[i];
        helpTallyMemory(log, ls, p, sub_index_segment, tier-1);
      }
    } else {
      log.accumulate(ls, MemoryFlavor.ArrayObject, p, index_segment.length);;
      for (int i = 0; i < index_segment.length; i++) {
        BaseType[] data_segment = (BaseType[]) index_segment[i];
        log.accumulate(ls, MemoryFlavor.ArrayReference, p, data_segment.length);
      }
    }
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for 4 ints: length, max_arraylet_length, num_segments, indexing_tiers
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 4 * Util.SizeOfInt);

    // Account for root_index, root_segment, index_entry_span
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 3);

    if (max_arraylet_length == 0) {
      log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
      log.accumulate(ls, MemoryFlavor.ArrayReference, p, length);
    } else {
      helpTallyMemory(log, ls, p, root_index, indexing_tiers);
    }
  }

  public static void main(String args[]) {

    // Instantiate but do not run the Bootstrap thread.  Just need a
    // a placeholder for memory accounting.
    ExtrememThread t = new Bootstrap(null, 42);
    Arraylet<Long> a;

    try {
      Trace.debug("Testing Arraylet with max size 4");
      a = new Arraylet<Long>(t, LifeSpan.Ephemeral, 4, 56);
      for (int i = 0; i < 56; i++)
        a.set(i, new Long(-10 * i));
      for (int i = 55; i >= 0; i--) {
        Long l = a.get(i);
        String s1 = Integer.toString(i);
        String s2 = Long.toString(l.longValue());
        Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during first batch");
    }

    try {
      Trace.debug("Testing Arraylet with max size 7");
      a = new Arraylet<Long>(t, LifeSpan.Ephemeral, 7, 61);
      for (int i = 0; i < 61; i++)
        a.set(i, new Long(-10 * i));
      for (int i = 60; i >= 0; i--) {
        Long l = a.get(i);
        String s1 = Integer.toString(i);
        String s2 = Long.toString(l.longValue());
        Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during second batch");
      Util.printException(x);
    }

    try {
      Trace.debug("Testing Arraylet with max size 0");
      a = new Arraylet<Long>(t, LifeSpan.Ephemeral, 0, 61);
      for (int i = 0; i < 61; i++)
        a.set(i, new Long(-10 * i));
      for (int i = 60; i >= 0; i--) {
        Long l = a.get(i);
        String s1 = Integer.toString(i);
        String s2 = Long.toString(l.longValue());
        Trace.debug("Array element[", s1, "] holds ", s2);
      }
    } catch (Exception x) {
      Trace.debug("caught exception during third batch");
      Util.printException(x);
    }
  }
}
