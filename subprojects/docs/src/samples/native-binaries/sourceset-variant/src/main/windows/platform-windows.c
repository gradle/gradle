#include "platform.h"

const char* platform_name = "Windows";

int max_path_length() { return 260; }

// 640K ought to be enough for anybody.
unsigned long long max_memory() { return KB(640); }

int is_posix_like() { return 0; }
