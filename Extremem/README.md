# Extremem

## Introduction

Extremem /ekstri:mem/ is a configurable test workload that helps evaluate the strengths and weakness of particular approaches to pause-less garbage collection.  The workload is designed to intermingle allocation and deallocation of heap objects with execution of business logic code.

The workload is structured as a simulation of Amazon customers looking up products by keyword searches and making purchasing decisions based on review information.  Review information is randomly generated for each query, adding to the artificially high transient memory allocation workload but having no direct correlation to the real world of Amazon retail.  Much of the work of this synthetic workload is in no way analogous to the real processes under which Amazon products are promoted and sold.

Configuration parameters allow workload runs to vary the amount of live memory retained, the rates at which new memory is allocated, the lifetime distributions of transient objects, and the amount of business logic computation that is intertwined with memory management operations.

## Usage

For any given configuration, Extremem allocation and deallocation rates are roughly steady throughout the run.  A small amount of variation in allocation and deallocation rates results because of differences in the lengths of the words that are randomly selected from the dictionary for the purposes of looking up and selecting between alternative product offerings.  Additional small variations result because of randomness in the number of candidate products that match each customer inquiry, and in the efforts required by each simulated customer to select between purchasing products, abandoning products, and saving products in the customer's viewing history for possible future purchase.

## Build

The project can be built using [Make].  Assure that environment variable JAVA_HOME names a path that holds a relevant JDK installation, with a bin subdirectory holding executable javac and java programs, and with include and include/linux subdirectories holding headers files required for compilation of compatible native methods.  Run the following command:
```
make clean install
```
This produces the Jar file extremem.jar in the src/main/java subdirectory.

## Command-Line Arguments

Command-line arguments configure Extremem to represent different combinations of workload requirements.  Various command-line arguments are described below, in approximate chronological order as to their relevance during execution of each Extremem simulation.  In each case, the sample argument display sets the argument to its default value.  Though not described explicitly in the descriptions of each argument below, larger data sizes generally result in lower cache locality, which will generally decrease workoad throughput.

### *-dFastAndFurious=false*

In the default Extremem configuration, the shared Customers and Products in-memory databases are each protected by a global
synchronization lock which allows multiple readers and a single writer.  Multiple customers can read from these databases
concurrently.  Each time a server thread replaces customers or products, a write-lock is required, causing all customer threads
to wait until the server thread has finished its changes to the database.  With the high transaction rates required to represent
allocations in excess of 2 GB per second, significant synchronization contention has been observed.  This flag changes the
synchronization protocol.  The FastAndFurious mode of operation replaces the global multiple-reader-single-writer lock with
a larger number of smaller-context locks.  Locks that protect much smaller scopes are held for much shorter
time frames, improving parallel access to shared data structures.  
The large majority of these smaller-context locks should normally be uncontended
because the contexts are so small that collisions by multiple threads on the same small contexts is normally
rare.  This mode of operation is identified as ``furious'' because it allows false positives and false
negatives.  During the process of replacing products, the indexes might report a match to a product that no longer exists.
Likewise, the indexes may not recognize a match for a product that has been newly added but is not yet indexed.  This mode
of operation properly uses synchronization to assure coherency of data structures.  The default value of the FastAndFurious flag
is false, preserving compatibility with the original Extremem mode of operation.  While configuring FastAndFurious=true allows
Extremem to simulate higher allocation rates with less interference from synchronization contention, disabling FastAndFurious
may reveal different weaknesses in particular GC approaches.  In particular, interference from synchronization causes allocations
to be more bursty.  While a single server thread locks indexes in order to replace products or customers, multiple customer
threads that would normally be allocating are idle, waiting for the server thread to releases its exclusive lock.  When the server
thread releases its lock, these customer threads resume execution and allocate at rates much higher than normal because they
have fallen behind their intended execution schedule.  This causes a burst of allocation, making it difficult for the GC
scheduling heuristic to predict when the allocation pool will become depleted.  If the heuristic is late to trigger the start
of GC, it is likely that the allocation pool will become exhausted before the GC replenishes it, resulting in a degenerated
stop-the-world GC pause.

## *-dPhasedUpdates=false*

