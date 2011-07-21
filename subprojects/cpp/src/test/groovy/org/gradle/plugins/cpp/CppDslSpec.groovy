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

class CppDslSpec extends CppProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "configure library through dsl"() {
        when:
        cpp {
            sourceSets {
                main {}
            }
            library {
                source sourceSets.main
            }
            executable {
                libs libraries.main
            }
        }

        then:
        tasks.all*.name.find { it == "compileMainLibrary" }
        tasks.all*.name.find { it == "linkMainLibrary" }
        tasks.all*.name.find { it == "linkMainExecutable" }

        and:
        def library = cpp.libraries.main
        library.file == file("$buildDir/binaries/main.so")

        and:
        def executable = cpp.executables.main
        executable.file == file("$buildDir/binaries/main")

        and:
        cpp.libraries.main.includes.size() == 1
    }

}