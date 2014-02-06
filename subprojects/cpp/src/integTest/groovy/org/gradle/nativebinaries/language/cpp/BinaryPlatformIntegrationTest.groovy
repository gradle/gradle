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

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.SystemInfo
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement
import org.gradle.nativebinaries.language.cpp.fixtures.app.PlatformDetectingTestApp
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativebinaries.language.cpp.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.lang.Unroll

@Requires(TestPrecondition.NOT_UNKNOWN_OS)
class BinaryPlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def testApp = new PlatformDetectingTestApp()
    def os = OperatingSystem.current()

    def setup() {
        buildFile << """
            apply plugin: 'cpp'

            executables {
                main {}
            }
        """

        testApp.writeSources(file("src/main"))
    }

    def "build binary for a default target platform"() {
        given:
        final SystemInfo systemInfo = Native.get(SystemInfo)
        def arch = systemInfo.architecture == SystemInfo.Architecture.amd64 ? [main: "x86_64", alt: "amd64"] : [main: "x86", alt: "i386"]

        when:
        succeeds "mainExecutable"

        then:
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == arch.main
        executable("build/binaries/mainExecutable/main").exec().out == "${arch.alt} ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objectFiles/mainExecutable/mainCpp")).arch.name == arch.main
    }

    def "configure component for a single target platform"() {
        when:
        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "x86"
                    }
                    x86_64 {
                        architecture "x86_64"
                    }
                }
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
            executables.main.targetPlatforms "x86"
"""

        and:
        succeeds "buildExecutables"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/main").exec().out == "i386 ${os.familyName}" * 2
    }

    def "library with matching platform is chosen by dependency resolution"() {
        given:
        testApp.executable.writeSources(file("src/exe"))
        testApp.library.writeSources(file("src/hello"))
        when:
        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "x86"
                    }
                    x86_64 {
                        architecture "x86_64"
                    }
                }
            }
            executables {
                exe {}
            }
            libraries {
                hello {}
            }
            sources.exe.cpp.lib libraries.hello.static
            executables.exe.targetPlatforms "x86"
"""

        and:
        succeeds "exeExecutable"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":exeExecutable")
        executable("build/binaries/exeExecutable/exe").binaryInfo.arch.name == "x86"
        executable("build/binaries/exeExecutable/exe").exec().out == "i386 ${os.familyName}" * 2
    }

    def "build binary for multiple target architectures"() {
        when:
        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "x86"
                    }
                    x86_64 {
                        architecture "x86_64"
                    }
                    itanium {
                        architecture "ia-64"
                    }
                    arm {
                        architecture "arm"
                    }
                }
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
"""

        and:
        succeeds "buildExecutables"

        then:
        executable("build/binaries/mainExecutable/x86/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/x86/main").exec().out == "i386 ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objectFiles/mainExecutable/x86/mainCpp")).arch.name == "x86"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            executable("build/binaries/mainExecutable/x86_64/main").assertDoesNotExist()
        } else {
            executable("build/binaries/mainExecutable/x86_64/main").binaryInfo.arch.name == "x86_64"
            executable("build/binaries/mainExecutable/x86_64/main").exec().out == "amd64 ${os.familyName}" * 2
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objectFiles/mainExecutable/x86_64/mainCpp")).arch.name == "x86_64"
        }

        // Itanium only supported on visualCpp
        if (toolChain.visualCpp) {
            executable("build/binaries/mainExecutable/itanium/main").binaryInfo.arch.name == "ia-64"
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"),"build/objectFiles/mainExecutable/itanium/mainCpp")).arch.name == "ia-64"
        } else {
            executable("build/binaries/mainExecutable/itanium/main").assertDoesNotExist()
        }

        // ARM only supported on visualCpp 2013
        if (toolChain.meets(ToolChainRequirement.VisualCpp2013)) {
            executable("build/binaries/mainExecutable/arm/main").binaryInfo.arch.name == "arm"
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objectFiles/mainExecutable/arm/mainCpp")).arch.name == "arm"
        } else {
            executable("build/binaries/mainExecutable/arm/main").assertDoesNotExist()
        }
    }

    def "can configure binary for multiple target operating systems"() {
        when:
        buildFile << """
            model {
                platforms {
                    windows {
                        operatingSystem "windows"
                    }
                    linux {
                        operatingSystem "linux"
                    }
                    osx {
                        operatingSystem "osx"
                    }
                }
            }

            binaries.matching({ it.targetPlatform.operatingSystem.windows }).all {
                cppCompiler.define "FRENCH"
            }
            task buildExecutables {
                dependsOn binaries.withType(ExecutableBinary).matching {
                    it.buildable
                }
            }
        """

        and:
        succeeds "buildExecutables"

        then:
        if (os.windows) {
            executable("build/binaries/mainExecutable/windows/main").exec().out == "amd64 windows" * 2
        } else if (os.linux) {
            executable("build/binaries/mainExecutable/linux/main").exec().out == "amd64 linux" * 2
        } else if (os.macOsX) {
            executable("build/binaries/mainExecutable/osx/main").exec().out == "amd64 os x" * 2
        } else {
            throw new AssertionError("Unexpected operating system")
        }
    }

    @Unroll
    def "fails with reasonable error message when trying to build for an unavailable #type"() {
        when:
        buildFile << """
            model {
                platforms {
                    unavailable {
                        ${config}
                    }
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause(TextUtil.toPlatformLineSeparators("""No tool chain is available to build for platform 'unavailable':
  - ${toolChain.instanceDisplayName}: Don't know how to build for platform 'unavailable'."""))

        where:
        type               | config
        "architecture"     | "architecture 'sparc'"
        "operating system" | "operatingSystem 'solaris'"
    }

    @Unroll
    def "fails with reasonable error message when trying to build for an unknown #type"() {
        when:
        settingsFile << """rootProject.name = 'bad'"""
        buildFile << """
            model {
                platforms {
                    bad {
                        ${badConfig}
                    }
                }
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad'.")
        failure.assertHasCause("Cannot convert the provided notation to an object of type ${type}: bad.")

        where:
        type               | badConfig
        "Architecture"     | "architecture 'bad'"
        "OperatingSystem" | "operatingSystem 'bad'"
    }

    def "fails with reasonable error message when trying to target an unknown platform"() {
        when:
        settingsFile << "rootProject.name = 'bad-platform'"
        buildFile << """
            model {
                platforms {
                    main
                }
            }
            executables.main.targetPlatforms "unknown"
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad-platform'.")
        failure.assertHasCause("Invalid Platform: 'unknown'")
    }

    def "fails with reasonable error message when depended on library has no variant with matching platform"() {
        when:
        settingsFile << "rootProject.name = 'no-matching-platform'"
        buildFile << """
            apply plugin: 'cpp'
            model {
                platforms {
                    one
                    two
                }
            }
            libraries {
                hello {
                    targetPlatforms "two"
                }
            }
            sources.main.cpp.lib libraries.hello
"""

        and:
        fails "oneMainExecutable"

        then:
        failure.assertHasDescription("No shared library binary available for library 'hello' with [flavor: 'default', platform: 'one', buildType: 'debug']")
    }

    def binaryInfo(TestFile file) {
        file.assertIsFile()
        if (os.macOsX) {
            return new OtoolBinaryInfo(file)
        }
        if (os.windows) {
            return new DumpbinBinaryInfo(file, toolChain)
        }
        return new ReadelfBinaryInfo(file)
    }

}
