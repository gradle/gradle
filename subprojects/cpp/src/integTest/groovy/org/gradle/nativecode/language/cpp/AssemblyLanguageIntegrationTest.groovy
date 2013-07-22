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

import org.gradle.internal.os.OperatingSystem

class AssemblyLanguageIntegrationTest extends AbstractLanguageIntegrationTest {
    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"
    def helloWorldApp = new MixedCAssemblerHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main
                }
            }
        """

        and:
        file("src/main/asm/broken.s") << """
.section    __TEXT,__text,regular,pure_instructions
.globl  _sum
.align  4, 0x90
_sum:
pushl
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':assembleMainExecutableMainAsm'.");
        failure.assertHasCause("Assemble failed; see the error output for details.")
    }

    class MixedCAssemblerHelloWorldApp {

        def englishOutput= "$HELLO_WORLD 12"
        def frenchOutput = "$HELLO_WORLD_FRENCH 12"

        def getCustomArgs() {
            if (OperatingSystem.current().isMacOsX()) {
                return """
                        compilerArgs "-m32"
                        assemblerArgs "-arch", "i386"
                        linkerArgs "-no_pie", "-arch", "i386"
                """
            }
            return ""
        }

        def appSources = [
            "c/main.c": """
                #include <stdio.h>
                #include "hello.h"

                int main () {
                    sayHello();
                    printf(" %d", sum(5, 7));
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
""",
            "asm/sum.s": getAsmSource()
        ]
    }

    private static def getAsmSource() {
        def os = OperatingSystem.current()
        if (os.isMacOsX()) {
            return osxAsmSource
        } else if (os.isWindows()) {
            return windowsAsmSource
        } else {
            return linuxAsmSource
        }
    }

    static private String osxAsmSource = '''
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
    static private String windowsAsmSource = '''
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
    static private String linuxAsmSource = '''
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

