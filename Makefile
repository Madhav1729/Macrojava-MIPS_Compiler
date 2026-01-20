PREFIX     ?= /usr/local
DESTDIR    ?=
BUILD      ?= build

FLEX       ?= flex
BISON      ?= bison
CXX        ?= g++
CXXFLAGS   ?= -O2 -std=c++17 -w
JAVAC      ?= javac
JAR        ?= jar
JAVA_RELEASE ?= 21
JAVACFLAGS ?= --release $(JAVA_RELEASE) -encoding UTF-8 -nowarn -XDsuppressNotes

OBJ      := $(BUILD)/obj
LIBDIR   := $(BUILD)/lib/mjc
PASSDIR  := $(LIBDIR)/passes
PP       := $(BUILD)/libexec/mjc/mjc-pp
DRIVER   := $(LIBDIR)/mjc.jar
LAUNCHER := $(BUILD)/bin/mjc

PASSES     := 2 3 4 5 6
PASS_JARS  := $(foreach n,$(PASSES),$(PASSDIR)/p$(n).jar)
DRIVER_SRC := $(wildcard driver/src/mjc/*.java)

.PHONY: all test install uninstall clean

all: $(LAUNCHER) $(DRIVER) $(PASS_JARS) $(PP)

$(OBJ)/p1/P1.tab.c: Module-1/P1.y
	@mkdir -p $(@D)
	$(BISON) -d -o $@ $<

$(OBJ)/p1/P1.lex.c: Module-1/P1.l $(OBJ)/p1/P1.tab.c
	$(FLEX) -o $@ $<

$(PP): $(OBJ)/p1/P1.lex.c $(OBJ)/p1/P1.tab.c
	@mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) -I$(OBJ)/p1 -o $@ $^

define java_pass
$(PASSDIR)/p$(1).jar: $(shell find Module-$(1) -name '*.java')
	@rm -rf $(OBJ)/p$(1)
	@mkdir -p $(OBJ)/p$(1) $$(@D)
	$(JAVAC) $(JAVACFLAGS) -d $(OBJ)/p$(1) -sourcepath Module-$(1) Module-$(1)/P$(1).java
	$(JAR) --create --file $$@ -C $(OBJ)/p$(1) .
endef
$(foreach n,$(PASSES),$(eval $(call java_pass,$(n))))

$(DRIVER): $(DRIVER_SRC)
	@rm -rf $(OBJ)/driver
	@mkdir -p $(OBJ)/driver $(@D)
	$(JAVAC) $(JAVACFLAGS) -Xlint:all -Werror -d $(OBJ)/driver $(DRIVER_SRC)
	$(JAR) --create --file $@ --main-class mjc.Main -C $(OBJ)/driver .

$(LAUNCHER): driver/mjc
	@mkdir -p $(@D)
	cp $< $@
	chmod 755 $@

test: all
	tests/run_tests.sh

install: all
	install -d $(DESTDIR)$(PREFIX)/bin $(DESTDIR)$(PREFIX)/lib/mjc/passes $(DESTDIR)$(PREFIX)/libexec/mjc
	install -m 755 $(LAUNCHER) $(DESTDIR)$(PREFIX)/bin/mjc
	install -m 644 $(DRIVER) $(DESTDIR)$(PREFIX)/lib/mjc/mjc.jar
	install -m 644 $(PASS_JARS) $(DESTDIR)$(PREFIX)/lib/mjc/passes/
	install -m 755 $(PP) $(DESTDIR)$(PREFIX)/libexec/mjc/mjc-pp

uninstall:
	rm -f $(DESTDIR)$(PREFIX)/bin/mjc
	rm -rf $(DESTDIR)$(PREFIX)/lib/mjc $(DESTDIR)$(PREFIX)/libexec/mjc

clean:
	rm -rf $(BUILD)
