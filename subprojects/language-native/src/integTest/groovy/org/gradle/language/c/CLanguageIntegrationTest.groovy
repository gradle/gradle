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
package org.gradle.language.c

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.gradle.util.Matchers.containsText

class CLanguageIntegrationTest extends AbstractNativeLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CHelloWorldApp()

    @ToBeFixedForConfigurationCache
    def "sources are compiled with C compiler"() {
        def app = new CCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
         """

        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == app.expectedOutput(toolChain)
    }

    @ToBeFixedForConfigurationCache
    def "can manually define C source sets"() {
        given:
        helloWorldApp.library.headerFiles.each { it.writeToDir(file("src/shared")) }

        file("src/main/c/main.c") << helloWorldApp.mainSource.content
        file("src/main/c2/hello.c") << helloWorldApp.librarySources[0].content
        file("src/main/sum-sources/sum.c") << helloWorldApp.librarySources[1].content

        and:
        buildFile << """
    model {
        components {
            main(NativeExecutableSpec) {
                sources {
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
        }
    }
"""

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

    @ToBeFixedForConfigurationCache
    def "uses headers co-located with sources"() {
        given:
        // Write headers so they sit with sources
        helloWorldApp.files.each {
            it.writeToFile(file("src/main/c/${it.name}"))
        }
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.source.include "**/*.c"
            }
        }
    }
}
"""

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

    @Issue("GRADLE-2943")
    @ToBeFixedForConfigurationCache
    def "can define macro #output"() {
        given:
        buildFile << """
        model {
            components {
                main(NativeExecutableSpec) {
                    binaries.all {
                        ${helloWorldApp.compilerDefine('CUSTOM', inString)}
                    }
                }
            }
        }
        """

        and:
        helloWorldApp.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.getCustomOutput(output)

        where:
        inString                           | output
        '"quoted"'                         | 'quoted'
        '"with space"'                     | 'with space'
        '"with\\\\"quote\\\\"internal"'    | 'with"quote"internal'
        '"with \\\\"quote\\\\" and space"' | 'with "quote" and space'
    }

    @ToBeFixedForConfigurationCache
    def "compiler and linker args can contain quotes and spaces"() {
        given:
        buildFile << '''
        model {
            components {
                main(NativeExecutableSpec) {
                    binaries.all {
                        // These are just some dummy arguments to test we don't blow up. Their effects are not verified.
                        if (toolChain in VisualCpp) {
                            cCompiler.args '/DVERSION="The version is \\'1.0\\'"'
                            linker.args '/MANIFESTUAC:level=\\'asInvoker\\' uiAccess=\\'false\\''
                        } else if (toolChain in Clang) {
                            cCompiler.args '-frandom-seed="here is the \\'random\\' seed"'
                            // TODO:DAZ Find something that works here (for all our CI machines)
                            // linker.args '-Wl,-client_name,"a \\'client\\' name"'
                        } else {
                            cCompiler.args '-frandom-seed="here is the \\'random\\' seed"'
                            // TODO:DAZ Find something that works on linux
                            // linker.args '-Wl,--auxiliary,"an \\'auxiliary\\' name"'
                        }
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

    @ToBeFixedForConfigurationCache
    def "build fails when compilation fails"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
         """

        and:
        file("src/main/c/broken.c") << """
        #include <stdio.h>

        'broken
"""
        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C compiler failed while compiling broken.c"))
    }

    @ToBeFixedForConfigurationCache
    def "build fails when multiple compilations fail"() {
        given:
        def brokenFileCount = 5
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
         """

        and:
        (1..brokenFileCount).each {
            file("src/main/c/broken${it}.c") << """
        #include <stdio.h>

        'broken
"""
        }

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.")
        failure.assertHasCause("Multiple build operations failed.")
        (1..brokenFileCount).each {
            failure.assertThatCause(containsText("C compiler failed while compiling broken${it}.c"))
        }
    }

    @Requires(UnitTestPreconditions.MacOs)
    @ToBeFixedForConfigurationCache
    def "can compile and link C code using standard macOS framework"() {
        given:
        buildFile << """
            model {
                components {
                    main(NativeLibrarySpec) {
                        binaries.all {
                            linker.args "-framework", "CoreFoundation"
                        }
                    }
                }
            }
         """

        and:
        file("src/main/c/includeFramework.c") << """
            #include <CoreFoundation/CoreFoundation.h>

            void sayHelloFoundation() {
                CFShow(CFSTR("Hello"));
            }
        """

        expect:
        succeeds 'mainSharedLibrary'
        result.assertTasksExecuted(":compileMainSharedLibraryMainC", ":linkMainSharedLibrary", ":mainSharedLibrary")
        result.assertTasksNotSkipped(":compileMainSharedLibraryMainC", ":linkMainSharedLibrary", ":mainSharedLibrary")
    }
}

