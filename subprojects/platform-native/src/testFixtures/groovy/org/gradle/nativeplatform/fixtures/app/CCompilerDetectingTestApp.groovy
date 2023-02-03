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
import org.gradle.nativeplatform.fixtures.AvailableToolChains.InstalledToolChain

class CCompilerDetectingTestApp extends TestApp {
    String expectedOutput(InstalledToolChain toolChain) {
        "C ${toolChain.typeDisplayName}"
    }

    @Override
    SourceFile getLibraryHeader() {
        sourceFile("headers", "c-detector.h", """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC detectCCompiler();
        """)
    }

    @Override
    List<SourceFile> getLibrarySources() {
        return [
            sourceFile("c", "c-detector.c", """
                #include <stdio.h>
                #include "c-detector.h"

                void detectCCompiler() {
                #if !defined(__cplusplus)
                    printf("C ");
                #endif
                #if defined(__clang__)
                    printf("clang");
                #elif defined(__GNUC__) && defined(__MINGW32__)
                    printf("mingw");
                #elif defined(__GNUC__) && defined(__CYGWIN__)
                    printf("gcc cygwin");
                #elif defined(__GNUC__)
                    printf("gcc");
                #elif defined(_MSC_VER)
                    printf("visual c++");
                #else
                    printf("unknown");
                #endif
                }
        """)
        ]
    }

    @Override
    SourceFile getMainSource() {
        return new SourceFile("c", "main.c", """
#include <stdio.h>
#include "c-detector.h"

int main () {
    detectCCompiler();
    return 0;
}
""")
    }
}