In the default Extremem configuration, the shared Customers and Products in-memory databases are each protected by a global
synchronization lock which allows multiple readers and a single writer.  Multiple customers can read from these databases
concurrently.  Each time a server thread replaces customers or products, a write-lock is required, causing all customer threads
to wait until the server thread has finished its changes to the database.  With the high transaction rates required to represent
allocations in excess of 2 GB per second, significant synchronization contention has been observed.  This flag changes the
synchronization protocol.  The PhasedUpdates mode of operation causes all intended changes to the shared data base to be placed
into a change log.  The change log is processed by a single thread running continuously in the background.  This thread
copies the existing data base, applies all changes present in the change log, then replaces the old data base with
the new data base.  In this mode of operation, the current data base is a read-only data structure requiring no synchronization
for access.  A 
synchronized method is used to obtain access to the most current version of the shared database.  Server threads synchronize
only for the purpose of placing intended changes into the change log.  PhasedUpdates and FastAndFurious options are mutually
exclusive.  The thread that rebuilds the database does not run if the change log is empty.

## *-dPhasedUpdateInterval=1m*

When PhasedUpdates is true, a dedicated background thread alternates between rebuilding of the Customers and Products
databases.  Each time it finishes building a database, it waits PhasedUpdateInterval amount of time before it begins
to rebuild the other database.

### *-dInitializationDelay=50ms*

It is important to complete all initialization of all global data structures before beginning to execute the experimental workload threads.  If
initialization is still running when the workload begins to execute, the
simulation will abort.  This value is added to the simulation startup delay
that is computed by multiplying the combined number of customer and server
threads by 500 microseconds to determine the time at which the simulation
workload begins to execute.  Increase the value of InitializationDelay if
the default delays are not sufficient.

### *-dRandomSeed=42*

Various choices made during the simulation depend on random number generation.  Overwrite the default value to change the simulation run.

### *-dMaxArrayLength=0*

Some garbage collection algorithms have difficulty with very large objects and arrays.  This option allows disabling of very large array allocations to test whether these might be the reason for observed very long pauses.  The Extremem workload by default maintains three potentially very indexed data structures for the purposes of representing information associated with all dictionary words, all  customers, and all products.  The number of entries in each of these indexed data structures are respectively DictionarySize, NumCustomers, and NumProducts.  Setting MaxArrayLength to zero allows each of these three indexed data structures to be represented by a contiguous Java array.  Setting MaxArrayLength to some other positive integer value imposes a limit on the sizes of the arrays that are used to represent these indexed data structures.  In the case that a non-zero value is set for MaxArrayLength, Extremem will use multiple smaller arrays to represent each of the indexed data structures.  A current limitation in the implementation of this constraint is that it does not affect the representation of HashMap data structures, such as one that is used to represent all customer names.

### *-dDictionaryFile=/usr/share/dict/words*

At startup, a global dictionary is initialized by fetching the specified number of words from this file.  If you are running Extremem on a Linux host, you may need to install the relevant package (wamerican on Ubuntu 18).  For non-Linux platforms, a dictionary file is available from github.  See *https://github.com/dwyl/english-words*

### *-dDictionarySize=25000*

At startup, a global dictionary is initialized by fetching the specified number of words from /usr/share/dict/words.  Words are selected at a sampling interval so as to span a breadth of the alphabetical range.  In case of requests for a larger vocabulary than the size of the dictionary (which is typically over 200,000 words), certain entries in the global dictionary may hold the same word.

Specifying a larger value of DictionarySize causes a larger amount of long-term memory in the simulation workload.  A larger vocabulary results in less frequent recurrences of randomly selected words.  Indirectly, this results in fewer product matches in response to a randomly selected query string.

### *-dNumProducts=20000*

At startup, this number of product entries are installed into the global data base.  Having a larger number of products causes an increase in the long-term memory represented by the simulation workload.  For a given DictionarySize, having a larger number of products results in a larger number of products matching a given customer query.  This in turn results in more transient memory allocation and more effort in deciding between multiple alternative products that match a given decision criteria.

### *-dProductNameLength=5*
Each product has a name consisting of this number of randomly selected words.  Having a larger number of words in each product name causes an increase in the long-term memory represented by the simulation workload.  For a given DictionarySize, having more words in the product name results in a larger number of products matching a given customer query.  This in turn results in more transient memory allocation and more effort in deciding between multiple alternative products that match a given decision criteria.

### *-dProductDescriptionLength=24*

