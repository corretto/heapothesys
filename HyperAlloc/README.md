# HyperAlloc 

## Introduction

HyperAlloc (/haɪpər alloc/) is a JVM garbage collector benchmark developed by the Amazon Corretto team. 

The Amazon Corretto team introduces the open-source HyperAlloc benchmark, a synthetic workload which simulates fundamental application characteristics that affect garbage collector latency. The benchmark creates and tests GC load scenarios defined by object allocation rates, heap occupancy, and JVM flags, then reports the resulting JVM pauses. OpenJDK developers can thus produce reference points to investigate capability boundaries of the technologies they are implementing. HyperAlloc is intended to be further enhanced to better model and predict additional application behaviors, (e.g., sharing available CPU power with the application, fragmentation effects, more dynamic and varied object demographics, OS scheduling symptoms). The application behavior that it currently simulates is narrowly specialized in its own way, but it is also intentionally minimalistic to provide boundary cases for what to expect. We aim to gain a rough idea of how different collector implementations perform when certain basic stress factors are dialed up and the collector’s leeway to act shrinks. With some cautious optimism, this setup can shine light on garbage collector choices and tuning options for application load projections and latency expectations.

HyperAlloc focuses on these two primary factors that are directly responsible for collector stress by increasing the urgency with which it has to act and thus play an important role when investigating GC behavior:

* The Java heap object allocation rate. 
* The Java heap occupancy, i.e. the total size of live objects, as determined by complete transitive object graph scanning by the collector. 

To configure HyperAlloc to reach a high sustained allocation rate, there are two parameters to vary: the number of worker threads and the object size range. On multi-core hosts, the most important one is the number of worker threads (-t <number of worker threads>, default: 4). The object size range is given by the minimum object size (-n <minimum object size in byte>, inclusive, default: 128 byte) and maximum object size (-x <maximum object size in byte>, exclusive, default: 1 KB). When creating a new object, HyperAlloc picks a random size between these two. The larger the allocated objects, the easier it is to achieve higher allocation rates. Using smaller objects, the constructed reference graphs become more complex. This provides limited experimentation with different allocation profiles. By default, HyperAlloc makes an educated guess based on the number of available CPU cores and the specified heap size.

As an experimental feature, HyperAlloc makes dynamic changes to the created object graph in order to exercise the memory read and write barriers typical of concurrent garbage collectors. Before beginning its main test phase, it stores long-lived objects in a hierarchical list of object groups. In order to exercise garbage collector marking phases, higher group objects randomly reference objects in the next lower group to create a somewhat complex and randomized reference graph. This graph does not remain static: HyperAlloc constantly replaces a portion of it and reshuffles references between the objects in it. You can control the long-lived object replacement ratio by specifying the -r option (<ratio of objects being replaced per minute>, default: 50). The default value means that 1/50 of objects will be replaced per minute. The reshuffled object reference ratio (-f <ratio of objects get reshuffled>, default: 100) default value means that when replacement happens, 1/100 of inter-object references are reshuffled.

To predict heap occupancy and allocation rates, HyperAlloc makes its own calculations based on knowledge of JVM-internal object representations, which depend on the JVM implementation in use. These are currently specific to the HotSpot JVM for JDK 8 or later. The calculations seem to agree with what HotSpot GC logs indicate as long as the following parameter is used correctly. HyperAlloc cannot automatically detect when the JVM uses compressed object references, i.e., 32-bit object references in a 64-bit JVM, aka “compressedOops”. You need to set the parameter “-c” to false when running HyperAlloc with a 32 GB or larger heap or with a collector that does not support “compressedOops”.

