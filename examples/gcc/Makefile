CC := gcc
LIB_DIR := mylib
LIB_SRC_DIR := $(LIB_DIR)/src
LIB_INCLUDE_DIR := $(LIB_SRC_DIR)/include
OS := $(shell uname -s)
ifeq ($(OS), Darwin)
   LINK_OPTS=-dynamiclib
   LIB_EXT=dylib
else
   LINK_OPTS=-shared
   LIB_EXT=so
endif

OBJECT_DIR := target/objects
LIB_SRC_FILES = $(wildcard $(LIB_SRC_DIR)/*.c) $(wildcard $(LIB_SRC_DIR)/**/*.c)
OBJFILES := $(patsubst $(LIB_SRC_DIR)%.c, $(OBJECT_DIR)%.o, $(LIB_SRC_FILES))

.PHONY: run

$(OBJECT_DIR)/%.o: $(LIB_SRC_DIR)/%.c $(LIB_INCLUDE_DIR)/mylib.h
	mkdir -p $(@D)
	$(CC) -c $< -I$(LIB_INCLUDE_DIR) -o $@

target/libmylib.$(LIB_EXT): $(OBJFILES)
	$(CC) $(LINK_OPTS) -o $@ $^

target/main.out: src/main.c target/libmylib.$(LIB_EXT) $(LIB_INCLUDE_DIR)/mylib.h
	$(CC) -I$(LIB_INCLUDE_DIR) $< -Ltarget -lmylib -o $@

run: target/main.out
	$< 5
