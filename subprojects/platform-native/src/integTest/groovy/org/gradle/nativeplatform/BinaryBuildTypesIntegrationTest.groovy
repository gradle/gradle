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
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class BinaryBuildTypesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def helloWorldApp = new CppHelloWorldApp()

    @LeaksFileHandles("can't delete build/binaries/mainExecutable/integration")
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
        }
    }
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

    @LeaksFileHandles
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
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.frenchOutput
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    @LeaksFileHandles
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
}
binaries.all {
    if (buildType == buildTypes.debug) {
        cppCompiler.define "FRENCH" // Equate 'debug' to 'french' for this test
    }
}
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
        failure.assertHasCause("Exception thrown while executing model rule: NativeComponentRules#createBinaries")
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
        fails "releaseMainExecutable"

        then:
        failure.assertHasDescription("No static library binary available for library 'hello' with [flavor: 'default', platform: '${NativePlatformsTestFixture.defaultPlatformName}', buildType: 'release']")
    }
}
