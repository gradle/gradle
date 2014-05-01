#ifdef defined(_WIN32) && defined(DLL_EXPORT)
#define LIB_FUNC __declspec(dllimport)
#else
#define LIB_FUNC
#endif

void LIB_FUNC hello();
