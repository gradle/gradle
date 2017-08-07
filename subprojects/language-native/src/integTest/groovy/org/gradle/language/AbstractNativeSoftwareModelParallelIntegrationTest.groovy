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

package org.gradle.language

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
abstract class AbstractNativeSoftwareModelParallelIntegrationTest extends AbstractNativeParallelIntegrationTest {
    abstract HelloWorldApp getApp()

    def "can execute link executable tasks in parallel"() {
        given:
        withComponentForApp()
        withTaskThatRunsParallelWith("linkMainExecutable")

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("linkMainExecutable")
    }

    def "can execute link shared library tasks in parallel"() {
        given:
        withComponentsForAppAndSharedLib()
        withTaskThatRunsParallelWith("linkMainLibSharedLibrary")

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("linkMainLibSharedLibrary")
    }

    def "can execute create static library tasks in parallel"() {
        given:
        withComponentsForAppAndStaticLib()
        withTaskThatRunsParallelWith("createMainLibStaticLibrary")

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("createMainLibStaticLibrary")
    }

    def "can execute compile tasks in parallel"() {
        given:
        withComponentForApp()
        withTaskThatRunsParallelWith("compileMainExecutableMain${app.sourceType.capitalize()}")

        when:
        succeeds("assemble", "parallelTask")

        then:
        assertTaskIsParallel("compileMainExecutableMain${app.sourceType.capitalize()}")
    }

    def withComponentForApp() {
        buildFile << app.pluginScript
        buildFile << app.getExtraConfiguration("mainExecutable")
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec)
                }
            }
        """

        app.writeSources(file("src/main"))
    }

    def withComponentsForAppAndStaticLib() {
        withComponentsForAppAndLib("static")
    }

    def withComponentsForAppAndSharedLib() {
        withComponentsForAppAndLib("shared")
    }

    def withComponentsForAppAndLib(String libType) {
        buildFile << app.pluginScript
        buildFile << app.getExtraConfiguration("mainExecutable")
        buildFile << app.getExtraConfiguration("mainLib${libType.capitalize()}Library")
        buildFile << """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources.${app.sourceType}.lib library: "mainLib", linkage: '${libType.toLowerCase()}'
                    }
                    mainLib(NativeLibrarySpec) {
                        binaries.withType(${oppositeLibType(libType).capitalize()}LibraryBinarySpec) {
                            buildable = false
                        }
                    }
                }
            }
        """

        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/mainLib"))
    }

    String oppositeLibType(String libType) {
        return libType.toLowerCase() == "static" ? "shared" : "static"
    }
}
