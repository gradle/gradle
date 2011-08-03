/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

import static org.gradle.util.TextUtil.escapeString

import spock.lang.*

class CppHelloWorldExecutableIntegrationSpec extends AbstractIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"

    def "build and execute simple hello world cpp program"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            #include <iostream>

            int main () {
              std::cout << "${escapeString(HELLO_WORLD)}";
              return 0;
            }
        """

        when:
        run "compileMain"

        then:
        def executable = file("build/binaries/main")
        executable.exists()
        executable.exec().out == HELLO_WORLD
    }

    def "build and execute simple hello world cpp program using header"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            cpp {
                sourceSets {
                    hello {}
                    main {
                        libs << project.libraries.create('hello') {
                            sourceSets << project.cpp.sourceSets.hello

                            spec {
                                sharedLibrary()
                            }
                        }
                    }
                }
            }
        """

        and:
        file("src", "hello", "cpp", "hello.cpp") << """
            #include <iostream>

            void hello () {
              std::cout << "${escapeString(HELLO_WORLD)}";
            }
        """

        and:
        file("src", "hello", "headers", "hello.h") << """
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
        run "compileMain"

        then:
        file("build/binaries/main").exec().out == HELLO_WORLD
    }

}