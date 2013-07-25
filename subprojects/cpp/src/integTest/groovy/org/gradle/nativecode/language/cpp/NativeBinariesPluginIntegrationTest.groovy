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
package org.gradle.nativecode.language.cpp
import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.nativecode.language.cpp.fixtures.app.CppCallingCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

class NativeBinariesPluginIntegrationTest extends AbstractBinariesIntegrationSpec {
    def helloWorldApp = new CppCallingCHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    // TODO:DAZ Fix this
    @Ignore("Not sure what the behaviour should be")
    def "assemble executable from component with no source"() {
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
        executable("build/binaries/mainExecutable/main").assertExists()
    }

    def "assemble executable from component with multiple language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main.cpp
                    source sources.main.c
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
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.main.cpp
                source sources.main.c
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
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.main
            }
        """
        
        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    private def useMixedSources() {
        helloWorldApp.sourceFiles.each { sourceFile ->
            file("src/main/${sourceFile.path}/${sourceFile.name}") << sourceFile.content
        }
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
        failure.assertHasCause("Link failed; see the error output for details.")
    }

    // TODO:DAZ Find a way to make library linking fail on linux
    @Requires([TestPrecondition.NOT_LINUX, TestPrecondition.NOT_UNKNOWN_OS])
    def "build fails when link shared library fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-lib"
        """

        and:
        file("src/main/cpp/hello.cpp") << """
            #include "test.h"
            void hello() {
                test();
            }
"""
        // Header file available, but no implementation to link
        file("src/main/cpp/test.h") << """
            int test();
"""

        when:
        fails "mainSharedLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainSharedLibrary'.");
        failure.assertHasCause("Link failed; see the error output for details.")
    }

    // TODO:DAZ Add test for failing to link static library
}
