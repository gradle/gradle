#include "platform.h"

const char* platform_name = "Linux";

int max_path_length() { return -1; }

unsigned long long max_memory() { return TB(128); }

int is_posix_like() { return 1; }
