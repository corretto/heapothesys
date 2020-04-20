# Heapothesys 

## Introduction

Heapothesys /hɪˈpɒθɪsɪs/ is a heap allocation JVM benchmark developed by the Amazon Corretto team. 

Heapothesys is a synthetic workload which simulates fundamental application characteristics that affect Garbage Collector latency. The benchmark creates and tests GC load scenarios defined by object allocation rates, heap occupancy, JVM flags, and hardware types. OpenJDK developers can use it to investigate relevant capability boundaries of the technologies they are implementing. It also helps narrow down initial garbage collector choices and tuning options for applications with various load projections and latency expectations.

* A Java heap object allocation rate target.
* A heap occupancy after major Garbage Collection target.

Heapothesys allows us to create load scenarios defined by these two parameters and quickly test them, changing JVM flags, object allocation rates, and heap sizes. JVM developers can use it to assess the strengths and weaknesses of the technologies they are implementing.

Heapothesys, while written from scratch, inherits its basic ideas from Gil Tene’s [HeapFragger](https://github.com/giltene/HeapFragger) workload. HeapFragger has additional features (e.g., inducing fragmentation and detecting generational promotion), whereas Heapothesys concentrates on accurately predicting the resulting allocation rate. Additionally, we thank to Gil for his [jHiccup](https://www.azul.com/jhiccup/) agent, which we utilize to measure JVM pauses.

## Disclaimer

The benchmark can be used to create load scenarios, test allocation rates in a JVM which the JVM developers(end-users) can use to assess the strengths and weaknesses of the technologies they are implementing.

This open source is not intended to be run on any form of production workload and will not run any customer code.

## Security

If you would like to report a potential security issue in this project, please do not create a GitHub issue. Instead, please follow the instructions here(https://aws.amazon.com/security/vulnerability-reporting/ ) or email AWS security directly.

## Usage

Heapothesys currently supports a steady allocation rate throughout the run. Its arguments are used to create and characterize such a load.

### Build

The project can be built using [Maven](https://maven.apache.org/). Run the following command:
```
mvn clean install
```
The JAR file can be found in the *target* folder.

### Command-line Arguments

The two primary arguments are allocation rate and heap occupancy:

* *-a < allocation rate in Mb per second >, default: 1024*
* *-h < heap occupancy in Mb >, default: 64*

The benchmark cannot always achieve the specified values. Run duration must be long enough for Heapothesys to meet the heap occupancy target, especially for those low allocation rate cases. You can set the benchmark run duration using:

* *-d < run duration in seconds >, default: 60*

In order to achieve high allocation rates, Heapothesys uses multiple worker threads. If the hardware has enough CPU cores, you can increase the number of worker threads to ensure achieving the target allocation rate.

* *-t < number of worker threads >, default: 4*

At run time, Heapothesys worker threads randomly create objects within a size range defined by the mimimum and maximum object size arguments:

* *-n < minimum object size in byte >, inclusive, default: 128 bytes*
* *-x < maximum object size in byte >, exclusive, default: 1Kb*

The bigger the average object size, the easier to generate a high allocation rate. However, larger object sizes also mean a lower object count when the allocation rate is fixed, which makes the reference graph less complex. That in turn reduces the tracing and marking load on the Garbage Collector. It is best to make the object size range be as representative as possible of the application you have in mind.

The next pair of arguments are used to control refreshing the long-lived object store, which is explained in the [Implementation](#object-store) section. The first defines the ratio at which objects get replaced in the object store.

* *-r < ratio of objects get replaced per minute >, default: 50*

The default value 50 means that 1/50 of the objects in the store are replaced with new objects every minute. For Generational Garbage Collectors, this ensures that major collections happen at some point. The second one is to exercise objects within the Object Store. It selects a portion of objects and reshuffles their values and references.

* *-f < ratio of objects get reshuffled >, default: 100*

The default value 100 means that when the Object Store replaces the objects, it will also pick 1/100 of the objects in the store and reshuffle their references.

Keep in mind is Compressed OOPs. To get an accurate allocation rate, you need to set the calculation for CompressedOops to false when you run with a 32G or larger heap, or with Garbage Collectors which do not support Compressed OOPs. At the moment, the tool does not automatically detect these situations.

* *-c < compressedOops support >, default: true*

At end of the run, Heapothesys writes the actual achieved allocation rate and the configuration into a file.

* *-l < result file name >, default: output.csv*

### Example

We normally use [JHiccup](https://www.azul.com/jhiccup/) to measure JVM pauses. You can either download it from its [website](https://www.azul.com/jhiccup-2/), or build it from the source code in its [GitHub repo](https://github.com/giltene/jHiccup). You can also use GC logs to measure safepoint times, allocation stalls, and Garbage Collection pauses. In the exmaple below, we run Heapothesys for the Shenandoah collector for 40 minutes using a 16Gb/s allocation rate and with 32Gb of a 64Gb heap occupied by long-lived objects.

```
jdk/jdk-13.0.2+8/bin/java -Xmx65536m -Xms65536m -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=traversal -XX:+UseLargePages -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -Xloggc:./results/16384_65536_32768/gc.log -javaagent:<path to jHiccup>/jHiccup.jar='-a -d 0 -i 1000 -l ./results/16384_65536_32768/heapothesis.hlog' -jar ./buildRoot/jar/Heapothesys-1.0.jar -a 16384 -h 65536 -d 2400 -m 128 -s 32640 -c false -t 16 -n 64 -x 32768 -l ./results/16384_65536_32768/output.csv
```

This command sets JHiccup as a Java agent and use it to create the hiccup log. The *output.csv* file contains the following information:
```
65536,16384,16384,0.5,false,16,64,32768,50,100,
```
The first column is the heap size in Mb, the second is allocation rate in Kb/sec, and the third is the actual achieved allocation rate. The fourth column is the long-lived object heap occupancy fraction.

## Implementation

The implementation of Heapothesys consists of three main components.

### Token Bucket

The implementation of the [Token Bucket](https://en.wikipedia.org/wiki/Token_bucket) algorithm is used by both Allocation Workers and the Object Store. For Allocation Workers, the bucket is refilled every second to control the allocation rate, while the Object Store uses it to control the per minute object replacing rate.

### Allocation Workers

The allocation work load are evenly divided among the Allocation Worker threads. A worker thread uses a token bucket to control its allocation rate. Generated objects are put into a list to make sure they will not be immediately collected. The life time of short lived objects can be controlled by setting the list length.

All worker threads share a single long-lived Object Store. When an object is removed from the internal list, a worker thread will try to add it to the input queue of the long-lived Object Store, but the Object Store controls whether to accept it based on its defined size. There is exponential backoff on attempts to promote short-lived objects into the long-lived object store.

### Object Store

The Object Store is used to store long-lived objects. It has two primary components:

 * A input queue to accept objects from allocation workers.
 * A list of object groups as an object store.

To start with, the Object Store just picks up objects from its input queue and transfers them into the store. Once the store size reaches the defined Object Store size, the Object Store uses a Token Bucket to control the rate at which to pick up objects from the input queue and randomly replace objects in the store. New objects are added to the input queue by Allocation Workers.

The Object Store is organized as a list of object lists. Objects from list *i* can randomly reference objects from the list *i+1*. Reshuffle happens during an object replacing cycle. Based on the setting of the reshuffle ratio, the Object Store picks some number of lists and shuffles their objects. Shuffling changes existing references to objects in the *i+1* list to different ones from the same list.
