#ifdef _WIN32
#define LIB_FUNC __declspec(dllexport)
#else
#define LIB_FUNC
#endif

void LIB_FUNC printBuildType();
