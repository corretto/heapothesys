// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.io.*;

/**
 * Represent arbitrary words (fetched from /usr/share/dict/words) and
 * support random generation thereof.
 */
class Words extends ExtrememObject {
  private final static int Stride = 59;

  // was String [] known_words;	// may be humongous object
  Arraylet<String> known_words;
  long twl;			// cumulative length of all words

  Words(ExtrememThread t, String file_name, LifeSpan ls,
	int num_words, int max_array_length) {
    super(t, ls);

    // Account for twl and known_words fields.
    MemoryLog log = t.memoryLog();
    Polarity Grow = Polarity.Expand;
    log.accumulate(ls, MemoryFlavor.ObjectReference, Grow, 1);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, Grow, Util.SizeOfLong);

    boolean seen_end = false;

    // There are 235,886 words in macos:/usr/share/dict/words.
    //  If necessary, some entries in dictionary may be repeats.
    known_words = new Arraylet<String>(t, ls, max_array_length, num_words);

    // Note:
    //  There's some unaccounted Ephemeral data allocated here to
    //  represent file, fr, and br.  This code runs only once during
    //  startup.  Ignore it.
    File file = new File(file_name);
    FileReader fr = null;
    BufferedReader br = null;
    try {
      twl = 0;
      int front_skip = 0;

      do {
	fr = new FileReader(file);
	br = new BufferedReader(fr);
	front_skip = skip(t, front_skip, br);
      } while (front_skip < 0);
      for (int i = 0; i < num_words; i++) {
	while (true) {
	  String w = br.readLine ();
	  if (w == null) {
	    br.close();
	    fr.close();
	    do {
	      fr = new FileReader(file);
	      br = new BufferedReader(fr);
	      front_skip = skip(t, front_skip, br);
	    } while (front_skip < 0);
	  } else {
	    int len = w.length();
	    twl += len;
	    known_words.set(i, w);
	    // Account for new word added to dictionary.
	    Util.ephemeralString(t, len);
	    Util.convertEphemeralString(t, ls, len);
	    break;
	  }
	}

	for (int j = 1; j < Stride; j++) {
	  String w = br.readLine();
	  if (w == null) {
	    br.close();
	    fr.close();
	    do {
	      fr = new FileReader(file);
	      br = new BufferedReader(fr);
	      front_skip = skip(t, front_skip, br);
	    } while (front_skip < 0);
	    j--;
	  } else {
	    int len = w.length();
	    Util.ephemeralString(t, len);
	    Util.abandonEphemeralString(t, len);
	  }
	}
      }
      br.close ();
      fr.close ();
    } catch (Exception x) {
      Util.internalError("Unable to read in words from dictionary");
    }
  }

  private static int skip(ExtrememThread t, int count, BufferedReader br) {
    try {
      for (int i = 0; i < count; i++) {
	String w = br.readLine();
	if (w == null)
	  return -1;
	int len = w.length();
	Util.ephemeralString(t, len);
	Util.abandonEphemeralString(t, len);
      }
    } catch (Exception x) {
      Util.internalError("Unable to skip over words in dictionary file");
    }
    return count + 1;
  }

  String arbitraryWord (ExtrememThread t) {
    int r = t.randomUnsignedInt();
    int random_index = r % known_words.length();
    return known_words.get(random_index);
  }

  void tallyMemory(MemoryLog log, LifeSpan ls, Polarity p) {
    super.tallyMemory(log, ls, p);
    
    // Account for twl and known_words fields
    log.accumulate(ls, MemoryFlavor.ObjectReference, p, 1);
    log.accumulate(ls, MemoryFlavor.ObjectRSB, p, Util.SizeOfLong);

    // Account for data referenced from known_words
    known_words.tallyMemory(log, ls, p);
    log.accumulate(ls, MemoryFlavor.StringObject, p, known_words.length());
    log.accumulate(ls, MemoryFlavor.ArrayObject, p, known_words.length());
    log.accumulate(ls, MemoryFlavor.StringData, p, twl);
  }
}