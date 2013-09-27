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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppCallingCHelloWorldApp

class NativeBinariesPluginIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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
            apply plugin: "cpp-exe"
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
            apply plugin: "cpp-lib"
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
            apply plugin: "cpp-lib"
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
