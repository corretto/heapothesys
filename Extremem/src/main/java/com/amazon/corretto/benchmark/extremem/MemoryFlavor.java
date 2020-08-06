// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

// The extremem workload reports the amount of memory allocated and
// deallocated in each of the categories identified below.  Certain
// garbage collection approaches are especially good or bad with certain
// flavors of data.  Configuration options allow experimental runs to
// tradeoff between uses of different flavors of memory.
//
// Optimizations allow certain JVM and garbage collection approaches
// to optimize the representation of certain data.  For example, JDK
// 11 usually represents String data as bytes rather than 2-byte
// characters.  And JVM's running with Compressed ordinary object
// pointers (OOPS) use 4 bytes instead of 8 to represent each
// reference stored within the heap.  Certain garbage collection
// techniques require extra metadata in headers or in some other
// side-loaded data structure.
//
// Customer-centered focus dictates that comparisons between
// alternative GC approaches account for performance in terms of the
// customer-defined data structures (i.e. amount of string data and
// object references rather than bytes required to implement.)

/**
 * MemoryFlavor represents the different ways memory may be used.
 */
enum MemoryFlavor {
  
  StringBuilder,	     // StringBuilder instances
  StringBuilderData,	     // "Characters" of StringBuilder data
  StringObject,		     // String instances
  StringData,		     // "Characters" of String data
  ArrayObject,		     // Array instances
  ArrayRSB,		     // Bytes of raw seething bits array data
  ArrayReference,	     // References within arrays
  PlainObject,		     // Object instances, excluding Array,
			     //    String, StringBuilder)
  ObjectRSB,		     // Bytes of raw seething bits within objects
  ObjectReference;	     // References within objects
  
  final static int OrdinalCount = ObjectReference.ordinal () + 1;
}