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

import org.gradle.nativecode.language.cpp.fixtures.app.HelloWorldApp
import org.gradle.nativecode.language.cpp.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativecode.language.cpp.fixtures.app.SourceFile

class AssemblyLanguageIntegrationTest extends AbstractLanguageIntegrationTest {
    // TODO: Would be better to have a "pure assembler" app here
    HelloWorldApp helloWorldApp = new AssemblerWithCHelloWorldApp()

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

    static class AssemblerWithCHelloWorldApp extends MixedLanguageHelloWorldApp {
        @Override
        SourceFile getMainSource() {
            return new SourceFile("c", "main.c", """
                #include <stdio.h>
                #include "hello.h"

                int main () {
                    sayHello();
                    printf("%d", sum(5, 7));
                    return 0;
                }
            """);
        }
    }
}