HyperAlloc, while written from scratch, inherits its basic ideas from Gil Tene’s [HeapFragger](https://github.com/giltene/HeapFragger) workload. HeapFragger has additional features (e.g., inducing fragmentation and detecting generational promotion), whereas HyperAlloc concentrates on accurately predicting the resulting allocation rate. Additionally, we thank to Gil for his [jHiccup](https://www.azul.com/jhiccup/) agent, which we utilize to measure JVM pauses.

## Disclaimer

This open source code is not intended to be run on any form of production system or to run any customer code.

The implementation can still be improved in many ways and updates are planned. In particular we aim to address:
- The shape of the object graph seems specialized, although it is meant to have little effect. We need to evaluate it for unwanted performance artefacts, compare with alternatives, and if necessary modify this shape.
- In cases where the desired maximum allocation rate is not met, we should check where if is a bottleneck in the benchmark.
- Stylistic issues in the code. In particular, we could remove certain unused setup complexities.
- Thread naming.
- Better instructions to use the benchmark and to analyze its output.

## Security

If you would like to report a potential security issue in this project, please do not create a GitHub issue. Instead, please follow the instructions here(https://aws.amazon.com/security/vulnerability-reporting/ ) or email AWS security directly.

## Usage

Invocation with the minimum recommended set of HyperAlloc parameters and a typical jHiccup configuration:
```
java -Xmx<bytes> -Xms<bytes> <GC options> <other JVM options> -Xloggc:<GC log file> -javaagent:<jHiccup directory>/jHiccup.jar='-a -d 0 -i 1000 -l <jHiccup log file>' -jar <HyperAlloc directory>/HyperaAlloc-1.0.jar -a <MB> -h <MB> -d <seconds> -c <true/false> -l <CVS output file>
```

### Build

The project can be built using [Maven](https://maven.apache.org/). Run the following command:
```
mvn clean install
```
The JAR file can be found in the *target* folder.

### Command-line Arguments

The two primary arguments are allocation rate and heap occupancy:

* -a < target allocation rate in Mb per second >, default: 1024
* -s < target heap occupancy in Mb >, default: 64 

Currently, the benchmark program needs to be told the heap size in use.
* -h < heap size in Mb >

The benchmark cannot always achieve the specified values. In particular, the run duration must be long enough for HyperAlloc to meet the heap occupancy target, especially for those low allocation rate cases. You can set the benchmark run duration using:

* -d < run duration in seconds >, default: 60

At end of the run, HyperAlloc writes the actual achieved allocation rate and the configuration into a file.

* -l < result file name >, default: output.csv

If you run with a 32G or larger heap or with a collector that does not support 32-bit object pointers, aka "compressedOops", you must set this paramteter to "false". Otherwise all object size calculations are off by nearly 50%. Currently, HyperAlloc does not automatically detect this.

* -c < compressedOops support >, default: true

In order to achieve high allocation rates, HyperAlloc uses multiple worker threads. If the hardware has enough CPU cores, you can increase the number of worker threads to ensure achieving the target allocation rate.

* -t < number of worker threads >, default: 4

At run time, HyperAlloc worker threads randomly create objects within a size range defined by the mimimum and maximum object size arguments:

The bigger the average object size, the easier to generate a high allocation rate. However, larger object sizes also mean a lower object count when the allocation rate is fixed. This makes the reference graph less complex, which in turn reduces the tracing and marking load on the Garbage Collector. Some experimentation may be neccessary to determine a better representative default setting here.

* -n < minimum object size in byte >, inclusive, default: 128 bytes
* -x < maximum object size in byte >, exclusive, default: 1Kb

The following options are all experimental features that we don't touch or set to minimal values when we want to observe collector behavior that is not influenced by any specific application behavior.

The next pair of arguments are used to control refreshing the long-lived object store, which is explained in the [Implementation](#object-store) section. The first defines the ratio at which objects get replaced in the object store.

* -r < ratio of objects get replaced per minute >, default: 50

The default value 50 means that 1/50 of the objects in the store are replaced with new objects every minute. For Generational Garbage Collectors, this ensures that major collections happen at some point. The second one is to exercise objects within the Object Store. It selects a portion of objects and reshuffles their values and references.

* -f < ratio of objects get reshuffled >, default: 100

The default value 100 means that when the Object Store replaces the objects, it will also pick 1/100 of the objects in the store and reshuffle their references.

### Example

We normally use [JHiccup](https://www.azul.com/jhiccup/) to measure JVM pauses. You can either download it from its [website](https://www.azul.com/jhiccup-2/), or build it from the source code in its [GitHub repo](https://github.com/giltene/jHiccup). You can also use GC logs to measure safepoint times, allocation stalls, and Garbage Collection pauses. In the exmaple below, we run HyperAlloc for the Shenandoah collector for 10 minutes using a 16Gb/s allocation rate and with 32Gb of a 64Gb heap occupied by long-lived objects.

```
jdk/jdk-13.0.2+8/bin/java -Xmx65536m -Xms65536m -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseLargePages -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -Xloggc:./results/16384_65536_32768/gc.log -javaagent:<path to jHiccup>/jHiccup.jar='-a -d 0 -i 1000 -l ./results/16384_65536_32768/hyperalloc.hlog' -jar ./buildRoot/jar/HyperAlloc-1.0.jar -a 16384 -h 32768 -d 600 -m 128 -c false -t 16 -n 64 -x 32768 -l ./results/16384_65536_32768/output.csv
```

This command sets JHiccup as a Java agent and use it to create the hiccup log. The *output.csv* file contains the following information:
```
65536,16384,16384,0.5,false,16,64,32768,50,100,
```
The first column is the heap size in Mb, the second is allocation rate in Kb/sec, and the third is the actual achieved allocation rate. The fourth column is the fraction of the total heap size that holds long-lived objects.  Subsequent entries in this line denote whether the the run uses compressed OOPs, the number of threads (as specified by -t option), the minimum allocated object size (as specified by the -n option), the maximum allocated object size (as specified by the -x option), the prune ratio (as specified by the -r option), and the reshuffle ratio (as specified by the -f option).

The sample command sets up hyperalloc.hlog to accumulate data from the JHiccup Java agent.  This output file is not human readable.  Use the jHiccupLogProcessor program to convert this file into a form that is more easily understood by human readers.  See the README.md file for jHiccup for additional detail.  The typical command is:

```
$ jHiccupLogProcessor -i hyperalloc.hlog -o readable.log
```

This produces two files, readable.log and readable.log.hgrm.  The first of these two files contains one line of output for approximately each 1-second interval of execution.  Consider, for example, the following two lines of this log file:
```
Time: IntervalPercentiles:count ( 50% 90% Max ) TotalPercentiles:count ( 50% 90% 99% 99.9% 99.99% Max )
1.783: I:832 (   4.653  62.128 140.509 ) T:832 (   4.653  62.128 132.121 140.509 140.509 140.509 )
```
During the reporting interval starting at time 1.783 seconds (from start up), 832 samples were collected.  Of these 832 samples, the P50 response time was 4.653 ms, the P90 response time was 62.128 ms, and the P100 (labeled Max) response time was 140.509 ms.  For this initial entry, the balues reported in the cumulative TotalPrecentiles columns are identical to the values in the IntervalPercentiles columns.  Besides reporting the same values for the P50, P90, and P100 entires, the cumulative columns also report a P99 value of 132.121 ms, a P99.9 value of 140.509 ms, and a P99.99 value of 140.509.  Since the total number of data samples is less than 1,000, the P99.9 value equals the P99.99 value which equals the P100 value.

The next line of this file reports on the measurements gathered during the second reporting interval, starting at 2.788 seconds (from startup).
```
2.788: I:825 (   3.506  17.170  43.254 ) T:1657 (   3.981  36.176 124.256 139.461 140.509 140.509 )
```
During this second interval, an additional 825 data samples were collected.  In the cumulative total columns, 825 is added to 832 to obtain a cumulative total of 1657 data samples.  For this second interval of time, the P50 response time was 3.506 ms, the P90 was 17.170 ms, and the P100 was 43.254 ms.  The cumulative totals columns show the percentile measurements for the two-second span accumulated from the first two intervals.  Note that the P100 and P99.99 percentiles are the same as for the first cumulative interval since the total number of accumulations is still less than 10,000 and the maximum response times from the first interval are longer than the maximum response times measured during the second interval.  Note that the P99.9 value for the two-second span is smaller than for the initial one-second span since the total number of samples is now greater than 1,000 and the second longest measurement from the initial time interval was remembered or approximated to have had value 139.461.  The cumulative percentiles for P50, P90, and P99 are all smaller than the values reported in the previous line of cumulative output.  This is because all of the response-time percentiles measured in the second time interval are lower than the values measured in the first time interval.

The last line of the readable.log file represents the last one-second interval measured during this execution run.  Note that a total of 48,679 data samples were gathered during 60.777 seconds of execution.  
```
60.777: I:801 (   3.457  14.418  36.700 ) T:48679 (   3.949  35.127 106.430 135.266 146.801 150.995 )
```
The content of readable.log.hgrm file is a histogram representation of the cumulative percentiles for the complete execution.  The first few lines of this file might be the following:
```
#[Overall percentile distribution between 0.000 and <Infinite> seconds (relative to StartTime)]
#[StartTime: 1596849642.829 (seconds since epoch), Sat Aug 08 01:20:42 UTC 2020]
       Value     Percentile TotalCount 1/(1-Percentile)

        0.02 0.000000000000         32           1.00
        0.15 0.100000000000       4998           1.11
```
This tells us that 32 data samples were measured to require less than 0.02 ms of time, with a total of 4,998 data samples requiring less than 0.15 ms of time.  The percentiles represented by these two entries are P0 (actually, P0.07, but rounded down) and P10.0 respectively.  Subsequent entries in this histogram chart correspond to the cumulative P50, P90, P99, P99.9, and P100 values reported in the last entry of the readable.log show above:
```
...
        3.95 0.500000000000      24351           2.00
...
       35.13 0.900000000000      43834          10.00
...
      104.86 0.989062500000      48156          91.43
      108.00 0.990625000000      48235         106.67
...
      134.22 0.998828125000      48625         853.33
      135.27 0.999023437500      48633        1024.00
...
      145.75 0.999890136719      48674        9102.22
      146.80 0.999902343750      48675       10240.00
...
      150.99 0.999981689453      48679       54613.33
      150.99 1.000000000000      48679
```

Note that the sample command line also requests generation of GC log files.  The content and interpretation of these log files depends on the type of garbage collection that is being performed.

It is important to recognize that jHiccup only measures pauses that impact the entire JVM at once.  Because of the way the jHiccup agent is attached to the HyperAlloc test workload, it is not able to measure or report pauses that might be seen by only one thread at a time.  This includes pauses caused by GC pacing and delays caused by copy-on-first-access read barriers with Shenandoah and ZGC garbage collectors.  The heapothesys/Extremem workload accounts for and reports these other effects in addition to global JVM pauses.

## Implementation

The implementation of HyperAlloc follows three main components.

### Token Bucket

A [token bucket](https://en.wikipedia.org/wiki/Token_bucket) algorithm is used by allocation workers and by the object store. For allocation workers, the bucket is refilled every second to control the allocation rate. The object store uses it to control the per-minute object replacment rate.

### Allocation Workers

The allocation work load is evenly divided among "allocation worker" threads. Such a thread uses a token bucket to control its allocation rate. Generated objects are put into a list to make sure they will not be immediately collected. The life time of short lived-objects can be controlled by setting the list length.

All worker threads share a single long-lived "object store". When an object is removed from the internal list, a worker thread will try to add it to the input queue of the long-lived object store. The object store controls whether to accept it based on its defined size. There is exponential backoff on attempts to promote short-lived objects into the long-lived object store.

### Object Store

The "object store" is used to keep long-lived objects alive. It has two main parts:

 * An input queue to accept objects from allocation workers.
 * A list of object groups, the store.

The Object Store picks objects from its input queue and transfers them into the store. Once the store size reaches its defined size, the object store uses a token bucket to control the rate at which to pick up objects from the input queue and randomly replace objects in the store. New objects are added to the input queue by allocation workers.

The object store is organized as a list of object lists. Objects from list *i* can randomly reference objects from the list *i+1*. Reshuffle happens during an object replacing cycle. Based on the setting of the reshuffle ratio, the object store picks some number of lists and shuffles their objects. Shuffling changes existing references to objects in the *i+1* list to different ones from the same list.
