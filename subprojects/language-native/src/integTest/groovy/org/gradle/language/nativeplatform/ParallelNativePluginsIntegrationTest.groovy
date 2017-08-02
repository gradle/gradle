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
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.AssemblerWithCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.MixedObjectiveCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.TestApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP
import static org.gradle.util.TestPrecondition.OBJECTIVE_C_SUPPORT

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelNativePluginsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    Map<String, TestApp> apps = [:]
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        executer.withArgument("--max-workers=4")
        apps = [
            c              : new CHelloWorldApp(),
            cpp            : new CppHelloWorldApp()
        ]
        if (OBJECTIVE_C_SUPPORT.fulfilled) {
            apps += [
                objectiveC     : new ObjectiveCHelloWorldApp(),
                objectiveCpp   : new ObjectiveCppHelloWorldApp()
            ]
        }
    }

    @Requires(OBJECTIVE_C_SUPPORT)
    def "can produce multiple executables from a single project in parallel"() {
        given:
        apps << [ mixedObjectiveC: new MixedObjectiveCHelloWorldApp() ]
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

    def "can execute link executable tasks in parallel"() {
        given:
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("link.*Executable")
    }

    def "can execute link shared library tasks in parallel"() {
        given:
        withComponentsForAppsAndSharedLibs(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("link.*SharedLibrary")
    }

    def "can execute create static library tasks in parallel"() {
        given:
        withComponentsForAppsAndStaticLibs(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("create.*StaticLibrary")
    }

    def "can execute compile tasks in parallel"() {
        given:
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("compile.*")
    }

    def "can execute assemble tasks in parallel"() {
        given:
        apps = [
            assemblerWithC1 : new AssemblerWithCHelloWorldApp(toolChain),
            assemblerWithC2 : new AssemblerWithCHelloWorldApp(toolChain)
        ]
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("assembleAssembler.*")
    }

    @RequiresInstalledToolChain(VISUALCPP)
    def "can execute windows resource compile tasks in parallel"() {
        given:
        apps = [
            windowsRC1 : new WindowsResourceHelloWorldApp(),
            windowsRC2 : new WindowsResourceHelloWorldApp()
        ]
        withComponentsForApps(apps)

        when:
        succeeds("assemble")

        then:
        assertTasksAreParallel("compile.*Rc")
    }

    /**
     * This is an approximation to check that a set of tasks execute in parallel by default.  Basically it checks to
     * see if any of the tasks execute concurrently with any other task.  We can't guarantee that another task will
     * always run concurrently while a given task is executing, so we settle for checking that the opposite does not
     * happen (i.e. that none of the tasks execute concurrently with other tasks).
     */
    void assertTasksAreParallel(String regex) {
        def tasks = buildOperations.all(ExecuteTaskBuildOperationType, new Spec<BuildOperationRecord>() {
            @Override
            boolean isSatisfiedBy(BuildOperationRecord record) {
                return record.displayName.matches("Task :${regex}")
            }
        })
        assert tasks.size() == apps.size()
        assert tasks.any { buildOperations.getOperationsConcurrentWith(ExecuteTaskBuildOperationType, it).size() > 0 }
    }

    def withComponentsForApps(Map<String, TestApp> apps) {
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

    def withComponentsForAppsAndStaticLibs(Map<String, TestApp> apps) {
        withComponentsForAppsAndLibs(apps, true)
    }

    def withComponentsForAppsAndSharedLibs(Map<String, TestApp> apps) {
        withComponentsForAppsAndLibs(apps, false)
    }

    def withComponentsForAppsAndLibs(Map<String, TestApp> apps, boolean useStaticLibs) {
        apps.each { name, app ->
            buildFile << app.pluginScript
            buildFile << app.getExtraConfiguration("${name}Executable")
            buildFile << app.getExtraConfiguration("${name}Lib${useStaticLibs ? 'Static' : 'Shared'}Library")
            buildFile << """
                model {
                    components {
                        ${name}(NativeExecutableSpec) {
                            sources.${app.sourceType}.lib library: "${name}Lib", linkage: '${useStaticLibs ? "static" : "shared"}'
                        }
                        ${name}Lib(NativeLibrarySpec) {
                            binaries.withType(${useStaticLibs ? "Shared" : "Static"}LibraryBinarySpec) {
                                buildable = false
                            }
                        }
                    }
                }
            """

            app.executable.writeSources(file("src/$name"))
            app.library.writeSources(file("src/${name}Lib"))
        }
    }
}
