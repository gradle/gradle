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

package org.gradle.nativecode.language.cpp.fixtures.app;

import org.gradle.internal.os.OperatingSystem;

public class MixedLanguageHelloWorldApp extends HelloWorldApp {

    String getCustomArgs() {
        if (OperatingSystem.current().isMacOsX()) {
            return """
                    compilerArgs "-m32"
                    assemblerArgs "-arch", "i386"
                    linkerArgs "-no_pie", "-arch", "i386"
            """
        }
        return super.getCustomArgs()
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
            }
"""),
            new SourceFile("asm", "sum.s", getAsmSource())
        ]
    }

    protected def getAsmSource() {
        def os = OperatingSystem.current()
        if (os.isMacOsX()) {
            return osxAsmSource
        } else if (os.isWindows()) {
            return windowsAsmSource
        } else {
            return linuxAsmSource
        }
    }

    private static String osxAsmSource = '''
.section    __TEXT,__text,regular,pure_instructions
.globl  _sum
.align  4, 0x90C
_sum:
pushl   %ebp
movl    %esp, %ebp
movl    12(%ebp), %eax
addl    8(%ebp), %eax
popl    %ebp
ret


.subsections_via_symbols
'''

    private static String windowsAsmSource = '''
.686P
.XMM
include   listing.inc
.model    flat

INCLUDELIB LIBCMT
INCLUDELIB OLDNAMES

PUBLIC    _sum
_TEXT     SEGMENT
_a$ = 8
_b$ = 12
_sum    PROC
push   ebp
mov    ebp, esp
mov    eax, DWORD PTR _a$[ebp]
add    eax, DWORD PTR _b$[ebp]
pop    ebp
ret    0
_sum    ENDP
_TEXT   ENDS
END
'''
    private static String linuxAsmSource = '''
    .file   "sum.c"
    .text
    .p2align 4,,15
.globl sum
    .type   sum, @function
sum:
.LFB0:
    .cfi_startproc
    leal    (%rsi,%rdi), %eax
    ret
    .cfi_endproc
.LFE0:
    .size   sum, .-sum
    .ident  "GCC: (Ubuntu/Linaro 4.5.2-8ubuntu4) 4.5.2"
    .section        .note.GNU-stack,"",@progbits
'''
}
