// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

/**
 * Polarity distinguishes between expanding and shrinking a memory
 * log.
 *
 * In some cases, objects that are allocated under the presumption of
 *  having very short life spans (e.g. Ephemeral) are subsequently
 *  determined to have longer life span (e.g. TransientLingering).
 *  When objects are so transitioned, we remove (Shrink) the object's
 *  memory usage from the tally for the Ephemeral tally and add (Expand)
 *  it to the TransientLingering tally.
 */
enum Polarity {
  Expand,		       	// Add to tally
  Shrink;		   	// Subtract from tally

  final static int OrdinalCount = Shrink.ordinal () + 1;
}