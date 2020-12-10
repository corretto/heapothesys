// Copyright Amazon.com, Inc. or its affiliates.  All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.corretto.benchmark.extremem;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * This class represents the Configuration of this experimental run.
 *
 * Memory accounting for Configuration is non-standard in the sense
 * that Configuration is not a subclass of ExtrememObject.  There is a
 * circular dependency between ExtrememThread and ExtrememObject.
 * Every ExtrememObject wants to know the ExtrememThread that is
 * responsible for its allocation.  Every instantiated ExtrememThread
 * wants to know the Configuration parameters that guide its behavior.
 *
 * The Configuration class is implemented to behave in a way that is
 * similar to ExtrememObject even though it is not a subclass.
 */
class Configuration {

  // Consider future enhancements to the Configuration.  For example:
  //  1. Use arrays or objects to represent "product descriptions",
  //     "keyword searches", "product reviews", "selection between
  //     alternative available products".
  //  2. Use finalizers and/or weak pointers to implement caching of
  //     recent product or customer lookup operations.  Confirm that
  //     these are handled with timeliness and efficiency.

  /*
   * Static fields represent the default values for various
   * configuration parameters.
   */

  static final boolean DefaultReportIndividualThreads = false;
  static final boolean DefaultReportCSV = false;

  static final int DefaultDictionarySize = 25000;
  static final String DefaultDictionaryFile = "/usr/share/dict/words";

  static final int DefaultNumProducts = 20000;
  static final int DefaultProductNameLength = 5;
  static final int DefaultProductDescriptionLength = 24;

  static final int DefaultMaxArrayLength = 0;
  static final int DefaultNumCustomers = 10000;
  static final int DefaultCustomerThreads = 1440;
  static final int DefaultCustomerPeriodMinutes = 4;
  static final int DefaultCustomerThinkSeconds = 200;
  static final int DefaultKeywordSearchCount = 5;
  static final int DefaultProductReviewLength = 32;
  static final int DefaultRandomSeed = 42;

  // How many words used in selection between alternative product options
  static final int DefaultSelectionCriteriaCount = 8;
  // Given a random number between 0 and 1.0, which range of values
  // correspond to Buy vs SaveForLater vs Abandon?
  static final float DefaultBuyThreshold = 0.4f;
  static final float DefaultSaveForLaterThreshold = 0.4f;
  // Abandon probability is 1.0 - (BuyThreshold + SaveForLaterThreshold)

  static final int DefaultServerThreads = 10;
  static final int DefaultServerPeriodMilliseconds = 500;
  static final int DefaultSalesTransactionQueueCount = 10;
  static final int DefaultBrowsingHistoryQueueCount = 10;
  static final int DefaultBrowsingExpirationMinutes = 10;
  static final int DefaultCustomerReplacementPeriodSeconds = 60;
  static final int DefaultCustomerReplacementCount = 16;
  static final int DefaultProductReplacementPeriodSeconds = 90;
  static final int DefaultProductReplacementCount = 64;

  static final long DefaultInitializationDelayMillis = 50;
  static final long DefaultDurationMinutes = 10;

  /*
   * Instance fields begin here.
   */
  private int DictionarySize;
  private int MaxArrayLength;
  private int NumCustomers;
  private int NumProducts;
  private int ProductNameLength;
  private int ProductDescriptionLength;
  private int ProductReviewLength;
  private int RandomSeed;

  private int KeywordSearchCount;
  private int CustomerThreads;
  private int ServerThreads;

  private boolean ReportIndividualThreads;
  private boolean ReportCSV;

  private final String[] args;
  private String DictionaryFile;
  private Words dictionary;

  private RelativeTime InitializationDelay;
  private RelativeTime SimulationDuration;

  private RelativeTime CustomerPeriod;
  private RelativeTime CustomerThinkTime;

  private RelativeTime ServerPeriod;
  private RelativeTime BrowsingExpiration;

  // Multiple concurrent Server threads execute with the same period,
  // with different stagger values.
  private RelativeTime CustomerReplacementPeriod;
  private int CustomerReplacementCount;

  // Multiple concurrent Server threads execute with the same period,
  // with different stagger values.
  private RelativeTime ProductReplacementPeriod;
  private int ProductReplacementCount;

  private int BrowsingHistoryQueueCount;
  private int SalesTransactionQueueCount;
   
  private int SelectionCriteriaCount;
  private float BuyThreshold;
  private float SaveForLaterThreshold;

  Configuration(String[] args) {
    this.args = args;
  }

