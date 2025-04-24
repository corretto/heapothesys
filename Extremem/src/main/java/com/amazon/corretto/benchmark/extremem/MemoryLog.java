// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * MemoryLog keeps track of how much of each MemoryFlavor and LifeSpan
 * has been accumulated for each ExtrememThread.
 *
 * Due to circularity issues, MemoryLog is not a subclass of
 * ExtrememObject.  For ease of integretion, it mimics certain
 * ExtrememObject behaviors.
 */
public class MemoryLog {
  static long log_sequence = 0;

  // Array is indexed by LifeSpan and MemoryFlavor.
  private final long [][] tallies;
  private final LifeSpan ls;

  // There's a circularity issue with constructing MemoryLog objects,
  // because the thread that allocates the memory log is probably the
  // same thread that is going to log its creation.  But that thread's
  // log doesn't yet exist.  So construct it first.  Then invoke
  // memoryFootprint ().
  //
  // MemoryLog does not extend ExtrememObject due to circularity of
  // logging the construction of the log itself.
  MemoryLog (LifeSpan ls) {
    this.ls = ls;
    tallies = new long [LifeSpan.OrdinalCount][MemoryFlavor.OrdinalCount];
  }

  static synchronized long sequenceNumber() {
    return log_sequence++;
  }

  // Only used for debugging.  Does NOT account for all ephemeral
  // memory allocations.
  final void traceLifeSpanSummary(LifeSpan ls, String message) {
    message = "$: " + message;

    long [] data = tallies[ls.ordinal()];
    String StringBuilderQty = (
      Long.toString(data[MemoryFlavor.StringBuilder.ordinal()]));
    
    String StringBuilderDataQty = (
      Long.toString(data[MemoryFlavor.StringBuilderData.ordinal()]));

    String StringObjectQty = (
      Long.toString(data[MemoryFlavor.StringObject.ordinal()]));

    String StringDataQty = (
      Long.toString(data[MemoryFlavor.StringData.ordinal()]));

    String ArrayObjectQty = (
      Long.toString(data[MemoryFlavor.ArrayObject.ordinal()]));

    String ArrayReferenceQty = (
      Long.toString(data[MemoryFlavor.ArrayReference.ordinal()]));

    String ArrayRSBQty = (
      Long.toString(data[MemoryFlavor.ArrayRSB.ordinal()]));

    String PlainObjectQty = (
      Long.toString(data[MemoryFlavor.PlainObject.ordinal()]));

    String ObjectReferenceQty = (
      Long.toString(data[MemoryFlavor.ObjectReference.ordinal()]));

    String ObjectRSBQty = (
      Long.toString(data[MemoryFlavor.ObjectRSB.ordinal()]));

    Trace.debug(message, ", ", StringBuilderQty,
                ", ", StringBuilderDataQty,
                ", ", StringObjectQty,
                ", ", StringDataQty,
                ", ", ArrayObjectQty,
                ", ", ArrayReferenceQty,
                ", ", ArrayRSBQty,
                ", ", PlainObjectQty,
                ", ", ObjectReferenceQty,
                ", ", ObjectRSBQty);
  }

