/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.nativeplatform

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.nativeplatform.fixtures.NativeInstallationFixture
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.MixedObjectiveCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.TestApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelNativePluginsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    Map<String, TestApp> apps = [:]
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        executer.withArgument("--max-workers=4")
    }

    @Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
    def "can produce multiple executables from a single project in parallel"() {
        given:
        apps = [
                c              : new CHelloWorldApp(),
                cpp            : new CppHelloWorldApp(),
                objectiveC     : new ObjectiveCHelloWorldApp(),
                objectiveCpp   : new ObjectiveCppHelloWorldApp(),
                mixedObjectiveC: new MixedObjectiveCHelloWorldApp()
        ]
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        Map<ExecutableFixture, HelloWorldApp> executables = apps.collectEntries { name, app ->
            def executable = executable("build/exe/$name/$name")
            executable.assertExists()
            [executable, app]
        }
        executables.every { executable, app ->
            executable.exec().out == app.englishOutput
        }

        and:
        buildOperations.assertConcurrentOperationsExecuted(ExecuteTaskBuildOperationType)
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can produce multiple executables that use a library from a single project in parallel"() {
        given:
        apps = [
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

    @Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
    def "can execute link tasks in parallel"() {
        given:
        apps = [
            c              : new CHelloWorldApp(),
            cpp            : new CppHelloWorldApp(),
            objectiveC     : new ObjectiveCHelloWorldApp(),
            objectiveCpp   : new ObjectiveCppHelloWorldApp()
        ]
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        assertLinkTasksAreParallel()
    }

    void assertLinkTasksAreParallel() {
        assertTaskAreParallel("link")
    }

    void assertTaskAreParallel(String type) {
        def linkTasks = buildOperations.all(ExecuteTaskBuildOperationType, new Spec<BuildOperationRecord>() {
            @Override
            boolean isSatisfiedBy(BuildOperationRecord record) {
                return record.displayName.startsWith("Task :${type}")
            }
        })
        assert linkTasks.size() == apps.size()
        assert linkTasks.any { buildOperations.getOperationsConcurrentAfter(ExecuteTaskBuildOperationType, it).size() > 0 }
    }

    def withComponentsForApps(Map<String, HelloWorldApp> apps) {
        apps.each { name, app ->
            buildFile << app.pluginScript
            buildFile << app.getExtraConfiguration("${name}Executable")
            buildFile << """
                model {
                    components {
                        ${name}(NativeExecutableSpec)
                    }
                }
            """

            app.writeSources(file("src/$name"))
        }
    }
}
