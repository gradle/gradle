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

class CppCallingCHelloWorldApp extends HelloWorldApp {
    @Override
    List<String> getPluginList() {
        return ['c', 'cpp']
    }

    @Override
    SourceFile getMainSource() {
        sourceFile("cpp", "main.cpp", """
            #include <iostream>
            extern "C" {
                #include "hello.h"
            }

            int main () {
              sayHello();
              std::cout << sum(5, 7);
              return 0;
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

            void DLL_FUNC sayHello();
            int DLL_FUNC sum(int a, int b);
        """)
    }

    List<SourceFile> librarySources = [
        new SourceFile("c", "hello.c", """
            #include <stdio.h>
            #include "hello.h"

            void DLL_FUNC sayHello() {
                #ifdef FRENCH
                printf("${HELLO_WORLD_FRENCH}\\n");
                #else
                printf("${HELLO_WORLD}\\n");
                #endif
                fflush(stdout);
            }

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

}
