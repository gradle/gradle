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


package org.gradle.language.assembler

import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.app.AssemblerWithCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp

import static org.gradle.util.Matchers.containsText

class AssemblyLanguageIntegrationTest extends AbstractNativeLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new AssemblerWithCHelloWorldApp(toolChain)

    def "build fails when assemble fails"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec)
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
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Assembler failed while compiling broken.s"))
    }

    def "can manually define Assembler source sets"() {
        given:
        helloWorldApp.mainSource.writeToDir(file("src/main"))
        helloWorldApp.getLibraryHeader().writeToDir(file("src/main"))
        helloWorldApp.librarySources[0].writeToDir(file("src/main"))
        file("src/main/sum-sources/sum.s") << helloWorldApp.librarySources[1].content

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                sumAsm(AssemblerSourceSet) {
                    source {
                        srcDir "src/main/sum-sources"
                    }
                }
            }
        }
    }
}
"""

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }
}

