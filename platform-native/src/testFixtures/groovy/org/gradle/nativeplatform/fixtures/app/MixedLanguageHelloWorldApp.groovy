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
import org.gradle.nativeplatform.fixtures.AvailableToolChains

class MixedLanguageHelloWorldApp extends HelloWorldApp {
    private final AvailableToolChains.InstalledToolChain toolChain

    MixedLanguageHelloWorldApp(AvailableToolChains.InstalledToolChain toolChain) {
        this.toolChain = toolChain
    }

    @Override
    List<String> getPluginList() {
        return ['c', 'cpp', 'assembler']
    }

    String getExtraConfiguration() {
        return """
            model {
                platforms {
                    x86 {
                        architecture "i386"
                    }
                }
                components {
                    all { it.targetPlatform "x86" }
                }
            }
"""
    }

    SourceFile getMainSource() {
        return new SourceFile("cpp", "main.cpp", """
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

    SourceFile getLibraryHeader() {
        return new SourceFile("headers", "hello.h", """
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC sayHello();
            int DLL_FUNC sum(int a, int b);
""")
    }

    List<SourceFile> getLibrarySources() {
        return  [
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

            // Ensure consistent asm name mapping on all platforms
            #if !defined(_MSC_VER)
            extern int sumx(int a, int b) asm("_sumx");
            #endif

            int DLL_FUNC sum(int a, int b) {
                return sumx(a, b);
            }
"""),
            new SourceFile("asm", "sum.s", getAsmSource())
        ]
    }

    protected def getAsmSource() {
        if (toolChain.isVisualCpp()) {
            return windowsMasmSource
        }
        return i386GnuAsmSource
    }

    // HACK: Ensure include root are correctly setup, use `INCLUDE <ks386.inc>`
    // See: https://github.com/gradle/gradle/issues/3662
    private static String windowsMasmSource = '''
.386
.model    flat

INCLUDE <ks386.inc>
PUBLIC    _sumx
_TEXT     SEGMENT
_sumx    PROC
mov    eax, DWORD PTR 4[esp]
add    eax, DWORD PTR 8[esp]
ret    0
_sumx    ENDP
_TEXT   ENDS
END
'''

    private static String i386GnuAsmSource = '''
    .text
    .globl  _sumx
_sumx:
    movl    8(%esp), %eax
    addl    4(%esp), %eax
    ret
'''

    private static String x64GnuAsmSource = '''
    .text
    .p2align 4,,15
.globl sumx
    .type   sumx, @function
sumx:
    leal    (%rsi,%rdi), %eax
    ret
'''
}
