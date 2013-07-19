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

import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CppLanguageIntegrationTest extends AbstractBinariesIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    static final MAIN_CPP = """
            #include "hello.h"

            int main () {
              hello();
              return 0;
            }
"""
    static final HELLO_CPP = """
            #include <iostream>

            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC hello() {
                #ifdef FRENCH
                std::cout << "${HELLO_WORLD_FRENCH}";
                #else
                std::cout << "${HELLO_WORLD}";
                #endif
            }
"""
    static final HELLO_H = """
            void hello();
"""

    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }


    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>

            'broken
        """

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.");
        failure.assertHasCause("C++ compile failed; see the error output for details.")
    }


    def "compile and link executable"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """

        and:
        file("src/main/cpp/main.cpp") << MAIN_CPP
        file("src/main/cpp/hello.cpp") << HELLO_CPP
        file("src/main/headers/hello.h") << HELLO_H

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/test")
        mainExecutable.assertExists()
        mainExecutable.exec().out == HELLO_WORLD
    }

    def "build executable with custom compiler arg"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
            executables.main.binaries.all {
                compilerArgs "-DFRENCH"
            }
        """

        and:
        file("src/main/cpp/main.cpp") << MAIN_CPP
        file("src/main/cpp/hello.cpp") << HELLO_CPP
        file("src/main/headers/hello.h") << HELLO_H

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/test")
        mainExecutable.assertExists()
        mainExecutable.exec().out == HELLO_WORLD_FRENCH
    }

    def "build executable with macro defined"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
            executables.main.binaries.all {
                define "FRENCH"
            }
        """

        and:
        file("src/main/cpp/main.cpp") << MAIN_CPP
        file("src/main/cpp/hello.cpp") << HELLO_CPP
        file("src/main/headers/hello.h") << HELLO_H

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/test")
        mainExecutable.assertExists()
        mainExecutable.exec().out == HELLO_WORLD_FRENCH
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build shared library and link into executable"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            sources {
                hello {}
            }
            libraries {
                hello {
                    source sources.hello
                }
            }
            sources.main.cpp.lib libraries.hello
        """

        and:
        file("src/main/cpp/main.cpp") << MAIN_CPP
        file("src/hello/cpp/hello.cpp") << HELLO_CPP
        file("src/hello/headers/hello.h") << HELLO_H

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/binaries/helloSharedLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/test").assertExists()

        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == HELLO_WORLD
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build static library and link into executable"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            sources {
                hello {}
            }
            libraries {
                hello {
                    source sources.hello
                    binaries.withType(StaticLibraryBinary) {
                        define "FRENCH"
                    }
                }
            }
            sources.main.cpp.lib libraries.hello.static
        """

        and:
        file("src/main/cpp/main.cpp") << MAIN_CPP
        file("src/hello/cpp/hello.cpp") << HELLO_CPP
        file("src/hello/headers/hello.h") << HELLO_H

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/binaries/helloStaticLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/test").assertExists()

        and:
        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.exec().out == HELLO_WORLD_FRENCH
    }
}
