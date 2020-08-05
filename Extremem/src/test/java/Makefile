
PKGDIR=com/amazon/corretto/benchmark/extremem

CLASSFILES=\
 $(PKGDIR)/PlaceHolder.class

compile: class_files
	echo finished compile of class files

# Requires JAVA_HOME environment variable
libAbsoluteTime.so: com_amazon_corretto_benchmark_extremem_AbsoluteTime.cpp \
		    com_amazon_corretto_benchmark_extremem_AbsoluteTime.h
	 g++ -shared -O3 -fPIC -lrt -I/usr/include \
	 -I$$JAVA_HOME/include -I$$JAVA_HOME/include/linux \
	 com_amazon_extremem_AbsoluteTime.cpp -o libAbsoluteTime.so

test: compile
	java "-Xlog:gc*,gc+phases=debug" -Djava.library.path=$$(pwd) $(PKGDIR)/MainTest


clean:
	rm -f $(CLASSFILES)

class_files: $(CLASSFILES)

$(PKGDIR)/%.class: $(PKGDIR)/%.java
	javac $<