Each product has a description consisting of this number of randomly selected words.  Having a larger number of words in each product description causes an increase in the long-term memory represented by the simulation workload.  For a given DictionarySize, having more words in the product description results in a larger number of products matching a given customer query.  This in turn results in more transient memory allocation and more effort in deciding between multiple alternative products that match a given decision criteria.

### *-dNumCustomers=10000*

At startup, this number of customer entries are installed into the global data base.  Having a larger number of customers causes an increase in the long-term memory represented by the simulation workload.  During simulation of customer behaviors, having larger numbers of customers decreases cache hit rates and memory locality as it pertains to card marking implementations for generational garbage collection.

### *-dCustomerThreads=1440*

At startup, this many customer threads are started up to run simulated customer sessions.  Each customer thread is affiliated with one browsing history queue and one pending sales transaction queue.  The browsing history queue holds representation of products that are saved for later so that a background server thread can expire the browsing history representation after the appropriate amount of time.  The pending sales transaction queue holds products that a customer has requested to purchase so that a background server thread can subsequently transact the sale.  Specifying a larger number of threads allows more customer sessions to run concurrently, which has the effect of causing more allocation of ephemeral and transient objects in memory.  Note that in the default configuration, each customer session spends most of its time blocked.  This simulates the time that a typical Amazon retail customer spends in pondering the decision between whether or not to buy, and which of multiple possible product offerings to buy.

### *-dCustomerPeriod=4m*

Each Customer thread waits this amount of time between starts of simulated customer sessions.  For each customer session, the customer thread randomly selects one customer for the session.  Specifying a shorter value for this argument allows more frequent customer sessions.  However, the CustomerPeriod must be at least as long as CustomerThinkTime. 

### *-dCustomerThinkTime=200s*

Between the moment when a customer is presented with the results of a product search inquiry and the moment when the customer actually makes a decision regarding which product, if any, to purchase, the customer simply sleeps for this amount of time.  Shortening this time and shortening the CustomerPeriod allows more transactions to occur more quickly.  Lengthening this time causes an increase in the lifespan of transient objects which might accidentally be promoted into the older generation with certain garbage collection techniques.

### *-dKeywordSearchCount=5*

Each customer inquiry consists of a string of this many randomly selected words.  Increasing this number results in an increase in the memory allocated during formulation of each customer inquiry and generally results in an increased number of candidate products that match the inquiry.  This in turn results in more transient memory allocation and more effort in deciding between multiple alternative products that match a given decision criteria.

### *-dAllowAnyMatch=true*

By default, each customer inquiry searches the Products data base for products that match the randomly generated keywords.
If AllowAnyMatch is true, then products containing at least one of the keywords are considered a search match.  If AllowAnyMatch
is false, then only products that contain all of the keywords are considered to match the inquiry.  Note that overriding the
default value of AllowAnyMatch will generally result in far fewer candidate products to be compared and evaluated.  With some
product data bases, the difference between AllowAnyMatch = true and AllowAnyMatch = false is over 5,000:1.  Setting this
paramater to false is especially useful when running with very large Product data bases.  When AllowAnyMatch is false,
a different more efficient algorithm is used to calculate the intersection of products matchine all search criteria.

### *-dProductReviewLength=32*

Each time a product is found to match a particular customer inquiry, the simulation randomly generates a product review containing this many words.  Product reviews represent thread-local data that is allocated before the customer begins to think about the choice between multiple candidate products.  The data is not discarded until after the customer has made this choice.  The final selection between multiple candidate products depends on how well the respective product reviews match the customer's selection criteria.  Increasing this value results in an increase in ephemeral and transient memory allocation and an increase in the effort required to compute the goodness of matches against the customer's selection criteria.

### *-dSelectionCriteriaCount=8*

Faced with a choice between multiple products matching an initial product inquiry, the customer generates its selection criteria by catenating together this many randomly selected words.  Increasing this value results in an increase in ephemeral and transient memory allocation and an increase in the effort required to compute the goodness of matches against the customer's selection criteria.

### *-dBuyThreshold=0.4*

Within a simulated customer session, after selecting a preferred product, the customer decides to purchase the product with probability specified by this value.  When a product is selected for purchase, a representation of the pending purchase is added to a SalesTransactionQueue.  After a background server thread processes the pending sales transaction, its data representation becomes garbage.  The frequency with which pending sales transactions are processed depends on the number of server threads and their execution periods.  If a selected product is neither purchased nor saved for later, the prospective SalesTransaction object is simply abandoned.  The probability of abandonment is 100% - (BuyThreshold + SaveForLaterThreshold).

