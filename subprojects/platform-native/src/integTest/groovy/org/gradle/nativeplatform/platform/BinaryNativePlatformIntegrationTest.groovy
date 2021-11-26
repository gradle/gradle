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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.PlatformDetectingTestApp
import org.gradle.nativeplatform.fixtures.binaryinfo.DumpbinBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.FileArchOnlyBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.OtoolBinaryInfo
import org.gradle.nativeplatform.fixtures.binaryinfo.ReadelfBinaryInfo
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32_AND_64

class BinaryNativePlatformIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def testApp = new PlatformDetectingTestApp()
    def os = OperatingSystem.current()

    def setup() {
        buildFile << """
plugins {
    id 'cpp'
}
model {
    components {
        main(NativeExecutableSpec)
    }
}
"""

        testApp.writeSources(file("src/main"))
    }

    // Tests will only work on x86 and x86-64 architectures
    def currentArch() {
        // On windows we currently target i386 by default, even on amd64
        if (OperatingSystem.current().windows || Native.get(SystemInfo).architecture == SystemInfo.Architecture.i386) {
            return [name: "x86", altName: "i386"]
        }
        return [name: "x86-64", altName: "amd64"]
    }

    @ToBeFixedForConfigurationCache
    def "build binary for a default target platform"() {
        given:
        def arch = currentArch()

        when:
        succeeds "mainExecutable"

        then:
        executedAndNotSkipped(":mainExecutable")
        executable("build/exe/main/main").arch.name == arch.name
        executable("build/exe/main/main").exec().out == "${arch.altName} ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/main/mainCpp")).arch.name == arch.name
    }

    @RequiresInstalledToolChain(SUPPORTS_32)
    @ToBeFixedForConfigurationCache
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
    components {
        main { targetPlatform "x86" }
    }
}
"""

        and:
        succeeds "assemble"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":mainExecutable")
        executable("build/exe/main/main").arch.name == "x86"
        executable("build/exe/main/main").exec().out == "i386 ${os.familyName}" * 2
    }

    @ToBeFixedForConfigurationCache
    def "defaults to current platform when platforms are defined but not targeted"() {
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
        executable("build/exe/main/main").arch.name == arch.name
        executable("build/exe/main/main").exec().out == "${arch.altName} ${os.familyName}" * 2
    }

    @RequiresInstalledToolChain(SUPPORTS_32)
    @ToBeFixedForConfigurationCache
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
    components {
        exe(NativeExecutableSpec) {
            targetPlatform "x86"
            sources {
                cpp.lib library: "hello", linkage: "static"
            }
        }
        hello(NativeLibrarySpec) {
            targetPlatform "x86"
        }
    }
}
"""

        and:
        succeeds "exeExecutable"

        then:
        // Platform dimension is flattened since there is only one possible value
        executedAndNotSkipped(":exeExecutable")
        executable("build/exe/exe/exe").arch.name == "x86"
        executable("build/exe/exe/exe").exec().out == "i386 ${os.familyName}" * 2
    }

    @ToBeFixedForConfigurationCache
    def "library with no platform defined is correctly chosen by dependency resolution"() {
        def arch = currentArch()

        given:
        testApp.executable.writeSources(file("src/exe"))
        testApp.library.writeSources(file("src/hello"))
        when:
        buildFile << """
model {
    components {
        exe(NativeExecutableSpec) {
            sources {
                cpp.lib library: 'hello', linkage: 'static'
            }
        }
        hello(NativeLibrarySpec)
    }
}
"""

        and:
        succeeds "exeExecutable"

        then:
        executedAndNotSkipped(":exeExecutable")
        executable("build/exe/exe/exe").arch.name == arch.name
        executable("build/exe/exe/exe").exec().out == "${arch.altName} ${os.familyName}" * 2
    }

    @RequiresInstalledToolChain(SUPPORTS_32_AND_64)
    @ToBeFixedForConfigurationCache
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
        arm {
            architecture "arm"
        }
    }
    components {
        main {
            targetPlatform "x86"
            targetPlatform "x86_64"
            targetPlatform "arm"
        }
    }
}

