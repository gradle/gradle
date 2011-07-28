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
                main {
                    outputs {
                        exe {
                            sourceDirSet "cpp"
                            includeDirSet "headers"
                        }
                        lib {
                            sourceDirSet "cpp"
                            includeDirSet "headers"
                            sharedLibrary()
                        }
                    }
                }
            }
        }

        then:
        tasks.all*.name.find { it == "compileMainExe" }
        tasks.all*.name.find { it == "compileMainLib" }

        and:
        cpp.sourceSets.main.outputs.exe.outputFile == file("$buildDir/binaries/mainExe")

        and:
        cpp.sourceSets.main.outputs.lib.outputFile == file("$buildDir/binaries/mainLib.so")
    }

}