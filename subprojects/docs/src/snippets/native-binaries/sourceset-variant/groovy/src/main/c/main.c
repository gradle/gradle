#include "platform.h"
#include "stdio.h"

int main(int argc, char** argv) {
  printf("Attributes of '%s' platform\n", platform_name);
  printf("Is Posix like?        %s\n", is_posix_like()?"true":"false");
  printf("Max Path Length:      %d bytes\n", max_path_length());
  printf("Max memory supported: %llu Kbytes\n", max_memory());
  return 0;
}
