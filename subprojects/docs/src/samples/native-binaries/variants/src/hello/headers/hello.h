#ifdef _WIN32
#ifdef DLL_EXPORT
#define LIB_FUNC __declspec(dllexport)
#endif
#endif
#ifndef LIB_FUNC
#define LIB_FUNC
#endif

void LIB_FUNC hello();
