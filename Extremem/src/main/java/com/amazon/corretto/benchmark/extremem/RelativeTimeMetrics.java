// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 *  RelativeTimeMetrics maintains a tally of how many time intervals
 *  fit into 32 different ranges of time.
 *
 *  By default, each time range represents a span of 256 microseconds,
 *  allowing distinct tallies to be maintained for each
 *  256-microsecond range over a full time span of 8 ms.
 *
 *  The data representation scales in the following ways:
 *
 *    The smallest time range spanned by any bucket is the default
 *    time range (256 microseconds).
 *
 *    Each bucket spans 2^N the range of the preceding bucket, for
 *    integer N >= 0.  In other words, the next bucket may span the
 *    same range as the preceding bucket, or twice its range or 4
 *    times its range, or any positive-integer-power-of-two times its
 *    range. 
 *
 *    In its most extreme configuration, the 32 buckets are organized
 *    to represent time spans as illustrated below:
 *
 *       [256 us] [512 us] [1024 us] [~2 ms] ...  [~2^29 ms]
 *       0        1        2         3            31
 *
 *    When a new entry is logged within the existing time span, simply
 *    increment the existing bucket's tally.
 *
 *    When a new entry whose value is greater than all existing
 *    buckets is logged, add buckets to the end of the representation,
 *    with each bucket spanning twice the duration as the previous
 *    bucket until a new bucket exists to span the new entry.
 *
 *    When a new entry whose value is smaller than all existing
 *    buckets is logged, add buckets to the start of the
 *    representation, with each bucket spanning 1/2 the range spanned
 *    by the bucket that follows it.
 *
 *    When expanding the range spanned by this RelativeTimeMetrics
 *    object either at the large or small end of its existing range,
 *    it is sometimes the case that there are not enough available
 *    slots to span the full desired new range.  Apply compression to
 *    the existing data by expanding the ranges of particular buckets,
 *    coalescing the contents of neighboring buckets, as necessary.
 */
class RelativeTimeMetrics extends ExtrememObject {
  // How many buckets used for histogram report?
  private final int HistoColumnCount = 64;

  // Assumed width of display device for histogram report
  private final int PageColumns = 80;

  // Count the RelativeTimeMetrics instantiations in order to
  // associate a unique identifying label to each.
  static int num_instances = 0;

  // Both BucketCount and DefaultIntervalMicroseconds Must be a power of 2.
  private final static int BucketCount = 32;
  private final static long DefaultIntervalMicroseconds = 256; 

  long fblb;			// first bucket low bound (in microseconds)
  long lbhb;			// last bucket high bound (in microseconds)

  int fbi;			// first bucket index: buckets[fbi] holds
				// count for first time interval


  long sis;			// smallest interval seen (in microseconds)
  long lis;			// largest interval seen (in microseconds)

  int biu;			// buckets in use currently
  int [] buckets;
  long [] bucket_bounds;	// bucket_bounds[i] holds the low
				// bound (microseconds) for tallies
				// accumulated in buckets[i], except
				// for bucket_bounds[fbi], which may
				// hold the high bound for the tallies
				// accumulated in the last bucket.
				// bucket_bounds[(i+1)%BucketCount]
				// holds the high bound for tallies
				// accumulated in bucket_bounds[i].
  				//
				// Accumulation of tallies uses a
				// binary search to find the index of
				// the bucket at which to accumulate.
  int total_entries = 0;
  long accumulated_microseconds = 0;

  private final String id;

  static synchronized int nextSequenceNo() {
    return num_instances++;
  }
  
  public RelativeTimeMetrics(ExtrememThread t, LifeSpan ls) {
    super(t, ls);
    buckets = new int [BucketCount];
    bucket_bounds = new long [BucketCount];
    sis = Long.MAX_VALUE;
    id = "RTM_" + nextSequenceNo();
    Trace.msg(4, id, ": sis intialized to ", Long.toString(sis));

    MemoryLog log = t.memoryLog();

    // Account for 3 reference fields: buckets, bucket_bounds, id.
    log.accumulate(ls, MemoryFlavor.ObjectReference, Polarity.Expand, 3);

    // Account for 3 int fields: fbi, biu, total_entries; and 5 long
    // fields: fblb, lbhb, sis, lis, accumulated_microseconds
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Polarity.Expand,
		   3 * Util.SizeOfInt + 5 * Util.SizeOfLong);