### *-dSaveForLaterThreshold=0.4*

Within a simulated customer session, after selecting a preferred product, the customer decides to save this product for later consideration with probability specified by this value.  A product that is saved for later is remembered by this customer for a period of time known as the BrowsingExpiration.  For each subsequent inquiry made by this customer within BrowsingExpiration of when a product was saved for later, the product will be included in the results of any product inquiry issued by the customer.  If a selected product is neither purchased nor saved for later, the prospective SalesTransaction object is simply abandoned.  The probability of abandonment is 100% - (BuyThreshold + SaveForLaterThreshold).

### *-dServerThreads=10*

Server threads are responsible for various background activities.  Having more server threads means background tidying can be more effectively divided between multiple cores.

### *-dServerPeriod=500ms*

Each server thread repeatedly waits for the start of its next period, performs the work associated with this period, and then sleeps until the start of its next period.  When multiple server threads are active, their periods are staggered so they do not begin at the same time.  In each period, the server thread performs exactly one of the following actions: (a) processes any pending sales that are associated with its affiliated SalesTransactionQueue, (b) expires any products on its affiliated BrowsingHistoryQueue whose expiration time has been reached, (c) replaces CustomerReplacementCount customers in the global customer data base if at least CustomerReplacementPeriod time has passed since the last time this server thread replaced customers, (d) replaces ProductReplacementCount products in the global product data base if at least ProductReplacementPeriod time has passed since the last time this server thread replaced products.  Setting a shorter period allows background activities to be completed more quickly.  Longer periods force certain transient objects, such as the representations of pending sales transactions, to live longer, presenting a more difficult challenge to generational garbage collectors that must refrain from promoting these pending sales representations into the old generation.

### *-dSalesTransactionQueueCount=10*

This represents the number of pending sales transaction queues.  Setting this to a larger number reduces the likelihood of contention between multiple customer threads that are inserting into these queues and multiple server threads that are extracting values from these queues.  Note that the queue itself resides in old-generation memory whereas the objects inserted into the queue reside in young-generation memory. 

### *-dBrowsingHistoryQueueCount=10*

This represents the number of browsing history queues that hold products that particular customers have saved for later consideration until the browsing history expiration time has been reached.  Setting this to a larger number  reduces the likelihood of contention between multiple customer threads that are inserting into these queues and multiple server threads that are extracting values from these queues.  Note that the queue itself resides in old-generation memory whereas the objects inserted into the queue reside in young-generation memory.

### *-dBrowsingExpiration=10m*

This represents the length of time a product saved for later by a customer will remain in the customer's browsing history.  Setting this to a longer value will cause saved-for-later products to have extended relevancy, increasing the likelihood they will be selected for future purchases. Longer browsing expiration times also increase the garbage collector's challenges that transient objects not be promoted into old-generation memory.

### *-dCustomerReplacementPeriod=60s*

Each server thread replaces CustomerReplacementCount customers once every CustomerReplacementPeriod.  This activity results in new garbage within old-generation memory.  Having a shorter CustomerReplacementPeriod or a larger CustomerReplacementCount will result in more significant mutation of old-generation memory, ultimately resulting in more frequent need for full garbage collections.

### *-dCustomerReplacementCount=16*

Each server thread replaces CustomerReplacementCount customers once every CustomerReplacementPeriod.  This activity results in new garbage within old-generation memory.  Having a shorter CustomerReplacementPeriod or a larger CustomerReplacementCount will result in more significant mutation of old-generation memory, ultimately resulting in more frequent need for full garbage collections.

### *-dProductReplacementPeriod=90s*

Each server thread replaces ProductReplacementCount products once every ProductReplacementPeriod.  This activity results in new garbage within old-generation memory.  Having a shorter ProductReplacementPeriod or a larger ProductReplacementCount will result in more significant mutation of old-generation memory, ultimately resulting in more frequent need for full garbage collections.

### *-dProductReplacementCount=64*

Each server thread replaces ProductReplacementCount products once every ProductReplacementPeriod.  This activity results in new garbage within old-generation memory.  Having a shorter ProductReplacementPeriod or a larger ProductReplacementCount will result in more significant mutation of old-generation memory, ultimately resulting in more frequent need for full garbage collections.

