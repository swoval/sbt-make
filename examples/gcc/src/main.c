#include "stdio.h"
#include "stdlib.h"
#include "mylib.h"

int main(int argc, char **argv) {
  int input = argc > 1 ? atoi(argv[1]) : 1;
  printf("foo(%d) = %d\nbar(%d) = %d\n", input, foo(input), input, bar(input));
  return 0;
}
