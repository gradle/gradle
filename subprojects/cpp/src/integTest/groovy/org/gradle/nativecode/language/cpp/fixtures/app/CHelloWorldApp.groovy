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

import org.gradle.util.TextUtil

class CHelloWorldApp extends IncrementalHelloWorldApp {

    @Override
    SourceFile getMainSource() {
        sourceFile("c", "main.c", """
            // Simple hello world app
            #include <stdio.h>
            #include "hello.h"

            int main () {
                sayHello();
                printf("%d", sum(5, 7));
                return 0;
            }
        """);
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
        """);
    }

    List<SourceFile> librarySources = [
        sourceFile("c", "hello.c", """
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
        """),
        sourceFile("c", "sum.c","""
            #include "hello.h"

            int DLL_FUNC sum(int a, int b) {
                return a + b;
            }
        """)
    ]

    SourceFile getAlternateMainSource() {
        sourceFile("c", "main.c", """
            #include "hello.h"

            int main () {
              sayHello();
              printf("goodbye");
              return 0;
            }
        """)
    }

    String alternateOutput = TextUtil.toPlatformLineSeparators("$HELLO_WORLD\ngoodbye")

    List<SourceFile> alternateLibrarySources = [
            sourceFile("c", "hello.c", """
                #include <stdio.h>
                #include "hello.h"

                void DLL_FUNC sayHello() {
                    printf("[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]\\n");
                    fflush(stdout);
                }
            """),
            sourceFile("c", "sum.c","""
                #include "hello.h"

                int DLL_FUNC sum(int a, int b) {
                    return a + b;
                }
            """)
    ]

    String alternateLibraryOutput = TextUtil.toPlatformLineSeparators("[${HELLO_WORLD} - ${HELLO_WORLD_FRENCH}]\n12")
}
