/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class WindowsResourceHelloWorldApp extends HelloWorldApp {
    @Override
    String getEnglishOutput() {
        return HELLO_WORLD
    }

    @Override
    String getFrenchOutput() {
        return HELLO_WORLD_FRENCH
    }

    @Override
    List<String> getPluginList() {
        ['cpp', 'windows-resources']
    }

    @Override
    String getExtraConfiguration() {
        return """
            model {
                binaries {
                    all {
                        linker.args "user32.lib"
                    }
                    withType(SharedLibraryBinarySpec) {
                        cppCompiler.define "DLL_EXPORT"
                    }
                }
            }
"""
    }

    @Override
    String getExtraConfiguration(String binaryName) {
        return getExtraConfiguration()
    }

    @Override
    String compilerArgs(String arg) {
        "rcCompiler.args '${arg}'"
    }

    @Override
    String compilerDefine(String define) {
        "rcCompiler.define '${define}'"
    }

    @Override
    String compilerDefine(String define, String value) {
        "rcCompiler.define '${define}', '${value}'"
    }

    @Override
    SourceFile getMainSource() {
        return sourceFile("cpp", "main.cpp", """
#include "hello.h"

int main () {
    hello();
    return 0;
}
""");
    }

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
#define IDS_HELLO    111

#ifdef DLL_EXPORT
#define DLL_FUNC __declspec(dllexport)
#define MODULE_HANDLE GetModuleHandle("hello")
#else
#define DLL_FUNC
#define MODULE_HANDLE null
#endif

void DLL_FUNC hello();
""");
    }

    List<SourceFile> librarySources = [
        sourceFile("cpp", "hello.cpp", """
#include <iostream>
#include <windows.h>
#include <string>
#include "hello.h"

std::string LoadStringFromResource(UINT stringID)
{
    HINSTANCE instance = GetModuleHandle("hello");
    WCHAR * pBuf = NULL;
    int len = LoadStringW(instance, stringID, reinterpret_cast<LPWSTR>(&pBuf), 0);
    std::wstring wide = std::wstring(pBuf, len);
    return std::string(wide.begin(), wide.end());
}

void DLL_FUNC hello() {
    std::string hello = LoadStringFromResource(IDS_HELLO);
    std::cout << hello;
}
"""),
        sourceFile("rc", "resources.rc", """
#include "hello.h"

STRINGTABLE
{
    #ifdef FRENCH
    IDS_HELLO, "${HELLO_WORLD_FRENCH}"
    #else
    IDS_HELLO, "${HELLO_WORLD}"
    #endif
}
""")
    ]

    List<SourceFile> getResourceSources() {
        getLibrarySources().subList(1, 2)
    }
}