  // Initialization is distinct from construction in order to break a
  // circular dependency.  In particular, the ExtrememThread
  // constructor requires a Configuration object as an argument in
  // order to properly configure the thread behavior. But
  // initialization of the Configuration object requires an 
  // ExtrememThread as an argument in order to account for the memory
  // allocation performed during initialization.
  //
  // The solution I've chosen is to distinguish a Bootstrap
  // ExtrememThread from all other ExtrememThreads.  The Bootstrap
  // thread is constructed with an uninitialized Configuration.
  // Unlike all other ExtrememThread subclasses, the Bootstrap thread
  // does not require that the Configuration object passed in as a
  // constructor argument represent meaningful configuration data.  Once
  // the Boostrap thread has established its memory logs, it invokes
  // initialize on its Configuration object.  Then it constructs and
  // eventually starts up all of the other ExtrememThread instances
  // required to run this simulation.
  void initialize(ExtrememThread t) {

    MemoryLog log = t.memoryLog();
    // Account for this object
    log.accumulate(LifeSpan.NearlyForever, MemoryFlavor.PlainObject,
                   Polarity.Expand, 1);

    // Account for 16 int fields: DictionarySize, MaxArrayLength,
    // NumCustomers, NumProducts,
    // ProductNameLength, ProductDescriptionLength, ProductReviewLength,
    // RandomSeed, KeywordSearchCount, CustomerThreads, ServerThreads,
    // CustomerReplacementCount, ProductReplacementCount,
    // BrowsingHistoryQueueCount, SalesTransactionQueueCount,
    // SelectionCriteriaCount; 2 float fields: BuyThreshold,
    // SaveForLaterThreshold; 2 boolean fields:
    // ReportIndividualThreads, ReportCSV
    log.accumulate(LifeSpan.NearlyForever, MemoryFlavor.ObjectRSB,
                   Polarity.Expand, 15 * Util.SizeOfInt +
                   2 * Util.SizeOfFloat + 2 * Util.SizeOfBoolean);

    // Account for 11 reference fields: args, dictionary,
    // DictionaryFile, InitializationDelay, SimulationDuration,
    // CustomerPeriod, CustomerThinkTime, ServerPeriod, BrowsingExpiration,
    // CustomerReplacementPeriod, ProductReplacementPeriod.
    log.accumulate(LifeSpan.NearlyForever,
                   MemoryFlavor.ObjectReference, Polarity.Expand, 11);

    ReportIndividualThreads = DefaultReportIndividualThreads;
    ReportCSV = DefaultReportCSV;
    DictionarySize = DefaultDictionarySize;
    DictionaryFile = new String(DefaultDictionaryFile);
    Util.tallyString(t.memoryLog(), LifeSpan.NearlyForever,
                     Polarity.Expand, DictionaryFile.length());

    MaxArrayLength = DefaultMaxArrayLength;
    NumCustomers = DefaultNumCustomers;
    NumProducts = DefaultNumProducts;
    ProductNameLength = DefaultProductNameLength;
    ProductDescriptionLength = DefaultProductDescriptionLength;
    ProductReviewLength = DefaultProductReviewLength;
    RandomSeed = DefaultRandomSeed;

    SimulationDuration = new RelativeTime(t, DefaultDurationMinutes * 60, 0);
    SimulationDuration.changeLifeSpan(t, LifeSpan.NearlyForever);

    InitializationDelay = (
      new RelativeTime(t, DefaultInitializationDelayMillis / 1000, (int)
                       (DefaultInitializationDelayMillis % 1000) * 1000000));
    InitializationDelay.changeLifeSpan(t, LifeSpan.NearlyForever);

    RelativeTime rt = new RelativeTime(t);
    CustomerPeriod = rt.addMinutes(t, DefaultCustomerPeriodMinutes);
    CustomerPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);

    CustomerThinkTime = rt.addSeconds(t, DefaultCustomerThinkSeconds);
    CustomerThinkTime.changeLifeSpan(t, LifeSpan.NearlyForever);

    BrowsingExpiration = rt.addMinutes(t, DefaultBrowsingExpirationMinutes);
    BrowsingExpiration.changeLifeSpan(t, LifeSpan.NearlyForever);

