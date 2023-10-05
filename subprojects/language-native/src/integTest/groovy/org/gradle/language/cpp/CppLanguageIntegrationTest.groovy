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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Assume

import static org.gradle.util.Matchers.containsText

class CppLanguageIntegrationTest extends AbstractNativeLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CppHelloWorldApp()

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
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C++ compiler failed while compiling broken.cpp"))
    }

    @ToBeFixedForConfigurationCache
    def "finds C and C++ standard library headers"() {
        // https://github.com/gradle/gradle-native/issues/282
        Assume.assumeFalse(toolChain.id == "gcccygwin")
        given:
        buildFile << """
            model {
                components {
                    main(NativeLibrarySpec)
                }
            }
         """

        and:
        file("src/main/cpp/includeIoStream.cpp") << """
            #include <stdio.h>
            #include <iostream>
        """

        when:
        executer.withArgument("--info")
        run 'mainSharedLibrary'

        then:
        output.contains("Found all include files for ':compileMainSharedLibraryMainCpp'")
    }

    @Requires(UnitTestPreconditions.MacOs)
    @ToBeFixedForConfigurationCache
    def "can compile and link C++ code using standard macOS framework"() {
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
        file("src/main/cpp/includeFramework.cpp") << """
            #include <CoreFoundation/CoreFoundation.h>

            void sayHelloFoundation() {
                CFShow(CFSTR("Hello"));
            }
        """

        expect:
        succeeds 'mainSharedLibrary'
        result.assertTasksExecuted(":compileMainSharedLibraryMainCpp", ":linkMainSharedLibrary", ":mainSharedLibrary")
        result.assertTasksNotSkipped(":compileMainSharedLibraryMainCpp", ":linkMainSharedLibrary", ":mainSharedLibrary")
    }

    @ToBeFixedForConfigurationCache
    def "sources are compiled with C++ compiler"() {
        def app = new CppCompilerDetectingTestApp()

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
    def "can manually define C++ source sets"() {
        given:
        helloWorldApp.library.headerFiles.each { it.writeToDir(file("src/shared")) }

        file("src/main/cpp/main.cpp") << helloWorldApp.mainSource.content
        file("src/main/cpp2/hello.cpp") << helloWorldApp.librarySources[0].content
        file("src/main/sum-sources/sum.cpp") << helloWorldApp.librarySources[1].content

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
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

    @RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
    @ToBeFixedForConfigurationCache
    def "system headers are not evaluated when compiler warnings are enabled"() {
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                cppCompiler.args "-Wall", "-Werror"
            }
        }
    }
}
         """

        expect:
        succeeds "mainExecutable"
        executable("build/exe/main/main").exec().out == app.expectedOutput(toolChain)
    }
}

