/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.language.AbstractNativeSoftwareModelParallelIntegrationTest
import org.gradle.nativeplatform.fixtures.NativeInstallationFixture
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class CppLanguageParallelIntegrationTest extends AbstractNativeSoftwareModelParallelIntegrationTest {
    HelloWorldApp app = new CppHelloWorldApp()

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    def "can produce multiple executables that use a library from a single project in parallel"() {
        given:
        def apps = [
            first : new ExeWithLibraryUsingLibraryHelloWorldApp(),
            second: new ExeWithLibraryUsingLibraryHelloWorldApp(),
            third : new ExeWithLibraryUsingLibraryHelloWorldApp(),
        ]

        apps.each { name, app ->
            buildFile << app.pluginScript
            buildFile << app.extraConfiguration

            app.executable.writeSources(file("src/${name}Main"))
            app.library.writeSources(file("src/${name}Hello"))
            app.greetingsHeader.writeToDir(file("src/${name}Hello"))
            app.greetingsSources*.writeToDir(file("src/${name}Greetings"))

            buildFile << """
                model {
                    // Allow static libraries to be linked into shared
                    binaries {
                        withType(StaticLibraryBinarySpec) {
                            if (toolChain in Gcc || toolChain in Clang) {
                                cppCompiler.args '-fPIC'
                            }
                        }
                    }

                    components {
                        ${name}Main(NativeExecutableSpec) {
                            sources {
                                cpp.lib library: '${name}Hello'
                            }
                        }
                        ${name}Hello(NativeLibrarySpec) {
                            sources {
                                cpp.lib library: '${name}Greetings', linkage: 'static'
                            }
                        }
                        ${name}Greetings(NativeLibrarySpec) {
                            sources {
                                cpp.lib library: '${name}Hello', linkage: 'api'
                            }
                        }
                    }
                }
            """
        }

        when:
        run(*apps.keySet().collect { "install${it.capitalize()}MainExecutable" })

        then:
        Map<NativeInstallationFixture, HelloWorldApp> installations = apps.collectEntries { name, app ->
            def installation = installation("build/install/${name}Main")
            [installation, app]
        }
        installations.every { installation, app ->
            installation.exec().out == app.englishOutput
        }

        and:
        buildOperations.assertConcurrentOperationsExecuted(ExecuteTaskBuildOperationType)
    }
}
