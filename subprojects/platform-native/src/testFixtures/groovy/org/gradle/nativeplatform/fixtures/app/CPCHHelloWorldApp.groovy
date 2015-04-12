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

class CPCHHelloWorldApp extends PCHHelloWorldApp {

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
            #ifndef HELLO_H
            #define HELLO_H
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC sayHello();
            int DLL_FUNC sum(int a, int b);

            #ifdef FRENCH
            #pragma message("<==== compiling bonjour.h ====>")
            #else
            #pragma message("<==== compiling hello.h ====>")
            #endif
            #endif
        """);
    }

    @Override
    public TestApp getAlternate() {
        return new TestApp() {
            @Override
            SourceFile getMainSource() {
                return getAlternateMainSource()
            }

            @Override
            SourceFile getLibraryHeader() {
                return sourceFile("headers", "hello.h", """
                    #ifndef HELLO_H
                    #define HELLO_H
                    #ifdef _WIN32
                    #define DLL_FUNC __declspec(dllexport)
                    #else
                    #define DLL_FUNC
                    #endif

                    void DLL_FUNC sayHello();
                    int DLL_FUNC sum(int a, int b);

                    #pragma message("<==== compiling althello.h ====>")
                    #endif
                """);
            }

            @Override
            List<SourceFile> getLibrarySources() {
                return getAlternateLibrarySources()
            }
        }
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return getLibrarySources("")
    }

    @Override
    List<SourceFile> getLibrarySources(String headerPath) {
        return [
                sourceFile("c", "hello.c", """
                #include "${headerPath}prefixHeader.h"

                #ifdef FRENCH
                char* greeting() {
                    return "${HELLO_WORLD_FRENCH}";
                }
                #endif
                #ifdef CUSTOM
                char* greeting() {
                    return CUSTOM;
                }
                #endif
                void DLL_FUNC sayHello() {
                    #if defined(FRENCH) || defined(CUSTOM)
                    printf("%s\\n", greeting());
                    #else
                    printf("${HELLO_WORLD}\\n");
                    #endif
                    fflush(stdout);
                }
            """),
                sourceFile("c", "sum.c", """
                #include "hello.h"

                int DLL_FUNC sum(int a, int b) {
                    return a + b;
                }
            """)
        ]
    }

    @Override
    String getIOHeader() {
        return "stdio.h"
    }

    @Override
    SourceFile getAlternateMainSource() {
        return getMainSource()
    }

    @Override
    String getAlternateOutput() {
        return null
    }

    @Override
    List<SourceFile> getAlternateLibrarySources() {
        return getLibrarySources()
    }

    @Override
    String getAlternateLibraryOutput() {
        return null
    }
}
