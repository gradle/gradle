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

package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.HelloWorldApp

class CppLanguageIntegrationTest extends AbstractLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CppHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
             executables {
                 main {}
             }
         """

        and:
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.");
        failure.assertHasCause("C++ compiler failed; see the error output for details.")
    }

    def "sources are compiled with C++ compiler"() {
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
             executables {
                 main {}
             }
         """

        expect:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == app.expectedOutput(toolChain)
    }

    def "can manually define C++ source sets"() {
        given:
        helloWorldApp.getLibraryHeader().writeToDir(file("src/shared"))

        file("src/main/cpp/main.cpp") << helloWorldApp.mainSource.content
        file("src/main/cpp2/hello.cpp") << helloWorldApp.librarySources[0].content
        file("src/main/sum-sources/sum.cpp") << helloWorldApp.librarySources[1].content


        and:
        buildFile << """
            sources {
                main {
                    cpp {
                        exportedHeaders {
                            srcDirs "src/shared/headers"
                        }
                    }
                    cpp2(CppSourceSet) {
                        exportedHeaders {
                            srcDirs "src/shared/headers"
                        }
                    }
                    cpp3(CppSourceSet) {
                        source {
                            srcDir "src/main/sum-sources"
                        }
                        exportedHeaders {
                            srcDirs "src/shared/headers"
                        }
                    }
                }
            }
            executables {
                main {}
            }
"""

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

}

