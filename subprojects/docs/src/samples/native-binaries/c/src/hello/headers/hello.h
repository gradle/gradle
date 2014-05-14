#if defined(_WIN32) && defined(DLL_EXPORT)
#define LIB_FUNC __declspec(dllexport)
#else
#define LIB_FUNC
#endif

void LIB_FUNC hello();

