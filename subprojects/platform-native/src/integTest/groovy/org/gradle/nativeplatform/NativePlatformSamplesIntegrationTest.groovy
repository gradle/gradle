/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.integtests.fixtures.Sample
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GccCompatible

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
@LeaksFileHandles
class NativePlatformSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule final TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider()
    @Rule public final Sample cppLib = sample(testDirProvider, 'cpp-lib')
    @Rule public final Sample cppExe = sample(testDirProvider, 'cpp-exe')
    @Rule public final Sample multiProject = sample(testDirProvider, 'multi-project')
    @Rule public final Sample flavors = sample(testDirProvider, 'flavors')
    @Rule public final Sample variants = sample(testDirProvider, 'variants')
    @Rule public final Sample toolChains = sample(testDirProvider, 'tool-chains')
    @Rule public final Sample prebuilt = sample(testDirProvider, 'prebuilt')
    @Rule public final Sample targetPlatforms = sample(testDirProvider, 'target-platforms')
    @Rule public final Sample sourcesetVariant = sample(testDirectoryProvider, "sourceset-variant")

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}", name)
    }

    def "exe"() {
        given:
        // Need to PATH to be set to find the 'strip' executable
        toolChain.initialiseEnvironment()

        and:
        sample cppExe

        when:
        EnableModelDsl.enable(executer)
        run "installMain"

        then:
        executedAndNotSkipped ":compileMainExecutableMainCpp", ":linkMainExecutable", ":stripMainExecutable", ":mainExecutable"

        and:
        executable(cppExe.dir.file("build/binaries/mainExecutable/main")).exec().out == "Hello, World!\n"
        installation(cppExe.dir.file("build/install/mainExecutable")).exec().out == "Hello, World!\n"

        cleanup:
        toolChain.resetEnvironment()
    }

    def "lib"() {
        given:
        sample cppLib

        when:
        run "mainSharedLibrary"

        then:
        executedAndNotSkipped ":compileMainSharedLibraryMainCpp", ":linkMainSharedLibrary", ":mainSharedLibrary"

        and:
        sharedLibrary(cppLib.dir.file("build/binaries/mainSharedLibrary/main")).assertExists()

        when:
        sample cppLib
        run "mainStaticLibrary"

        then:
        executedAndNotSkipped ":compileMainStaticLibraryMainCpp", ":createMainStaticLibrary", ":mainStaticLibrary"

        and:
        staticLibrary(cppLib.dir.file("build/binaries/mainStaticLibrary/main")).assertExists()
    }

    def flavors() {
        when:
        sample flavors
        run "installEnglishMainExecutable"

        then:
        executedAndNotSkipped ":compileEnglishHelloSharedLibraryHelloCpp", ":linkEnglishHelloSharedLibrary", ":englishHelloSharedLibrary"
        executedAndNotSkipped ":compileEnglishMainExecutableMainCpp", ":linkEnglishMainExecutable", ":englishMainExecutable"

        and:
        executable(flavors.dir.file("build/binaries/mainExecutable/english/main")).assertExists()
        sharedLibrary(flavors.dir.file("build/binaries/helloSharedLibrary/english/hello")).assertExists()

        and:
        installation(flavors.dir.file("build/install/mainExecutable/english")).exec().out == "Hello world!\n"

        when:
        sample flavors
        run "installFrenchMainExecutable"

        then:
        executedAndNotSkipped ":compileFrenchHelloSharedLibraryHelloCpp", ":linkFrenchHelloSharedLibrary", ":frenchHelloSharedLibrary"
        executedAndNotSkipped ":compileFrenchMainExecutableMainCpp", ":linkFrenchMainExecutable", ":frenchMainExecutable"

        and:
        executable(flavors.dir.file("build/binaries/mainExecutable/french/main")).assertExists()
        sharedLibrary(flavors.dir.file("build/binaries/helloSharedLibrary/french/hello")).assertExists()

        and:
        installation(flavors.dir.file("build/install/mainExecutable/french")).exec().out == "Bonjour monde!\n"
    }

    def variants() {
        when:
        sample variants
        run "assemble"

        then:
        final debugX86 = executable(variants.dir.file("build/binaries/mainExecutable/x86Debug/main"))
        final releaseX86 = executable(variants.dir.file("build/binaries/mainExecutable/x86Release/main"))
        final debugX64 = executable(variants.dir.file("build/binaries/mainExecutable/x64Debug/main"))
        final releaseX64 = executable(variants.dir.file("build/binaries/mainExecutable/x64Release/main"))
        final debugIA64 = executable(variants.dir.file("build/binaries/mainExecutable/itaniumDebug/main"))
        final releaseIA64 = executable(variants.dir.file("build/binaries/mainExecutable/itaniumRelease/main"))

        debugX86.binaryInfo.arch.name == "x86"
        debugX86.assertDebugFileExists()
        debugX86.exec().out == "Hello world!\n"

        releaseX86.binaryInfo.arch.name == "x86"
        releaseX86.assertDebugFileDoesNotExist()
        releaseX86.exec().out == "Hello world!\n"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            debugX64.assertDoesNotExist()
            releaseX64.assertDoesNotExist()
        } else {
            debugX64.binaryInfo.arch.name == "x86_64"
            releaseX64.binaryInfo.arch.name == "x86_64"
        }

        // Itanium not built
        debugIA64.assertDoesNotExist()
        releaseIA64.assertDoesNotExist()
    }

    def "tool chains"() {
        given:
        sample toolChains

        when:
        run "installMainExecutable"

        then:
        executable(toolChains.dir.file("build/binaries/mainExecutable/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    def multiProject() {
        given:
        sample multiProject

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary(multiProject.dir.file("lib/build/binaries/mainSharedLibrary/main")).assertExists()
        executable(multiProject.dir.file("exe/build/binaries/mainExecutable/main")).assertExists()
        installation(multiProject.dir.file("exe/build/install/mainExecutable")).exec().out == "Hello, World!\n"
    }

    @RequiresInstalledToolChain(GccCompatible)
    def "target platforms"() {
        given:
        sample targetPlatforms
        and:
        targetPlatforms.dir.file("build.gradle") << """
model {
    toolChains {
        all{
            target("arm"){
                cppCompiler.withArguments { args ->
                    args << "-m32"
                }
                linker.withArguments { args ->
                    args << "-m32"
                }
            }
            target("sparc")
        }
    }
}
"""

        when:
        run "installArmMainExecutable", "installSparcMainExecutable"

        then:
        executable(targetPlatforms.dir.file("build/binaries/mainExecutable/arm/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
        executable(targetPlatforms.dir.file("build/binaries/mainExecutable/arm/main")).binaryInfo.arch.isI386()

        executable(targetPlatforms.dir.file("build/binaries/mainExecutable/sparc/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    def prebuilt() {
        given:
        inDirectory(prebuilt.dir.file("3rd-party-lib/util"))
        run "assemble"

        and:
        sample prebuilt

        when:
        succeeds "assemble"

        then:

        executable(prebuilt.dir.file("build/binaries/mainExecutable/debug/main")).exec().out ==
"""Built with Boost version: 1_55
Util build type: DEBUG
"""
        executable(prebuilt.dir.file("build/binaries/mainExecutable/release/main")).exec().out ==
"""Built with Boost version: 1_55
Util build type: RELEASE
"""
    }

    def sourcesetvariant() {
        given:
        sample sourcesetVariant

        final String platformName
        if (OperatingSystem.current().isMacOsX()) {
            platformName = "MacOSX"
        } else if (OperatingSystem.current().isLinux()) {
            platformName = "Linux"
        } else if (OperatingSystem.current().isWindows()) {
            platformName = "Windows"
        } else {
            platformName = "Unknown"
        }

        when:
        run "installMainExecutable", "tasks"

        then:
        executedAndNotSkipped(":compileMainExecutableMainPlatform$platformName", ":installMainExecutable")

        and:
        executable(sourcesetVariant.dir.file("build/binaries/mainExecutable/main")).assertExists()
        installation(sourcesetVariant.dir.file("build/install/mainExecutable")).exec().out.contains("Attributes of '$platformName' platform")
    }
}
