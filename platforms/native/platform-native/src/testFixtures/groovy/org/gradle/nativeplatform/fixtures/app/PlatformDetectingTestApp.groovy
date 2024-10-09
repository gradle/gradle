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

class PlatformDetectingTestApp extends TestApp {
    @Override
    SourceFile getMainSource() {
        sourceFile("cpp", "main.cpp", """
#include <iostream>
using namespace std;
#include "hello.h"

int main () {
    ${outputPlatform()}

    outputLibraryPlatform();
}
""")
    }

    @Override
    SourceFile getLibraryHeader() {
        sourceFile("headers", "hello.h", """
#ifdef _WIN32
#define DLL_FUNC __declspec(dllexport)
#else
#define DLL_FUNC
#endif

void DLL_FUNC outputLibraryPlatform();
        """)
    }


    List<SourceFile> librarySources = [
        new SourceFile("cpp", "hello.cpp", """
#include <iostream>
using namespace std;
#include "hello.h"

void DLL_FUNC outputLibraryPlatform() {
    ${outputPlatform()}
}
        """)
    ]

    def outputPlatform() {
        return """
    #if defined(__x86_64__) || defined(_M_X64)
    cout << "amd64";
    #elif defined(__i386) || defined(_M_IX86)
    cout << "i386";
    #elif defined(__arm64__)
    cout << "arm64";
    #elif defined(_M_IA64)
    cout << "itanium";
    #else
    cout << "unknown";
    #endif
    cout << " ";

    #if defined(__linux__)
    cout << "linux";
    #elif defined(__APPLE__) && defined(__MACH__)
    cout << "os x";
    #elif defined(_WIN32) || defined (_WIN64) || defined (__CYGWIN__)
    cout << "windows";
    #else
    cout << "unknown";
    #endif
"""
    }
}
