
PKGDIR=com/amazon/corretto/benchmark/extremem

CLASSFILES=\
 $(PKGDIR)/AbsoluteTime.class \
 $(PKGDIR)/Actor.class \
 $(PKGDIR)/Arraylet.class \
 $(PKGDIR)/ArrayletOflong.class \
 $(PKGDIR)/Bootstrap.class \
 $(PKGDIR)/BrowsingHistory.class \
 $(PKGDIR)/BrowsingHistoryQueue.class \
 $(PKGDIR)/ConcurrencyControl.class \
 $(PKGDIR)/Configuration.class \
 $(PKGDIR)/Customer.class \
 $(PKGDIR)/CustomerLog.class \
 $(PKGDIR)/CustomerLogAccumulator.class \
 $(PKGDIR)/Customers.class \
 $(PKGDIR)/CustomerThread.class \
 $(PKGDIR)/ExtrememHashSet.class \
 $(PKGDIR)/ExtrememObject.class \
 $(PKGDIR)/ExtrememThread.class \
 $(PKGDIR)/ExtrememUncaughtExceptionHandler.class \
 $(PKGDIR)/HighResolutionTime.class \
 $(PKGDIR)/Is64Bit.class \
 $(PKGDIR)/LifeSpan.class \
 $(PKGDIR)/Main.class \
 $(PKGDIR)/MemoryFlavor.class \
 $(PKGDIR)/MemoryLog.class \
 $(PKGDIR)/Polarity.class \
 $(PKGDIR)/Product.class \
 $(PKGDIR)/Products.class \
 $(PKGDIR)/RelativeTime.class \
 $(PKGDIR)/RelativeTimeMetrics.class \
 $(PKGDIR)/Report.class \
 $(PKGDIR)/SalesTransaction.class \
 $(PKGDIR)/SalesTransactionQueue.class \
 $(PKGDIR)/ServerLog.class \
 $(PKGDIR)/ServerLogAccumulator.class \
 $(PKGDIR)/ServerThread.class \
 $(PKGDIR)/Trace.class \
 $(PKGDIR)/Util.class \
 $(PKGDIR)/Words.class

compile: class_files
	jar cmf META-INF/MANIFEST.MF extremem.jar com

test: compile
	java "-Xlog:gc*,gc+phases=debug" -Djava.library.path=$$(pwd) $(PKGDIR)/MainTest

clean:
	rm -f extremem.jar
	rm -f $(CLASSFILES)

class_files: $(CLASSFILES)

$(PKGDIR)/%.class: $(PKGDIR)/%.java
	javac $<

