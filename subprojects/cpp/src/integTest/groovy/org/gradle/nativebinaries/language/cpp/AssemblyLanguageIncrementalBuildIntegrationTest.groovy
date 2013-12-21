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

package org.gradle.nativebinaries.language.cpp
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.HelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class AssemblyLanguageIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    HelloWorldApp app = new MixedLanguageHelloWorldApp(toolChain)
    TestFile asmSourceFile
    def install

    def "setup"() {
        buildFile << """
            apply plugin: 'assembler'
            apply plugin: 'c'
            apply plugin: 'cpp'

            $app.extraConfiguration

            libraries {
                hello {}
            }
            executables {
                main {
                    binaries.all {
                        lib libraries.hello
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        asmSourceFile = file("src/hello/asm/sum.s")

        run "installMainExecutable"

        install = installation("build/install/mainExecutable")
    }

    def "does not re-execute build with no change"() {
        when:
        run "mainExecutable"

        then:
        nonSkippedTasks.empty
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "reassembles binary with assembler option change"() {
        when:
        buildFile << """
            libraries {
                hello {
                    binaries.all {
                        if (toolChain in VisualCpp) {
                            assembler.args '/Zf'
                        } else {
                            assembler.args '-W'
                        }
                    }
                }
            }
"""

        run "installMainExecutable"

        then:
        executedAndNotSkipped ":assembleHelloSharedLibraryHelloAsm"

        and:
        install.exec().out == app.englishOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "reassembles binary with target platform change"() {
        when:
        buildFile.text = buildFile.text.replace("i386", "x86")

        run "installMainExecutable"

        then:
        executedAndNotSkipped ":assembleHelloSharedLibraryHelloAsm"

        and:
        install.exec().out == app.englishOutput
    }

    def "cleans up stale object files when source file renamed"() {
        def oldObjFile = objectFile("build/objectFiles/helloSharedLibrary/helloAsm/sum")
        def newObjFile = objectFile("build/objectFiles/helloSharedLibrary/helloAsm/changed_sum")
        assert oldObjFile.file
        assert !newObjFile.file

        when:
        asmSourceFile.renameTo(file("src/hello/asm/changed_sum.s"))
        run "mainExecutable"

        then:
        executedAndNotSkipped ":assembleHelloSharedLibraryHelloAsm"

        and:
        !oldObjFile.file
        newObjFile.file
    }

    def "reassembles binary with source comment change"() {
        when:
        asmSourceFile << "# A comment at the end of the file\n"
        run "mainExecutable"

        then:
        executedAndNotSkipped ":assembleHelloSharedLibraryHelloAsm"
    }
}

