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

class CppHelloWorldExecutableIntegrationSpec extends AbstractIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"

    def "build and execute simple hello world cpp program"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            archivesBaseName = "hello"

            cpp {
                sourceSets {
                    main { }
                }
            }
            
            task compile(type: org.gradle.plugins.cpp.CompileCpp) {
                source cpp.sourceSets.main.cpp
                destinationDir = { "\$buildDir/object-files" }
            }
            
            task link(type: org.gradle.plugins.cpp.LinkCpp) {
                source compile.outputs.files
                output { "\$buildDir/executables/\$archivesBaseName" }
            }
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
        run "link"

        then:
        def executable = file("build/executables/hello")
        executable.exists()
        executable.exec().out == HELLO_WORLD
    }

    def "build and execute simple hello world cpp program using header"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            archivesBaseName = "hello"
            
            cpp {
                sourceSets {
                    main { }
                }
            }
            
            task compile(type: org.gradle.plugins.cpp.CompileCpp) {
                source cpp.sourceSets.main.cpp
                headers cpp.sourceSets.main.headers
                destinationDir = { "\$buildDir/object-files" }
            }
            
            task link(type: org.gradle.plugins.cpp.LinkCpp) {
                source compile.outputs.files
                output { "\$buildDir/executables/\$archivesBaseName" }
            }
        """

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
        run "link"

        then:
        def executable = file("build/executables/hello")
        executable.exists()
        executable.exec().out == HELLO_WORLD
    }

}