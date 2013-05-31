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

class CppBinariesIntegrationTest extends AbstractBinariesIntegrationSpec {
    def "can configure the binaries of a C++ application"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            executables {
                main {
                    binaries.all {
                        outputFile = file('${executable("build/test").toURI()}')
                        compilerArgs << '-DENABLE_GREETING'
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

    def "can configure the binaries of a C++ library"() {
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
                    binaries.all {
                        outputFile = file('${staticLibrary("build/hello").toURI()}')
                        compilerArgs << '-DENABLE_GREETING'
                    }
                }
            }
            executables {
                main {
                    binaries.all {
                        lib project.binaries.helloStaticLibrary
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
        staticLibrary("build/hello").file
        executable("build/install/mainExecutable/test").exec().out == "Hello!"
    }
}