    // Account for buckets and bucket_bounds arrays.
    log.accumulate(ls, MemoryFlavor.ArrayObject, Polarity.Expand, 2);
    log.accumulate(ls, MemoryFlavor.ArrayRSB, Polarity.Expand,
		   BucketCount * (Util.SizeOfInt + Util.SizeOfLong));
  }

  void addToLog(RelativeTimeMetrics other) {
    int index = other.fbi;
    boolean seen_sis = false;
    for (int i = 0; i < other.biu; i++) {
      int next_index = incrIndex(index);
      long bucket_average;
      if (!seen_sis) {
	if (other.bucket_bounds[next_index] > other.sis) {
	  seen_sis = true;
	  bucket_average = (other.bucket_bounds[next_index] - other.sis) / 2;
	  this.addToLog(other.sis);
	  for (int j = other.buckets[index] - 1; j > 0; j--)
	    this.addToLog(bucket_average);
	} // else, this is an empty bucket that precedes sis
      } else if (i == other.biu - 1) {
	bucket_average = (other.lis - other.startAt(index)) / 2;
	this.addToLog(other.lis);
	for (int j = other.buckets[index] - 1; j > 0; j--)
	  this.addToLog(bucket_average);
      } else {
	bucket_average = other.startAt(index) + other.spanAt(index) / 2;
	for (int j = other.buckets[index]; j > 0; j--)
	  this.addToLog(bucket_average);
      }
      index = next_index;
    }
  }

  // This service is designed to work only with non-negative values.
  // I have found through experimentation that, due to an "error" in
  // the implementation of Thread.sleep(), we may end up reporting
  // that certain latencies are negative (especially the latencies for
  // do-nothing operations).  I have addressed problem by rounding up
  // any negative values to zero.
  void addToLog(long value) {

    Trace.msg(4, "");
    Trace.msg(4, id, ": addToLog (" + value + ")");

    if (value < 0)		// workaround bug in Thread.sleep()
      value = 0;

    total_entries++;
    accumulated_microseconds += value;
    if (biu == 0) {
      sis = value;
      Trace.msg(4, id, ": sis set to ", Long.toString(sis));
      lis = value;
      Trace.msg(4, id, ": lis set to ", Long.toString(lis));

      long new_bucket_offset = value & ~(DefaultIntervalMicroseconds - 1);
      fblb = new_bucket_offset;
      lbhb = new_bucket_offset + DefaultIntervalMicroseconds;

      bucket_bounds[0] = fblb;
      bucket_bounds[1] = lbhb;

      biu = 1;
    } else {
      if (value < fblb)
	expandLowRange(value);
      else if (value >= lbhb)
	expandHighRange(value);

      if (value < sis) {
	sis = value;
	Trace.msg(4, id, ": sis set to ", Long.toString(sis));
      }
      else if (value > lis) {
	lis = value;
	Trace.msg(4, id, ": lis set to ", Long.toString(lis));
      }
    }
    accumulateTally (value);

    Trace.msg(4, id, ": @ end of addToLog (" + value + ")");
    if (Trace.enabled(3))
      dump();
  }

  /**
   * Use binary search to determine destination bucket.
   * Precondition: value is known to fall within range of current span.
   */
  private void accumulateTally (long value) {

    Trace.msg(4, id, ": accumulateTally (" + value + ")");

    if (biu > BucketCount)
      Util.internalError (id + ": accumulateTally biu > BucketCount");

    int lb = 0;			// low bound
    int ub = biu - 1;		// high bound
    while (true) {
      // Invariant: destination bucket's index is between lb and ub inclusive
      int probe = (ub + lb) / 2;
      int probe_index = incrIndexBy (fbi, probe);

      Trace.msgNoLine(4, id, ": accumulateTally, fbi: ", Integer.toString(fbi),
		      ", lb: ", Integer.toString(lb),
		      ", ub: ", Integer.toString(ub));
      Trace.msg(4, ", probe: ", Integer.toString(probe),
		", probe_index: ", Integer.toString(probe_index));
      Trace.msg(4, id, ":  spans " + debug_us2s (spanAt (probe_index)),
		" starting from ",
		debug_us2s((probe > 0)? bucket_bounds [probe_index]: fblb));

      if (ub < lb)
	Util.internalError ("accumulateTally bounds are inverted");

      if ((probe == 0) && (value < fblb))
	Util.internalError ("accumulating out-of-range value");
      else if ((probe != 0) && (value < bucket_bounds[probe_index]))
	ub = probe - 1;
      else if (value >= bucket_bounds[incrIndex(probe_index)]) {
	lb = probe + 1;
      } else {
	buckets[probe_index]++;
	break;
      }
    }
  }

  /* How many buckets are required to span this gap, assuming that we
   * want the smallest bucket to span DefaultIntervalMicroseconds.
   * Assume gap > 0.  Set span_result[0] to represent the number of
   * microseconds spanned by the buckets used to span the gap.  */
  private int bucketsToSpan (long gap, long [] span_result) {
    long span = 0;
    long bucket_span = DefaultIntervalMicroseconds;

    gap += DefaultIntervalMicroseconds - 1;
    gap /= DefaultIntervalMicroseconds;
    int bucket_count = 0;
    if ((gap & 0x01) == 0) {
      bucket_count = 2;
      span += 2 * bucket_span;
      bucket_span *= 2;
      gap /= 2;
      while ((gap & 0x01) == 0) {
	bucket_count++;
	span += bucket_span;
	bucket_span *= 2;
	gap /= 2;
      }
      // We have just represented a gap of size 2^n with buckets of
      // size 2^(n-1), 2^(n-2), ... , 1, and 1.
      bucket_span *= 2;
      gap /= 2;
    }
    while (gap != 0) {
      if ((gap & 0x01) != 0) {
	bucket_count++;
	span += bucket_span;
      }
      bucket_span *= 2;
      gap /= 2;
    }

    Trace.msg(4, id, ": bucketsToSpan (",
	      Long.toString(gap), ") returns ",
	      Integer.toString(bucket_count));
    Trace.msg(4, id, ": (  (spanning ", Long.toString(span), ")");
    span_result[0] = span;
    return bucket_count;
  }

  /* Return true if it's possible to squash the tail into a declining
   * power-of-two sequence with largest_span anchored at the largest
   * bucket.  For example:
   *   Given tail sequence:       1 1 1 2 4 4 8 16 ...
   *                         \- ... ----/ \------/
   * canSquash(8, 32) succeeds:     16      32
   *                              ? ? ^ ^ ^
   * canSquash(5, 4) fails:           1 2 4  (doesn't fully cover)
   *
   *   Given tail sequence:       1 16 16 16 16 ...
   *                                   ^  \---/
   *                                   16  32
   * canSquash(5, 32) fails because we cannot span "1 16" with "1 2 4 8".
   *
   * Theorem: As existing bucket spans are accumulated into a
   * desired subsumption_span, either each bucket consumes the entirety
   * of the remaining uncommitted space within the subsumption_span,
   * or it leaves an uncommitted space that is at least as large as
   * what it consumes.  Since all remaining bucket spans are no larger
   * than the bucket most recently subsumed, they will either fill the
   * space or they will in turn leave space that can be filled by the
   * buckets that follow in decreasing order.
   *
   * Corollary: I can test whether canSquash() by simply confirming
   * that the cumulative size of all subsumption_buckets is no larger
   * than (2 * bucket_span) - 1.
   */
  private boolean canSquash(int subsumption_buckets, long bucket_span) {
    long accumulation = 0;
    for (int i = 0; i < subsumption_buckets; i++)
      accumulation += spanAt(incrIndexBy(fbi, i));
    if (accumulation <= (bucket_span * 2) - 1)
      return true;
    else
      return false;
  }

  /* A "fold" (my terminology) has the effect of replacing an
   * accumulation of similar-sized buckets (e.g. 1, 1, 2, 2, 2, 2, 4) 
   * with a sequence of buckets sized 1, 2, 4, 8, ... 2^(n-2), 2^(n-1)
   * to span a gap of size 2^n - 1.
   *
   * requiredSpan is known to be a multiple of DefaultIntervalMicroseconds;
   */
  private void doFold(long requiredSpan) {
    /* We enforce the following constraints on folds:
     *  1. There must be n available buckets to hold the fold.
     *  2. The bucket that becomes the right neighbor of the fold must
     *     be of size 2^(n-1) or larger.
     * While either constraint is not satisfied and there is at least
     * one larger neighbor, we expand the fold to subsume an additional
     * larger neighbor.  Subsuming a larger neighbor that spans
     * twice the range of the previously largest fold bucket does not
     *  improve compliance with constraint 2.  In the case that
     *  subsuming the next larger neighbor makes it possible to
     *  satisfy constraint 2, we know that the newly subsumed neighbor
     *  was either smaller or the same size as the previously largest
     *  bucket in the fold. Thus, we are assured that processing of
     *  overlap between existing data and the new fold will always
     *  coalesce at least the two largest nodes in the overlapping
     *  region.  */
    int span_buckets = 1;
    long span_of_buckets = DefaultIntervalMicroseconds;
    long span_of_largest_bucket = span_of_buckets;
    long required_fblb = fblb - requiredSpan;

    Trace.msg(4, id, ": doFold(", Long.toString(requiredSpan),
	      ") represents new fblb: ", Long.toString(required_fblb));
    if (Trace.enabled(3))
      dump();
    while (span_of_buckets < requiredSpan) {
      span_buckets++;
      span_of_largest_bucket *= 2;
      span_of_buckets += span_of_largest_bucket;
    }

    Trace.msg(4, id, ": span_buckets: ", Integer.toString(span_buckets),
	      ", span_of_buckets: ", Long.toString(span_of_buckets),
	      ", largest span: ", Long.toString(span_of_largest_bucket));

    // Find the desired overlap with existing contents.
    //
    //  Suppose the existing tail looks like:
    //    1, 1, 1, 2, 4, 8, 16, 16, 16, 16, 16, 16, 32, ...
    //                                          ^^
    //                                          A  
    //  and span_of_largest_bucket is 32.  In other words, suppose my
    //  proposed folded span consists of:
    //    1, 2, 4, 8, 16, 32
    //  My naive first preference for the overlap point would be at
    //  point A.  But this fold doesn't properly align with the
    //  existing data.  In other words, we can't figure out how to
    //  combine the values of the existing buckets into the folded
    //  buckets.  See below:
    //
    //    1, 1, 1, 2, 4, 8, 16, 16, 16, 16, 16, 16, 32, ...
    //       \--/  \-----------------/  \/  \----/
    //    1    2    does not fit in 8   16    32
    //
    //  The canSquash() method answers the question of whether a
    //  particular fold is capable of representing the data contained
    //  within existing buckets.
    
    int neighbor_index = fbi;
    int adjusted_span_buckets = span_buckets;
    long adjusted_largest_span = span_of_largest_bucket;
    long adjusted_requiredSpan = requiredSpan;
    long adjusted_span_of_buckets = span_of_buckets;

    boolean found_overlap = false;
    // Number of existing buckets to overlap with new logarithmic span.
    int overlap_candidate = 0;

    // The search for an overlap candidate proceeds in two dimensions:
    //
    //  1. Consider each possible point at which the existing fold
    //     might align.  
    //
    //  2. Consider expanding the fold by adding a new entry that is
    //     larger than the previous largest entry.  Once the fold is
    //     expanded, we should consider aligning at the same
    //     overlap_candidate position that did not work previously
    //     before expanding it again.

    while (overlap_candidate < biu) {
      // Since overlap_candidate < biu, neighbor_index known to be valid.
      if ((adjusted_span_of_buckets >= adjusted_requiredSpan) &&
	  (spanAt(neighbor_index) >= adjusted_largest_span) &&
	  canSquash(overlap_candidate, adjusted_largest_span)) {
	found_overlap = true;
	break;
      } else if (adjusted_span_of_buckets >=
		 adjusted_requiredSpan + spanAt(neighbor_index)) {
	// Try the same fold at a higher overlap_candidate index
	adjusted_requiredSpan += spanAt(neighbor_index);
	overlap_candidate++;
	neighbor_index = incrIndex(neighbor_index);
      } else {
	// Try an expanded fold at the same overlap_candidate position
	adjusted_largest_span *= 2;
	adjusted_span_of_buckets += adjusted_largest_span;
	adjusted_span_buckets++;
      }
    }

    Trace.msg(4, id, ": found_overlap: ", found_overlap? "true": "false",
	      ",: overlap_candidate: ", Integer.toString(overlap_candidate),
	      ", adjusted_span_of_buckets: ",
	      Long.toString(adjusted_span_of_buckets));
    Trace.msg(4, id, ": adjusted_largest_span: ",
	      Long.toString(adjusted_largest_span),
	      ", adjusted_requiredSpan: ",
	      Long.toString(adjusted_requiredSpan));

    if (!found_overlap) {
      // Subsume everything.  For example, suppose span_of_largest_bucket
      // is 1024 but the current state buckets hold only 1 2 4 8.  The
      // search for a suitable overlap_candidate will fail and control
      // flows to here.  The solution is to consolidate the entirety
      // of the existing span into a single consolidation bucket.
      int index = fbi;
      int tally = 0;
      for (int i = 0; i < biu; i++) {
	tally += buckets[index];
	index = incrIndex(index);
      }

      adjusted_largest_span = span_of_largest_bucket;
      adjusted_requiredSpan = requiredSpan;
      adjusted_span_of_buckets = span_of_buckets;
      adjusted_span_buckets = span_buckets;

      long total_span = lbhb - fblb;

      biu = 1;
      buckets[fbi] = tally;

      if ((total_span <= adjusted_largest_span) &&
	(span_of_buckets - total_span >= requiredSpan)) {
	// Overlap the largest span with consolidation bucket.
	// Exercise discretion to preserve log resolution in the small
	// time ranges.
	long excess = (span_of_buckets - total_span) - requiredSpan;
	// Put the excess at the high end of the consolidation
	// bucket's span.
	lbhb += excess;
	bucket_bounds[incrIndex(fbi)] = lbhb;
	fblb = lbhb - adjusted_largest_span;

	adjusted_span_of_buckets -= adjusted_largest_span;
	adjusted_span_buckets--;
	adjusted_largest_span /= 2;

	overlap_candidate = 1;
      } else {
	// Otherwise, logarithmic expansion precedes consolidation
	// bucket.  The consolidation bucket is the larger of twice
	// the span_of_largest_bucket and the first power of two size
	// as large as total_span.
	long consolidation_span = span_of_largest_bucket * 2;
	while (consolidation_span < total_span)
	  consolidation_span *= 2;
	lbhb = fblb + consolidation_span;
	bucket_bounds[incrIndex(fbi)] = lbhb;
	overlap_candidate = 0;
      }
    } else {
      // Turn overlap_candidate tail buckets into a power-of-two sequence,
      // but don't expand the tail yet.  We'll prepend additional
      // buckets in the code that follows.
      int fill_index = incrIndexBy(fbi, overlap_candidate - 1);
      int index = fill_index;
      int compress_count = overlap_candidate;
      int fill_count = 0;
      int fill_follow_index = incrIndex(fill_index);
      long end_of_fill_span = bucket_bounds[fill_follow_index];
      while (compress_count > 0) {
	long bucket_span = adjusted_largest_span;
	int tally = 0;
	while ((bucket_span > 0) && (compress_count > 0)) {
	  bucket_span -= spanAt(index);
	  tally += buckets[index];
	  index = decrIndex(index);
	  compress_count--;
	}

	Trace.msg(4, id, " Filling @", Integer.toString(fill_index), " with ",
		  Integer.toString(tally), ", spanning ",
		  debug_us2s(adjusted_largest_span));

	buckets[fill_index] = tally;
	fill_count++;
	bucket_bounds[fill_follow_index] = end_of_fill_span;

	fblb = end_of_fill_span - adjusted_largest_span;
	adjusted_span_of_buckets -= adjusted_largest_span;
	adjusted_span_buckets--;

	Trace.msg(4, id, " Start bound adjusted to: ", debug_us2s(fblb));

	end_of_fill_span = fblb;
	fill_follow_index = fill_index;
	fill_index = decrIndex(fill_index);
	adjusted_largest_span /= 2;
      }
      int bucket_change = overlap_candidate - fill_count;
      fbi = incrIndexBy(fbi, bucket_change);
      biu -= bucket_change;
    }

    Trace.msg(4, id, ": After squashing tail, dump is: ");
    if (Trace.enabled(3))
      dump();

    Trace.msg(4, id, " After processing overlap effects");
    Trace.msg(4, id, " adjusted_span_of_buckets: ",
	      Long.toString(adjusted_span_of_buckets),
	      ", adjusted_largest_span: ",
	      Long.toString(adjusted_largest_span),
	      ", adjusted_span_buckets: ",
	      Long.toString(adjusted_span_buckets));

    long overshoot = adjusted_span_of_buckets - (fblb - required_fblb);
    if (overshoot < 0)
      overshoot = 0;

    Trace.msg(4, id, ": overshoot is: ", Long.toString(overshoot));
    
    // if (overshoot > 0) prepending the logarithmic tree reaches
    // further than necessary.  Remove from the logarithmic tree any
    // buckets that are not required for the required span.
    //
    // If we exhuast our bucket budget before we have spanned the
    // full desired range, simply double the span of the bucket that
    // precedes the exhaustion point.  For example, if our intention
    // is to emit the tail sequence:
    //
    //   1, 2, 4, 8, 16, 32, 64, 128
    //
    // and we find ourselves limited to only 4 available slots, then
    // we replace with:
    //
    //   _, _, _, _, 32, 32, 64, 128  */

    int available_slots = BucketCount - biu;
    if (available_slots > 0) {
      while ((available_slots > 0) && (adjusted_span_of_buckets > 0)) {
	if (overshoot > adjusted_largest_span) {

	  Trace.msg(4, id, ": skipping overshot bucket with span: ",
		    Long.toString(adjusted_largest_span));
	  
	  // Skip this span, as it pushes us beyond target required_fblb.
	  overshoot -= adjusted_largest_span;
	} else {
	  bucket_bounds[fbi] = fblb;
	  biu++;
	  fbi = decrIndex(fbi);
	  buckets[fbi] = 0;
	  fblb -= adjusted_largest_span;
	  available_slots--;
	}
	adjusted_span_of_buckets -= adjusted_largest_span;
	adjusted_largest_span /= 2;
      }
      
      // We have run out of slots before we spanned the desired range.
      // Doubling the span of the last emitted bucket is guaranteed to
      // cover the entire span.  But we don't want to cause fblb to have
      // a negative value.
      //
      // Suppose, for example, that available_slots = 3, span_of_buckets
      // is 31, and overshoot is 7.  We will emit: 
      //
      //                8 16
      //
      // Will skip 4, 2, 1 which sum to overshoot value 7.  Suppose,
      // available_slots = 3, span_of_buckets is 63, overshoot is 13.
      // We emit:
      //
      //                  2 16 32
      //
      // We skip 8, 4, 1.  The real problem occurs if the overshoot is
      // part of the tail that has to be omitted.  For example, suppose
      // available_slots = 3, span_of_buckets is 63, overshoot is 5.
      // We emit:
      //
      //                  8 16 32
      //
      // and we're out of slots.  If I replace 8 with 16, I will cover
      // the intended span, including the overshoot, possibly resulting
      // in a negative value for fblb.  If I leave the span for this
      // slot at 8, I am 2 units too short, and I do not cover the
      // required cumulative range of spans.  I would like to put 10
      // into the position that holds 8, but my invariant says each span
      // is a power of 2.
      //
      // It is rare that this will come up.  If it does, we'll accept a
      // negative value of fblb.
      
    }

    if (adjusted_span_of_buckets > 0) {
      // We've run out of buckets before spanning the full desired
      // range.  We know that we are within spanAt(fbi) of fulfilling
      // the desired range, given that each neighbor's span is
      // typically twice the span of the node at its next lower index
      // (within the logarithmic spanning tree).
      //
      // But we cannot double the span of fbi unless its neighbor is
      // twice its own size.
      //
      // At this point, we must compress the data without adding new
      // buckets. We have two options:
      //
      //  1. if spanAt(incrIndex(fbi)) > spanAt(fbi), just double the
      //     span at fbi.
      //  2. else, spanAt(incrIndex(fbi)) == spanAt(fbi):
      //      Find N where spanAt(fbi) = spanAt(incrIndexBy(fbi, i))
      //      for all values of i <= N and (N == biu) or
      //      spanAt(incrIndexBy(fbi, N+1) > spanAt(increIndexBy(fbi, N).
      //      In this case, coalesce buckets at N and N-1.

      if (spanAt(incrIndex(fbi)) > spanAt(fbi)) {
	Trace.msg(4, id, ": adjusted_span_of_buckets > 0, " +
		  "spanAt(fbi) < spanAt(fbi+1)");
	fblb -= spanAt(fbi);
      } else {
	long each_span = spanAt(fbi);
	// By test above, there are at least two buckets with same span
	int count = 2;
	int index = incrIndex(fbi);
	while (count <= biu) {
	  int last_index = index;
	  index = incrIndex(index);
	  if (spanAt(index) != each_span) {
	    index = last_index;
	    break;
	  }
	  count++;
	}
	// count is number of consecutive buckets with same span (each_span).
	// index identifies position of last of these buckets.
	
	Trace.msg(4, id, ": adjusted_span_of_buckets > 0, ",
		  Integer.toString(count), " buckets have same span");

	assert (count > 1): "Expect multiple buckets to span same range";
	int left_index = decrIndex(index);
	// Double the span of the last of these buckets.
	buckets[index] += buckets[left_index];
	bucket_bounds[index] -= each_span;
	// Slide (count - 2) buckets forward.
	for (int i = (count -2); i > 0; i--) {
	  index = left_index;
	  left_index = decrIndex(left_index);
	  buckets[index] = buckets[left_index];
	  bucket_bounds[index] = bucket_bounds[left_index];
	}
	// Upon exiting loop, index points to bucket most recently
	// initialized.  left_index points to bucket from which this
	// most recently initialized bucket's tally was copied.
	// left_index is same as fbi.

	// Initialize first bucket's tally to zero and shift its start
	// range forward by each_span.
	bucket_bounds[index] = fblb;
	buckets[fbi] = 0;
	fblb -= each_span;
      }
    }

    Trace.msg(4, id, ": Done with doFold()");
    if (Trace.enabled(3))
      dump();
  }
  
  /* Insert a new bucket to log accumulation of value.  Assume value
   * is less than sis and less than lblb. */
  private void expandLowRange(long value) {

    Trace.msg(4, "");
    Trace.msg(4, id, ": expandLowRange (", Long.toString(value), ")");
    if (Trace.enabled(3))
      dump();

    value -= value % DefaultIntervalMicroseconds; // round value down

    // If the new value is at a great distance from the current fblb,
    // use a logarithmic tree to span the distance.
    long gap2fblb = fblb - value;
    if ((gap2fblb > (6 * DefaultIntervalMicroseconds)) &&
	(gap2fblb > (BucketCount - biu) * DefaultIntervalMicroseconds)) {
      doFold(gap2fblb);		// Build logarithmic tree to span gap
    }

    Trace.msg(4, id, ": in expandLowRange (), value: ", Long.toString(value),
	      ", fblb: ", Long.toString(fblb));

    if (value < fblb) {
      // new small bucket count
      int nsbc = (int) ((fblb - value) / DefaultIntervalMicroseconds);

      Trace.msg(4, id, ": nsbc: ", Long.toString(nsbc),
		", biu: ", Integer.toString(biu));

      while (nsbc + biu > BucketCount) {
	compress(nsbc);
	nsbc = (int) ((fblb - value) / DefaultIntervalMicroseconds);
      }
      while (value < fblb) {
	bucket_bounds[fbi] = fblb;
	fblb -= DefaultIntervalMicroseconds;
	biu++;
	fbi = decrIndex (fbi);
	buckets[fbi] = 0;
	// Do not overwrite bucket_bounds[fbi] for last iteration of
	// this loop as this slot holds the upper bound for last
	// bucket in the case that (biu == BucketCount).  If fbi does
	// not represent the last iteration, bucket_bounds[fbi] will
	// be overwritten at the top of this loop on its next
	// iteration. 
      }
    }

    Trace.msg(4, id, ":  after expandLowRange ()");
    if (Trace.enabled(3))
      dump();
  }

  /* Insert a new bucket to log accumulation of value.  */
  private void expandHighRange (long value) {

    Trace.msg(4, id, ": expandHighRange (", Long.toString(value), ")");

    if (spanAt (incrIndexBy (fbi, biu - 1)) == DefaultIntervalMicroseconds) {
      // No exponentially sized buckets yet.
      int new_linear_buckets = 1 + (int) ((value - lbhb)
					  / DefaultIntervalMicroseconds);
      Trace.msg(4, id, ": new linear buckets is ",
		Integer.toString(new_linear_buckets));
      if (biu + new_linear_buckets <= BucketCount) {
	// Make the new linear buckets and we're done.
	for (int i = 0; i < new_linear_buckets; i++) {
	  int expand_index = incrIndexBy (fbi, biu++);
	  buckets [expand_index] = 0;
	  bucket_bounds [expand_index] = lbhb;
	  lbhb += DefaultIntervalMicroseconds;
	}
	bucket_bounds [incrIndexBy (fbi, biu)] = lbhb;
	Trace.msg(4, id, ": after linear expandHighRange ()");

	if (Trace.enabled(3))
	  dump();
	
	return;
      }	// else, need to expand exponentially
    } 

    while (value >= lbhb) {
      if (biu >= 32)
	compress(1);

      if (biu >= 32)
	Util.internalError ("  expecting compress to leave biu < 32");
      
      if (value >= lbhb) {
	long next_exponential_size = spanAt (incrIndexBy (fbi, biu - 1)) * 2;
	int next_index = incrIndexBy (fbi, biu);
	buckets [next_index] = 0;
	lbhb += next_exponential_size;
	bucket_bounds [incrIndex (next_index)] = lbhb;
	biu++;

	Trace.msg(4, id, ": after compress, added slot @",
		  Integer.toString(next_index), " of size ",
		  debug_us2s(next_exponential_size));
	Trace.msg(4, id, ": biu is: ", Integer.toString(biu));
	
      }
    }

    Trace.msg(4, id, ":  after expandHighRange ()");
    if (Trace.enabled(3))    
      dump();
  }
    
  private final int incrIndex (int index) {
    return (index + 1) % BucketCount;
  }

  private final int incrIndexBy (int index, int by) {
    return (index + by) % BucketCount;
  }

  private final int decrIndex (int index) {
    index--;
    if (index < 0)
      index += BucketCount;
    return index;
  }

  // Assume by <= BucketCount 
  private final int decrIndexBy (int index, int by) {
    index -= by;
    while (index < 0)
      index += BucketCount;
    return index;
  }

  private final long spanAt (int index) {
    long lb = (index == fbi)? fblb: bucket_bounds[index];
    return bucket_bounds[incrIndex (index)] - lb;
  }

  private final long startAt (int index) {
    return (index == fbi)? fblb: bucket_bounds[index];
  }

  /* Adjust entries within the time-log to make the representation
   * more concise.  Require precondition that biu >= 2.  The
   * desired_slots argument is a hint that may be used by compress()
   * to select between alternative approaches.
   *
   * Each invocation of compress shall make, at minimum, one
   * additional bucket available to count tallies of events pertaining
   * to a new span of time values. 
   *
   * When tradeoffs are necessary, give preference to preserving
   * precision in the smaller-valued time spans.
   *
   * There are several techniques in existing practice:
   *
   * 1. Searching from largest to smallest time spans, look for a
   *    sequence of three or more buckets, each of which spans the
   *    same duration.  Coalesce the two chronologically later buckets
   *    into a new bucket which spans the cumulative range.  Repeat
   *    for as many consecutive sequences of three buckets with this
   *    same duration as are present.
   *
   *    For example, assume the array holds the following buckets before
   *    compression: 
   *
   *      32 entries, each spanning 1X
   *
   *    Following compression, the array will hold:
   *
   *      2 entries each spanning 1X, 15 entries each spanning 2X
   *
   *     Assume, before compression, the array holds:
   *
   *      2 entries each spanning 1X, 15 entries each spanning 2X
   *
   *     Following compression, it will hold:
   *
   *      2 entries each spanning 1X,
   *      1 entry spanning 2X,
   *      7 entries each spanning 4X
   *
   *     Suppose this array is further compressed.  The result will hold:
   *
   *      2 entries each spanning 1X,
   *      1 entry spanning 2X,
   *      1 entry spanning 4X,
   *      3 entries spanning 8X
   *
   *   Yet another compression would yield:
   *
   *      2 entries each spanning 1X,
   *      1 entry spanning 2X,
   *      1 entry spanning 4X,
   *      1 entry spanning 8X,
   *      1 entry spanning 16X
   *
   * 2. Coalesce consecutive pairs of buckets that have the same span.
   *    Work from larger-span buckets to shorter-span buckets,
   *    coalescing all consecutive pairs with a single pass through
   *    the existing log.
   *
   * 3. Pick an entry within the existing log.  Turn the next-smaller
   *    entry into an entry that spans the same range as its
   *    next-larger entry.  Since there are no doubles and no triples,
   *    this has the effect of subsuming all smaller entries into the
   *    newly enlarged span. 
   */
  private void compress(int desired_slots) {
    boolean coalesced_triples = false;

    Trace.msg(4, id, ": compress ()");
    if (Trace.enabled(3))
      dump();

    // First, try to coalesce triples
    for (int candidate_count = biu; candidate_count >= 3; ) {
      int candidate_index = incrIndexBy (fbi, candidate_count - 1);
      long candidate_span = spanAt (candidate_index);
      // See how many buckets have the same span
      int sscb = 1;		// same-sized consecutive buckets

      // neighbor_count is the number of buckets that precede the candidate
      int neighbor_count = candidate_count - 1;

      for (int neighbor_index = decrIndex (candidate_index);
	   (neighbor_count > 0) && (spanAt (neighbor_index) == candidate_span);
	   neighbor_index = decrIndex (neighbor_index)) {
	sscb++;
	neighbor_count--;
      }

      Trace.msgNoLine(4, id, ": candidate_count: ",
		      Integer.toString(candidate_count),
		      ", candidate_index: ", Integer.toString(candidate_index),
		      ", sscb: ", Integer.toString(sscb));
      
      Trace.msg(4, ", neighbor_count: ", Integer.toString(neighbor_count));

      if (sscb >= 3) {
	// Must leave at least one, possibly 2 of these uncoalesced.
	int coalesce_count = (sscb - 1) / 2;
	int coalesce_2nd_index = candidate_index;
	int coalesce_1st_index = decrIndex (coalesce_2nd_index);
	Trace.msg(4, id, ": coalescing ", Integer.toString(coalesce_count));
	for (int i = 0; i < coalesce_count; i++) {
	  // candidate end of span
	  long ceos = bucket_bounds[incrIndex(candidate_index)];
	  buckets[candidate_index] = (buckets[coalesce_1st_index] +
				      buckets[coalesce_2nd_index]);
	  bucket_bounds[candidate_index] = ceos - 2 * candidate_span;

	  Trace.msg(4, id, ": coalescing @",
		    Integer.toString(coalesce_1st_index),
		    " and @", Integer.toString(coalesce_2nd_index),
		    " onto @", Integer.toString(candidate_index));

	  candidate_index = decrIndex (candidate_index);
	  coalesce_1st_index = decrIndexBy (coalesce_1st_index, 2);
	  coalesce_2nd_index = decrIndexBy (coalesce_2nd_index, 2);
	}

	Trace.msg(4, id, ": sliding ",
		  Integer.toString(candidate_count - coalesce_count * 2),
		  " buckets forward");

	// We've removed coalesce_count buckets.  Slide everything forward.
	int source_index = decrIndexBy (candidate_index, coalesce_count);
	for (int i = candidate_count - coalesce_count * 2; i > 0; i--) {
	  long original_span = spanAt (source_index);
	  long ceos = bucket_bounds[incrIndex (candidate_index)];
	  buckets[candidate_index] = buckets[source_index];
	  bucket_bounds[candidate_index] = ceos - original_span;

	  Trace.msg(4, id, ":  sliding @", Integer.toString(source_index),
		    " onto @", Integer.toString(candidate_index));

	  source_index = decrIndex (source_index);
	  candidate_index = decrIndex (candidate_index);
	}
	biu -= coalesce_count;
	fbi = incrIndexBy (fbi, coalesce_count);
	coalesced_triples = true;
	break;			// we've done our compression
      } else
	candidate_count -= sscb;
    }

    if (!coalesced_triples) {
      // If we failed to find three consecutive buckets with the same
      // span, try this alternative strategy: coalesce consecutive
      // pairs of buckets that have the same span.  Work from
      // larger-span buckets to shorter-span buckets. 
      Trace.msg(4, id, ": coalescing of triples failed");

      int dest_index = incrIndexBy (fbi, biu - 1);
      int second_source_index = dest_index;
      int first_source_index = decrIndex (second_source_index);
      int coalesce_count = 0;
      int i;
      for (i = 0; i < biu - 1; i++) {

	Trace.msgNoLine(4, id, ": trying to coalesce @",
			Integer.toString(second_source_index),
			"(", Long.toString(spanAt (second_source_index)),
			") with @", Integer.toString(first_source_index));
	Trace.msg(4, "(", Long.toString(spanAt (first_source_index)), ")");

	if (spanAt (second_source_index) == spanAt(first_source_index)) {
	  buckets[dest_index] = (buckets[first_source_index]
				 + buckets[second_source_index]);

	  Trace.msg(4, id, ": coalescing @",
		    Integer.toString(first_source_index),
		    " and @", Integer.toString(second_source_index),
		    " onto @", Integer.toString(dest_index));

	  if (first_source_index != fbi)
	    bucket_bounds[dest_index] = bucket_bounds[first_source_index];
	  first_source_index = decrIndexBy (first_source_index, 2);
	  second_source_index = decrIndexBy (second_source_index, 2);
	  coalesce_count++;
	  i++;			// extra increment: coalesced two buckets
	} else {
	  if (second_source_index != dest_index) {
	    buckets[dest_index] = buckets[second_source_index];
	    // second_source_index known to != fbi
	    bucket_bounds[dest_index] = bucket_bounds[second_source_index];

	    Trace.msg(4, id, ":  sliding @",
		      Integer.toString(second_source_index),
		      " onto @", Integer.toString(dest_index));

	  } else
	    Trace.msg(4, id, ":  not sliding @",
		      Integer.toString(second_source_index),
		      " onto @", Integer.toString(dest_index));
	  second_source_index = first_source_index;
	  first_source_index = decrIndex (first_source_index);
	}
	dest_index = decrIndex (dest_index);
      }
      if (second_source_index == fbi)
	buckets[dest_index] = buckets[second_source_index];

      if (coalesce_count > 0) {
	biu -= coalesce_count;
	fbi = incrIndexBy (fbi, coalesce_count);
      } else {
	// Failed to coalesce triples or doubles.  Take more radical
	// action.  Pick an entry within the existing log.  Turn the
	// next-smaller entry into an entry that spans the same 
	// range as its next-larger entry.  Since there are no doubles
	// and no triples, this has the effect of subsuming all
	// smaller entries into the newly enlarged span.
	if (biu == 2) {
	  int last_index = incrIndex(fbi);
	  long last_span = spanAt(last_index);
	  fblb = lbhb - 2 * last_span;
	  buckets[last_index] += buckets[fbi];
	  fbi = incrIndex(fbi);
	  biu--;
	} else {
	  int available_slots = BucketCount - biu;
	  int discard_slots = desired_slots - available_slots;
	  if (discard_slots >= biu)
	    discard_slots = biu - 1;
	  // The discard_neighbor is the bucket that will hold the
	  // consolidation of all discarded slots.
	  int discard_neighbor_index = incrIndexBy(fbi, discard_slots);
	  int discard_index = decrIndex(discard_neighbor_index);
	  // Since we know there are no doubles and no triples, we are
	  // assured that we can double the span @ discard_neighbor_index
	  // without making this bucket's span larger than its next
	  // larger neighbor's span, and when we do double the size of
	  // this bucket, the enlarged bucket span will subsume all
	  // buckets at smaller index values.
	  for (int k = 0; k < discard_slots; k++) {
	    buckets[discard_neighbor_index] += buckets[discard_index];
	    discard_index = decrIndex(discard_index);
	  }
	  fblb = (bucket_bounds[discard_neighbor_index]
		  - spanAt(discard_neighbor_index));
	  fbi = discard_neighbor_index;
	  biu -= discard_slots;
	}
      }
    }

    Trace.msg(4, id, ": after compress ()");
    if (Trace.enabled(3))
      dump();
  }
  
  /**
   * This method, similar to us2s, is used only for debugging.  It
   * provides no memory usage accounting.  The reason for this method
   * in addition to the us2s method is to enable the compiler to more
   * easily optimize away invocations of debug_us2s in cases that the
   * returned String is dead.  It is more difficult for the compiler
   * to optimize away invocations of us2s because the compiler is not
   * allowed to alter the side effects associated with memory
   * accounting operations that are present in us2s.
   *
   * The result returned from this method is an Ephemeral String.
   *
   * The code that invokes toString is expected to account for the
   * returned String object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  static String debug_us2s(long us) {
    String result;
    String prefix;
    long quotient = 0;

    if (us == 0)
      return "0us";
    else if (us < 0) {
      us *= -1;
      prefix = "-";
    } else
      prefix = "";

    if (us > (3600 * 1000000L)) {	// more than 1 hour
      quotient = us / (3600 * 1000000L);
      us = us % (3600 * 1000000L);
      result = String.valueOf(quotient) + "h";
    } else
      result = "";
    if (us > (60 * 1000000L)) {	// more than 1 minute
      quotient = us / (60 * 1000000L);
      us = us % (60 * 1000000L);
      if (result.length() > 0)
	result += ":" + String.valueOf(quotient) + "m";
      else
	result += String.valueOf(quotient) + "m";
    }
    if ((us == 0) || (us > 1000000L)) {	// more than 1 s
      quotient = us / (1000000L);
      us = us % (1000000L);
      if (result.length() > 0)
	result += ":" + String.valueOf(quotient) + "s";
      else
	result += String.valueOf(quotient) + "s";
    }
    if (us > 1000L) {		// more than 1 ms
      quotient = us / 1000L;
      us = us % 1000L;
      if (result.length() > 0)
	result += ":" + String.valueOf(quotient) + "ms";
      else
	result += String.valueOf(quotient) + "ms";
    }
    if (us > 0) {
      if (result.length() > 0)
	result += ":" + String.valueOf(us) + "us";
      else
	result += String.valueOf(us) + "us";
    }
    return prefix + result;
  }

  /**
   * This method, used for reporting, returns an Ephemeral
   * String representation of this.
   *
   * The code that invokes toString is expected to account for the
   * returned String object's memory eventually becoming garbage,
   * possibly adjusting the accounting of its LifeSpan along the way
   * to becoming garbage. 
   */
  static String us2s (ExtrememThread t, long us) {
    String result;
    long quotient = 0;

    if (us == 0)
      return "0us";
    else if (us < 0) {
      us *= -1;
      result = "-";
    } else
      result = "";

    int sb_length = result.length();
    int sb_capacity = Util.ephemeralStringBuilder(t, sb_length);

    if (us > (3600 * 1000000L)) {	// more than 1 hour
      quotient = us / (3600 * 1000000L);
      us = us % (3600 * 1000000L);
      result += String.valueOf(quotient) + "h:";
      
      int digits = Util.decimalDigits(quotient);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if (us > (60 * 1000000L)) {	// more than 1 minute
      quotient = us / (60 * 1000000L);
      us = us % (60 * 1000000L);
      result += String.valueOf(quotient) + "m:";

      int digits = Util.decimalDigits(quotient);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if ((us == 0) || us > (1000000L)) {	// more than 1 s
      quotient = us / (1000000L);
      us = us % (1000000L);
      result += String.valueOf(quotient) + "s:";

      int digits = Util.decimalDigits(quotient);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if (us > 1000L) {		// more than 1 ms
      quotient = us / 1000L;
      us = us % 1000L;
      result += String.valueOf(quotient) + "ms:";

      int digits = Util.decimalDigits(quotient);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    if (us > 0) {
      result += String.valueOf(us) + "us:";

      int digits = Util.decimalDigits(us);
      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, digits);
      sb_length += digits;

      sb_capacity = Util.ephemeralStringBuilderAppend(t, sb_length,
						      sb_capacity, 2);
      sb_length += 2;
    }
    
    // Assume compiler optimizes this (so there's not a String
    // followed by a substring operation).
    Util.ephemeralStringBuilderToString(t, sb_length - 1, sb_capacity);
    return result.substring(0, result.length() - 1);
  }

  void addToReportTally(long[] histo_columns, long lb, long histo_span,
                        long sample_midpoint, int increment) {
    int bucket_index = (int) ((sample_midpoint - lb) / histo_span);
    histo_columns[bucket_index] += increment;
  }

  /* Accumulate bucket tallies into the reporting buckets represented
   * by histo_columns, where lb represents the low bound on time
   * spanned by the histogram (i.e. the time at which the first
   * histogram bucket begins) and histo_span is the amount of time
   * represented by each histogram bucket.
   *
   * lb and histo_span are multiples of 256.  histo_columns is known
   * to have HistoColumnCount (64) elements.  The entries of
   * histo_columns are not necessarily initialized to zero prior to
   * invocation of repack.
   */
  void repack(long[] histo_columns, long lb, long histo_span) {

    for (int i = 0; i < HistoColumnCount; i++)
      histo_columns[i] = 0;
    int index = fbi;
    for (int i = 0; i < biu; i++) {
      // model each existing bucket as a linear prog
      //  We're looking at bucket i, containing N entries
      //  Suppose 

      // Every data "bucket" has a hit_rate which is the total number
      // of samples divided by the number of 256-microsecond intervals
      // spanned by this bucket.

      // Since data buckets may not align exactly with report buckets,
      // we need a way to map between them.

      // Model each data bucket's distribution by assuming linear
      // progression of hit_rate throughout the interval of time
      // spanned by the bucket, with the hit_rate at the beginning of
      // the interval matching the preceding bucket's hit_rate and the
      // hit_rate at the end of the interval matching the hit_rate of
      // the interval that follows.

      // Use floating point y-intercept and slope to model each
      // data bucket's distribution.  y-intercept is relative to the
      // a time value of zero.  Thus, the y-intercept value may be
      // negative.

      // To compute how much of a particular data bucket pertains to a
      // particular reporting quantum, take the calculate the y-value
      // at the midpoint of the reporting quantum, rounding fractional
      // results to the nearest integer.

      // For complete "integrity", correct round-off errors by summing
      // the contributions over all reported quanta and adding or
      // subtracting to report totals, working from the center time of
      // the data bucket's time span outward.
      

      float right_rate, left_rate;

      if (buckets[index] > 0) {
        int segment_quanta;

        int bucket_tally = buckets[index];
        float my_slope;
        float my_intercept;
        long segment_start;
        if (i == 0) {             // use sis instead of region span
          long start_of_span = sis;
          int end_index = incrIndex(index);
          long end_of_span = bucket_bounds[end_index];
          segment_start = truncate256(start_of_span);

          // end_of_interval and segment_start are both multiples of 256
          segment_quanta = (int) ((end_of_span - segment_start) / 256);
          float this_rate = ((float) buckets[index]) / segment_quanta;
       
          left_rate = 0.0F;
          if (biu > 1) {
            int next_index = incrIndex(index);
            float right_tally = (float) buckets[next_index];
            right_rate = right_tally / (spanAt(next_index) / 256);
          } else
            right_rate = this_rate * 2;
          
          // Log the sis value within the first bucket now.
          // Spread the remaining tally values as appropriate.
          addToReportTally(histo_columns, lb, histo_span,
                           sis, 1);
          bucket_tally--;
        } else if (i + 1 == biu) {
          segment_start = bucket_bounds[index];
          long end_of_span = truncate256(lis);
          if (end_of_span < lis)
            end_of_span += 256;
          segment_quanta = (int) ((end_of_span - segment_start) / 256);
          float this_rate = ((float) buckets[index] / segment_quanta);
          
          if (biu > 1) {
            int prev_index = decrIndex(index);
            float left_tally = (float) buckets[prev_index];
            left_rate = left_tally / (spanAt(prev_index) / 256);
          } else
            left_rate = this_rate * 2;
          right_rate = 0.0F;
          
          if (lis > segment_start) {
            // Log the lis value within the last bucket now.
            // Spread the remaining tally values as appropriate.
            addToReportTally(histo_columns, lb, histo_span, lis, 1);
            bucket_tally--;
          }
          // expect my slope to be negative
        } else {
          // since this is not first and not last, we know it has neighbors
          segment_start = bucket_bounds[index];
          int next_index = incrIndex(index);
          int prev_index = decrIndex(index);

          long end_of_span = bucket_bounds[next_index];
          segment_quanta = (int) ((end_of_span - segment_start) / 256);
          float this_rate = ((float) buckets[index] / segment_quanta);
          
          float left_tally = (float) buckets[prev_index];
          left_rate = left_tally / (spanAt(prev_index) / 256);
          
          float right_tally = (float) buckets[next_index];
          right_rate = right_tally / (spanAt(next_index) / 256);
          
          if ((lis >= segment_start) && (lis < end_of_span))  {
            // Log the lis value within the last bucket now.
            // Spread the remaining tally values as appropriate.
            addToReportTally(histo_columns, lb, histo_span, lis, 1);
            bucket_tally--;
          }
        }
        
        my_slope = (right_rate - left_rate) / segment_quanta;
        float midpoint_time = segment_start + segment_quanta * 128;
        my_intercept =
        ((float) bucket_tally) / segment_quanta - my_slope * midpoint_time;
        
        if (bucket_tally < segment_quanta) {
          long midpoint;
          if (my_slope > 0.0) {      // fill in from high end
            int pad = segment_quanta - bucket_tally;
            midpoint = segment_start - 128 + pad * 256;
          } else if (my_slope < 0.0) { // fill in from low end
            midpoint = segment_start + 128;
          } else {              // fill form middle
            int pad = (segment_quanta - bucket_tally) / 2;
            midpoint = segment_start - 128 + pad * 256;
          }

          while (bucket_tally-- > 0) {
            addToReportTally(histo_columns, lb, histo_span, midpoint, 1);
            midpoint += 256;
          }
        } else {
          // But don't allow the rate to go negative
          if (my_intercept + (segment_start + 128) * my_slope < 0) {
            // form a triangle that slopes downward to the left, the
            // enclosing rectangle holding twice my tally.
            left_rate = 0.0F;
            right_rate = (2 * (float) bucket_tally) / segment_quanta;
            my_slope = (right_rate - left_rate) / segment_quanta;
            my_intercept =
            ((float) bucket_tally) / segment_quanta - my_slope * midpoint_time;
          } else if ((segment_start - 128 + 256 * segment_quanta) * my_slope
                     + my_intercept < 0) {
            // form a triangle that slopes downward to the right, the
            // enclosing rectangle holding twice my tally.
            left_rate = (2 * (float) bucket_tally) / segment_quanta;
            right_rate = 0.0F;
            my_slope = (right_rate - left_rate) / segment_quanta;
            my_intercept =
            ((float) bucket_tally) / segment_quanta - my_slope * midpoint_time;
          }

          for (int j = 0; j < segment_quanta; j++) {
            long quanta_midpoint = segment_start + 128 + 256 * j;
            int quanta_contribution = java.lang.Math.round(
              my_intercept + quanta_midpoint * my_slope);
            addToReportTally(histo_columns, lb, histo_span,
                             quanta_midpoint, quanta_contribution);
          }
        }
      }
      index = incrIndex(index);
    }
  }

  
  // Return greatest multiple of 256 that is less than or equal to val
  private final long truncate256(long val) {
    long multiple = val / 256;
    return multiple * 256;
  }

  // Return smallest multiple of 256 for which BucketCount (64) * this
  // multiple spans the range from low_bound to upper_bound
  private final long repackSpan(long low_bound, long upper_bound) {
    long total_span = upper_bound - low_bound;
    long proposed_span = truncate256(total_span / BucketCount);
    while (low_bound + HistoColumnCount * proposed_span < upper_bound)
      proposed_span += 256;
    return proposed_span;
  }

  void report(ExtrememThread t, boolean reportCSV) {

    String s;
    int l;

    if (total_entries == 0) {
      Report.output("Total measurements: 0");
      Report.output("  no further information available");
      return;
    }

    long median = 0;
    if (biu == 1) {
      // Very rough approximation
      median = lis + sis / 2;
    } else {
      // Calculate median assuming each logged data value
      // represents the middle of its respective range
      int entries_to_median = total_entries / 2;
      int index = fbi;
      for (int i = 0; i < biu; i++) {
	int bucket_count = buckets[index];
	long entry_value;
	
	if (sis > startAt(index))
	  entry_value = (sis + (startAt(index) + spanAt(index))) / 2;
	else if (lis < startAt(index) + spanAt(index))
	  // This test handles the case that lis is spanned by other than
	  // last bucket in use.
	  entry_value = (startAt(index) + lis) / 2;
	else
	  entry_value = startAt(index) + spanAt(index) / 2;

	entries_to_median -= bucket_count;
	if (entries_to_median <= 0) {
	  median = entry_value;
	  break;
	}
	index = incrIndex(index);
      }
    }

    long average = accumulated_microseconds / total_entries;
    if (Trace.enabled(1))
      Report.output("                  Table: ", id);
    // otherwise, reporting the table id has little relevance

    if (reportCSV)
      Report.output("Total Measurement,Min,Max,Mean,Approximate Median");

    s = Integer.toString(total_entries);
    l = s.length();
    Util.ephemeralString(t, l);
    if (reportCSV)
      Report.outputNoLine(s, ",");
    else
      Report.output("     Total measurements: ", s);
    Util.abandonEphemeralString(t, l);
      
    if (reportCSV) {
      s = Long.toString(sis);
      l = s.length();
      Util.ephemeralString(t, l);
      Report.outputNoLine(s, ",");
    } else {
      s = us2s(t, sis);
      l = s.length();
      Report.output("           ranging from: ", s);
    }
    Util.abandonEphemeralString(t, l);
      
    if (reportCSV) {
      s = Long.toString(lis);
      l = s.length();
      Util.ephemeralString(t, l);
      Report.outputNoLine(s, ",");
    } else {
      s = us2s(t, lis);
      l = s.length();
      Report.output("                     to: ", s);
    }
    Util.abandonEphemeralString(t, l);
      
    if (reportCSV) {
      s = Long.toString(average);
      l = s.length();
      Util.ephemeralString(t, l);
      Report.outputNoLine(s, ",");
    } else {
      s = us2s(t, average);
      l = s.length();
      Report.output("        average latency: ", s);
    }
    Util.abandonEphemeralString(t, l);

    if (reportCSV) {
      s = Long.toString(median);
      l = s.length();
      Util.ephemeralString(t, l);
      Report.output(s);
    } else {
      s = us2s(t, median);
      l = s.length();
      Report.output("(approx) median latency: ", s);
    }
    Util.abandonEphemeralString(t, l);

    if (reportCSV) {
      s = Integer.toString(biu);
      Util.ephemeralString(t, s.length());
      Report.output("Buckets in use,", s);
      Util.abandonEphemeralString(t, s.length());
      
      Report.output("Bucket Start,Bucket End, Bucket Tally");
      int index = fbi;
      for (int i = 0; i < biu; i++) {
	String s1 = Long.toString(startAt(index));
	String s2 = Long.toString(startAt(index) + spanAt(index));
	String s3 = Integer.toString(buckets[index]);

	Util.ephemeralString(t, s1.length());
	Util.ephemeralString(t, s2.length());
	Util.ephemeralString(t, s3.length());

	Report.output(s1, ",", s2, ",", s3);

	Util.abandonEphemeralString(t, s1.length());
	Util.abandonEphemeralString(t, s2.length());
	Util.abandonEphemeralString(t, s3.length());

	index++;
	if (index >= BucketCount)
	  index = 0;
      }
    } else {

      // Make a histogram with 64 equal-sized buckets
      long histo_columns[] = new long[HistoColumnCount];

      Trace.msg(4, id, ":Preparing histogram for ",
		Integer.toString(total_entries));
      Trace.msg(4, id, ":           ranging from: ", debug_us2s(sis));
      Trace.msg(4, id, ":                     to: ", debug_us2s(lis));
      Trace.msg(4, id, ":            from (fblb): ", debug_us2s(fblb));
      Trace.msg(4, id, ":              to (lbhb): ", debug_us2s(lbhb));

      long repack_lb = truncate256(sis);
      long repack_bucket_span = repackSpan(repack_lb, lis);
      repack(histo_columns, repack_lb, repack_bucket_span);

      int max_histo_size = 0;
      for (int i = 0; i < HistoColumnCount; i++) {
        if (histo_columns[i] > max_histo_size)
          max_histo_size = (int) histo_columns[i];
      }
      int num_rows = 0;
      int two_to_rows = 1;
      while (two_to_rows < max_histo_size) {
	num_rows++;
	two_to_rows += two_to_rows;
      }
      Report.output();
      Report.output("Logarithmic histogram (Each column's stars represent ",
		    "a binary tally)");

      // all values of seen_star initially false
      boolean[] seen_star = new boolean[HistoColumnCount];
      while (num_rows >= 0) {
        String histo_count = Integer.toString(two_to_rows);
        int pad = 7 - histo_count.length();
        for (int i = 0; i < pad; i++)
          Report.outputNoLine(" ");
        Report.outputNoLine(histo_count);
        Report.outputNoLine(" ");
	for (int col = 0; col < HistoColumnCount; col++) {
	  if (two_to_rows <= histo_columns[col]) {
	    Report.outputNoLine("*");
            seen_star[col] = true;
            histo_columns[col] -= two_to_rows;
          }
	  else if (seen_star[col])
	    Report.outputNoLine("0");
          else
	    Report.outputNoLine(" ");
	}
	Report.output();
	two_to_rows /= 2;
	num_rows--;
      }
      Report.outputNoLine("        ");
      Report.output(
	"----------------------------------------------------------------");
      Report.outputNoLine("        ");
      Report.output(
	"^               ^               ^               ^              ^");
      int available_columns = PageColumns - 3 * (HistoColumnCount / 4);
      String last_label = us2s(t, (repack_lb +
                                   HistoColumnCount * repack_bucket_span));
      int last_label_length = last_label.length();
      int pad_columns = (available_columns - last_label_length) / 2;
      if (pad_columns < 0)
	pad_columns = 0;
      Report.outputNoLine("        ");
      Report.outputNoLine(
	"|               |               |               |");
      for (int i = 0; i < pad_columns; i++)
	Report.outputNoLine(" ");
      Report.output(last_label);
      Util.abandonEphemeralString(t, last_label_length);
      
      s = us2s(t, repack_lb + HistoColumnCount * repack_bucket_span *  3 / 4);
      l = s.length();
      Report.outputNoLine("        ");
      Report.output(
	"|               |               |               +--- ", s);
      Util.abandonEphemeralString(t, l);
      
      s = us2s(t, repack_lb + HistoColumnCount * repack_bucket_span / 2); 
      l = s.length();
      Report.outputNoLine("        ");
      Report.output(
	"|               |               +--- ", s);
      Util.abandonEphemeralString(t, l);
      
      s = us2s(t, repack_lb + HistoColumnCount * repack_bucket_span / 4); 
      l = s.length();
      Report.outputNoLine("        ");
      Report.output(
	"|               +--- ", s);
      Util.abandonEphemeralString(t, l);
      
      s = us2s(t, repack_lb);
      l = s.length();
      Report.outputNoLine("        ");
      Report.output(
	"+--- ", s);
      Util.abandonEphemeralString(t, l);
    }
  }

  void dumpDebug() {
    // Output diagnostic information about data structure to stderr
    Trace.debug(id, ": RelativeTimeMetrics, Count: ",
		Integer.toString(BucketCount), ", DefaultInterval: ",
		Long.toString(DefaultIntervalMicroseconds));
    Trace.debug(id, ":  first bucket low bound: ", debug_us2s (fblb));
    Trace.debug(id, ":  last bucket high bound: ", debug_us2s (lbhb));
    Trace.debug(id, ":  first bucket index (in use): ",
		Integer.toString(fbi), " (", Integer.toString(biu), ")");
    Trace.debug(id, ":  smallest interval seen: ", Long.toString(sis));
    Trace.debug(id, ":  largest interval seen: ", Long.toString(lis));
    int index = fbi;
    int total_tallies = 0;
    for (int i = 0; i < biu; i++) {
      Trace.debug(id, ":    tally for range [", Integer.toString(index),
		  "] starting at ",
		  debug_us2s((i == 0)? fblb: bucket_bounds[index]),
		  " spanning ", debug_us2s(spanAt (index)),
		  ": ", String.valueOf (buckets[index]));
      total_tallies += buckets[index];
      index = incrIndex (index);
    }
    Trace.debug(id, ": Total measurements: " + total_tallies);
  }

  // dump is used for debugging
  void dump() {
    // Output diagnostic information about data structure to stderr
    Trace.msg(4, id, ": RelativeTimeMetrics, Count: ",
	      Integer.toString(BucketCount), ", DefaultInterval: ",
	      Long.toString(DefaultIntervalMicroseconds));
    Trace.msg(4, id, ":  first bucket low bound: ", debug_us2s (fblb));
    Trace.msg(4, id, ":  last bucket high bound: ", debug_us2s (lbhb));
    Trace.msg(4, id, ":  first bucket index (in use): ",
	      Integer.toString(fbi), " (", Integer.toString(biu), ")");
    Trace.msg(4, id, ":  smallest interval seen: ", Long.toString(sis));
    Trace.msg(4, id, ":  largest interval seen: ", Long.toString(lis));
    int index = fbi;
    int total_tallies = 0;
    for (int i = 0; i < biu; i++) {
      Trace.msgNoLine(4, id, ":    tally for range [", Integer.toString(index),
		      "] starting at ",
		      debug_us2s((i == 0)? fblb: bucket_bounds[index]),
		      " spanning ", debug_us2s(spanAt (index)));
      Trace.msg(4, ": ", String.valueOf (buckets[index]));
      total_tallies += buckets[index];
      index = incrIndex (index);
    }
    Trace.msg(4, id, ": Total measurements: " + total_tallies);
  }

  // This main method is only present for the purpose of enabling
  // stand-along testing of this data type.
  public static void main(String args[]) {
    Configuration config = new Configuration(args);
    Bootstrap t = new Bootstrap(config, 0);
    RelativeTimeMetrics rtm = new RelativeTimeMetrics(t, LifeSpan.Ephemeral);
    rtm.test();
  }

  void test() {
    // Repeatedly, insert values into data structure and dump.

    Trace.debug(id, ": @ Start of testing");
    dumpDebug();

    /* do 512 entries in linear * 250 span starting at 80 ms, spans 128 ms */
    long timestamp = 80 * 1000;
    for (int i = 0; i < 512; i++) {
      addToLog(timestamp);
      timestamp += 250;
    }

    Trace.debug(id, ":@ Added 512 entries from 80 ms to 208 ms");
    dumpDebug();
    /* Expect:
     * RTM_0:@ Added 512 entries from 80 ms to 208 ms
     * RTM_0: RelativeTimeMetrics, Count: 32, DefaultInterval: 256 
     * RTM_0:  first bucket low bound: 79ms:872us 
     * RTM_0:  last bucket high bound: 218ms:112us
     * RTM_0:  first bucket index (in use): 15 (24)
     * RTM_0:  smallest interval seen: 80000
     * RTM_0:  largest interval seen: 207750
     * RTM_0:    tally for range [15] starting at 79ms:872us spanning 256us: 1
     * RTM_0:    tally for range [16] starting at 80ms:128us spanning 256us: 1
     * RTM_0:    tally for range [17] starting at 80ms:384us spanning 512us: 2
     * RTM_0:    tally for range [18] starting at 80ms:896us spanning 512us: 2
     * RTM_0:    tally for range [19] starting at 81ms:408us spanning 512us: 2
     * RTM_0:    tally for range [20] starting at 81ms:920us spanning 512us: 2
     * RTM_0:    tally for range [21] starting at 82ms:432us spanning 512us: 2
     * RTM_0:    tally for range [22] starting at 82ms:944us spanning 512us: 2
     * RTM_0:    tally for range [23] starting at 83ms:456us spanning 512us: 2
     * RTM_0:    tally for range [24] starting at 83ms:968us spanning 512us: 2
     * RTM_0:    tally for range [25] starting at 84ms:480us spanning 512us: 2
     * RTM_0:    tally for range [26] starting at 84ms:992us spanning 512us: 3
     * RTM_0:    tally for range [27] starting at 85ms:504us spanning 512us: 2
     * RTM_0:    tally for range [28] starting at 86ms:16us spanning 512us: 2
     * RTM_0:    tally for range [29] starting at 86ms:528us spanning 512us: 2
     * RTM_0:    tally for range [30] starting at 87ms:40us spanning 512us: 2
     * RTM_0:    tally for range [31] starting at 87ms:552us spanning 512us: 2
     * RTM_0:    tally for range [0] starting at 88ms:64us spanning 1ms:24us: 4
     * RTM_0:    tally for range [1] starting at 89ms:88us spanning 2ms:48us: 8
     * RTM_0:    tally for range [2] starting at 91ms:136us\
     *                                                  spanning 4ms:96us: 16
     * RTM_0:    tally for range [3] starting at 95ms:232us\
     *                                                 spanning 8ms:192us: 33
     * RTM_0:    tally for range [4] starting at 103ms:424us\
     *                                                 spanning 16ms:384us: 66
     * RTM_0:    tally for range [5] starting at 119ms:808us\
     *                                                 spanning 32ms:768us: 131
     * RTM_0:    tally for range [6] starting at 152ms:576us\
     *                                                 spanning 65ms:536us: 221
     * RTM_0: Total measurements: 512                  
     */

    /* Now add 512 linear entries in descending sequence * 25,
     * starting at 80 ms, spans 12.8 ms.  */
    timestamp = 80 * 1000 - 25;
    for (int i = 0; i < 512; i++) {
      addToLog(timestamp);
      timestamp -= 25;
    }

    Trace.debug(id, ":@ Added 512 entries from 67.2 ms to 80 ms");
    dumpDebug();

    /* Expect:
     * RTM_0:@ Added 512 entries from 67.2 ms to 80 ms
     * RTM_0: RelativeTimeMetrics, Count: 32, DefaultInterval: 256
     * RTM_0:  first bucket low bound: 67ms:72us
     * RTM_0:  last bucket high bound: 218ms:112us
     * RTM_0:  first bucket index (in use): 7 (32)
     * RTM_0:  smallest interval seen: 67200
     * RTM_0:  largest interval seen: 207750
     * RTM_0:    tally for range [7] starting at 67ms:72us spanning 256us: 6
     * RTM_0:    tally for range [8] starting at 67ms:328us spanning 256us: 10
     * RTM_0:    tally for range [9] starting at 67ms:584us spanning 256us: 10
     * RTM_0:    tally for range [10] starting at 67ms:840us spanning 256us: 10
     * RTM_0:    tally for range [11] starting at 68ms:96us spanning 256us: 11
     * RTM_0:    tally for range [12] starting at 68ms:352us spanning 256us: 10
     * RTM_0:    tally for range [13] starting at 68ms:608us spanning 256us: 10
     * RTM_0:    tally for range [14] starting at 68ms:864us spanning 256us: 10
     * RTM_0:    tally for range [15] starting at 69ms:120us spanning 256us: 11
     * RTM_0:    tally for range [16] starting at 69ms:376us spanning 256us: 10
     * RTM_0:    tally for range [17] starting at 69ms:632us spanning 256us: 10
     * RTM_0:    tally for range [18] starting at 69ms:888us spanning 256us: 10
     * RTM_0:    tally for range [19] starting at 70ms:144us spanning 512us: 21
     * RTM_0:    tally for range [20] starting at 70ms:656us spanning 512us: 20
     * RTM_0:    tally for range [21] starting at 71ms:168us spanning 512us: 21
     * RTM_0:    tally for range [22] starting at 71ms:680us spanning 512us: 20
     * RTM_0:    tally for range [23] starting at 72ms:192us spanning 512us: 21
     * RTM_0:    tally for range [24] starting at 72ms:704us spanning 512us: 20
     * RTM_0:    tally for range [25] starting at 73ms:216us spanning 512us: 21
     * RTM_0:    tally for range [26] starting at 73ms:728us spanning 512us: 20
     * RTM_0:    tally for range [27] starting at 74ms:240us spanning 512us: 21
     * RTM_0:    tally for range [28] starting at 74ms:752us spanning 512us: 20
     * RTM_0:    tally for range [29] starting at 75ms:264us spanning 512us: 21
     * RTM_0:    tally for range [30] starting at 75ms:776us \
     *                                                  spanning 1ms:24us: 40
     * RTM_0:    tally for range [31] starting at 76ms:800us \
     *                                                  spanning 2ms:48us: 82
     * RTM_0:    tally for range [0] starting at 78ms:848us \
     *                                                  spanning 4ms:96us: 58
     * RTM_0:    tally for range [1] starting at 82ms:944us \
     *                                                  spanning 4ms:96us: 17
     * RTM_0:    tally for range [2] starting at 87ms:40us \
     *                                                 spanning 8ms:192us: 32
     * RTM_0:    tally for range [3] starting at 95ms:232us \
     *                                                 spanning 8ms:192us: 33
     * RTM_0:    tally for range [4] starting at 103ms:424us \
     *                                                spanning 16ms:384us: 66
     * RTM_0:    tally for range [5] starting at 119ms:808us \
     *                                                spanning 32ms:768us: 131
     * RTM_0:    tally for range [6] starting at 152ms:576us \
     *                                                spanning 65ms:536us: 221
     * RTM_0: Total measurements: 1024
     */
    
    /* Add some longer time spans, starting at 500 ms */
    timestamp = 500 * 1000;
    for (int i = 0; i < 8; i++) {
      addToLog(timestamp);
      timestamp += timestamp;
    }

    Trace.debug(id, ":@ Added 8 entries: 0.5s, 1s, 2s 4s, 8s, 16s, 32s, 64s");
    dumpDebug();

    /* Note that we shift the point at which we begin using larger
     * spans to an earlier index value.
     *
     * Expect:
     * RTM_0:@ Added 8 entries: 0.5s, 1s, 2s 4s, 8s, 16s, 32s, 64s
     * RTM_0: RelativeTimeMetrics, Count: 32, DefaultInterval: 256
     * RTM_0:  first bucket low bound: 67ms:72us
     * RTM_0:  last bucket high bound: 1m:7s:195ms:904us
     * RTM_0:  first bucket index (in use): 16 (32)
     * RTM_0:  smallest interval seen: 67200
     * RTM_0:  largest interval seen: 64000000
     * RTM_0:    tally for range [16] starting at 67ms:72us spanning 256us: 6
     * RTM_0:    tally for range [17] starting at 67ms:328us spanning 256us: 10
     * RTM_0:    tally for range [18] starting at 67ms:584us spanning 256us: 10
     * RTM_0:    tally for range [19] starting at 67ms:840us spanning 256us: 10
     * RTM_0:    tally for range [20] starting at 68ms:96us spanning 256us: 11
     * RTM_0:    tally for range [21] starting at 68ms:352us spanning 256us: 10
     * RTM_0:    tally for range [22] starting at 68ms:608us spanning 256us: 10
     * RTM_0:    tally for range [23] starting at 68ms:864us spanning 256us: 10
     * RTM_0:    tally for range [24] starting at 69ms:120us spanning 256us: 11
     * RTM_0:    tally for range [25] starting at 69ms:376us spanning 256us: 10
     * RTM_0:    tally for range [26] starting at 69ms:632us spanning 256us: 10
     * RTM_0:    tally for range [27] starting at 69ms:888us spanning 256us: 10
     * RTM_0:    tally for range [28] starting at 70ms:144us spanning 512us: 21
     * RTM_0:    tally for range [29] starting at 70ms:656us \
     *                                                   spanning 1ms:24us: 41
     * RTM_0:    tally for range [30] starting at 71ms:680us \
     *                                                   spanning 1ms:24us: 41
     * RTM_0:    tally for range [31] starting at 72ms:704us \
     *                                                   spanning 2ms:48us: 82
     * RTM_0:    tally for range [0] starting at 74ms:752us \
     *                                                   spanning 4ms:96us: 163
     * RTM_0:    tally for range [1] starting at 78ms:848us \
     *                                                  spanning 8ms:192us: 75
     * RTM_0:    tally for range [2] starting at 87ms:40us \
     *                                                  spanning 8ms:192us: 32
     * RTM_0:    tally for range [3] starting at 95ms:232us \
     *                                                  spanning 8ms:192us: 33
     * RTM_0:    tally for range [4] starting at 103ms:424us \
     *                                                 spanning 16ms:384us: 66
     * RTM_0:    tally for range [5] starting at 119ms:808us \
     *                                                 spanning 32ms:768us: 131
     * RTM_0:    tally for range [6] starting at 152ms:576us \
     *                                                 spanning 65ms:536us: 221
     * RTM_0:    tally for range [7] starting at 218ms:112us \
     *                                                 spanning 131ms:72us: 0
     * RTM_0:    tally for range [8] starting at 349ms:184us \
     *                                                spanning 262ms:144us: 1
     * RTM_0:    tally for range [9] starting at 611ms:328us \
     *                                                spanning 524ms:288us: 1
     * RTM_0:    tally for range [10] starting at 1s:135ms:616us \
     *                                              spanning 1s:48ms:576us: 1
     * RTM_0:    tally for range [11] starting at 2s:184ms:192us \
     *                                              spanning 2s:97ms:152us: 1
     * RTM_0:    tally for range [12] starting at 4s:281ms:344us \
     *                                             spanning 4s:194ms:304us: 1
     * RTM_0:    tally for range [13] starting at 8s:475ms:648us \
     *                                             spanning 8s:388ms:608us: 1
     * RTM_0:    tally for range [14] starting at 16s:864ms:256us \
     *                                            spanning 16s:777ms:216us: 1
     * RTM_0:    tally for range [15] starting at 33s:641ms:472us \
     *                                            spanning 33s:554ms:432us: 1
     * RTM_0: Total measurements: 1032
    */       

    /* Now add some fine detail with short time spans: 500 to 1000 us. */
    timestamp = 500;
    for (int i = 0; i < 50; i++) {
      addToLog(timestamp);
      timestamp += 10;
    }

    Trace.debug(id, ":@ Added 50 entries from 500 us to 1 ms");
    dumpDebug();

    /* Note that we have to coalesce buckets for long time spans in
     * order to improve precision of tabulations at low end.
     *
     * Expect:
     * RTM_0:@ Added 50 entries from 500 us to 1 ms
     * RTM_0: RelativeTimeMetrics, Count: 32, DefaultInterval: 256
     * RTM_0:  first bucket low bound: 0us
     * RTM_0:  last bucket high bound: 1m:7s:195ms:904us
     * RTM_0:  first bucket index (in use): 2 (14)
     * RTM_0:  smallest interval seen: 500
     * RTM_0:  largest interval seen: 64000000
     * RTM_0:    tally for range [2] starting at 0us spanning 1ms:24us: 50
     * RTM_0:    tally for range [3] starting at 1ms:24us spanning 4ms:96us: 0
     * RTM_0:    tally for range [4] starting at 5ms:120us \
     *                                                spanning 16ms:384us: 0
     * RTM_0:    tally for range [5] starting at 21ms:504us \
     *                                                spanning 65ms:536us: 541
     * RTM_0:    tally for range [6] starting at 87ms:40us \
     *                                                spanning 131ms:72us: 483
     * RTM_0:    tally for range [7] starting at 218ms:112us \
     *                                                spanning 131ms:72us: 0
     * RTM_0:    tally for range [8] starting at 349ms:184us \
     *                                               spanning 262ms:144us: 1
     * RTM_0:    tally for range [9] starting at 611ms:328us \
     *                                               spanning 524ms:288us: 1
     * RTM_0:    tally for range [10] starting at 1s:135ms:616us \
     *                                             spanning 1s:48ms:576us: 1
     * RTM_0:    tally for range [11] starting at 2s:184ms:192us \
     *                                             spanning 2s:97ms:152us: 1
     * RTM_0:    tally for range [12] starting at 4s:281ms:344us \
     *                                            spanning 4s:194ms:304us: 1
     * RTM_0:    tally for range [13] starting at 8s:475ms:648us \
     *                                            spanning 8s:388ms:608us: 1
     * RTM_0:    tally for range [14] starting at 16s:864ms:256us \
     *                                           spanning 16s:777ms:216us: 1
     * RTM_0:    tally for range [15] starting at 33s:641ms:472us \
     *                                           spanning 33s:554ms:432us: 1
     * RTM_0: Total measurements: 1082
     */
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);

    // Account for 3 reference fields: buckets, bucket_bounds, id.
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 3);

    // Account for 3 int fields: fbi, biu, total_entries; and 5 long
    // fields: fblb, lbhb, sis, lis, accumulated_microseconds
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
		   3 * Util.SizeOfInt + 5 * Util.SizeOfLong);

    // Account for buckets and bucket_bounds arrays.
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, 2);
    log.accumulate(ls, MemoryFlavor.ArrayRSB, p,
		   BucketCount * (Util.SizeOfInt + Util.SizeOfLong));
  }
}
