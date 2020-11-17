// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.util.Collection;
import java.util.logging.Logger;
import java.util.logging.Level;

class Util {

  static final boolean Is64BitJVM = is64Bit();

  /* Every String object (even substrings) is presumed to consist of
   * the following information:
   *   A reference to array of byte[] for latin, char[] for other.
   *   The length of the array represents the String length.
   *   A byte indicating encoding used (8-bit or 16-bit or hybrid...)
   *   An int hash
   */

  // Sizes are measured in bytes.
  static final int SizeOfBoolean = 1; // Can't represent a single bit.
  static final int SizeOfByte = 1;
  static final int SizeOfChar = 2;
  static final int SizeOfShort = 2;
  static final int SizeOfInt = 4;
  static final int SizeOfFloat = 4;
  static final int SizeOfLong = 8;
  static final int SizeOfDouble = 8;
  // Treat compressed OOPS representation as if it requires 8 bytes.
  static final int SizeOfReference = Is64BitJVM? 8: 4;

  static final int InitialHashMapArraySize = 16;

  static Logger logger = Logger.getGlobal();

  /*
   * Generic utilities
   */

  static void internalError(String msg) {
    System.err.print("Internal error: ");
    System.err.println(msg);
    System.exit(-1);
  }

  static void fatalException(String msg, Throwable t) {
    System.err.print("Intecepted fatal exception: ");
    System.err.println(msg);
    printException(t);
    System.exit(-1);
  }

  static void printException(Throwable t) {
      logger.log(Level.INFO, t.getMessage(), t);
  }

  // Use this service, without a message, when there is a strong
  // likelihood that we are out of memory and any attempt to print
  // a message will result in exceptions, thereby circumventing the exit
  // attempt.
  static void severeInternalError() {
    System.exit(-1);
  }

  static boolean is64Bit() {
    return Is64Bit.is64bit0();
  }

  /**
   * Return a string that is uniquely associated with the unsigned integer
   * value of index.
   *
   * The code that invokes this service is expected to account for the
   * returned String object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage.
   */
  static String i2s(ExtrememThread t, int index) {
    if (index == 0) {
      // Allocate a new String so caller an deal with result consistently.
      ephemeralString(t, 1);
      return new String("a");
    } else {
      String result = "";
      int result_len = 0;
      while (index > 0) {
	result = "abcdefghijklmnopqrstuvwxyz".charAt(index % 26) + result;
	int capacity = ephemeralStringBuilder(t, 1);
	capacity = ephemeralStringBuilderAppend(t, 1, capacity, result_len);
	if (result_len > 0)
	  abandonEphemeralString(t, result_len);
	index /= 26;
	result_len++;
	ephemeralStringBuilderToString(t, result_len, capacity);
      }
      return result;
    }
  }

  // Returned string is presumed to have Ephemeral lifespan.
  static String randomString(ExtrememThread t,
			     int length, Configuration config) {
    String result = "";
    int capacity = Util.ephemeralStringBuilder(t, 0);
    int count = 0;
    for (int i = 0; i < length; i++) {
      if (result.length() > 0) {
	result += " ";
	capacity = Util.ephemeralStringBuilderAppend(t, count, capacity, 1);
	count += 1;
      }
      String new_word = config.arbitraryWord(t);
      int len = new_word.length();
      result += new_word;
      capacity = Util.ephemeralStringBuilderAppend(t, count, capacity, len);
      count += len;
    }
    // Assume StringBuilder converted to String result
    Util.ephemeralStringBuilderToString(t, count, capacity);
    return result;
  }

  /*
   * Memory accounting utilities for autoboxed types
   */

