// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

// The Extremem workload distinguishes between five distinct classes
// of object lifetimes.  Except for the Ephemeral life time, which
// typically live significantly shorter than 5 microseconds, the
// duration represented by each symbolically named life span is
// determined by configuration parameters.
//
// Using configuration parameters to control the duration of each
// symbolic life span and the number of objects instantiated with each
// distinct life span allows Extremem to mimic the behavior of various
// real-world applications and services.

enum LifeSpan {
  // Very short-lived data, within function activation or one
  // iteration of loop.  Typical ephemeral objects live less than 5
  // microseconds. 
  Ephemeral,
  // Temporary data preserved while a customer is "thinking".  Typical
  // TransientShort objects live less than 2 minutes.
  TransientShort,
  // Purchases that must be processed.  Typical TransientIntermediate
  // objects live less than 5 minutes.
  TransientIntermediate,
  // Products saved for later.  Typical TransientLingering objects
  // live less than 30 minutes.
  TransientLingering,
  // Dictionary words, Customers, Products.  In typical
  // configurations, NearlyForever objects live from the start to the
  // end of the simulated workload, with fewer than 1% of these
  // objects becoming garbage.  Configuration options allow control
  // over the percentage of NearlyForever objects that become eligible
  // for garbage collection.
  NearlyForever;

  final static int OrdinalCount = NearlyForever.ordinal () + 1;
}