    ServerPeriod = rt.addMillis(t, DefaultServerPeriodMilliseconds);
    ServerPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);

    CustomerReplacementPeriod = (
      rt.addSeconds(t, DefaultCustomerReplacementPeriodSeconds));
    CustomerReplacementPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);

    ProductReplacementPeriod = (
      rt.addSeconds(t, DefaultProductReplacementPeriodSeconds));
    ProductReplacementPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);

    rt.garbageFootprint(t);

    CustomerReplacementCount = DefaultCustomerReplacementCount;
    ProductReplacementCount = DefaultProductReplacementCount;

    KeywordSearchCount = DefaultKeywordSearchCount;
    CustomerThreads = DefaultCustomerThreads;

    ServerThreads = DefaultServerThreads;
    
    BrowsingHistoryQueueCount = DefaultBrowsingHistoryQueueCount;
    SalesTransactionQueueCount = DefaultSalesTransactionQueueCount;
  
    SelectionCriteriaCount = DefaultSelectionCriteriaCount;

    BuyThreshold = DefaultBuyThreshold;
    SaveForLaterThreshold = DefaultSaveForLaterThreshold;

    parseArguments(t, args);
    assureConfiguration(t);
    t.replaceSeed(RandomSeed);
    dictionary = new Words(t, DictionaryFile, LifeSpan.NearlyForever,
                           DictionarySize, MaxArrayLength);
  }

  private static String[] boolean_patterns = {
    "ReportCSV",
    "ReportIndividualThreads",
  };

  private static String[] uint_patterns = {
    "BrowsingHistoryQueueCount",
    "CustomerReplacementCount",
    "CustomerThreads",
    "DictionarySize",
    "KeywordSearchCount",
    "MaxArrayLength",
    "NumCustomers",
    "NumProducts",
    "ProductDescriptionLength",
    "ProductNameLength",
    "ProductReplacementCount",
    "ProductReviewLength",
    "RandomSeed",
    "SalesTransactionQueueCount",
    "SelectionCriteriaCount",
    "ServerThreads",
  };

  private static String[] float_patterns = {
    "BuyThreshold",
    "SaveForLaterThreshold",
  };

  private static String[] time_patterns = {
    "BrowsingExpiration",
    "CustomerPeriod",
    "CustomerReplacementPeriod",
    "CustomerThinkTime",
    "InitializationDelay",
    "ProductReplacementPeriod",
    "ServerPeriod",
    "SimulationDuration",
  };

  private static String[] name_patterns = {
    "DictionaryFile",
  };

  /* No need for memory accounting within this method.  Just print the
   * usage information and kill the jvm.  */
  static void usage(String msg) {
    System.err.print("Command-line error: ");
    System.err.println(msg);
    System.err.println("Usage: extremem <argument>*");
    System.err.println("where <argument> is");
    for (int i = 0; i < boolean_patterns.length; i++)
      System.err.println("  -d" + boolean_patterns[i] + "=true|false");
    for (int i = 0; i < uint_patterns.length; i++)
      System.err.println("  -d" + uint_patterns[i] + "=<unsigned integer>");
    for (int i = 0; i < float_patterns.length; i++)
      System.err.println("  -d" + float_patterns[i] + "=<float in [0.0,1.0)>");
    for (int i = 0; i < time_patterns.length; i++)
      System.err.println("  -d" + time_patterns[i] + "=<time>");
    for (int i = 0; i < name_patterns.length; i++)
      System.err.println("  -d" + time_patterns[i] + "=<name>");
    System.err.println ("   where <time> is an unsigned integer followed by:");
    System.err.println ("     ms (for milliseconds)");
    System.err.println ("     s (for seconds)");
    System.err.println ("     m (for minutes)");
    System.err.println ("     h (for hours)");
    System.err.println ("     d (for days)");
    
    Util.internalError("Simulation Aborted");
  }

  void doNameArg(ExtrememThread t,
                 int index, String keyword, String nameString) {
    switch (index) {
      case 0:
        if (keyword.equals("DictionaryFile")) {
          Util.tallyString(t.garbageLog(), LifeSpan.NearlyForever,
                           Polarity.Expand, DictionaryFile.length());
          DictionaryFile = nameString;
          Util.tallyString(t.memoryLog(), LifeSpan.NearlyForever,
                           Polarity.Expand, DictionaryFile.length());
          break;
        }
      default:
        usage("Unexpected internal error in doNameArg");
    }
  }

  void doBooleanArg(int index, String keyword, String booleanString) {
    boolean b = true;
    if (booleanString.equals("false"))
      b = false;
    else if (booleanString.equals("true"))
      b = true;
    else
      usage("boolean command-line option requires"
            + " either \"true\" or \"false\"");

    switch (index) {
      case 0:
        if (keyword.equals("ReportCSV")) {
          ReportCSV = b;
          break;
        }
      case 1:
        if (keyword.equals("ReportIndividualThreads")) {
          ReportIndividualThreads = b;
          break;
        }
      default:
        usage("Unexpected internal error in doBooleanArg");
    }
  }

  void doUintArg(int index, String keyword, String uintString) {
    int u = 0;
    for (int i = 0; i < uintString.length(); i++) {
      char c = uintString.charAt(i);
      if (!Character.isDigit(c))
        usage("Unexpected character in unsigned int encoding");
      u = u * 10 + Character.digit(c, 10);
    }

    switch (index) {
      case 0:
        if (keyword.equals("BrowsingHistoryQueueCount")) {
          BrowsingHistoryQueueCount = u;
          break;
        }
      case 1:
        if (keyword.equals("CustomerReplacementCount")) {
          CustomerReplacementCount = u;
          break;
        }
      case 2:
        if (keyword.equals("CustomerThreads")) {
          CustomerThreads = u;
          break;
        }
      case 3:
        if (keyword.equals("DictionarySize")) {
          DictionarySize = u;
          break;
        }
      case 4:
        if (keyword.equals("KeywordSearchCount")) {
          KeywordSearchCount = u;
          break;
        }
      case 5:
        if (keyword.equals("MaxArrayLength")) {
          MaxArrayLength = u;
          break;
        }
      case 6:
        if (keyword.equals("NumCustomers")) {
          NumCustomers = u;
          break;
        }
      case 7:
        if (keyword.equals("NumProducts")) {
          NumProducts = u;
          break;
        }
      case 8:
        if (keyword.equals("ProductDescriptionLength")) {
          ProductDescriptionLength = u;
          break;
        }
      case 9:
        if (keyword.equals("ProductNameLength")) {
          ProductNameLength = u;
          break;
        }
      case 10:
        if (keyword.equals("ProductReplacementCount")) {
          ProductReplacementCount = u;
          break;
        }
      case 11:
        if (keyword.equals("ProductReviewLength")) {
          ProductReviewLength = u;
          break;
        }
      case 12:
        if (keyword.equals("RandomSeed")) {
          RandomSeed = u;
          break;
        }
      case 13:
        if (keyword.equals("SalesTransactionQueueCount")) {
          SalesTransactionQueueCount = u;
          break;
        }
      case 14:
        if (keyword.equals("SelectionCriteriaCount")) {
          SelectionCriteriaCount = u;
          break;
        }
      case 15:
        if (keyword.equals("ServerThreads")) {
          ServerThreads = u;
          break;
        }
      default:
        usage("Unexpected internal error in doUintArg");
    }
  }

  void doFloatArg(int index, String keyword, String floatString) {
    float f = 0;
    float multiplier = 0.1f;
    if ((floatString.charAt(0) != '0') || (floatString.charAt(1) != '.'))
      usage("Float encoding must begin with \"0.\"");
    for (int i = 2; i < floatString.length(); i++) {
      char c = floatString.charAt(i);
      if (!Character.isDigit(c))
        usage("Unexpected character in float encoding");
      f += Character.digit(c, 10) * multiplier;
      multiplier /= 10.0f;
    }

    switch (index) {
      case 0:
        if (keyword.equals("BuyThreshold")) {
          BuyThreshold = f;
          break;
        }
      case 1:
        if (keyword.equals("SaveForLaterThreshold")) {
          SaveForLaterThreshold = f;
          break;
        }
      default:
        usage("Unexpected internal error in doFloattArg");
    }
  }

  void doTimeArg(ExtrememThread t,
                 int index, String keyword, String timeString) {
    MemoryLog log = t.memoryLog();
    MemoryLog garbage = t.garbageLog();
    long u = 0;
    int i;
    for (i = 0;
         i < timeString.length() && Character.isDigit(timeString.charAt(i));
         i++) {
      char c = timeString.charAt(i);
      if (!Character.isDigit(c))
        usage("Unexpected character in time encoding");
      u = u * 10 + Character.digit(c, 10);
    }
    char unit_selector = '$';
    if (i + 1 == timeString.length())
      unit_selector = timeString.charAt(i);
    else if ((i + 2 == timeString.length()) &&
             (timeString.charAt(i) == 'm') && (timeString.charAt(i+1) == 's'))
      unit_selector = '@';
      
    switch (unit_selector) {
      case 'd':
        u *= 24;                // convert days to hours
      case 'h':
        u *= 60;                // convert hours to minutes
      case 'm':
        u *= 60;                // convert minutes to seconds
      case 's':
        u *= 1000;              // convert seconds to ms
      case '@':
        break;
      case '$':
      default:
        usage("Time suffix must be ms, s, m, h, or d");
    }

    long secs;
    int nanos;

    secs = u / 1000;
    nanos = ((int) (u % 1000)) * 1000000;

    switch (index) {
      case 0:
        if (keyword.equals("BrowsingExpiration")) {
          BrowsingExpiration.garbageFootprint(t);
          BrowsingExpiration = new RelativeTime(t, secs, nanos);
          BrowsingExpiration.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 1:
        if (keyword.equals("CustomerPeriod")) {
          CustomerPeriod.garbageFootprint(t);
          CustomerPeriod = new RelativeTime(t, secs, nanos);
          CustomerPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 2:
        if (keyword.equals("CustomerReplacementPeriod")) {
          CustomerReplacementPeriod.garbageFootprint(t);
          CustomerReplacementPeriod = new RelativeTime(t, secs, nanos);
          CustomerReplacementPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 3:
        if (keyword.equals("CustomerThinkTime")) {
          CustomerThinkTime.garbageFootprint(t);
          CustomerThinkTime = new RelativeTime(t, secs, nanos);
          CustomerThinkTime.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 4:
        if (keyword.equals("InitializationDelay")) {
          InitializationDelay.garbageFootprint(t);
          InitializationDelay = new RelativeTime(t, secs, nanos);
          InitializationDelay.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 5:
        if (keyword.equals("ProductReplacementPeriod")) {
          ProductReplacementPeriod.garbageFootprint(t);
          ProductReplacementPeriod = new RelativeTime(t, secs, nanos);
          ProductReplacementPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 6:
        if (keyword.equals("ServerPeriod")) {
          ServerPeriod.garbageFootprint(t);
          ServerPeriod = new RelativeTime(t, secs, nanos);
          ServerPeriod.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      case 7:
        if (keyword.equals("SimulationDuration")) {
          SimulationDuration.garbageFootprint(t);
          SimulationDuration = new RelativeTime(t, secs, nanos);
          SimulationDuration.changeLifeSpan(t, LifeSpan.NearlyForever);
          break;
        }
      default:
        usage("Unexpected internal error in doTimeArg");
    }
  }

  private int searchPatterns(String[] patterns, String keyword) {
    for (int i = 0; i < patterns.length; i++) {
      if (keyword.equals(patterns[i]))
        return i;
    }
    return -1;
  }

  private boolean sufficientVocabulary(int vocab_size, int num_words,
                                       int unique_ids) {
    long possible_ids = 1;
    while (num_words-- > 0) {
      possible_ids *= vocab_size;
      if (possible_ids > unique_ids)
        return true;
    }
    return false;
  }

  private void assureConfiguration(ExtrememThread t) {
    // Ignore memory allocation accounting along early termination paths.

    if (DictionarySize < 1)
      usage("DictionarySize must be greater or equal to 1");

    if (NumProducts < 1)
      usage("NumProducts must be greater or equal to 1");

    if (NumCustomers < 1)
      usage("NumCustomers must be greater or equal to 1");

    if (!sufficientVocabulary(DictionarySize, ProductNameLength, NumProducts))
      usage("Dictionary too small to generate unique product names");

    // 2 words comprise a customer name
    if (!sufficientVocabulary(DictionarySize, 2, NumCustomers))
      usage("Dictionary too small to generate unique customer names");

    if (CustomerThinkTime.compare(CustomerPeriod) >= 0)
      usage("CustomerThinkTime must be less than CustomerPeriod");
    
    if (BuyThreshold + SaveForLaterThreshold > 1.0)
      usage("BuyThreshold plus SaveForLaterThreshold must be not exceed 1");

    RelativeTime product = (
      ServerPeriod.multiplyBy(t, ServerThread.TotalAttentionPoints));
    if (product.compare(CustomerReplacementPeriod) >= 0)
      usage("ServerPeriod multiplied by " + ServerThread.TotalAttentionPoints
            + " must be no longer than CustomerReplacementPeriod");
    product.garbageFootprint(t);

    product = ServerPeriod.multiplyBy(t, ServerThread.TotalAttentionPoints);
    if (product.compare(ProductReplacementPeriod) >= 0)
      usage("ServerPeriod multiplied by " + ServerThread.TotalAttentionPoints
            + " must be no longer than ProductReplacementPeriod");
    product.garbageFootprint(t);

    if (BrowsingHistoryQueueCount > CustomerThreads)
      usage("CustomerThreads must be greater or equal to " +
            "BrowsingHistoryQueueCount");

    if (BrowsingHistoryQueueCount > ServerThreads)
      usage("ServerThreads must be greater or equal to " +
            "BrowsingHistoryQueueCount");

    if (SalesTransactionQueueCount > CustomerThreads)
      usage("CustomerThreads must be greater or equal to " +
            "SalesTransactionQueueCount");

    if (SalesTransactionQueueCount > ServerThreads)
      usage("ServerThreads must be greater or equal to " +
            "SalesTransactionQueueCount");
  }

  private void parseArguments(ExtrememThread t, String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("-d")) {
        arg = arg.substring(2);

        // Account for arg created by substring
        int len = arg.length();
        Util.ephemeralString(t, len);

        int eokw = arg.indexOf("=");
        if (eokw < 0)
          usage("Command-line arguments must include \"=\"");
        String keyword = arg.substring(0, eokw);
        String value = arg.substring(eokw + 1);

        // Account for creation of keyword and value
        Util.ephemeralString(t, keyword.length());
        Util.ephemeralString(t, value.length());

        int match;
        if ((match = searchPatterns(boolean_patterns, keyword)) >= 0)
          doBooleanArg(match, keyword, value);
        else if ((match = searchPatterns(uint_patterns, keyword)) >= 0)
          doUintArg(match, keyword, value);
        else if ((match = searchPatterns(float_patterns, keyword)) >= 0)
          doFloatArg(match, keyword, value);
        else if ((match = searchPatterns(time_patterns, keyword)) >= 0)
          doTimeArg(t, match, keyword, value);
        else if ((match = searchPatterns(name_patterns, keyword)) >= 0)
          doNameArg(t, match, keyword, value);
        else
          usage("Unrecognized option name: " + keyword);

        // Account for garbage of arg, keyword, value.  Keyword and value
        // sum to one less than length of arg because '=' is excluded.
        Util.abandonEphemeralStrings(t, 3, len + (len - 1));
      } else
        usage("Command-line arguments must begin with -d");
    }
  }

  int DictionarySize() {
    return DictionarySize();
  }

  String DictionaryFile() {
    return DictionaryFile();
  }

  int MaxArrayLength() {
    return MaxArrayLength;
  }

  int NumCustomers() {
    return NumCustomers;
  }
  
  int NumProducts() {
    return NumProducts;
  }
  
  int ProductNameLength() {
    return ProductNameLength;
  }

  int ProductDescriptionLength() {
    return ProductDescriptionLength;
  }
  
  int ProductReviewLength() {
    return ProductReviewLength;
  }

  // In theory, we can seed with a 64-bit value, but 32 bits should
  // provide sufficient variability in workload behaviors.
  int RandomeSeed() {
    return RandomSeed;
  }
  
  int KeywordSearchCount() {
    return KeywordSearchCount;
  }
  
  int CustomerThreads() {
    return CustomerThreads;
  }
  
  boolean ReportIndividualThreads() {
    return ReportIndividualThreads;
  }

  boolean ReportCSV() {
    return ReportCSV;
  }

  int ServerThreads() {
    return ServerThreads;
  }

  RelativeTime SimulationDuration() {
    return SimulationDuration;
  }

  RelativeTime InitializationDelay() {
    return InitializationDelay;
  }

  RelativeTime CustomerPeriod() {
    return CustomerPeriod;
  }
  
  RelativeTime CustomerThinkTime() {
    return CustomerThinkTime;
  }
  
  RelativeTime ServerPeriod() {
    return ServerPeriod;
  }
  
  RelativeTime BrowsingExpiration() {
    return BrowsingExpiration;
  }

  int BrowsingHistoryQueueCount() {
    return BrowsingHistoryQueueCount;
  }
  
  int SalesTransactionQueueCount() {
    return SalesTransactionQueueCount;
  }
   
  int SelectionCriteriaCount() {
    return SelectionCriteriaCount;
  }

  float BuyThreshold() {
    return BuyThreshold;
  }
  
  float SaveForLaterThreshold() {
    return SaveForLaterThreshold;
  }

  int CustomerReplacementCount() {
    return CustomerReplacementCount;
  }

  int ProductReplacementCount() {
    return ProductReplacementCount;
  }

  RelativeTime CustomerReplacementPeriod() {
    return CustomerReplacementPeriod;
  }

  RelativeTime ProductReplacementPeriod() {
    return ProductReplacementPeriod;
  }

  // Dictionary services
  String arbitraryWord(ExtrememThread t) {
    return dictionary.arbitraryWord(t);
  }

  void dumpCSV(ExtrememThread t) {
    String s;
    int l;

    // Ignore memory accounting for MXBeans
    Report.outputNoLine("JVM configuration");
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    List<String> listOfArguments = runtimeMxBean.getInputArguments();
    for (int i = 0; i < listOfArguments.size(); i++)
      Report.outputNoLine(",arg:", listOfArguments.get(i));

    Report.output();
    Report.output("ReportIndividualThreads,",
                  ReportIndividualThreads? "true": "false");
    Report.output("ReportCSV,", ReportCSV? "true": "false");

    Report.output();
    Report.output("Simulation configuration");

    s = Integer.toString(RandomSeed);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("RandomSeed,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(InitializationDelay.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("InitializationDelay,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(SimulationDuration.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("SimulationDuration,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(MaxArrayLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("MaxArrayLength,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(DictionarySize);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("DictionarySize,", s);
    Util.abandonEphemeralString(t, l);

    s = DictionaryFile;
    Report.output("DictionaryFile,", s);

    s = Integer.toString(BrowsingHistoryQueueCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("BrowsingHistoryQueueCount,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(SalesTransactionQueueCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("SalesTransactionQueueCount,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Server thread configuration");

    s = Integer.toString(ServerThreads);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ServerThreads,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(ServerPeriod.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ServerPeriod,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("Customer maintenance");

    s = Long.toString(CustomerReplacementPeriod.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("CustomerReplacementPeriod,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(CustomerReplacementCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("CustomerReplacementCount,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("Product maintenance");

    s = Long.toString(ProductReplacementPeriod.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ProductReplacementPeriod,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductReplacementCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ProductReplacementCount,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Customer thread configuration");

    s = Integer.toString(CustomerThreads);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("CustomerThreads,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(CustomerPeriod.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("CustomerPeriod,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(CustomerThinkTime.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("CustomerThinkTime,", s);
    Util.abandonEphemeralString(t, l);

    s = Long.toString(BrowsingExpiration.microseconds());
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("BrowsingExpiration,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Customer configuration");

    s = Integer.toString(NumCustomers);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("NumCustomers,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(KeywordSearchCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("KeywordSearchCount,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(SelectionCriteriaCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("SelectionCriteriaCount,", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(BuyThreshold);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("BuyThreshold,", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(SaveForLaterThreshold);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("SaveForLaterThreshold,", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Product configuration");

    s = Integer.toString(NumProducts);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("NumProducts,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductNameLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ProductNameLength,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductDescriptionLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ProductDescriptionLength,", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductReviewLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("ProductReviewLength,", s);
    Util.abandonEphemeralString(t, l);


  }

  void dump(ExtrememThread t) {
    String s;
    int l;

    Report.output("JVM configuration");

    // Ignore memory accounting for MXBeans
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    List<String> listOfArguments = runtimeMxBean.getInputArguments();
    for (int i = 0; i < listOfArguments.size(); i++)
      Report.output(listOfArguments.get(i));

    Report.output();
    Report.output("Individual thread report (ReportIndividualThreads): ",
                  ReportIndividualThreads? "true": "false");
    Report.output("                    Exporting to Excel (ReportCSV): ",
                  ReportCSV? "true": "false");

    Report.output();
    Report.output("Simulation configuration");

    s = Integer.toString(RandomSeed);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("    Seed for random number generation (RandomSeed): ", s);
    Util.abandonEphemeralString(t, l);

    s = InitializationDelay.toString(t);
    l = s.length();
    Report.output("               Startup Pause (InitializationDelay): ", s);
    Util.abandonEphemeralString(t, l);

    s = SimulationDuration.toString(t);
    l = s.length();
    Report.output("                     Duration (SimulationDuration): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(MaxArrayLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("             Maximum array length (MaxArrayLength): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(DictionarySize);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("              Words in dictionary (DictionarySize): ", s);
    Util.abandonEphemeralString(t, l);

    s = DictionaryFile;
    Report.output("Full path name of dictionary file (DictionaryFile): ", s);

    s = Integer.toString(BrowsingHistoryQueueCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("    Browsing queue qty (BrowsingHistoryQueueCount): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(SalesTransactionQueueCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("Transaction queue qty (SalesTransactionQueueCount): ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Server thread configuration");

    s = Integer.toString(ServerThreads);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("           Number of server threads (ServerThreads): ", s);
    Util.abandonEphemeralString(t, l);

    s = ServerPeriod.toString(t);
    l = s.length();
    Report.output("                Server thread period (ServerPeriod): ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("Customer maintenance");

    s = CustomerReplacementPeriod.toString(t);
    Report.output("     Replacement period (CustomerReplacementPeriod): ", s);
    Util.abandonEphemeralString(t, s);

    s = Integer.toString(CustomerReplacementCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("       Replacement count (CustomerReplacementCount): ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("Product maintenance");

    s = ProductReplacementPeriod.toString(t);
    Report.output("      Replacement period (ProductReplacementPeriod): ", s);
    Util.abandonEphemeralString(t, s);

    s = Integer.toString(ProductReplacementCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("        Replacement count (ProductReplacementCount): ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Customer thread configuration");

    s = Integer.toString(CustomerThreads);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                Number of threads (CustomerThreads): ", s);
    Util.abandonEphemeralString(t, l);

    s = CustomerPeriod.toString(t);
    Report.output("                     Thread period (CustomerPeriod): ", s);
    Util.abandonEphemeralString(t, s);

    s = CustomerThinkTime.toString(t);
    Report.output("                     Think time (CustomerThinkTime): ", s);
    Util.abandonEphemeralString(t, s);

    s = BrowsingExpiration.toString(t);
    Report.output("       Save-for-later duration (BrowsingExpiration): ", s);
    Util.abandonEphemeralString(t, s);

    Report.output("");
    Report.output("Customer configuration");

    s = Integer.toString(NumCustomers);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                              Number (NumCustomers): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(KeywordSearchCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                Words in query (KeywordSearchCount): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(SelectionCriteriaCount);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("           Words to select (SelectionCriteriaCount): ", s);
    Util.abandonEphemeralString(t, l);

    Report.output(" Decision ratios:");
    s = Float.toString(BuyThreshold);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                     Buy (BuyThreshold): ", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(SaveForLaterThreshold);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output(" Save for later (SaveForLaterThreshold): ", s);
    Util.abandonEphemeralString(t, l);

    s = Float.toString(1.0f - (BuyThreshold + SaveForLaterThreshold));
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                                 Ignore: ", s);
    Util.abandonEphemeralString(t, l);

    Report.output("");
    Report.output("Product configuration");

    s = Integer.toString(NumProducts);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                               Number (NumProducts): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductNameLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("                  Words in name (ProductNameLength): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductDescriptionLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("    Words in description (ProductDescriptionLength): ", s);
    Util.abandonEphemeralString(t, l);

    s = Integer.toString(ProductReviewLength);
    l = s.length();
    Util.ephemeralString(t, l);
    Report.output("              Words in review (ProductReviewLength): ", s);
    Util.abandonEphemeralString(t, l);
  }

  void garbageFootprint(ExtrememThread t) {
    MemoryLog garbage = t.garbageLog();
    Polarity Grow = Polarity.Expand;

    // Account for my self object
    garbage.accumulate(LifeSpan.NearlyForever, MemoryFlavor.PlainObject,
                       Grow, 1);

    // Account for 16 int fields: DictionarySize, MaximumArrayLength,
    // NumCustomers, NumProducts,
    // ProductNameLength, ProductDescriptionLength, ProductReviewLength,
    // RandomSeed, KeywordSearchCount, CustomerThreads, ServerThreads,
    // CustomerReplacementCount, ProductReplacementCount,
    // BrowsingHistoryQueueCount, SalesTransactionQueueCount,
    // SelectionCriteriaCount; 2 float fields: BuyThreshold,
    // SaveForLaterThreshold; 2 boolean fields:
    // ReportIndividualThreads, ReportCSV.
    garbage.accumulate(LifeSpan.NearlyForever, MemoryFlavor.ObjectRSB,
                       Grow, 15 * Util.SizeOfInt +
                       2 * Util.SizeOfFloat + 2 * Util.SizeOfBoolean);

    // Account for 11 reference fields: args, dictionary, DictionaryFile
    // InitializationDelay, SimulationDuration, CustomerPeriod,
    // CustomerThinkTime, ServerPeriod, BrowsingExpiration,
    // CustomerReplacementPeriod, ProductReplacementPeriod 
    garbage.accumulate(LifeSpan.NearlyForever,
                       MemoryFlavor.ObjectReference, Polarity.Expand, 11);

    Util.tallyString(t.garbageLog(), LifeSpan.NearlyForever,
                     Polarity.Expand, DictionaryFile.length());
    InitializationDelay.garbageFootprint(t);
    SimulationDuration.garbageFootprint(t);
    CustomerPeriod.garbageFootprint(t);
    CustomerThinkTime.garbageFootprint(t);
    BrowsingExpiration.garbageFootprint(t);
    ServerPeriod.garbageFootprint(t);
    CustomerReplacementPeriod.garbageFootprint(t);
    ProductReplacementPeriod.garbageFootprint(t);

    dictionary.garbageFootprint(t);
  }
}
