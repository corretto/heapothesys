JVM_OPTS=-Djava.library.path=. -Xms4g -Xmx4g -verbose:gc

all:
#	javac -h . CriticalGC.java
	cc -I. -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -shared -o libCriticalGC.so CriticalGC.c

clean:
	rm CriticalGC.class libCriticalGC.so CriticalGC.h *.log

run-parallel:
	time java ${JVM_OPTS} -XX:+UseParallelGC CriticalGC

run-cms:
	time java ${JVM_OPTS} -XX:+UseConcMarkSweepGC CriticalGC

run-g1:
	time java ${JVM_OPTS} -XX:+UseG1GC CriticalGC

run-shenandoah:
	time java ${JVM_OPTS} -XX:+UseShenandoahGC CriticalGC
