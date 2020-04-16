# Heapothesys 

## Introduction

Heapothesys (/hɪˈpɒθɪsɪs/) is a JVM garbage collector benchmark developed by the Amazon Corretto team. 

Announcing the open-source Heapothesys benchmark, a synthetic workload which simulates fundamental application characteristics that affect garbage collector latency. The benchmark creates and tests GC load scenarios defined by object allocation rates, heap occupancy, and JVM flags, then reports the resulting JVM pauses. OpenJDK developers can thus produce reference points to investigate capability boundaries of the technologies they are implementing. Heapothesys does not provide accurate predictions of real application behavior, as there are many unaddressed factors that also affect garbage collection performance and other JVM latencies (e.g., sharing available CPU power with the application, fragmentation effects, more dynamic and varied object demographics). Heapothesys can be regarded as a micro-benchmark that is intentionally not trying to model any application behavior besides creating time pressure for the collector. Otherwise we would measure effects of specific ways in which certain applications behave and others don’t. This constrained behavior is special in its own way, too, but it is near one end of the spectrum and thus it provides a boundary for what to expect. In particular, we can gain a rough idea of how different collector implementations perform when certain basic stress factors are dialed up and their leeway to act shrinks. This can shed some light on garbage collector choices and tuning options for applications with various load projections and latency expectations.

Heapothesys is a starting point to explore GC performance in multiple dimensions. It however, does not attempt to fully model a real application. It focuses on predictively arranging two primary factors that impact collector stress:

* The Java heap object allocation rate. 
* The Java heap occupancy, i.e. the total size of live objects, as determined by complete transitive object graph scanning by the collector. 

To predict heap occupancy and allocation rates, Heapothesys makes its own calculations based on knowledge of JVM-internal object representations, which depend on the JVM implementation in use. These are currently specific to the HotSpot JVM for JDK 8 or later. The calculations seem to agree with what HotSpot GC logs indicate as long as the following parameter is used correctly. Heapothesys cannot automatically detect when the JVM uses compressed object references, i.e., 32-bit object references in a 64-bit JVM, aka “compressedOops”. You need to set the parameter “-c” to false when running Heapothesys with a 32 GB or larger heap or with a collector that does not support “compressedOops”.

Heapothesys, while written from scratch, inherits its basic ideas from Gil Tene’s [HeapFragger](https://github.com/giltene/HeapFragger) workload. HeapFragger has additional features (e.g., inducing fragmentation and detecting generational promotion), whereas Heapothesys concentrates on accurately predicting the resulting allocation rate. Additionally, we thank to Gil for his [jHiccup](https://www.azul.com/jhiccup/) agent, which we utilize to measure JVM pauses.

## Disclaimer

This open source code is not intended to be run on any form of production system ot to run any customer code.

## Security

If you would like to report a potential security issue in this project, please do not create a GitHub issue. Instead, please follow the instructions here(https://aws.amazon.com/security/vulnerability-reporting/ ) or email AWS security directly.

## Usage

Invocation with the minimum recommended set of Heapothesys parameters and a typical jHiccup configuration:
```
java -Xmx<bytes> -Xms<bytes> <GC options> <other JVM options> -Xloggc:<GC log file> -javaagent:<jHiccup directory>/jHiccup.jar='-a -d 0 -i 1000 -l <jHiccup log file>' -jar <Heapothesys directory>/Heapothesys-1.0.jar -a <MB> -h <MB> -d <seconds> -c <true/false> -l <CVS output file>
```

### Build

The project can be built using [Maven](https://maven.apache.org/). Run the following command:
```
mvn clean install
```
The JAR file can be found in the *target* folder.

### Command-line Arguments

The two primary arguments are allocation rate and heap occupancy:

* *-a < target allocation rate in Mb per second >, default: 1024 *
* *-s < target heap occupancy in Mb >, default: 64 *

Currently, the benchmark program needs to be told the heap size in use.
* *-h < heap size in Mb >

The benchmark cannot always achieve the specified values. In particular, the run duration must be long enough for Heapothesys to meet the heap occupancy target, especially for those low allocation rate cases. You can set the benchmark run duration using:

* *-d < run duration in seconds >, default: 60 *

At end of the run, Heapothesys writes the actual achieved allocation rate and the configuration into a file.

* *-l < result file name >, default: output.csv *

If you run with a 32G or larger heap or with a collector that does not support 32-bit object pointers, aka "compressedOops", you must set this paramteter to "false". Otherwise all object size calculations are off by nearly 50%. Currently, Heapothesys does not automatically detect this.

* *-c < compressedOops support >, default: true *

In order to achieve high allocation rates, Heapothesys uses multiple worker threads. If the hardware has enough CPU cores, you can increase the number of worker threads to ensure achieving the target allocation rate.

* *-t < number of worker threads >, default: 4*

At run time, Heapothesys worker threads randomly create objects within a size range defined by the mimimum and maximum object size arguments:

The bigger the average object size, the easier to generate a high allocation rate. However, larger object sizes also mean a lower object count when the allocation rate is fixed. This makes the reference graph less complex, which in turn reduces the tracing and marking load on the Garbage Collector. Some experimentation may be neccessary to determine a better representative default setting here.

* *-n < minimum object size in byte >, inclusive, default: 128 bytes *
* *-x < maximum object size in byte >, exclusive, default: 1Kb *

The following options are all experimental features that we don't touch or set to minimal values when we want to observe collector behavior that is not influenced by any specific application behavior.

The next pair of arguments are used to control refreshing the long-lived object store, which is explained in the [Implementation](#object-store) section. The first defines the ratio at which objects get replaced in the object store.

* *-r < ratio of objects get replaced per minute >, default: 50 *

The default value 50 means that 1/50 of the objects in the store are replaced with new objects every minute. For Generational Garbage Collectors, this ensures that major collections happen at some point. The second one is to exercise objects within the Object Store. It selects a portion of objects and reshuffles their values and references.

* *-f < ratio of objects get reshuffled >, default: 100 *

The default value 100 means that when the Object Store replaces the objects, it will also pick 1/100 of the objects in the store and reshuffle their references.

### Example

We normally use [JHiccup](https://www.azul.com/jhiccup/) to measure JVM pauses. You can also use GC logs to measure safepoint times, allocation stalls, and Garbage Collection pauses. In the exmaple below, we run Heapothesys for the Shenandoah collector for 10 minutes using a 16Gb/s allocation rate and with 32Gb of a 64Gb heap occupied by long-lived objects.

```
jdk/jdk-13.0.2+8/bin/java -Xmx65536m -Xms65536m -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseLargePages -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -Xloggc:./results/16384_65536_32768/gc.log -javaagent:./buildRoot/jar/jHiccup.jar='-a -d 0 -i 1000 -l ./results/16384_65536_32768/heapothesis.hlog' -jar ./buildRoot/jar/Heapothesys-1.0.jar -a 16384 -h 32768 -d 600 -m 128 -c false -t 16 -n 64 -x 32768 -l ./results/16384_65536_32768/output.csv
```

This command sets JHiccup as a Java agent and use it to create the hiccup log. The *output.csv* file contains the following information:
```
65536,16384,16384,0.5,false,16,64,32768,50,100,
```
The first column is the heap size in Mb, the second is allocation rate in Kb/sec, and the third is the actual achieved allocation rate. The fourth column is the long-lived object heap occupancy fraction.

## Implementation

The implementation of Heapothesys follows three main components.

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
