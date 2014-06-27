/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppCallingCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class NativeBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppCallingCHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "skips building executable binary with no source"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").assertDoesNotExist()
    }

    def "assemble task constructs all buildable binaries"() {
        given:
        new CHelloWorldApp().writeSources(file("src/main"))

        and:
        buildFile << """
    apply plugin: 'c'

    model {
        buildTypes {
            debug
            optimised
            release
        }
    }
    executables {
        main
    }
    binaries.all { binary ->
        if (binary.buildType == buildTypes.optimised) {
            binary.buildable = false
        }
    }
"""
        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":debugMainExecutable", ":releaseMainExecutable"
        notExecuted ":optimisedMainExecutable"

        and:
        executable("build/binaries/mainExecutable/debug/main").assertExists()
        executable("build/binaries/mainExecutable/optimised/main").assertDoesNotExist()
        executable("build/binaries/mainExecutable/release/main").assertExists()
    }

    // TODO:DAZ Once use of the extension has been rolled into the rest of the integration tests, this test won't be necessary
    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can define native binaries using nativeRuntime extension"() {
        given:
        helloWorldApp.library.writeSources(file("src/hello"))
        helloWorldApp.executable.writeSources(file("src/main"))

        and:
        buildFile << """
    apply plugin: 'cpp'
    apply plugin: 'c'

    nativeRuntime {
        executables {
            main
        }
        libraries {
            hello
        }
        sources.main.cpp.lib libraries.hello
    }
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == helloWorldApp.englishOutput
    }

    // Test for temporary backward-compatibility layer for native binaries. Plan is to deprecate in 2.1 and remove in 2.2.
    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can define native binaries using 1.12 compatible api"() {
        given:
        helloWorldApp.library.writeSources(file("src/hello"))
        helloWorldApp.executable.writeSources(file("src/main"))

        and:
        buildFile << """
    apply plugin: 'cpp'
    apply plugin: 'c'

    executables {
        main
    }
    libraries {
        hello
    }
    sources.main.cpp.lib libraries.hello
    task buildAllExecutables {
        dependsOn binaries.withType(ExecutableBinary).matching {
            it.buildable
        }
    }
"""
        when:
        succeeds "buildAllExecutables"

        then:
        executable("build/binaries/mainExecutable/main").assertExists()

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable from component with multiple language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cpp"
            sources {
                test {}
            }
            executables {
                main {
                    source sources.test.cpp
                    source sources.test.c
                }
            }
        """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable binary directly from language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cpp"
            sources {
                test {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.test.cpp
                source sources.test.c
            }
        """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable binary directly from functional source set"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cpp"
            sources {
                test {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.test
            }
        """
        
        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "ignores java sources added to binary"() {
        given:
        useMixedSources()
        file("src/test/java/HelloWorld.java") << """
    This would not compile
"""

        when:
        buildFile << """
            apply plugin: "c"
            apply plugin: "cpp"
            apply plugin: "java"

            executables {
                main {
                    source sources.test.cpp
                    source sources.test.c
                    source sources.test.java
                }
            }
         """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    private def useMixedSources() {
        helloWorldApp.writeSources(file("src/test"))
    }

    def "build fails when link executable fails"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
        """

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            int thing() { return 0; }
        """

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.");
        failure.assertHasCause("Linker failed; see the error output for details.")
    }

    def "build fails when link library fails"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            libraries { main {} }
        """

        and:
        file("src/main/cpp/hello1.cpp") << """
            void hello() {
            }
"""

        and:
        file("src/main/cpp/hello2.cpp") << """
            void hello() {
            }
"""

        when:
        fails "mainSharedLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainSharedLibrary'.");
        failure.assertHasCause("Linker failed; see the error output for details.")
    }

    def "build fails when create static library fails"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            libraries { main {} }
            binaries.withType(StaticLibraryBinary) {
                staticLibArchiver.args "not_a_file"
            }
        """

        and:
        file("src/main/cpp/hello.cpp") << """
            void hello() {
            }
"""

        when:
        fails "mainStaticLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':createMainStaticLibrary'.");
        failure.assertHasCause("Static library archiver failed; see the error output for details.")
    }
}
