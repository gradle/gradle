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

package org.gradle.nativebinaries.language.cpp.fixtures.app

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
    String getTargetPlatformsScript() {
        return """
            binaries.all {
                linker.args "user32.lib"
            }
"""
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
    SourceFile getMainSource() {
        return sourceFile("cpp", "main.cpp", """
            #include "strings.h"

            int main () {
                std::string hello = LoadStringFromResource(IDS_HELLO);
                std::cout << hello;
                return 0;
            }
"""
        );
    }

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "strings.h", """
            #include <iostream>
            #include <windows.h>
            #include <string>

            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            #define IDS_HELLO    111

            std::string DLL_FUNC LoadStringFromResource(UINT stringID);
        """);
    }

    List<SourceFile> librarySources = [
        sourceFile("cpp", "loader.cpp", """
            #include "strings.h"

            std::string LoadStringFromResource(UINT stringID)
            {
                HINSTANCE instance = NULL;
                WCHAR * pBuf = NULL;
                int len = LoadStringW(instance, stringID, reinterpret_cast<LPWSTR>(&pBuf), 0);
                std::wstring wide = std::wstring(pBuf, len);
                return std::string(wide.begin(), wide.end());
            }
        """),
        sourceFile("rc", "strings.rc", """
            #include "strings.h"

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
}