"""

        and:
        succeeds "assemble"

        then:
        executable("build/exe/main/x86/main").arch.name == "x86"
        executable("build/exe/main/x86/main").exec().out == "i386 ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/main/x86/mainCpp")).arch.name == "x86"

        executable("build/exe/main/x86_64/main").arch.name == "x86-64"
        executable("build/exe/main/x86_64/main").exec().out == "amd64 ${os.familyName}" * 2
        binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/main/x86_64/mainCpp")).arch.name == "x86-64"

        // ARM only supported on visualCpp 2012+
        if (toolChain.meets(ToolChainRequirement.VISUALCPP_2012_OR_NEWER)) {
            executable("build/exe/main/arm/main").arch.name == "arm"
            binaryInfo(objectFileFor(file("src/main/cpp/main.cpp"), "build/objs/main/arm/mainCpp")).arch.name == "arm"
        } else {
            executable("build/exe/main/arm/main").assertDoesNotExist()
        }
    }

    @RequiresInstalledToolChain(SUPPORTS_32)
    @ToBeFixedForConfigurationCache
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
            architecture "x86"
        }
        windows {
            operatingSystem "windows"
            architecture "x86"
        }
        linux {
            operatingSystem "linux"
            architecture "x86"
        }
    }
    components {
        main { targetPlatform "$currentOs" }
    }
    binaries {
        all {
            if (targetPlatform.operatingSystem.windows) {
                cppCompiler.define "FRENCH"
            }
        }
    }
}
        """
        and:
        succeeds "assemble"

        then:
        if (os.windows) {
            executable("build/exe/main/main").exec().out == "i386 windows" * 2
        } else if (os.linux) {
            executable("build/exe/main/main").exec().out == "i386 linux" * 2
        } else if (os.macOsX) {
            executable("build/exe/main/main").exec().out == "i386 os x" * 2
        } else {
            throw new AssertionError("Unexpected operating system")
        }
    }

    @ToBeFixedForConfigurationCache
    def "fails with reasonable error message when trying to build for an #type"() {
        when:
        buildFile << """
model {
    platforms {
        unavailable {
            ${config}
        }
    }
    components {
        main { targetPlatform 'unavailable' }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainCpp'.")
        failure.assertHasCause("""No tool chain is available to build for platform 'unavailable':
  - ${toolChain.instanceDisplayName}:
      - Don't know how to build for platform 'unavailable'.""")

        where:
        type                           | config
        "unavailable architecture"     | "architecture 'sparc'"
        "unavailable operating system" | "operatingSystem 'solaris'"
        "unknown architecture"         | "architecture 'unknown'"
        "unknown operating system"     | "operatingSystem 'unknown'"
    }

    def "fails with reasonable error message when trying to target an unknown platform"() {
        when:
        settingsFile << "rootProject.name = 'bad-platform'"
        buildFile << """
model {
    platforms {
        main
    }
    components {
        main { targetPlatform "unknown" }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: NativeComponentModelPlugin.Rules#createBinaries")
        failure.assertHasCause("Invalid NativePlatform: unknown")
    }

    def "fails with reasonable error message when depended on library has no variant with matching platform"() {
        when:
        settingsFile << "rootProject.name = 'no-matching-platform'"
        buildFile << """
model {
    platforms {
        one
        two
    }
    components {
        hello(NativeLibrarySpec) {
            targetPlatform "two"
        }
        main {
            targetPlatform "one"
            sources {
                cpp.lib library: 'hello'
            }
        }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':linkMainExecutable'.")
        failure.assertHasCause("No shared library binary available for library 'hello' with [flavor: 'default', platform: 'one', buildType: 'debug']")
    }

    @Issue("GRADLE-3499")
    @ToBeFixedForConfigurationCache(because = ":components")
    def "can create a binary which name contains dots"() {
        when:
        buildFile << '''
            model {
                components {
                    'foo.bar'(NativeLibrarySpec)
                }
            }
        '''
        then:
        succeeds 'components'

    }

    def binaryInfo(TestFile file) {
        // Only the arch functionality is needed for this test, so fall back to the file utility if nothing else works.
        file.assertIsFile()
        if (os.macOsX) {
            return new OtoolBinaryInfo(file, toolchainUnderTest.runtimeEnv)
        }
        if (os.windows) {
            return DumpbinBinaryInfo.findVisualStudio() ? new DumpbinBinaryInfo(file) : new FileArchOnlyBinaryInfo(file)
        }
        if (ReadelfBinaryInfo.canUseReadelf()) {
            return new ReadelfBinaryInfo(file, toolchainUnderTest.runtimeEnv)
        } else {
            return new FileArchOnlyBinaryInfo(file)
        }
    }

}
