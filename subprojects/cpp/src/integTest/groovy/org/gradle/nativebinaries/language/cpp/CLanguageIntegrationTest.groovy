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

import org.gradle.nativebinaries.language.cpp.fixtures.app.CCompilerDetectingTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.HelloWorldApp
import spock.lang.Issue
import spock.lang.Unroll

class CLanguageIntegrationTest extends AbstractLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CHelloWorldApp()

    def "sources are compiled with C compiler"() {
        def app = new CCompilerDetectingTestApp()

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

    def "can manually define C source sets"() {
        given:
        helloWorldApp.getLibraryHeader().writeToDir(file("src/shared"))

        file("src/main/c/main.c") << helloWorldApp.mainSource.content
        file("src/main/c2/hello.c") << helloWorldApp.librarySources[0].content
        file("src/main/sum-sources/sum.c") << helloWorldApp.librarySources[1].content


        and:
        buildFile << """
            sources {
                main {
                    c {
                        exportedHeaders {
                            srcDirs "src/shared/headers"
                        }
                    }
                    c2(CSourceSet) {
                        exportedHeaders {
                            srcDirs "src/shared/headers"
                        }
                    }
                    c3(CSourceSet) {
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

    @Issue("GRADLE-2943")
    @Unroll
    def "can define macro #output"() {
        given:
        buildFile << """
            executables {
                main {
                    binaries.all {
                        ${helloWorldApp.compilerDefine('CUSTOM', inString)}
                    }
                }
            }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.getCustomOutput(output)

        where:
        inString                           | output
        '"quoted"'                         | 'quoted'
        '"with space"'                     | 'with space'
        '"with\\\\"quote\\\\"internal"'    | 'with"quote"internal'
        '"with \\\\"quote\\\\" and space"' | 'with "quote" and space'
    }

    def "compiler and linker args can contain quotes and spaces"() {
        given:
        buildFile << '''
            executables {
                main {
                    binaries.all {
                        // These are just some dummy arguments to test we don't blow up. Their effects are not verified.
                        if (toolChain in VisualCpp) {
                            cCompiler.args '/DVERSION="The version is \\'1.0\\'"'
                            linker.args '/MANIFESTUAC:level=\\'asInvoker\\' uiAccess=\\'false\\''
                        } else if (toolChain in Clang) {
                            cCompiler.args '-frandom-seed="here is the \\'random\\' seed"'
                            linker.args '-Wl,-client_name,"a \\'client\\' name"'
                        } else {
                            cCompiler.args '-frandom-seed="here is the \\'random\\' seed"'
                            linker.args '-Wl,--auxiliary,"an \\'auxiliary\\' name"'
                        }
                    }
                }
            }
        '''

        and:
        helloWorldApp.writeSources(file("src/main"))

        expect:
        succeeds "mainExecutable"
    }

    def "build fails when compilation fails"() {
        given:
        buildFile << """
             executables {
                 main {}
             }
         """

        and:
        file("src/main/c/broken.c") << """
        #include <stdio.h>

        'broken
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.");
        failure.assertHasCause("C compiler failed; see the error output for details.")
    }
}

