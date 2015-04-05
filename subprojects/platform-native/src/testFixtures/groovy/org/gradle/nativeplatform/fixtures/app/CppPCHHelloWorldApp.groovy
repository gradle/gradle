/*
 * Copyright 2015 the original author or authors.
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

class CppPCHHelloWorldApp extends PCHHelloWorldApp {
    @Override
    SourceFile getMainSource() {
        return sourceFile("cpp", "main.cpp", """
            // Simple hello world app
            #include <iostream>
            #include "hello.h"

            int main () {
              Greeter greeter;
              greeter.sayHello();
              std::cout << sum(5, 7);
              return 0;
            }
        """);
    }

    SourceFile getAlternateMainSource() {
        sourceFile("cpp", "main.cpp", """
            #include <iostream>
            #include "hello.h"

            int main () {
              Greeter greeter;
              greeter.sayHello();
              return 0;
            }
        """)
    }

    String alternateOutput = "$HELLO_WORLD\n"

    @Override
    SourceFile getLibraryHeader() {
        getLibraryHeader("")
    }

    @Override
    SourceFile getLibraryHeader(String path) {
        return sourceFile("headers/${path}", "hello.h", """
            #ifndef HELLO_H
            #define HELLO_H
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            class Greeter {
                public:
                void DLL_FUNC sayHello();
            };

            int DLL_FUNC sum(int a, int b);
            #pragma message("<==== compiling hello.h ====>")
            #endif
        """);
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return getLibrarySources("")
    }

    @Override
    List<SourceFile> getLibrarySources(String path) {
        return [
                sourceFile("cpp", "hello.cpp", """
                    #include "${path}hello.h"
                    #include <iostream>

                    #ifdef FRENCH
                    const char* greeting() {
                        return "${HELLO_WORLD_FRENCH}";
                    }
                    #endif

                    void DLL_FUNC Greeter::sayHello() {
                        #ifdef FRENCH
                        std::cout << greeting() << std::endl;
                        #else
                        std::cout << "${HELLO_WORLD}" << std::endl;
                        #endif
                    }
                """),
                sourceFile("cpp", "sum.cpp", """
                    #include "${path}hello.h"

                    int DLL_FUNC sum(int a, int b) {
                        return a + b;
                    }
                """)
        ]
    }

    @Override
    SourceFile getSystemHeader() {
        return getSystemHeader("")
    }

    @Override
    SourceFile getSystemHeader(String path) {
        sourceFile("headers/${path}", "systemHeader.h", """
            #ifndef SYSTEMHEADER_H
            #define SYSTEMHEADER_H
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif
            void DLL_FUNC systemCall();
            #pragma message("<==== compiling systemHeader.h ====>")
            #endif
        """)
    }

    @Override
    String getIOHeader() {
        return "iostream"
    }

    List<SourceFile> alternateLibrarySources = [
        sourceFile("cpp", "hello.cpp", """
            #include <iostream>
            #include "hello.h"

            void DLL_FUNC Greeter::sayHello() {
                std::cout << "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]" << std::endl;
            }

            // Extra function to ensure library has different size
            int anotherFunction() {
                return 1000;
            }
        """),
        sourceFile("cpp", "sum.cpp", """
            #include "hello.h"

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    String alternateLibraryOutput = "[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]\n12"

}
