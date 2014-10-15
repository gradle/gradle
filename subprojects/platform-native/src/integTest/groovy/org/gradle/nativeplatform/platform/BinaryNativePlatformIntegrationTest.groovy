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

package org.gradle.nativeplatform.platform

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.SystemInfo
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.PlatformDetectingTestApp
import org.gradle.nativeplatform.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires(TestPrecondition.NOT_UNKNOWN_OS)
class BinaryNativePlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
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

    def currentArch() {
        def arch =  [name: "x86_64", altName: "amd64"]
        // Tool chains on Windows currently build for i386 by default, even on amd64
        if (OperatingSystem.current().windows || Native.get(SystemInfo).architecture == SystemInfo.Architecture.i386) {
            arch = [name: "x86", altName: "i386"]
        }
        return arch;
    }

    def "build binary for a default target platform"() {
        given:
        def arch = currentArch();

        when:
        succeeds "mainExecutable"

        then:
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == arch.name
        executable("build/binaries/mainExecutable/main").exec().out == "${arch.altName} ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/mainExecutable/mainCpp")).arch.name == arch.name
    }

    def "configure component for a single target platform"() {
        when:
        buildFile << """
            model {
                platforms {
                    sparc {
                        architecture "sparc"
                    }
                    x86 {
                        architecture "x86"
                    }
                    x86_64 {
                        architecture "x86_64"
                    }
                }
            }
            executables.main.targetPlatform "x86"
"""

        and:
        succeeds "assemble"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/main").exec().out == "i386 ${os.familyName}" * 2
    }


    def "use platform as default when only one platform is defined"() {
        when:
        buildFile << """
            model {
                platforms {
                    x86 {
                        architecture "x86"
                    }
                }
            }
"""

        and:
        succeeds "assemble"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/main").exec().out == "i386 ${os.familyName}" * 2
    }

    def "defaults to current platform if platforms are ambiguous (no targets & more than one)"() {
        def arch = currentArch()
        when:
        buildFile << """
            model {
                platforms {
                    sparc {
                        architecture "sparc"
                    }
                    x86 {
                        architecture "x86"
                    }
                }
            }
"""

        and:
        succeeds "assemble"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/binaries/mainExecutable/main").binaryInfo.arch.name == arch.name
        executable("build/binaries/mainExecutable/main").exec().out == "${arch.altName} ${os.familyName}" * 2
    }

    def "library with matching platform is enforced by dependency resolution"() {
        given:
        testApp.executable.writeSources(file("src/exe"))
        testApp.library.writeSources(file("src/hello"))
        when:
        buildFile << """
            model {
                platforms {
                    sparc {
                        architecture "sparc"
                    }
                    x86 {
                        architecture "x86"
                    }
                    x86_64 {
                        architecture "x86_64"
                    }
                }
            }
            executables {
                exe {
                    targetPlatform "x86"
                    sources {
                        cpp.lib library: "hello", linkage: "static"
                    }
                }
            }
            libraries {
                hello {
                    targetPlatform "x86"
                }
            }
"""

        and:
        succeeds "exeExecutable"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":exeExecutable")
        executable("build/binaries/exeExecutable/exe").binaryInfo.arch.name == "x86"
        executable("build/binaries/exeExecutable/exe").exec().out == "i386 ${os.familyName}" * 2
    }

    def "library with no platform defined is correctly chosen by dependency resolution"() {
        def arch = currentArch();

        given:
        testApp.executable.writeSources(file("src/exe"))
        testApp.library.writeSources(file("src/hello"))
        when:
        buildFile << """
            executables {
                exe
            }
            libraries {
                hello
            }
            sources {
                exe.cpp.lib libraries.hello.static
            }
"""

        and:
        succeeds "exeExecutable"

        then:
        executedAndNotSkipped(":exeExecutable")
        executable("build/binaries/exeExecutable/exe").binaryInfo.arch.name == arch.name
        executable("build/binaries/exeExecutable/exe").exec().out == "${arch.altName} ${os.familyName}" * 2
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

            executables.main.targetPlatform "x86", "x86_64", "itanium", "arm"
"""

        and:
        succeeds "assemble"

        then:
        executable("build/binaries/mainExecutable/x86/main").binaryInfo.arch.name == "x86"
        executable("build/binaries/mainExecutable/x86/main").exec().out == "i386 ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/mainExecutable/x86/mainCpp")).arch.name == "x86"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            executable("build/binaries/mainExecutable/x86_64/main").assertDoesNotExist()
        } else {
            executable("build/binaries/mainExecutable/x86_64/main").binaryInfo.arch.name == "x86_64"
            executable("build/binaries/mainExecutable/x86_64/main").exec().out == "amd64 ${os.familyName}" * 2
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/mainExecutable/x86_64/mainCpp")).arch.name == "x86_64"
        }

        // Itanium only supported on visualCpp
        if (toolChain.visualCpp) {
            executable("build/binaries/mainExecutable/itanium/main").binaryInfo.arch.name == "ia-64"
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"),"build/objs/mainExecutable/itanium/mainCpp")).arch.name == "ia-64"
        } else {
            executable("build/binaries/mainExecutable/itanium/main").assertDoesNotExist()
        }

        // ARM only supported on visualCpp 2013
        if (toolChain.meets(ToolChainRequirement.VisualCpp2013)) {
            executable("build/binaries/mainExecutable/arm/main").binaryInfo.arch.name == "arm"
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/mainExecutable/arm/mainCpp")).arch.name == "arm"
        } else {
            executable("build/binaries/mainExecutable/arm/main").assertDoesNotExist()
        }
    }

    def "can configure binary for multiple target operating systems"() {
        String currentOs
        if (os.windows) {
            currentOs = "windows"
        } else if (os.linux) {
            currentOs = "linux"
        } else if (os.macOsX) {
            currentOs = "osx"
        } else {
            throw new AssertionError("Unexpected operating system")
        }

        when:
        buildFile << """
            model {
                platforms {
                    osx {
                        operatingSystem "osx"
                    }
                    windows {
                        operatingSystem "windows"
                    }
                    linux {
                        operatingSystem "linux"
                    }
                }
            }

            binaries.matching({ it.targetPlatform.operatingSystem.windows }).all {
                cppCompiler.define "FRENCH"
            }

            executables.main.targetPlatform "$currentOs"
        """
        and:
        succeeds "assemble"

        then:
        if (os.windows) {
            executable("build/binaries/mainExecutable/main").exec().out == "amd64 windows" * 2
        } else if (os.linux) {
            executable("build/binaries/mainExecutable/main").exec().out == "amd64 linux" * 2
        } else if (os.macOsX) {
            executable("build/binaries/mainExecutable/main").exec().out == "amd64 os x" * 2
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
        failure.assertHasCause("""No tool chain is available to build for platform 'unavailable':
  - ${toolChain.instanceDisplayName}: Don't know how to build for platform 'unavailable'.""")

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
            executables.main.targetPlatform "unknown"
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'bad-platform'.")
        failure.assertHasCause("Invalid NativePlatform: unknown")
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
                    targetPlatform "two"
                }
            }
            executables.main.targetPlatform "one"

            executables.main.sources {
                cpp.lib libraries.hello
            }
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("No shared library binary available for library 'hello' with [flavor: 'default', platform: 'one', buildType: 'debug']")
    }

    def binaryInfo(TestFile file) {
        file.assertIsFile()
        if (os.macOsX) {
            return new OtoolBinaryInfo(file)
        }
        if (os.windows) {
            return new DumpbinBinaryInfo(file)
        }
        return new ReadelfBinaryInfo(file)
    }

}
