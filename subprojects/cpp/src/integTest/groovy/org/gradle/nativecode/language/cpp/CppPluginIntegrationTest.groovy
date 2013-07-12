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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.TextUtil.escapeString
// TODO:DAZ Verify that linkerArgs are set correctly: use '-L' to choose library to link
class CppPluginIntegrationTest extends AbstractBinariesIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>

            'broken
        """

        expect:
        fails "mainExecutable"
    }

    def "build fails when link fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            int thing() { return 0; }
        """

        expect:
        fails "mainExecutable"
    }

    def "build and execute program with compiler arg"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                english {
                    source sources.main
                }
                french {
                    source sources.main.cpp
                    binaries.all {
                        compilerArgs "-DFRENCH"
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>

            int main () {
                #ifdef FRENCH
                std::cout << "${escapeString(HELLO_WORLD_FRENCH)}";
                #else
                std::cout << "${escapeString(HELLO_WORLD)}";
                #endif
                return 0;
            }
        """

        when:
        run "englishExecutable", "frenchExecutable"

        then:
        def englishExecutable = executable("build/binaries/englishExecutable/english")
        englishExecutable.assertExists()
        englishExecutable.exec().out == HELLO_WORLD

        and:
        def frenchExecutable = executable("build/binaries/frenchExecutable/french")
        frenchExecutable.assertExists()
        frenchExecutable.exec().out == HELLO_WORLD_FRENCH
    }

    def "build and execute program from multiple source files"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "hello.cpp") << """
            #include <iostream>

            void hello () {
              std::cout << "${escapeString(HELLO_WORLD)}";
            }
        """

        and:
        file("src", "main", "headers", "hello.h") << """
            void hello();
        """

        and:
        file("src", "main", "cpp", "main.cpp") << """
            #include "hello.h"

            int main () {
              hello();
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/test").exec().out == HELLO_WORLD
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build, install and execute program with shared library"() {
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
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/hello/cpp/hello.cpp") << """
            #include <iostream>
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC hello(const char* str) {
              std::cout << str;
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include <iostream>
            #include "hello.h"

            int main (int argc, char** argv) {
              hello("${escapeString(HELLO_WORLD)}");
              for ( int i = 1; i < argc; i++ ) {
                std::cout << "[" << argv[i] << "]";
              }
              return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/binaries/helloSharedLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/test").assertExists()

        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.assertIncludesLibraries("hello")
        install.exec().out == HELLO_WORLD
        install.exec("a", "1 2 3").out.contains("[a][1 2 3]")

        // Ensure installed binary is not dependent on the libraries in their original locations
        when:
        file("build/binaries").deleteDir()

        then:
        install.exec().out == HELLO_WORLD
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build, install and execute program with static library"() {
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
            sources.main.cpp.lib libraries.hello.static
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/hello/cpp/hello.cpp") << """
            #include <iostream>

            void hello(const char* str) {
              std::cout << str;
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include <iostream>
            #include "hello.h"

            int main (int argc, char** argv) {
              hello("${escapeString(HELLO_WORLD)}");
              for ( int i = 1; i < argc; i++ ) {
                std::cout << "[" << argv[i] << "]";
              }
              return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/binaries/helloStaticLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/test").assertExists()

        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.assertIncludesLibraries()
        install.exec().out == HELLO_WORLD
        install.exec("a", "1 2 3").out.contains("[a][1 2 3]")

        // Ensure installed binary is not dependent on the libraries in their original locations
        when:
        file("build/binaries").deleteDir()

        then:
        install.exec().out == HELLO_WORLD
    }
}
