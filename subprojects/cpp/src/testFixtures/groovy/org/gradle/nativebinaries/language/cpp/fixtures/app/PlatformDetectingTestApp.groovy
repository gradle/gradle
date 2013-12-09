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

class PlatformDetectingTestApp extends TestComponent {
    @Override
    List<SourceFile> getHeaderFiles() {
        return []
    }

    @Override
    List<SourceFile> getSourceFiles() {
        return [
                sourceFile("cpp", "main.cpp", """
#include <iostream>
using namespace std;

int main () {

#if defined(__x86_64__) || defined(_M_X64)
    cout << "amd64";
#elif defined(__i386) || defined(_M_IX86)
    cout << "i386";
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
#elif defined(_WIN32) || defined (_WIN64)
    cout << "windows";
#else
    cout << "unknown";
#endif

}
""")
        ]
    }
}
