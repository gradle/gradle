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

package org.gradle.language.fixtures.app

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent

class DuplicateAssemblerBaseNamesTestApp extends TestNativeComponent{

    AvailableToolChains.InstalledToolChain toolChain

    DuplicateAssemblerBaseNamesTestApp(AvailableToolChains.InstalledToolChain toolChain) {
        this.toolChain = toolChain
    }

    def plugins = ["c", "assembler"]

    @Override
    List<SourceFile> getSourceFiles() {
        return  [
            sourceFile("c", "main.c", """
            #include <stdio.h>

            // Ensure consistent asm name mapping on all platforms
            #if !defined(_MSC_VER)
            extern int sum1(int a, int b) asm("_sum1");
            extern int sum2(int a, int b) asm("_sum2");
            #endif

            int main () {
                printf("foo%dfoo%d", sum1(1, 0), sum2(1, 1));
                fflush(stdout);
                return 0;
            }

            """),
            sourceFile("asm", "foo1/sum.s", getAsmSource("sum1")),
            sourceFile("asm", "foo2/sum.s", getAsmSource("sum2"))
        ]
    }

    @Override
    List<SourceFile> getHeaderFiles() {
        []
    }

    protected def getAsmSource(String methodName) {
        if (toolChain.isVisualCpp()) {
            return """
.386
.model    flat

PUBLIC    _${methodName}
_TEXT     SEGMENT
_${methodName}    PROC
mov    eax, DWORD PTR 4[esp]
add    eax, DWORD PTR 8[esp]
ret    0
_${methodName}    ENDP
_TEXT   ENDS
END
"""
        }else{
            return """
.text
.globl  _${methodName}
_${methodName}:
movl    8(%esp), %eax
addl    4(%esp), %eax
ret
"""

        }
    }
}
