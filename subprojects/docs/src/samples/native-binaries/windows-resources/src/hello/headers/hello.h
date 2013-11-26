#ifdef DLL_EXPORT
#define DLL_FUNC __declspec(dllexport)
#else
#define DLL_FUNC
#endif

void DLL_FUNC hello();
