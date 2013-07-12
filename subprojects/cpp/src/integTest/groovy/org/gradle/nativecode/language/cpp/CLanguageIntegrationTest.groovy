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

import static org.gradle.util.TextUtil.escapeString

class CLanguageIntegrationTest extends AbstractBinariesIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main.c
                }
            }
        """

        and:
        file("src", "main", "c", "helloworld.c") << """
            #include <stdio.h>

            'broken
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
                    source sources.main.c
                }
                french {
                    source sources.main.c
                    binaries.all {
                        compilerArgs "-DFRENCH"
                    }
                }
            }
        """

        and:
        file("src", "main", "c", "helloworld.c") << """
            #include <stdio.h>

            int main () {
                #ifdef FRENCH
                printf("${escapeString(HELLO_WORLD_FRENCH)}");
                #else
                printf("${escapeString(HELLO_WORLD)}");
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

    def "build and execute program with mixed c and c++ files"() {
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
        file("src", "main", "headers", "hello.h") << """
            void hello();
        """

        and:
        file("src", "main", "c", "hello.c") << """
            #include <stdio.h>
            #include "hello.h"

            void hello () {
                printf("${escapeString(HELLO_WORLD)}");
            }
        """

        and:
        file("src", "main", "cpp", "main.cpp") << """
            extern "C" {
                #include "hello.h"
            }

            int main () {
                hello();
                return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == HELLO_WORLD
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "build, install and execute program with shared library"() {
        given:
        buildFile << """
            apply plugin: "cpp"

            sources {
                main {}
                hello {}
            }
            executables {
                main {
                    source sources.main
                }
            }
            libraries {
                hello {
                    source sources.hello
                }
            }
            sources.main.c.lib libraries.hello
        """

        and:
        file("src/hello/c/hello.c") << """
            #include <stdio.h>
            #ifdef _WIN32
            #define DLL_FUNC __declspec(dllexport)
            #else
            #define DLL_FUNC
            #endif

            void DLL_FUNC hello(const char* str) {
                printf("%s", str);
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/c/main.c") << """
            #include <stdio.h>
            #include "hello.h"

            int main (int argc, char** argv) {
                int i;
                for ( i = 1; i < argc; i++ ) {
                    printf("[");
                    printf("%s", argv[i]);
                    printf("]");
                }
                hello("${escapeString(HELLO_WORLD)}");
                return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        sharedLibrary("build/binaries/helloSharedLibrary/hello").assertExists()
        executable("build/binaries/mainExecutable/main").assertExists()

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
}
