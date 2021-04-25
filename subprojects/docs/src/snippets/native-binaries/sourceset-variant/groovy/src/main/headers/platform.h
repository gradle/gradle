#ifndef PLATFORM_H
#define PLATFORM_H

extern const char* platform_name;

int max_path_length();

unsigned long long max_memory();

int is_posix_like();

#define KB(x) x
#define MB(x) KB(x)*1024
#define GB(x) MB(x)*1024
#define TB(x) GB(x)*1024

#endif // PLATFORM_H
