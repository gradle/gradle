#ifdef _WIN32
#define LIB_FUNC __declspec(dllimport)
#else
#define LIB_FUNC
#endif

void LIB_FUNC hello();
