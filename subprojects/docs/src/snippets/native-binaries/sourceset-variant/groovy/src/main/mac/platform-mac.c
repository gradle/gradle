#include "platform.h"

const char* platform_name = "MacOSX";

int max_path_length() { return 1024; }

unsigned long long max_memory() { return GB(96); }

int is_posix_like() { return 1; }
