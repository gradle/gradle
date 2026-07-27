/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.app.TestNativeComponent


// TODO integrate objective-c/cpp we have coverage on windows
public class DuplicateMixedSameBaseNamesTestApp extends TestNativeComponent {

    AvailableToolChains.InstalledToolChain toolChain

    public DuplicateMixedSameBaseNamesTestApp(AvailableToolChains.InstalledToolChain toolChain) {
        this.toolChain = toolChain
    }

    def plugins = ["assembler", "c", "cpp"]

    def functionalSourceSets = [asm:'**/*.s', c:'**/*.c', cpp:'**/*.cpp']

    @Override
    public List<SourceFile> getSourceFiles() {
        [sourceFile("c", "main.c", """
            #include <stdio.h>
            #include "hello.h"

            // Ensure consistent asm name mapping on all platforms
            #if !defined(_MSC_VER)
            extern void sayFooFromAsm() asm("_sayFooFromAsm");
            #endif

            int main () {
                sayFooFromC();
                sayFooFromCpp();
                //sayFooFromObjectiveC //TODO RG
                //sayFooFromObjectiveCpp //TODO RG
                sayFooFromAsm();
                return 0;
            }"""),

                sourceFile("c", "foo.c", """
            #include <stdio.h>
            #include "hello.h"

            void sayFooFromC() {
                printf("fooFromC\\n");
            }
        """),
           sourceFile("cpp", "foo.cpp", """
            #include "hello.h"
            #include <iostream>
            using namespace std;

            void sayFooFromCpp() {
                cout << "fooFromCpp" << std::endl;;
            }

        """),
                sourceFile("asm", "foo.s", asmSource())
        ]
    }

    String asmSource() {
        if (toolChain.isVisualCpp()) {
            return """
.386
;.model flat
.model small,c
.data
msg db "fooFromAsm", 10, 0
.code
includelib MSVCRT
extrn printf:near
extrn exit:NEAR
public sayFooFromAsm
sayFooFromAsm proc
push    offset msg
call    printf
mov eax,0
push eax
call exit
sayFooFromAsm endp
end
"""
        } else {
            return """
.text

LC0:
.ascii "fooFromAsm\\12\\0"
.globl _sayFooFromAsm

_sayFooFromAsm:
        pushl   %ebp
        movl    %esp, %ebp
        subl    \$8, %esp
        andl    \$-16, %esp
        movl    \$0, %eax
        movl    %eax, -4(%ebp)
        movl    -4(%ebp), %eax
        movl    \$LC0, (%esp)
        call    ${(OperatingSystem.current().isMacOsX() || OperatingSystem.current().isWindows()) ? '_printf' : 'printf'}
        movl    \$0, %eax
        leave
        ret
        """
        }
    }

    @Override
    public List<SourceFile> getHeaderFiles() {
        [sourceFile("headers", "hello.h", """
            #ifdef __cplusplus
            extern "C" {
            #endif
            void sayFooFromCpp();
            #ifdef __cplusplus
            }
            #endif
            void sayFooFromC();
            void sayFooFromAsm();
            //void sayFooFromObjectiveC();
            //void sayFooFromObjectiveCpp();
           """)
        ]
    }
}
