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

package org.gradle.nativecode.language.cpp.fixtures.app

class CppHelloWorldApp extends IncrementalHelloWorldApp {
    @Override
    SourceFile getMainSource() {
        return sourceFile("cpp", "main.cpp", """
            // Simple hello world app
            #include <iostream>
            #include "hello.h"

            int main () {
              sayHello();
              std::cout << " " << sum(5, 7);
              return 0;
            }
        """);
    }

    SourceFile getAlternateMainSource() {
        sourceFile("cpp", "main.cpp", """
            #include <iostream>
            #include "hello.h"

            int main () {
              sayHello();
              return 0;
            }
        """)
    }

    String alternateOutput = "$HELLO_WORLD"

    @Override
    SourceFile getLibraryHeader() {
        return sourceFile("headers", "hello.h", """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC sayHello();
            int DLL_FUNC sum(int a, int b);
        """);
    }

    List<SourceFile> librarySources = [
        sourceFile("cpp", "hello.cpp", """
            #include <iostream>
            #include "hello.h"

            void DLL_FUNC sayHello() {
                #ifdef FRENCH
                std::cout << "${HELLO_WORLD_FRENCH}";
                #else
                std::cout << "${HELLO_WORLD}";
                #endif
            }
        """),
        sourceFile("cpp", "sum.cpp", """
            #include "hello.h"

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    List<SourceFile> alternateLibrarySources = [
        sourceFile("cpp", "hello.cpp", """
            #include <iostream>
            #include "hello.h"

            void DLL_FUNC sayHello() {
                std::cout << "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]";
            }
        """),
        sourceFile("cpp", "sum.cpp", """
            #include "hello.h"

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    String alternateLibraryOutput = "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}] 12"

}
