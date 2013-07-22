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

package org.gradle.nativecode.language.cpp

class CppCallingCLanguageIntegrationTest extends AbstractLanguageIntegrationTest {
    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    def helloWorldApp = new CppCallingCHelloWorldApp()

    class CppCallingCHelloWorldApp {
        def englishOutput = "$HELLO_WORLD 12"
        def frenchOutput = "$HELLO_WORLD_FRENCH 12"
        def customArgs = ""

        def appSources = [
                "cpp/main.cpp": """
                #include <iostream>
                extern "C" {
                    #include "hello.h"
                }

                int main () {
                  sayHello();
                  std::cout << " " << sum(5, 7);
                  return 0;
                }
    """
        ]

        def libraryHeaders = [
                "headers/hello.h": """
                #ifdef _WIN32
                #define DLL_FUNC __declspec(dllexport)
                #else
                #define DLL_FUNC
                #endif

                void sayHello();
                int sum(int a, int b);
    """
        ]

        def librarySources = [
                "c/hello.c": """
                #include <stdio.h>
                #include "hello.h"

                void DLL_FUNC sayHello() {
                    #ifdef FRENCH
                    printf("${HELLO_WORLD_FRENCH}");
                    #else
                    printf("${HELLO_WORLD}");
                    #endif
                }

                int DLL_FUNC sum(int a, int b) {
                    return a + b;
                }
    """
        ]
    }
}