### *-dSimulationDuration=10m*

The simulation ends after all customer and server threads have been running for this amount of time.  The threads do not begin to run until InitializationDelay has passed since the start of execution, assuring that all global and thread-local data structures have been initialized prior to the start of the simulation run.  Thus, the true expected duration of the simulation run equals the total number of server and customer threads multiplied by 500 microseconds, plus the InitializationDelay, plus SimulationDuration, plus whatever additional time is required to produce the report that describes the performance and latency metrics for the simulated workload.  Each customer thread and each server thread terminates as soon as it confirms that the start of its next execution period would occur after the intended end of the simulation.  Thus, it is possible that the simulation will begin reporting results even before the requested end time for the simulation.

### *-dReportIndividualThreads=false*

Setting this value to true results in a detailed summary of each thread's individual behavior.  Otherwise, the behaviors of all threads are folded together into a single cumulative report.

### *-dReportCSV=false*

Setting this value to true causes the produced reports to be output as comma-separated values that can be imported into Excel for further analysis.  This feature is only partially implemented at this time.

### *-dResponseTimeMeasurements=0*

Setting this value to a value greater than 0 causes logs of the specified number of response times to be maintained by each Customer thread and each Server thread.  When these logs are maintained, a percentile report is output based on the content of these logs.  If the simulation run gathers more response time measurements than the specified size of each log, arbitrarily selected latency measurements will be ignored.  Care is taken, however, to not lose track of the minimum and maximum values logged by each thread.  Each thread maintains an independent log of the specified size.  Thus, changing the value of this simulation parameter has the effect of changing the size of live memory that is managed by the simulated workload.

## Example executions

To run the default workload configuration from within this directory, after successfully running "make install":
```
java -jar src/main/java/extremem.jar
```
To run a very small memory configuration for 1 minute duration:
```
java -jar src/main/java/extremem.jar \
    -dDictionarySize=100 -dServerThreads=1 -dCustomerThreads=1 \
    -dNumProducts=10 -dNumCustomers=1 -dBrowsingHistoryQueueCount=1 \
    -dSalesTransactionQueueCount=1 -dSimulationDuration=1m
```
To run with GC logging enabled:
```
java "-Xlog:gc*,gc+phases=debug" -jar src/main/java/extremem.jar
```
To run a stress workload, try:
```
java -jar src/main/java/extremem.jar \
    -dDictionarySize=50000 -dCustomerThreads=1000 \
    -dCustomerPeriod=12s -dCustomerThinkTime=8s -dSimulationDuration=20m
```

## Interpreting Results

The report displays response times for each of the various distinct operations that are performed by the Extremem workload.  The average response times give an approximation of overall performance.  A lower average response time corresponds to improved throughput.

Also reported are the minimum and maximum latencies and a histogram showing the distribution of latencies between these minimum and maximum values.  JVM configurations that offer predictable latency will have a small spread between the minimum and maximum latencies.  Typical JVM configurations which are not well suited to workloads that require compliance with tight timeliness constraints exhibit an average latency that is very close to the minimum latency, with maximum latencies that are tens or hundreds of ms from the minimum.  A JVM that is well suited for time-critical workloads will report maximum latencies that are within a few ms of the minimum latency.  The tolerance for variation in response times depends on the needs of individual real-world applications.

Certain additional information is included in the reports to help characterize attributes of the workload that might be responsible for variations in reported latency measurements.  For example, certain combinations of configuration choices might result in high contention for locked resources.  In this case, lock contention rather than garbage collection shortfalls may be responsible for inconsistencies in response latencies.  Consult the reports of locking behaviors to assess the impact of locking on response latencies.

The Extremem workload uses random number generation to govern behavior.  With certain Extremem configuration choices, it is possible that random selection of behaviors within the constraints of the configuration parameters is responsible for the high variability in response latencies.  For example, if the number of products produced in response to a customer inquiry ranges from 0 to 100, this must be taken into account in the analysis of latency variability.  Consult the reports of workload variability to assess the impact of random behavior choices on response latencies.

In the case that average latencies for particular operations are not close to the minimum latency, it is likely that the Extremem workload as configured exceeds the capacity of the JVM configuration.  Confirm this by running the experiment for a longer duration and comparing the average reported latencies.  If the system's resources are over subscribed, expect that average response times to increase proportional to the length of the simulation duration.

    
