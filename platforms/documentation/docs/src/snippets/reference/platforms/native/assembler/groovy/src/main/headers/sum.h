// Ensure that function name is consistently mapped to assembler
#ifdef _MSC_VER
int sum(int a, int b);
#else
extern int sum(int a, int b) asm("_sum");
#endif
