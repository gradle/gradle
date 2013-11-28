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
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class BinaryBuildTypesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppHelloWorldApp()

    def "creates debug and release variants"() {
        when:
        helloWorldApp.writeSources(file("src/main"))
        and:
        buildFile << """
            apply plugin: 'cpp'
            model {
                buildTypes {
                    create("debug") {
                        ext.debug = true
                    }
                    create("integration") {
                        ext.debug = true
                    }
                    create("release") {
                        ext.debug = false
                    }
                }
            }
            binaries.all { binary ->
                if (toolChain in Gcc && buildType.debug) {
                    cppCompiler.args "-g"
                }
                if (toolChain in VisualCpp) {
                    // Apply to all debug build types: 'debug' and 'integration'
                    if (buildType.debug) {
                        cppCompiler.args '/Zi'
                        cppCompiler.define 'DEBUG'
                        linker.args '/DEBUG'
                    }
                }
                // Apply to 'integration' type binaries only
                if (buildType == buildTypes['integration']) {
                    cppCompiler.define "FRENCH"
                }
            }
            executables {
                main {}
            }
        """
        and:
        succeeds "debugMainExecutable", "integrationMainExecutable", "releaseMainExecutable"

        then:
        with(executable("build/binaries/mainExecutable/debug/main")) {
            it.assertExists()
            it.assertDebugFileExists()
            it.exec().out == helloWorldApp.englishOutput
        }
        with (executable("build/binaries/mainExecutable/integration/main")) {
            it.assertExists()
            it.assertDebugFileExists()
            it.exec().out == helloWorldApp.frenchOutput
        }
        with (executable("build/binaries/mainExecutable/release/main")) {
            it.assertExists()
            it.assertDebugFileDoesNotExist()
            it.exec().out == helloWorldApp.englishOutput
        }
    }

    def "configure component for a single build type"() {
        when:
        helloWorldApp.writeSources(file("src/main"))
        buildFile << """
            apply plugin: 'cpp'
            model {
                buildTypes {
                    create("debug")
                    create("release")
                }
            }
            executables {
                main {
                    targetBuildTypes "release"
                }
            }
            binaries.all { binary ->
                if (buildType == buildTypes.release) {
                    cppCompiler.define "FRENCH"
                }
            }
"""

        and:
        succeeds "mainExecutable"

        then:
        // Build type dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "executable with build type depends on library with matching build type"() {
        when:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            apply plugin: 'cpp'
            model {
                buildTypes {
                    create("debug") { }
                    create("release") {}
                }
            }
            binaries.all {
                if (buildType == buildTypes.debug) {
                    cppCompiler.define "FRENCH" // Equate 'debug' to 'french' for this test
                }
            }
            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.cpp.lib libraries.hello.static
        """
        and:
        succeeds "installDebugMainExecutable", "installReleaseMainExecutable"

        then:
        installation("build/install/mainExecutable/debug").exec().out == helloWorldApp.frenchOutput
        installation("build/install/mainExecutable/release").exec().out == helloWorldApp.englishOutput
    }

    def "fails with reasonable error message when trying to target an unknown build type"() {
        when:
        settingsFile << "rootProject.name = 'bad-build-type'"
        buildFile << """
            model {
                buildTypes {
                    create("debug") {}
                }
            }
            executables {
                main {
                    targetBuildTypes "unknown"
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad-build-type'.")
        failure.assertHasCause("Invalid BuildType: 'unknown'")
    }
}
