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
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CppBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can configure the binaries of a C++ application"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            executables {
                main {
                    binaries.all {
                        outputFile file('${executable("build/test").toURI()}')
                        cppCompiler.define 'ENABLE_GREETING'
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              #ifdef ENABLE_GREETING
              std::cout << "Hello!";
              #endif
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/test")
        executable.exec().out == "Hello!"
    }

    def "can build debug binaries for a C++ executable"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            executables {
                main {
                    binaries.all {
                        if (toolChain in VisualCpp) {
                            cppCompiler.args '/Zi'
                            linker.args '/DEBUG'
                        } else {
                            cppCompiler.args '-g'
                        }
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              std::cout << "Hello!";
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/test")
        executable.exec().out == "Hello!"
        executable.assertDebugFileExists()
        // TODO - need to verify that the debug info ended up in the binary
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can configure the binaries of a C++ library"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            libraries {
                hello {
                    binaries.all {
                        outputFile file('${staticLibrary("build/hello").toURI()}')
                        cppCompiler.define 'ENABLE_GREETING'
                    }
                }
            }
            executables {
                main {
                    binaries.all {
                        lib libraries.hello.static
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/hello/cpp/hello.cpp") << """
            #include <iostream>

            void hello(const char* str) {
              #ifdef ENABLE_GREETING
              std::cout << str;
              #endif
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include "hello.h"

            int main () {
              hello("Hello!");
              return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/hello").assertExists()
        installation("build/install/mainExecutable").exec().out == "Hello!"
    }

    def "can configure a binary to use additional source sets"() {
        given:
        buildFile << """
            apply plugin: "cpp"

            sources {
                main {
                    cpp {
                        exportedHeaders.srcDir "src/shared/headers"
                    }
                }
                util {
                    cpp {
                        exportedHeaders.srcDir "src/shared/headers"
                    }
                }
            }
            executables {
                main {
                    binaries.all {
                        source sources.util.cpp
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/shared/headers/greeting.h") << """
            void greeting();
"""

        file("src/util/cpp/greeting.cpp") << """
            #include <iostream>
            #include "greeting.h"

            void greeting() {
                std::cout << "Hello!";
            }
        """

        file("src/main/cpp/helloworld.cpp") << """
            #include "greeting.h"

            int main() {
                greeting();
                return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/main")
        executable.exec().out == "Hello!"
    }

    def "can customize binaries before and after linking"() {
        def helloWorldApp = new CppHelloWorldApp()
        given:
        buildFile << """
            apply plugin: 'cpp'
            executables {
                main {}
            }

            binaries.withType(ExecutableBinary) { binary ->
                def preLink = task("\${binary.name}PreLink") {
                    dependsOn binary.tasks.withType(CppCompile)

                    doLast {
                        println "Pre Link"
                    }
                }
                binary.tasks.link.dependsOn preLink

                def postLink = task("\${binary.name}PostLink") {
                    dependsOn binary.tasks.link

                    doLast {
                        println "Post Link"
                    }
                }

                binary.builtBy postLink
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        succeeds "mainExecutable"

        then:
        executedTasks.tail() == [":compileMainExecutableMainCpp", ":mainExecutablePreLink", ":linkMainExecutable", ":mainExecutablePostLink", ":mainExecutable"]
    }
}
