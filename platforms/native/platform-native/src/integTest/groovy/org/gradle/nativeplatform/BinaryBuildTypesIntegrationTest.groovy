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
package org.gradle.nativeplatform

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

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
        debug {
            ext.debug = true
        }
        integration {
            ext.debug = true
        }
        release {
            ext.debug = false
        }
    }
    components {
        main(NativeExecutableSpec) {
            binaries.all { binary ->
                if (toolChain in Gcc && buildType.debug) {
                    cppCompiler.args "-g"
                }
                if (toolChain in VisualCpp) {
                    // Apply to all debug build types: 'debug' and 'integration'
                    if (buildType.debug) {
                        cppCompiler.args ${toolChain.meets(ToolChainRequirement.VISUALCPP_2013_OR_NEWER) ? "'/Zi', '/FS'" : "'/Zi'"}
                        cppCompiler.define 'DEBUG'
                        linker.args '/DEBUG'
                    }
                }
                // Apply to 'integration' type binaries only
                if (buildType == buildTypes['integration']) {
                    cppCompiler.define "FRENCH"
                }
            }
        }
    }
}
        """
        and:
        succeeds "mainDebugExecutable", "mainIntegrationExecutable", "mainReleaseExecutable"

        then:
        with(executable("build/exe/main/debug/main")) {
            it.assertExists()
            it.assertDebugFileExists()
            it.exec().out == helloWorldApp.englishOutput
        }
        with (executable("build/exe/main/integration/main")) {
            it.assertExists()
            it.assertDebugFileExists()
            it.exec().out == helloWorldApp.frenchOutput
        }
        with (executable("build/exe/main/release/main")) {
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
        debug
        release
    }
    components {
        main(NativeExecutableSpec) {
            targetBuildTypes "release"
            binaries.all { binary ->
                if (buildType == buildTypes.release) {
                    cppCompiler.define "FRENCH"
                }
            }
        }
    }
}
"""

        and:
        succeeds "mainExecutable"

        then:
        // Build type dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/exe/main/main").exec().out == helloWorldApp.frenchOutput
    }

    @Requires(UnitTestPreconditions.CanInstallExecutable)
    def "executable with build type depends on library with matching build type"() {
        when:
        helloWorldApp.executable.writeSources(file("src/main"))
        helloWorldApp.library.writeSources(file("src/hello"))

        and:
        buildFile << """
apply plugin: 'cpp'
model {
    buildTypes {
        debug
        release
    }
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello", linkage: "static"
            }
        }
        hello(NativeLibrarySpec)
    }
    binaries {
        all {
            if (buildType == buildTypes.debug) {
                cppCompiler.define "FRENCH" // Equate 'debug' to 'french' for this test
            }
        }
    }
}
        """
        and:
        succeeds "installMainDebugExecutable", "installMainReleaseExecutable"

        then:
        installation("build/install/main/debug").exec().out == helloWorldApp.frenchOutput
        installation("build/install/main/release").exec().out == helloWorldApp.englishOutput
    }

    def "fails with reasonable error message when trying to target an unknown build type"() {
        when:
        settingsFile << "rootProject.name = 'bad-build-type'"
        buildFile << """
model {
    buildTypes {
        debug
    }
    components {
        main(NativeExecutableSpec) {
            targetBuildTypes "unknown"
        }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: NativeComponentModelPlugin.Rules#createBinaries")
        failure.assertHasCause("Invalid BuildType: 'unknown'")
    }

    def "fails with reasonable error message when depended on library has no variant with matching build type"() {
        when:
        settingsFile << "rootProject.name = 'no-matching-build-type'"
        buildFile << """
apply plugin: 'cpp'
model {
    buildTypes {
        debug
        release
    }
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.lib library: "hello", linkage: "static"
            }
        }
        hello(NativeLibrarySpec) {
            targetBuildTypes "debug"
        }
    }
}
"""

        and:
        fails "mainReleaseExecutable"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':linkMainReleaseExecutable'.")
        failure.assertHasCause("No static library binary available for library 'hello' with [flavor: 'default', platform: '${NativePlatformsTestFixture.defaultPlatformName}', buildType: 'release']")
    }
}
