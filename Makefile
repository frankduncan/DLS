ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

ifneq (,$(findstring CYGWIN,$(shell uname -s)))
  COLON=\;
  JAVA_HOME := `cygpath -up "$(JAVA_HOME)"`
else
  COLON=:
endif

JAVAC:=$(JAVA_HOME)/bin/javac
SRCS=$(wildcard src/*.java)

dls.jar: $(SRCS) manifest.txt Makefile
	mkdir -p classes
	$(JAVAC) -g -deprecation -Xlint:all -Xlint:-serial -Xlint:-path -encoding us-ascii -source 1.6 -target 1.6 -classpath $(NETLOGO)/NetLogoLite.jar:$(NETLOGO)/NetLogo.jar -d classes $(SRCS)
	jar cmf manifest.txt dls.jar -C classes .

dls.zip: dls.jar
	rm -rf dls
	mkdir dls
	cp -rp dls.jar README.md Makefile src manifest.txt dls
	zip -rv dls.zip dls
	rm -rf dls
