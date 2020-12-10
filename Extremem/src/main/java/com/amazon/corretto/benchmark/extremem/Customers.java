// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.util.HashMap;

/**
 * Keep track of all currently active customers.
 */
class Customers extends ExtrememObject {
  static final float DefaultLoadFactor = 0.75f;

  final private ConcurrencyControl cc;
  final private Configuration config;
  // was final private String[] customer_names;
  final private Arraylet<String> customer_names;
  final private HashMap<String, Customer> customer_map;

  private int cbhs = 0;         // cumulative browsing history size.

  private long cncl;            // customer name cumulative length
  private long next_customer_no = 0;

  Customers(ExtrememThread t, LifeSpan ls, Configuration config) {
    super(t, ls);
    int num_customers = config.NumCustomers();
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;

    // Account for cc, config, customer_names, customer_map
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 4);
    // Account for long cncl, next_customer_no; int cbhs
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow,
                   2 * Util.SizeOfLong + Util.SizeOfInt);

    this.cc = new ConcurrencyControl(t, ls);
    this.config = config;

    // The String instances stored in customer_names are the exact
    // same String instances that serve as names for each of the
    // embedded Customer objects.
    this.customer_names = new Arraylet<String>(t, ls, config.MaxArrayLength(),
                                               num_customers);

    int capacity = Util.computeHashCapacity(num_customers,
                                            DefaultLoadFactor,
                                            Util.InitialHashMapArraySize);
    this.customer_map = new HashMap<String, Customer>(capacity,
                                                      DefaultLoadFactor);
    // Account for the HashMap referenced by customer_map.
    Util.tallyHashMap(log, ls, Grow, config.NumCustomers(), DefaultLoadFactor);

    cncl = 0;
    for (int i = 0; i < num_customers; i++) {
      String name = randomDistinctName(t);
      int name_length = name.length();
      Util.convertEphemeralString(t, ls, name_length);
      this.customer_names.set(i, name);
      Trace.msg(4, "customer_names[", Integer.toString(i), "] is ", name);
      Customer customer = new Customer(t, ls, name, next_customer_no++);
      cbhs += customer.browsingHistorySize();
      cncl += name_length;
      customer_map.put(name, customer);
    }
  }

  private synchronized int adjust_cbhs(int delta) {
    int orig = cbhs;
    cbhs += delta;
    return cbhs;
  }

  /* Return an ephemeral String with first character of input replaced with an
   *  upper case version of itself.  */
  private static String capitalize(ExtrememThread t, String input) {
    int length = input.length();
    Util.ephemeralString(t, length);
    return Character.toUpperCase(input.charAt(0)) + input.substring(1);
  }

  // The thread that invokes this method already holds a read or
  // write lock, if necessary.
  private String randomDistinctName (ExtrememThread t) {
    String full_name;
    final Polarity Grow = Polarity.Expand;
    do {
      // Potentially infinite loop terminates as soon as we randomly
      // generate a name that is not already in the Customer data
      // base.  Will normally not require more than a single pass, but
      // having a "very small" dictionary and a very large number of
      // customers could cause excessive iteration here.

      String first = capitalize(t, config.arbitraryWord(t));
      int first_len = first.length();
      String last = capitalize(t, config.arbitraryWord(t));
      int last_len = last.length();

      // ignore the StringBuilder allocations.  Assume optimized out.
      full_name = first + " " + last;
      int capacity = Util.ephemeralStringBuilder(t, first_len);
      capacity = Util.ephemeralStringBuilderAppend(t, first_len, capacity, 1);
      capacity = Util.ephemeralStringBuilderAppend(t, first_len, capacity,
                                                   last_len);
      int full_name_length = first_len + last_len + 1;
      Util.ephemeralStringBuilderToString(t, full_name_length, capacity);
      Util.abandonEphemeralString(t, first_len);
      Util.abandonEphemeralString(t, last_len);

      if (customer_map.get(full_name) == null) {
        // Caller will convert returned String from Ephemeral
        return full_name;
      } else
        Util.abandonEphemeralString(t, full_name_length);
    } while (true);
  }

  Customer selectRandomCustomer(ExtrememThread t) {
    RandomSelector rs = new RandomSelector(t, LifeSpan.Ephemeral, this);
    cc.actAsReader(rs);
    Customer result = rs.one;
    rs.garbageFootprint(t);
    return result;
  }
  
  Customer controlledSelectRandomCustomer(ExtrememThread t) {
    int index = t.randomUnsignedInt() % config.NumCustomers();
    String name = customer_names.get(index);
    Customer c = customer_map.get(name);
    return c;
  }

  void replaceRandomCustomer(ExtrememThread t) {
    RandomReplacer rr = new RandomReplacer(t, LifeSpan.Ephemeral, this);
    cc.actAsWriter(rr);
    rr.garbageFootprint(t);
  }

  // Returns previous value.
  private synchronized long adjust_cncl(int increment) {
    long previous = cncl;
    cncl += increment;
    return previous;
  }

  void addSaveForLater(Customer c, ExtrememThread t, BrowsingHistory h) {
    int bqes_delta = c.addSaveForLater(t, h);
    adjust_cbhs(bqes_delta);
  }

  void controlledReplaceRandomCustomer(ExtrememThread t) {
    int index = t.randomUnsignedInt() % config.NumCustomers();

    String new_customer_name = randomDistinctName(t);
    int new_customer_length = new_customer_name.length();
    MemoryLog log = t.memoryLog();
    Util.convertEphemeralString(t, this.intendedLifeSpan(),
                                new_customer_length);
    adjust_cncl(new_customer_length);

    String name = customer_names.get(index);
    Customer obsolete_customer = customer_map.get(name);

    Trace.msg(4, "controlledReplaceRandomCustomer()");
    Trace.msg(4, "  index is ", Integer.toString(index));
    Trace.msg(4, "   name is ", name);
    Trace.msg(4, " obsolete_customer has id ",
              Long.toString(obsolete_customer.id()));
    Trace.msg(4, "  name ", obsolete_customer.name());

    // Give the decommissioned customer opportunity to unhook
    // saved-for-later products.
    adjust_cbhs(-obsolete_customer.prepareForDemise(t));
    String obsolete_customer_name = obsolete_customer.name();
    int obsolete_len = obsolete_customer_name.length();
    Util.abandonNonEphemeralString(t, this.intendedLifeSpan(), obsolete_len);
    adjust_cncl(-obsolete_len);
    // The obsolete_customer is not garbage collected until it is no
    // longer referenced from any pending sales transactions.  We'll
    // account for its garbage here rather than adding logic to
    // reclaim Customer memory immediately following particular sales
    // transactions.
    obsolete_customer.garbageFootprint(t);

    customer_names.set(index, new_customer_name);

    Trace.msg(4, "customer_names[", Integer.toString(index),
              "] replaced with ", new_customer_name);

    customer_map.remove(obsolete_customer_name);
    Util.abandonHashEntry(t, this.intendedLifeSpan());

    Customer customer = new Customer(t, LifeSpan.NearlyForever,
                                     new_customer_name, next_customer_no++);
    adjust_cbhs(customer.browsingHistorySize());
    customer_map.put(new_customer_name, customer);

    Util.addHashEntry(t, this.intendedLifeSpan());
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    int num_customers = config.NumCustomers();
    
    // Account for cc, config, customer_names, customer_map
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 4);
    // Account for long cncl, next_customer_no; int cbhs.
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p,
                   2 * Util.SizeOfLong + Util.SizeOfInt);

    // Account for the data referenced from customer_names.
    customer_names.tallyMemory(log, ls, p);

    // Account for the String objects referenced from customer_names
    log.accumulate(ls, MemoryFlavor.StringObject, p, num_customers);
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, num_customers);
    log.accumulate(ls, MemoryFlavor.StringData, p, adjust_cncl(0));

    // Account for memory consumed by cc ConcurrencyControl object.
    cc.tallyMemory(log, ls, p);

    // Memory for config Configuration accounted elsewhere..

    // Account for the HashMap referenced by customer_map.
    Util.tallyHashMap(log, ls, p, num_customers, DefaultLoadFactor);

    // Account for the Customer objects referenced from customer_map
    // (String names are accumulated above.)
    Customer.tallyMemory(log, ls, p, num_customers, adjust_cbhs(0));
  }

  void report(ExtrememThread t) {
    Report.output("Customers concurrency report:");
    cc.report(t, config.ReportCSV());
  }

  /*
   * Static inner classes.
   */

  private static class RandomSelector extends ExtrememObject implements Actor {
    private ExtrememThread t;
    private Customers all;
    Customer one;

    RandomSelector(ExtrememThread t, LifeSpan ls, Customers all) {
      super(t, ls);

      // account for t, all, one
      t.memoryLog().accumulate (ls, MemoryFlavor.ObjectReference,
                                Polarity.Expand, 3);
      this.t = t;
      this.all = all;
    }

    public void act() {
      one = all.controlledSelectRandomCustomer(t);
    }

    void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
      super.tallyMemory(log, ls, p);
      
      // Account for t, all, one
      log.accumulate(ls, MemoryFlavor.ObjectReference, p, 3);
    }
  }

  private static class RandomReplacer extends ExtrememObject implements Actor {
    ExtrememThread t;
    Customers c;

    RandomReplacer(ExtrememThread t, LifeSpan ls, Customers c) {
      super(t, ls);
      // account for t and c fields
      t.memoryLog().accumulate (ls, MemoryFlavor.ObjectReference,
                                Polarity.Expand, 2);
      this.t = t;
      this.c = c;
    }

    public void act() {
      c.controlledReplaceRandomCustomer(t);
    }

    void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
      super.tallyMemory(log, ls, p);
      
      // Account for t, c
      log.accumulate(ls, MemoryFlavor.ObjectReference, p, 2);
    }
  }
}
