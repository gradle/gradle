#include <iostream>
#include <windows.h>
#include <string>
#include "hello.h"
#include "resources.h"

std::string LoadStringFromResource(UINT stringID)
{
    HINSTANCE instance = GetModuleHandle("hello");
    WCHAR * pBuf = NULL;
    int len = LoadStringW(instance, stringID, reinterpret_cast<LPWSTR>(&pBuf), 0);
    std::wstring wide = std::wstring(pBuf, len);
    return std::string(wide.begin(), wide.end());
}

void hello() {
    std::string hello = LoadStringFromResource(IDS_HELLO);
    std::cout << hello << std::endl;
}
