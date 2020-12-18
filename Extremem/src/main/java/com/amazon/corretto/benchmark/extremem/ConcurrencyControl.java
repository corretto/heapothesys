// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * Allow multiple readers to access controllee object concurrently.
 * Allow only one writer and no readers to access controllee
 * concurrently.
 *
 * Writing is very rare.  When a write is pending, stall additional
 * readers until the write has been performed.
 *
 * When a write is requested, block new reader requests until the
 * write has been performed.  The write must wait until all existing
 * readers, or other writers, complete their access.
 */
class ConcurrencyControl extends ExtrememObject {
  static int cc_count = 0;

  private int num_readers = 0;
  private int num_writers = 0;
  private int waiting_readers = 0;
  private int waiting_writers = 0;
  private int max_waits_per_read, max_waits_per_write;
  private int min_waits_per_write = Integer.MAX_VALUE;
  private int min_waits_per_read = Integer.MAX_VALUE;

  private long total_reader_requests;
  private long total_writer_requests;
  private long total_reader_waits;
  private long total_writer_waits;

  private final String cc_id;

  static synchronized int assignId() {
    return cc_count++;
  }

  ConcurrencyControl(ExtrememThread t, LifeSpan ls) {
    super(t, ls);
    int idno = assignId();
    cc_id = "CC_" + Integer.toString(assignId());

    MemoryLog log = t.memoryLog();
    // Account for int fields: num_readers, num_writers, waiting_readers,
    // waiting_writers, max_waits_per_read, max_waits_per_write,
    // min_waits_per_write, min_waits_per_read; 
    // long fields: total_reader_requests,
    // total_writer_requests, total_reader_waits, total_writer_waits
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Polarity.Expand,
                   8 * Util.SizeOfInt + 4 * Util.SizeOfLong);
    // Account for String field cc_id
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 1);

    // 3 is length of "CC_"
    int capacity = Util.ephemeralStringBuilder(t, 3);
    int digits = Util.decimalDigits(idno);
    Util.ephemeralString(t, digits);
    capacity = Util.ephemeralStringBuilderAppend(t, 3, capacity, digits);
    Util.abandonEphemeralString(t, digits);
    Util.ephemeralStringBuilderToString(t, 3 + digits, capacity);
    Util.convertEphemeralString(t, ls, 3 + digits);
  }

  /**
   * If there is a pending write, don't enter until the write has been
   * serviced. 
   */
  private synchronized void acquireLockForRead () {
    waiting_readers++;
    int num_waits = 0;
    Trace.msg(4, cc_id, ": acquiring read lock, contention(",
              Integer.toString(waiting_readers), ", ",
              Integer.toString(num_writers), ")");
    while ((num_writers > 0) || (waiting_writers > 0)) {
      try {
        num_waits++;
        wait ();
      } catch (InterruptedException x) {
        ;                       // ignore the interrupt, wait some more
      }
    }
    if (num_waits < min_waits_per_read)
      min_waits_per_read = num_waits;
    if (num_waits > max_waits_per_read)
      max_waits_per_read = num_waits;
    total_reader_waits += num_waits;
    waiting_readers--;
    num_readers++;
    Trace.msg(4, cc_id, ": acquired read lock, readers: ",
              Integer.toString(num_readers));
  }

  private synchronized void acquireLockForWrite () {
    waiting_writers++;
    int num_waits = 0;
    Trace.msg(4, cc_id, ": acquring write lock, contention(",
              Integer.toString(waiting_readers + num_readers), ", ",
              Integer.toString(waiting_writers + num_writers), ")");
    while ((num_readers > 0) || (num_writers > 0)) {
      try {
        num_waits++;
        wait ();
      } catch (InterruptedException x) {
        ;                       // ignore the interrupt, wait some more
      }
    }
    if (num_waits < min_waits_per_write)
      min_waits_per_write = num_waits;
    if (num_waits > max_waits_per_write)
      max_waits_per_write = num_waits;
    total_writer_waits += num_waits;;
    waiting_writers--;
    num_writers++;
    Trace.msg(4, cc_id, ": acquired writelock");
  }

  private synchronized void releaseReadLock () {
    num_readers--;
    Trace.msg(4, cc_id, ": releasing read lock, readers: ",
              Integer.toString(num_readers),
              ", waiting_writers: ", Integer.toString(waiting_writers));
    if ((num_readers == 0) && (waiting_writers > 0))
      notifyAll();
  }

  private synchronized void releaseWriteLock () {
    num_writers--;
    Trace.msg(4, cc_id, ": releasing write lock, contention(",
              Integer.toString(waiting_readers), ", ",
              Integer.toString(waiting_writers), ")");
    if ((waiting_writers > 0) || (waiting_readers > 0))
      notifyAll ();
  }

  void actAsReader (Actor actor) {
    try {
      total_reader_requests++;
      acquireLockForRead ();
      actor.act ();
    } finally {
      releaseReadLock ();
    }
  }
  
  void actAsWriter (Actor actor) {
    try {
      total_writer_requests++;
      acquireLockForWrite ();
      actor.act ();
    } finally {
      releaseWriteLock ();
    }
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    // Account for cc_id
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);


    // Account for int fields: num_readers, num_writers, waiting_readers,
    // waiting_writers, max_waits_per_read, max_waits_per_write,
    // min_waits_per_write, min_waits_per_read; 
    // long fields: total_reader_requests,
    // total_writer_requests, total_reader_waits, total_writer_waits
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
                   8 * Util.SizeOfInt + 4 * Util.SizeOfLong);

    Util.tallyString(log, ls, p, cc_id.length());
  }

  void report(ExtrememThread t, boolean reportCSV) {
    String s = Long.toString(total_reader_requests);
    int l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("Total reader requests,", s);
    else
      Report.output("       Total read requests: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Long.toString(total_reader_waits);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("requiring this many waits,", s);
    else
      Report.output(" requiring this many waits: ", s);
    Util.abandonEphemeralString(t, l);

    float average;
    int min, max;
    if (total_reader_requests > 0) {
      average = ((float) total_reader_waits) / total_reader_requests;
      min = min_waits_per_read;
      max = max_waits_per_read;
    } else {
      average = 0f;
      min = 0;
      max = 0;
    }
    
    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average waits,", s);
    else
      Report.output("             average waits: ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("ranging from,", s);
    else
      Report.output("              ranging from: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("to,", s);
    else
      Report.output("                        to: ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    s = Long.toString(total_writer_requests);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("Total writer requests,", s);
    else
      Report.output("     Total writer requests: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Long.toString(total_writer_waits);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("requiring this many waits,", s);
    else
      Report.output(" requiring this many waits: ", s);
    Util.abandonEphemeralString(t, l);

    if (total_writer_requests > 0) {
      average = ((float) total_writer_waits) / total_writer_requests;
      min = min_waits_per_write;
      max = max_waits_per_write;
    } else {
      average = 0f;
      min = 0;
      max = 0;
    }

    s = Float.toString(average);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("average waits,", s);
    else
      Report.output("             average waits: ", s);
    Util.abandonEphemeralString(t, l);
    
    s = Integer.toString(min);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("ranging from,", s);
    else
      Report.output("              ranging from: ", s);
    Util.abandonEphemeralString(t, l);
      
    s = Integer.toString(max);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.output("to,", s);
    else
      Report.output("                        to: ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
  }
}
