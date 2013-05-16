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
package org.gradle.plugins.cpp

import static org.gradle.util.TextUtil.escapeString

class CppPluginIntegrationTest extends AbstractBinariesIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"

    def "build and execute simple cpp program"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>

            int main () {
              std::cout << "${escapeString(HELLO_WORLD)}";
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/test")
        executable.isFile()
        executable.exec().out == HELLO_WORLD
    }

    def "build simple cpp library"() {
        given:
        buildFile << """
            apply plugin: "cpp-lib"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            int DLL_FUNC main () {
              std::cout << "${escapeString(HELLO_WORLD)}";
              return 0;
            }
        """

        when:
        run "mainSharedLibrary"

        then:
        sharedLibrary("build/binaries/test").isFile()
    }

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
        executable("build/binaries/test").exec().out == HELLO_WORLD
    }

    def "build, install and execute program with shared library"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            cpp {
                sourceSets {
                    hello {}
                }
            }
            libraries {
                hello {
                    sourceSets << cpp.sourceSets.hello
                }
            }
            cpp.sourceSets.main.libs << libraries.hello
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
        sharedLibrary("build/binaries/hello").isFile()
        executable("build/binaries/test").isFile()

        executable("build/install/mainExecutable/test").exec().out == HELLO_WORLD
        executable("build/install/mainExecutable/test").exec("a", "1 2 3").out.contains("[a][1 2 3]")

        // Ensure installed binary is not dependent on the libraries in their original locations
        when:
        file("build/binaries").deleteDir()

        then:
        executable("build/install/mainExecutable/test").exec().out == HELLO_WORLD
    }
}
