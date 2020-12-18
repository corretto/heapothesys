// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.util.Random;

// Note that this does not extend ExtrememObject.
abstract class ExtrememThread extends java.lang.Thread {
  // We dedicate a distinct random number generator to each
  // ExtrememThread, thereby eliminating synchronization overhead and
  // assuring reproducible behaviors (independent of interleaved
  // execution of multiple threads.)
  private Random random;

  LifeSpan ls = LifeSpan.NearlyForever;

  protected Configuration config;

  /* Each ExtrememThread logs its own memory allocations.
   *
   * It would not be practical to collect all of this allocation data
   * in this way in a real workload.  However, in the context of this
   * "artificial" workload, this record keeping represents some of the
   * normal computation that is typically interleaved with allocation
   * of objects.
   */
  protected final MemoryLog memory_log, garbage_log;

  // Threads are presumed to run NearlyForever.  Each ExtrememThread
  // is instantiated by a traditional java.lang.Thread instance which
  // is not an ExtrememThread.  The memory for each ExtrememThread
  // instantiation is logged as part of that Thread's footprint.
  ExtrememThread(Configuration config, long random_seed) {
    this.config = config;
    random = new Random (random_seed);
    memory_log = new MemoryLog(LifeSpan.NearlyForever);
    garbage_log = new MemoryLog(LifeSpan.NearlyForever);
    doPrivateTally(memory_log, LifeSpan.NearlyForever, Polarity.Expand);
    this.setDefaultUncaughtExceptionHandler(
      ExtrememUncaughtExceptionHandler.instance);
  }

  abstract public void runExtreme();

  public final void run() {
    try {
      runExtreme();
    } catch (Throwable t) {
      Util.fatalException("Detected in ExtrememThread.run(): ", t);
    }
  }

  // Only used by Bootstrap thread because config is not initialized
  // until after the thread begins to run and command-line options may
  // override default seed value.
  void replaceSeed(long new_seed) {
    random.setSeed(new_seed);
  }

  int randomUnsignedInt() {
    int result = random.nextInt();
    if (result < 0)
      result = -result;

    // It turns out that -Integer.MIN_VALUE equals itself.  Found this
    // out the hard way.  In this very rare situation, discard the result.
    if (result < 0)
      return randomUnsignedInt();
    else
      return result;
  }

  long randomLong() {
    return random.nextLong();
  }

  float randomFloat() {         // Returns float between 0 and 1.0.
    return random.nextFloat();
  }

  double randomDouble() {               // Returns float between 0 and 1.0.
    return random.nextDouble();
  }

  // Result is assumed to be LifeSpan.Ephemeral.
  String[] randomKeywords(int count) {
    String[] result = new String[count];
    for (int i = 0; i < count; i++) {
      result[i] = randomWord();
    }
    memory_log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
                          Polarity.Expand, 1);
    memory_log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
                          Polarity.Expand, count);
    return result;
  }

  String randomWord() {
    return config.arbitraryWord (this);
  }


  MemoryLog memoryLog() {
    return memory_log;
  }

  MemoryLog garbageLog() {
    return garbage_log;
  }

  private final void doPrivateTally(MemoryLog log, LifeSpan ls, Polarity p) {
    // Account for this object
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 1);
    // Account for fields random, config, memory_log, garbage_log, ls
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 5);

    // Assume the Random object has only one long word of state.
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 1);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, Util.SizeOfLong);

    // The contents of config is accounted elsewhere
    memory_log.tallyMemory(log, ls, p);
    garbage_log.tallyMemory(log, ls, p);
  }

  /* tallyMemory accounts for the memory of this object and the
   * objects it instantiates, but not all of the objects this object
   * references, as objects instantiated elsewhere and simply
   * referenced by this object are accounted for at their
   * instantiation point.
   *
   * Every subclass overrides this method if its size may differ from
   * the size of its superclass.
   *
   * In general, constructors of non-final classes should not invoke
   * tallyMemory because subclasses haven't yet been fully
   * constructed, and their implementations of tallyMemory may 
   * depend on fields not yet initialized.
   */
  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    doPrivateTally(log, ls, p);
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
   * constructor, these referenced objects are not included as part of
   * the memory footprint.
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
   * Typical usage is a newly allocated object was presumed to be
   * Ephemeral, but was subsequently determined have longer life.
   * Adjust tallies appropriately.
   */
  final void changeLifeSpan (ExtrememThread t, LifeSpan ls) {
    this.tallyMemory(t.memoryLog(), this.ls, Polarity.Shrink);
    this.ls = ls;
    this.tallyMemory(t.memoryLog(), this.ls, Polarity.Expand);
  }
}