  static void ephemeralLong(ExtrememThread t) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.PlainObject, Grow, 1);
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong);
  }

  static void abandonEphemeralLong(ExtrememThread t) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.PlainObject, Grow, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong);
  }

  static void nonEphemeralLong(ExtrememThread t, LifeSpan ls) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(ls, MemoryFlavor.PlainObject, Grow, 1);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong);
  }

  static void abandonNonEphemeralLong(ExtrememThread t, LifeSpan ls) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    garbage.accumulate(ls, MemoryFlavor.PlainObject, Grow, 1);
    garbage.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong);
  }

  /*
   * Memory accounting utilities for arrays
   */

  static void ephemeralReferenceArray(ExtrememThread t, int len) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference, Grow, len);
  }

  static void abandonEphemeralReferenceArray(ExtrememThread t, int len) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.ArrayReference, Grow, len);
  }

  static void ephemeralRSBArray(ExtrememThread t, int len, int element_size) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB, Grow,
		   len * element_size);
  }

  static void abandonEphemeralRSBArray(ExtrememThread t,
				       int len, int element_size) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 1);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB, Grow,
		       len * element_size);
  }

  static void referenceArray(ExtrememThread t, LifeSpan ls, long len) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, Grow, len);
  }

  static void abandonReferenceArray(ExtrememThread t, LifeSpan ls, long len) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    garbage.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
    garbage.accumulate(ls, MemoryFlavor.ArrayReference, Grow, len);
  }

  // Convert an ephemeral String[length] array to have ls LifeSpan.
  // The Strings referenced from the array are presumed to have no shorter
  // LifeSpan than ls.
  static void convertStringArray(ExtrememThread t, LifeSpan ls, int length) {
    MemoryLog log = t.memoryLog();
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		   Polarity.Shrink, 1);
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
		   Polarity.Shrink, length);
    log.accumulate(ls, MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    log.accumulate(ls, MemoryFlavor.ArrayReference, Polarity.Expand, length);
  }

  static void abandonStringArray(ExtrememThread t, LifeSpan ls, int length) {
    MemoryLog garbage = t.garbageLog();

    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		       Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
		       Polarity.Expand, length);
  }

  /*
   * Memory accounting utilities for TreeMap
   */

  /**
   * A TreeMap has root reference, int size and modification_count.
   *
   * Each entry in the TreeMap is represented by an Entry object,
   * comprised of reference fields key, content, left, right, parent;
   * and boolean field color (for balancing the tree).
   */
  static void tallyTreeMap(long tree_entries,
			   MemoryLog log, LifeSpan ls, Polarity p) {
    log.accumulate (ls, MemoryFlavor.PlainObject, p, 1);
    log.accumulate (ls, MemoryFlavor.ObjectReference, p, 1);
    log.accumulate (ls, MemoryFlavor.ObjectRSB, p, 2 * Util.SizeOfInt);

    // Entry Node representations
    log.accumulate (ls, MemoryFlavor.PlainObject, p, tree_entries);
    log.accumulate (ls, MemoryFlavor.ObjectReference, p, tree_entries * 5);
    log.accumulate (ls, MemoryFlavor.ObjectRSB, p,
		    tree_entries * Util.SizeOfBoolean);
  }

  static void createTreeNode(ExtrememThread t, LifeSpan ls) {
    Polarity Grow = Polarity.Expand;
    MemoryLog memory = t.memoryLog();
    memory.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    memory.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 5);
    memory.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfBoolean);
  }

  static void abandonTreeNode(ExtrememThread t, LifeSpan ls) {
    Polarity Grow = Polarity.Expand;
    MemoryLog garbage = t.garbageLog();
    garbage.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    garbage.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 5);
    garbage.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfBoolean);
  }

  /*
   * Memory accounting utilities for HashMap
   */

  // Compute the size of the array required to represent count entries.
  static int computeHashCapacity(int count, float load_factor, int capacity) {
    while (count > (int) (capacity * load_factor))
      capacity *= 2;
    return capacity;
  }

  static void createHashMap(ExtrememThread t, LifeSpan ls) {
    int capacity = InitialHashMapArraySize;
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;

    // A HashMap object is represented by an int size, an int
    // modification count field, an int expand threshold count
    // representing the count at which the array will next be
    // expanded, and a float load factor.
    log.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    // Account for array reference
    log.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 1);
    // Account for count, threshold, next_expand, and load factor
    log.accumulate (ls, MemoryFlavor.ObjectRSB, Grow,
		    3 * Util.SizeOfInt + Util.SizeOfFloat);

    // Account for referenced array
    log.accumulate (ls, MemoryFlavor.ArrayObject, Grow, 1);
    log.accumulate (ls, MemoryFlavor.ArrayReference, Grow, capacity);
  }

  static void abandonHashMap(ExtrememThread t, LifeSpan ls,
			     float load_factor, Collection c) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;

    int size = c.size();
    int capacity = computeHashCapacity(size, load_factor,
				       InitialHashMapArraySize);

    // A HashMap object is represented by an int size, an int
    // modification count field, an int expand threshold count
    // representing the count at which the array will next be
    // expanded, and a float load factor.
    garbage.accumulate(ls, MemoryFlavor.PlainObject, Grow, 1);
    // Account for array reference
    garbage.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 1);
    // Account for count, threshold, next_expand, and load factor
    garbage.accumulate(ls, MemoryFlavor.ObjectRSB, Grow,
		       3 * Util.SizeOfInt + Util.SizeOfFloat);

    // Account for referenced array
    garbage.accumulate(ls, MemoryFlavor.ArrayObject, Grow, 1);
    garbage.accumulate(ls, MemoryFlavor.ArrayReference, Grow, capacity);

    tallyHashNodes(garbage, ls, Grow, size);
  }

  // Account for the HashMap nodes.  Each node consists of a key,
  //  content, and next reference fields and an int hash_code field.

  // Account for creation of one HashMap Node.
  static void createHashNode(ExtrememThread t, LifeSpan ls) {
    Polarity Grow = Polarity.Expand;
    MemoryLog memory = t.memoryLog();
    memory.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    memory.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 3);
    memory.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }

  // Account for garbage collection of one HashMap Node.
  static void abandonHashNode(ExtrememThread t, LifeSpan ls) {
    Polarity Grow = Polarity.Expand;
    MemoryLog garbage = t.garbageLog();
    garbage.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    garbage.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 3);
    garbage.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }

  // Account for n HashNodes
  static void tallyHashNodes(MemoryLog l, LifeSpan ls, Polarity p, int n) {
    l.accumulate(ls, MemoryFlavor.PlainObject, p, n);
    l.accumulate (ls, MemoryFlavor.ObjectReference, p, n * 3);
    l.accumulate (ls, MemoryFlavor.ObjectRSB, p, n * Util.SizeOfInt);

  }

  // Usie addHashEntry and abandonHashEntry to adjust the number of
  // Hash entries without endeavoring to account for changes in the
  // underlying hash table array.
  static void addHashEntry(ExtrememThread t, LifeSpan ls) {
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    // Identify that one node of HashMap representation has become garbage.
    // Each node consists of a key, content, and next reference fields
    // and an int hash_code field.
    log.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    log.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 3);
    log.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }

  static void abandonHashEntry(ExtrememThread t, LifeSpan ls) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;
    // Identify that one node of HashMap representation has become garbage.
    // Each node consists of a key, content, and next reference fields
    // and an int hash_code field.
    garbage.accumulate (ls, MemoryFlavor.PlainObject, Grow, 1);
    garbage.accumulate (ls, MemoryFlavor.ObjectReference, Grow, 3);
    garbage.accumulate (ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfInt);
  }

  /**
   * A HashMap object consists of an array, a long count field, a
   * float load_factor field.  The length of the array is:
   *
   *   16: if count < load_factor * 16
   *   count / load_factor: larger values of count
   *
   * The count argument of tallyHashMap() represents the current
   * number of entries within the HashMap table.  This method assumes
   * that the associated HashMap object has not significantly
   * decreased its count.  (Otherwise, its assessment of accompanying
   * array size may be incorrect.)
   */
  static void tallyHashMap(MemoryLog log, LifeSpan ls, Polarity p,
			   int count, float load_factor) {
    int initial_array_capacity = (int) (InitialHashMapArraySize * load_factor);
    int capacity = computeHashCapacity(count, load_factor,
				       InitialHashMapArraySize);

    // A HashMap object is represented by an int size, an int
    // modification count field, an int expand threshold count
    // representing the count at which the array will next be
    // expanded, and a float load factor.
    log.accumulate (ls, MemoryFlavor.PlainObject, p, 1);
    // Account for array reference
    log.accumulate (ls, MemoryFlavor.ObjectReference, p, 1);
    // Account for count, threshold, next_expand, and load factor
    log.accumulate (ls, MemoryFlavor.ObjectRSB, p,
		    (3 * Util.SizeOfInt + Util.SizeOfFloat));

    // Account for referenced array
    log.accumulate (ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate (ls, MemoryFlavor.ArrayReference, p, capacity);

    // Each value stored into the HashMap is represented by a Node
    // object.  Each Node object consists of a key, content, and next
    //  reference fields and an int hash_code field.
    //
    // Account for the Node entries.
    log.accumulate (ls, MemoryFlavor.PlainObject, p, count);
    log.accumulate (ls, MemoryFlavor.ObjectReference, p, count * 3);
    log.accumulate (ls, MemoryFlavor.ObjectRSB, p, count * Util.SizeOfInt);
  }

  /*
   * Memory accounting utilities for HashSet
   */

  /* Account for the HashSet representations associated with name and
   * descriptor indexes used for Product lookups.  This accounting
   * does not include memory to represent the individual Long
   * entitities that are inserted into the HashSet.
   */
  private static void
  tallyHashSetIterator(MemoryLog log, LifeSpan ls, Polarity p) {
    // Assume a HashSetIterator maintains the following information:
    //  A reference to the associated HashSet
    //  A snapshot of the associated HashSet's modification count
    //  An index for the "current" bucket within the HashSet's array
    //  A pointer to the next Node within a linked list of Nodes
    //    associated with the "current bucket"
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 1);
    // Account for int fields: expected modification count, current
    // bucket index
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, 2 * Util.SizeOfInt);
    // Account for reference fields: the HashSet, next Node
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
  }

  static void createEphemeralHashSetIterator(ExtrememThread t) {
    tallyHashSetIterator(t.memoryLog(), LifeSpan.Ephemeral, Polarity.Expand);
  }

  static void abandonEphemeralHashSetIterator(ExtrememThread t) {
    tallyHashSetIterator(t.garbageLog(), LifeSpan.Ephemeral, Polarity.Expand);
  }

  /*
   * Memory accounting utilities for StringBuilder
   */

  // returns capacity.  count is length of initial String.
  static int ephemeralStringBuilder(ExtrememThread t, int count) {
    final int InitialStringBuilderCapacity = 16;
    int capacity = count + InitialStringBuilderCapacity;
    t.memoryLog().accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringBuilder,
			     Polarity.Expand, 1);
    t.memoryLog().accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
			     Polarity.Expand, 1);
    t.memoryLog().accumulate(LifeSpan.Ephemeral,
			     MemoryFlavor.StringBuilderData,
			     Polarity.Expand, capacity);
    return capacity;
  }

  // Returns new capacity of StringBuilder.  Prefer to work with known
  // capacity rather than taking string lengths as it is more
  // efficient and avoids prempature conversion of StringBuilders to
  // Strings.  Also, expect that a good compiler will constant-fold a
  // strlen() invocation on a literal string constant.
  static int ephemeralStringBuilderAppend(ExtrememThread t,
					  int count_b4, int capacity_b4,
					  int appendage_length) {
    if(appendage_length <= (capacity_b4 - count_b4))
      return capacity_b4;
    else {
      int new_linear_capacity = count_b4 + appendage_length;
      int new_2x_capacity = capacity_b4 * 2 + 2;
      int new_capacity = ((new_linear_capacity > new_2x_capacity)?
			  new_linear_capacity: new_2x_capacity);
      t.garbageLog().accumulate(LifeSpan.Ephemeral,
				MemoryFlavor.StringBuilderData,
				Polarity.Expand, capacity_b4);
      t.garbageLog().accumulate(LifeSpan.Ephemeral,
				MemoryFlavor.ArrayObject,
				Polarity.Expand, 1);
      t.memoryLog().accumulate(LifeSpan.Ephemeral,
			       MemoryFlavor.StringBuilderData,
			       Polarity.Expand, new_capacity);
      t.memoryLog().accumulate(LifeSpan.Ephemeral,
			       MemoryFlavor.ArrayObject,
			       Polarity.Expand, 1);
      return new_capacity;
    }
  }

  // Discard the StringBuilder and its backing store.  Allocate string
  // and its backing store.
  static void ephemeralStringBuilderToString(ExtrememThread t,
					     int count, int capacity) {
    MemoryLog garbage = t.garbageLog();
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringBuilder,
		       Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		       Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringBuilderData,
		       Polarity.Expand, capacity);

    MemoryLog memory = t.memoryLog();
    memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringObject,
		      Polarity.Expand, 1);
    memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		      Polarity.Expand, 1);
    memory.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringData,
		      Polarity.Expand, count);
  }

  static void abandonEphemeralStringBuilder(ExtrememThread t,
					    int count, int capacity) {
    t.garbageLog().accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringBuilder,
			      Polarity.Expand, 1);
    t.garbageLog().accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
			      Polarity.Expand, 1);
    t.garbageLog().accumulate(LifeSpan.Ephemeral,
			      MemoryFlavor.StringBuilderData,
			      Polarity.Expand, capacity);
  }

  /*
   * Memory accounting utilities for String
   */

  // How many decimal digits required to represent val?  val assumed >= 0.
  static int decimalDigits(long val) {
    final int BitsInLong = 64;
    int leading_zeros = Long.numberOfLeadingZeros(val);
    // base 2 log of val is >= log2floor and < log2floor + 1
    int log2floor = (BitsInLong - 1) - leading_zeros;
    // divide log2floor by 3.322 to obtain log10floor, where
    // base 10 log f val is >= log10floor and < log10floor + 1
    // (because 2^3.322 = 10)

    // Since we are dividing by a number smaller than 3.322, our
    // log10floor_approximation may be > log10floor.  So it's possible
    // that log10(val) is actually smaller than log10floor_approximation.
    int log10floor_approximation = log2floor / 3;

    switch (log10floor_approximation) {
      case 0:
	// if (val == 7), log2floor = 2, log10floor_approximation = 0
	return 1;
      case 1:
	// if (val == 8), log2floor = 3, log10floor_approximation = 1
	// if (val == 9), log2floor = 3, log10floor_approximation = 1
	// if (val == 10), log2floor = 3, log10floor_approximation = 1
	// if (val == 32), log2floor = 4, log10floor_approximation = 1
	// if (val == 64), log2floor = 5, log10floor_approximation = 1
	// if (val == 99), log2floor = 5, log10floor_approximation = 1
	// if (val == 100), log2floor = 5, log10floor_approximation = 1
	return (val < 10) ? 1: ((val < 100)? 2: 3);
      case 2:
	// if (val == 128, log2floor = 6, log10floor_approximation = 2
	// if (val == 256, log2floor = 7, log10floor_approximation = 2
	// if (val == 512, log2floor = 8, log10floor_approximation = 2
	// if (val == 999), log2floor = 8, log10floor_approximation = 2
	// if (val == 1000), log2floor = 8, log10floor_approximation = 2
      case 3:
	// if (val == 1024, log2floor = 9, log10floor_approximation = 3
	// if (val == 2048, log2floor = 10, log10floor_approximation = 3
	// if (val == 4096, log2floor = 11, log10floor_approximation = 3
	return (val < 1000)? 3: 4;
      case 4:
	// if (val == 8,192, log2floor = 12, log10floor_approximation = 4
	// if (val == 17,384, log2floor = 13, log10floor_approximation = 4
	// if (val == 32,768, log2floor = 14, log10floor_approximation = 4
	return (val < 10000)? 4: 5;
      case 5:
	// if (val == 65,536, log2floor = 15, log10floor_approximation = 5
	// if (val == 131,072, log2floor = 16, log10floor_approximation = 5
	// if (val == 262,144, log2floor = 17, log10floor_approximation = 5
	return (val < 100000)? 5: 6;
      case 6:
	// if (val == 524,288, log2floor = 18, log10floor_approximation = 6
	// if (val == 1,048,576, log2floor = 19, log10floor_approximation = 6
	// if (val == 2,097,152, log2floor = 20, log10floor_approximation = 6
	return (val < 1000000L)? 6: 7;
      case 7:
	// if (val ==  4,194,304, log2floor = 21, log10floor_approximation = 7
	// if (val ==  8,388,608, log2floor = 22, log10floor_approximation = 7
	// if (val == 16,777,216, log2floor = 23, log10floor_approximation = 7
	return (val < 10000000L)? 7: 8;
      case 8:
	// if (val ==  33,554,432, log2floor = 24, log10floor_approximation = 8
	// if (val ==  67,108,864, log2floor = 25, log10floor_approximation = 8
	// if (val == 134,217,728, log2floor = 26, log10floor_approximation = 8
	return (val < 100000000L)? 8: 9;
      case 9:
	// if (val ==   268,435,456, log2floor = 27, log10floor_approx = 9
	// if (val ==   536,870,912, log2floor = 28, log10floor_approx = 9
	// if (val == 1,073,741,824, log2floor = 29, log10floor_approx = 9
	return (val < 1000000000L)? 9: 10;
      case 10:
	// if (val == 2,147,483,648, log2floor = 30, log10floor_approx = 10
	// if (val == 4,294,967,296, log2floor = 31, log10floor_approx = 10
	// if (val == 8,589,934,592, log2floor = 32, log10floor_approx = 10
	return (val < 10000000000L)? 10: 11;
      case 11:
	// if (val == 17,179,869,184, log2floor = 33, log10floor_approx = 11
	// if (val == 34,359,738,368, log2floor = 34, log10floor_approx = 11
	// if (val == 68,719,476,736, log2floor = 35, log10floor_approx = 11
	return (val < 10000000000L)? 11: 12;
      case 12:
	// if (val == 137,438,953,472, log2floor = 36, log10floor_approx = 12
	// if (val == 274,877,906,944, log2floor = 37, log10floor_approx = 12
	// if (val == 549,755,813,888, log2floor = 38, log10floor_approx = 12
	return (val < 100000000000L)? 12: 13;
      case 13:;
	// if val == 1,099,511,627,776, log2floor = 39, log10floor_approx = 13
	// if val == 2,199,023,255,552, log2floor = 40, log10floor_approx = 13
	// if val == 4,398,046,511,104, log2floor = 41, log10floor_approx = 13
	return 13;
      case 14:
	// if val ==  8,796,093,022,208, log2floor = 42, log10floor = 14
	// if val == 17,592,186,044,416, log2floor = 43, log10floor = 14
	// if val == 35,184,372,088,832, log2floor = 44, log10floor = 14
	return (val < 10000000000000L)? 14: 15;
      case 15:
	// if val ==  70,368,744,177,664, log2floor = 45, log10floor = 15
	// if val == 140,737,488,355,328, log2floor = 46, log10floor = 15
	// if val == 281,474,976,710,656, log2floor = 47, log10floor = 15
	return (val < 10000000000000L)? 15: 16;
      case 16:
	// This and following cases not fully analyzed.  Assume the pattern
	// continues as established above.  At worse, I am off-by-one
	// in the tabulation of String data elements allocated.
	return (val < 100000000000000L)? 16: 17;
      case 17:
	return (val < 1000000000000000L)? 17: 18;
      case 18:
	return (val < 10000000000000000L)? 18: 19;
      case 19:
	return (val < 10000000000000000L)? 19: 20;
      case 20:
	return (val < 100000000000000000L)? 20: 21;
      case 21:
	return (val < 1000000000000000000L)? 21: 22;
      default:
	return 22;
    }
  }

  static void abandonNonEphemeralString(ExtrememThread t,
					LifeSpan ls, int len) {
    MemoryLog garbage = t.garbageLog();

    garbage.accumulate(ls, MemoryFlavor.StringObject, Polarity.Expand, 1);
    garbage.accumulate(ls, MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    garbage.accumulate(ls, MemoryFlavor.StringData, Polarity.Expand, len);
  }

  static void ephemeralString(ExtrememThread t, int len) {
    MemoryLog log = t.memoryLog();
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.StringObject, Polarity.Expand, 1);
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.StringData, Polarity.Expand, len);
  }

  /* Move accounting of Ephemeral String of length len to category ls. */
  static void convertEphemeralString(ExtrememThread t, LifeSpan ls, int len) {
    MemoryLog log = t.memoryLog();
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.StringObject, Polarity.Shrink, 1);
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.ArrayObject, Polarity.Shrink, 1);
    log.accumulate(LifeSpan.Ephemeral,
		   MemoryFlavor.StringData, Polarity.Shrink, len);

    log.accumulate(ls, MemoryFlavor.StringObject, Polarity.Expand, 1);
    log.accumulate(ls, MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    log.accumulate(ls, MemoryFlavor.StringData, Polarity.Expand, len);
  }

  static void abandonEphemeralString(ExtrememThread t, int len) {
    MemoryLog garbage = t.garbageLog();

    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.StringObject, Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.StringData, Polarity.Expand, len);
  }

  static void abandonEphemeralString(ExtrememThread t, String s) {
    MemoryLog garbage = t.garbageLog();
    int len = s.length();
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.StringObject, Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.ArrayObject, Polarity.Expand, 1);
    garbage.accumulate(LifeSpan.Ephemeral,
		       MemoryFlavor.StringData, Polarity.Expand, len);
  }

  static void abandonEphemeralStrings(ExtrememThread t,
				      int num_strings, int cumulative_len) {
    MemoryLog garbage = t.garbageLog();

    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringObject,
		       Polarity.Expand, num_strings);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		       Polarity.Expand, num_strings);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringData,
		       Polarity.Expand, cumulative_len);
  }

  static void abandonIdenticalEphemeralStrings(ExtrememThread t,
					       int num_strings, int len) {
    MemoryLog garbage = t.garbageLog();

    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringObject,
		       Polarity.Expand, num_strings);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
		       Polarity.Expand, num_strings);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.StringData,
		       Polarity.Expand, num_strings * len);
  }

  static void tallyString(MemoryLog log, LifeSpan ls, Polarity p, int len) {
    log.accumulate(ls, MemoryFlavor.StringObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1);
    log.accumulate(ls, MemoryFlavor.StringData, p, len);
  }

  static void tallyStrings(MemoryLog log, LifeSpan ls, Polarity p,
			   int string_count, long cumulative_len) {
    log.accumulate(ls, MemoryFlavor.StringObject, p, string_count);
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, string_count);
    log.accumulate(ls, MemoryFlavor.StringData, p, cumulative_len);
  }
}