  final void accumulate (LifeSpan ls, MemoryFlavor mf,
                         Polarity p, long count) {
    if (Trace.enabled(1)) {
      long sequence = sequenceNumber();
      Trace.msg(1, "accumulate(", ls.toString(), ", ", mf.toString(), ", ",
                p.toString(), ", ", Long.toString(count), ")#",
                Long.toString(sequence), " affecting ",
                Long.toString(tallies[ls.ordinal()][mf.ordinal()]), " @ ",
                Integer.toString(this.hashCode()));
    }
    
    if (p == Polarity.Shrink)
      tallies[ls.ordinal()][mf.ordinal()] -= count;
    else                        // p == Polarity.Expand
      tallies [ls.ordinal()][mf.ordinal()] += count;

    // Tallies should never go negative.  The only reason to decrement
    // a tally is to undo a previous increment.
    if (tallies[ls.ordinal()][mf.ordinal()] < 0)
      throw new IllegalStateException("Negative memory tally for ["
				      +  ls + ", " + mf + "] after count " + count + " with Polarity " + p );
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    // Account for this object
    log.accumulate(ls, MemoryFlavor.PlainObject, p, 1);
    // Account for ls, tallies fields of this object
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
    // Account for the array referenced by tallies
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 1 + LifeSpan.OrdinalCount);
    log.accumulate(ls, MemoryFlavor.ArrayReference, p, LifeSpan.OrdinalCount);
    log.accumulate(ls, MemoryFlavor.ArrayRSB, p, Util.SizeOfLong *
                   LifeSpan.OrdinalCount * MemoryFlavor.OrdinalCount);
  }

  /**
   * Combine the statistics represented by other MemoryLog into this MemoryLog.
   */
  public synchronized void foldInto(MemoryLog other) {
    for (LifeSpan ls: LifeSpan.values())
      for (MemoryFlavor mf: MemoryFlavor.values())
        tallies[ls.ordinal()][mf.ordinal()] += (
          other.tallies[ls.ordinal()][mf.ordinal()]);
  }

  /**
   * Combine the statistics represented by garbage MemoryLog by
   * removing its quantities from this MemoryLog.  (The result of this
   * tabulation represents memory leakage.)
   */
  public synchronized void foldOutof(MemoryLog garbage) {
    for (LifeSpan ls: LifeSpan.values())
      for (MemoryFlavor mf: MemoryFlavor.values())
        tallies[ls.ordinal()][mf.ordinal()] -= (
          garbage.tallies[ls.ordinal()][mf.ordinal()]);
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
   * the meory footprint.
   */
  final void memoryFootprint(ExtrememThread t) {
    this.tallyMemory(t.memoryLog(), ls, Polarity.Expand);
  }

  /**
   * When this object becomes garbage, tally that this thread has
   * opportunity to reclaim this much more memory.
   */
  final void garbageFootprint (ExtrememThread t) {
    this.tallyMemory(t.garbageLog(), ls, Polarity.Expand);
  }

  // Assume 0 <= qty < 1000.  Represent qty with exactly 3 characters
  static private String threeDigits(ExtrememThread t, long qty) {
    if (qty >= 100) {
      Util.ephemeralString(t, 3);
      return Long.toString(qty);
    } else if (qty >= 10) {
      int capacity = Util.ephemeralStringBuilder(t, 1);
      capacity = Util.ephemeralStringBuilderAppend(t, 1, capacity, 2);
       Util.ephemeralStringBuilderToString(t, 3, capacity);
       return " " + Long.toString(qty);
     } else {
       int capacity = Util.ephemeralStringBuilder(t, 2);
       capacity = Util.ephemeralStringBuilderAppend(t, 1, capacity, 1);
       Util.ephemeralStringBuilderToString(t, 3, capacity);
       return "  " + Long.toString(qty);
     }
   }

   // Return a string 4 characters wide to represent unsigned memory qty.
   // Assume qty less than 1000T.
   static private String memRep(ExtrememThread t, long qty) {
     // Memory behavior is same in all cases
     int capacity = Util.ephemeralStringBuilder(t, 3);
     capacity = Util.ephemeralStringBuilderAppend(t, 1, capacity, 1);
     Util.ephemeralStringBuilderAppend(t, 3, capacity, 1);
     Util.ephemeralStringBuilderToString(t, 4, capacity);
     // Value returned from threeDigits() is now garbage
     Util.abandonEphemeralString(t, 3);

     if (qty >= 1000000000000L) {
       return threeDigits(t, qty / 1000000000000L) + "T";
     } else if (qty >= 1000000000) {
       return threeDigits(t, qty / 1000000000) + "G";
     } else if (qty >= 1000000) {
       return threeDigits(t, qty / 1000000) + "M";
     } else if (qty >= 1000) {
       return threeDigits(t, qty / 1000) + "K";
     } else {                   // qty < 1000
       return threeDigits(t, qty) + " ";
     }
   }

  // Return a string 5 character wide to represent signed memory qty
  static private String signedMemRep(ExtrememThread t, long qty) {
    // Memory behavior is same in both cases
    int capacity = Util.ephemeralStringBuilder(t, 1);
    capacity = Util.ephemeralStringBuilderAppend(t, 1, capacity, 4);
    // Value returned from memRep() is now garbage
    Util.abandonEphemeralString(t, 4);
    Util.ephemeralStringBuilderToString(t, 5, capacity);
    
    if (qty < 0)
      return "-" + memRep(t, -qty);
    else
      return " " + memRep(t, qty);
  }

  private static void reportCSVOneRow(ExtrememThread t,
                                      String label, long[] deltas) {
    String StringBuilderQty = (
      Long.toString(deltas[MemoryFlavor.StringBuilder.ordinal()]));
    Util.ephemeralString(t, StringBuilderQty.length());
    
    String StringBuilderDataQty = (
      Long.toString(deltas[MemoryFlavor.StringBuilderData.ordinal()]));
    Util.ephemeralString(t, StringBuilderDataQty.length());

    String StringObjectQty = (
      Long.toString(deltas[MemoryFlavor.StringObject.ordinal()]));
    Util.ephemeralString(t, StringObjectQty.length());

    String StringDataQty = (
      Long.toString(deltas[MemoryFlavor.StringData.ordinal()]));
    Util.ephemeralString(t, StringDataQty.length());

    String ArrayObjectQty = (
      Long.toString(deltas[MemoryFlavor.ArrayObject.ordinal()]));
    Util.ephemeralString(t, ArrayObjectQty.length());

    String ArrayReferenceQty = (
      Long.toString(deltas[MemoryFlavor.ArrayReference.ordinal()]));
    Util.ephemeralString(t, ArrayReferenceQty.length());

    String ArrayRSBQty = (
      Long.toString(deltas[MemoryFlavor.ArrayRSB.ordinal()]));
    Util.ephemeralString(t, ArrayRSBQty.length());

    String PlainObjectQty = (
      Long.toString(deltas[MemoryFlavor.PlainObject.ordinal()]));
    Util.ephemeralString(t, PlainObjectQty.length());

    String ObjectReferenceQty = (
      Long.toString(deltas[MemoryFlavor.ObjectReference.ordinal()]));
    Util.ephemeralString(t, ObjectReferenceQty.length());

    String ObjectRSBQty = (
      Long.toString(deltas[MemoryFlavor.ObjectRSB.ordinal()]));
    Util.ephemeralString(t, ObjectRSBQty.length());

    Report.output(label, ", ", StringBuilderQty,
                  ", ", StringBuilderDataQty,
                  ", ", StringObjectQty,
                  ", ", StringDataQty,
                  ", ", ArrayObjectQty,
                  ", ", ArrayReferenceQty,
                  ", ", ArrayRSBQty,
                  ", ", PlainObjectQty,
                  ", ", ObjectReferenceQty,
                  ", ", ObjectRSBQty);
    
    Util.abandonEphemeralString(t, StringBuilderQty.length());
    Util.abandonEphemeralString(t, StringBuilderDataQty.length());
    Util.abandonEphemeralString(t, StringObjectQty.length());
    Util.abandonEphemeralString(t, StringDataQty.length());
    Util.abandonEphemeralString(t, ArrayObjectQty.length());
    Util.abandonEphemeralString(t, ArrayReferenceQty.length());
    Util.abandonEphemeralString(t, ArrayRSBQty.length());
    Util.abandonEphemeralString(t, PlainObjectQty.length());
    Util.abandonEphemeralString(t, ObjectReferenceQty.length());
    Util.abandonEphemeralString(t, ObjectRSBQty.length());
  }

  static void reportCumulativeCSV(ExtrememThread t, MemoryLog log) {
    long[][] delta = log.tallies;

    Report.output("LifeSpan, StringBuilder, StringBuilderData, StringObject, "
                  + "StringData, ArrayObject, ArrayReference, ArrayRSB, "
                  + "PlainObject, ObjectReference, ObjectRSB");
       
    long[] deltas = delta[LifeSpan.Ephemeral.ordinal()];
    reportCSVOneRow(t, "Ephemeral", deltas);

    deltas = delta[LifeSpan.TransientShort.ordinal()];
    reportCSVOneRow(t, "TransientShort", deltas);

    deltas = delta[LifeSpan.TransientIntermediate.ordinal()];
    reportCSVOneRow(t, "TransientIntermediate", deltas);
       
    deltas = delta[LifeSpan.TransientLingering.ordinal()];
    reportCSVOneRow(t, "TransientLingering", deltas);

    deltas = delta[LifeSpan.NearlyForever.ordinal()];
    reportCSVOneRow(t, "NearlyForever", deltas);
  }

  static void reportHeaders() {
    Report.output(
      "              ",
      "StringBuilder                   ArrayObject");
    Report.output(
      "        net   |",
      "       StringBuilderData       |       ArrayData");
    Report.output(
      "  allocated   |",
      "       |       StringObject    |       |       PlainObject");
    Report.output(
      "              |",
      "       |       |       StringData      |       |      ObjectData");
    Report.output(
      "______________|",
      "_______|_______|_______|_______|_______|_______|_______|_________");
  }
  
  static void reportDivider() {
    Report.output(
      "______________|",
      "_______|_______|_______|_______|_______|_______|_______|_________");
  }

  static void reportCumulativeOneRow(ExtrememThread t, String label1,
                                     String label2, long[] deltas) {
    if (label2 != null) {
      Report.outputNoLine(" ", label1);
      for (int spaces = 13 - label1.length(); spaces > 0; spaces--) 
        Report.outputNoLine(" ");
      Report.output(
        "|       |       |       |       |       |       |       |");
    } else 
      label2 = label1;

    Report.outputNoLine(" ", label2);
    for (int spaces = 13 - label2.length(); spaces > 0; spaces--) 
      Report.outputNoLine(" ");
    Report.outputNoLine("| ");

    String StringBuilderQty = (
      signedMemRep(t, deltas[MemoryFlavor.StringBuilder.ordinal()]));
    String StringBuilderDataQty = (
      signedMemRep(t, deltas[MemoryFlavor.StringBuilderData.ordinal()]));
    String StringObjectQty = (
      signedMemRep(t, deltas[MemoryFlavor.StringObject.ordinal()]));
    String StringDataQty = (
      signedMemRep(t, deltas[MemoryFlavor.StringData.ordinal()]));
    String ArrayObjectQty = (
      signedMemRep(t, deltas[MemoryFlavor.ArrayObject.ordinal()]));
    String ArrayDataQty = (
      signedMemRep(t, deltas[MemoryFlavor.ArrayReference.ordinal()]
                   * Util.SizeOfReference
                   + deltas[MemoryFlavor.ArrayRSB.ordinal()]));
    String PlainObjectQty = (
      signedMemRep(t, deltas[MemoryFlavor.PlainObject.ordinal()]));
    String ObjectDataQty = (
      signedMemRep(t, deltas[MemoryFlavor.ObjectReference.ordinal()]
                   * Util.SizeOfReference
                   + deltas[MemoryFlavor.ObjectRSB.ordinal()]));

    Report.output(StringBuilderQty, " | ", 
                  StringBuilderDataQty, " | ",
                  StringObjectQty, " | ",
                  StringDataQty, " | ",
                  ArrayObjectQty, " | ",
                  ArrayDataQty, " | ",
                  PlainObjectQty, " | ",
                  ObjectDataQty);

      // Reclaim memory for 8 5-character signedMemRep() results
      Util.abandonIdenticalEphemeralStrings(t, 8, 5);
  }

  static void reportCumulative(ExtrememThread t,
                               boolean reportCSV, MemoryLog log) {
                               
    // log is not the current thread's log, so the values being
    // reported will not be corrupted by ongoing allocations during
    // report printing.
    if (reportCSV)
      reportCumulativeCSV(t, log);
    else {
      long[][] delta = log.tallies;
      reportHeaders();
       
      long[] deltas = delta[LifeSpan.Ephemeral.ordinal()];
      reportCumulativeOneRow(t, "Ephemeral", null, deltas);
      reportDivider();

      deltas = delta[LifeSpan.TransientShort.ordinal()];
      reportCumulativeOneRow(t, "Transient", "Short", deltas);
      reportDivider();

      deltas = delta[LifeSpan.TransientIntermediate.ordinal()];
      reportCumulativeOneRow(t, "Transient", "Intermediate", deltas);
      reportDivider();
       
      deltas = delta[LifeSpan.TransientLingering.ordinal()];
      reportCumulativeOneRow(t, "Transient", "Lingering", deltas);
      reportDivider();
       
      deltas = delta[LifeSpan.NearlyForever.ordinal()];
      reportCumulativeOneRow(t, "NearlyForever", null, deltas);
      reportDivider();
    }
  }

  static void reportOneRow(ExtrememThread t, String label, long[] values) {

    Report.outputNoLine(" ", label);
    for (int spaces = 13 - label.length(); spaces > 0; spaces--) 
      Report.outputNoLine(" ");
    Report.outputNoLine("|  ");

    String StringBuilderQty = (
      memRep(t, values[MemoryFlavor.StringBuilder.ordinal()]));
    String StringBuilderDataQty = (
      memRep(t, values[MemoryFlavor.StringBuilderData.ordinal()]));
    String StringObjectQty = (
      memRep(t, values[MemoryFlavor.StringObject.ordinal()]));
    String StringDataQty = (
      memRep(t, values[MemoryFlavor.StringData.ordinal()]));
    String ArrayObjectQty = (
      memRep(t, values[MemoryFlavor.ArrayObject.ordinal()]));
    String ArrayDataQty = (
      memRep(t, values[MemoryFlavor.ArrayReference.ordinal()]
                   * Util.SizeOfReference
                   + values[MemoryFlavor.ArrayRSB.ordinal()]));
    String PlainObjectQty = (
      memRep(t, values[MemoryFlavor.PlainObject.ordinal()]));
    String ObjectDataQty = (
      memRep(t, values[MemoryFlavor.ObjectReference.ordinal()]
                   * Util.SizeOfReference
                   + values[MemoryFlavor.ObjectRSB.ordinal()]));

    Report.output(StringBuilderQty, " |  ", 
                  StringBuilderDataQty, " |  ",
                  StringObjectQty, " |  ",
                  StringDataQty, " |  ",
                  ArrayObjectQty, " |  ",
                  ArrayDataQty, " |  ",
                  PlainObjectQty, " |  ",
                  ObjectDataQty);

      // Reclaim memory for 8 4-character memRep() results
      Util.abandonIdenticalEphemeralStrings(t, 8, 4);
  }

  static void reportCSV(ExtrememThread t,
                        long[][] allocs, long[][] discards, long[][] deltas) {
    Report.output("LifeSpan, StringBuilder, StringBuilderData, StringObject, "
                  + "StringData, ArrayObject, ArrayReference, ArrayRSB, "
                  + "PlainObject, ObjectReference, ObjectRSB");
    
    long[] a_row = allocs[LifeSpan.Ephemeral.ordinal()];
    long[] d_row = discards[LifeSpan.Ephemeral.ordinal()];
    long[] delta_row = deltas[LifeSpan.Ephemeral.ordinal()];

    reportCSVOneRow(t, "Ephemeral Allocations", a_row);
    reportCSVOneRow(t, "Ephemeral Discards", d_row);
    reportCSVOneRow(t, "Ephemeral Net", delta_row);

    a_row = allocs[LifeSpan.TransientShort.ordinal()];
    d_row = discards[LifeSpan.TransientShort.ordinal()];
    delta_row = deltas[LifeSpan.TransientShort.ordinal()];
    
    reportCSVOneRow(t, "TransientShort Allocations", a_row);
    reportCSVOneRow(t, "TransientShort Discards", d_row);
    reportCSVOneRow(t, "TransientShort Net", delta_row);

    a_row = allocs[LifeSpan.TransientIntermediate.ordinal()];
    d_row = discards[LifeSpan.TransientIntermediate.ordinal()];
    delta_row = deltas[LifeSpan.TransientIntermediate.ordinal()];
    
    reportCSVOneRow(t, "TransientIntermediate Allocations", a_row);
    reportCSVOneRow(t, "TransientIntermediate Discards", d_row);
    reportCSVOneRow(t, "TransientIntermediate Net", delta_row);

    a_row = allocs[LifeSpan.TransientLingering.ordinal()];
    d_row = discards[LifeSpan.TransientLingering.ordinal()];
    delta_row = deltas[LifeSpan.TransientLingering.ordinal()];

    reportCSVOneRow(t, "TransientLingering Allocations", a_row);
    reportCSVOneRow(t, "TransientLingering Discards", d_row);
    reportCSVOneRow(t, "TransientLingering Net", delta_row);

    a_row = allocs[LifeSpan.NearlyForever.ordinal()];
    d_row = discards[LifeSpan.NearlyForever.ordinal()];
    delta_row = deltas[LifeSpan.NearlyForever.ordinal()];

    reportCSVOneRow(t, "NearlyForever Allocations", a_row);
    reportCSVOneRow(t, "NearlyForever Discards", d_row);
    reportCSVOneRow(t, "NearlyForever Net", delta_row);
  }

  static void report(ExtrememThread t, boolean reportCSV,
                     MemoryLog alloc_log, MemoryLog discard_log) {
       
    int lifespans = LifeSpan.OrdinalCount;
    int flavors = MemoryFlavor.OrdinalCount;
       
    // Report the snapshot of memory usage before generation of this
    // report results in additional allocation and garbage.
    long[][] allocs = new long[lifespans][flavors];
    long[][] discards = new long[lifespans][flavors];
    long[][] deltas = new long[lifespans][flavors];
    for (int i = 0; i < LifeSpan.OrdinalCount; i++) {
      for (int j = 0; j < MemoryFlavor.OrdinalCount; j++) {
        allocs[i][j] = alloc_log.tallies[i][j];
        discards[i][j] = discard_log.tallies[i][j];
        deltas[i][j] = allocs[i][j] - discards[i][j];
      }
    }
    // Account for three temporary arrays (allocs, discards, deltas),
    // each holding five inner arrays.
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject, Grow, 18);
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
                   Grow, 3 * 5);
    log.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB,
                   Grow, 3 * 5 * 10 * Util.SizeOfLong);
    
    if (reportCSV) {
      reportCSV(t, allocs, discards, deltas);
    } else {
      reportHeaders();
       
      long[] a_row = allocs[LifeSpan.Ephemeral.ordinal()];
      long[] d_row = discards[LifeSpan.Ephemeral.ordinal()];
      long[] delta_row = deltas[LifeSpan.Ephemeral.ordinal()];

      reportOneRow(t, "", a_row);
      reportOneRow(t, "Ephemeral", d_row);
      reportCumulativeOneRow(t, "", null, delta_row);
      reportDivider();
       
      a_row = allocs[LifeSpan.TransientShort.ordinal()];
      d_row = discards[LifeSpan.TransientShort.ordinal()];
      delta_row = deltas[LifeSpan.TransientShort.ordinal()];

      reportOneRow(t, "", a_row);
      reportOneRow(t, "Transient", d_row);
      reportCumulativeOneRow(t, "Short", null, delta_row);
      reportDivider();

      a_row = allocs[LifeSpan.TransientIntermediate.ordinal()];
      d_row = discards[LifeSpan.TransientIntermediate.ordinal()];
      delta_row = deltas[LifeSpan.TransientIntermediate.ordinal()];

      reportOneRow(t, "", a_row);
      reportOneRow(t, "Transient", d_row);
      reportCumulativeOneRow(t, "Intermediate", null, delta_row);
      reportDivider();

      a_row = allocs[LifeSpan.TransientLingering.ordinal()];
      d_row = discards[LifeSpan.TransientLingering.ordinal()];
      delta_row = deltas[LifeSpan.TransientLingering.ordinal()];

      reportOneRow(t, "", a_row);
      reportOneRow(t, "Transient", d_row);
      reportCumulativeOneRow(t, "Lingering", null, delta_row);
      reportDivider();

      a_row = allocs[LifeSpan.NearlyForever.ordinal()];
      d_row = discards[LifeSpan.NearlyForever.ordinal()];
      delta_row = deltas[LifeSpan.NearlyForever.ordinal()];

      reportOneRow(t, "", a_row);
      reportOneRow(t, "NearlyForever", d_row);
      reportCumulativeOneRow(t, "", null, delta_row);
      reportDivider();
    }
    MemoryLog garbage = t.garbageLog();
    // accumulate the allocs, discards, and deltas arrays, each with
    // five inner arrays.
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayObject,
                       Grow, 18);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayReference,
                       Grow, 3 * 5);
    garbage.accumulate(LifeSpan.Ephemeral, MemoryFlavor.ArrayRSB,
                       Grow, 3 * 5 * 10 * Util.SizeOfLong);
  }

  final void report(ExtrememThread t) {
    for (LifeSpan ls: LifeSpan.values())
      for (MemoryFlavor mf: MemoryFlavor.values()) {
        String s = Long.toString(tallies[ls.ordinal()][mf.ordinal()]);
        int len = s.length();
        Util.ephemeralString(t, len);
        Report.output(ls.name(), " ", mf.name(), ": ", s);
        Util.abandonEphemeralString(t, len);
      }
    Report.output();
  }
}
