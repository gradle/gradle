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

import org.gradle.integtests.fixtures.Sample
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
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
    @Rule public final Sample customCheck = sample(testDirectoryProvider, "custom-check")

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
        run "installMain"

        then:
        executedAndNotSkipped ":compileMainExecutableMainCpp", ":linkMainExecutable", ":stripMainExecutable", ":mainExecutable"

        and:
        executable(cppExe.dir.file("build/exe/main/main")).exec().out == "Hello, World!\n"
        installation(cppExe.dir.file("build/install/main")).exec().out == "Hello, World!\n"

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
        sharedLibrary(cppLib.dir.file("build/libs/main/shared/main")).assertExists()

        when:
        sample cppLib
        run "mainStaticLibrary"

        then:
        executedAndNotSkipped ":compileMainStaticLibraryMainCpp", ":createMainStaticLibrary", ":mainStaticLibrary"

        and:
        staticLibrary(cppLib.dir.file("build/libs/main/static/main")).assertExists()
    }

    def flavors() {
        given:
        sample flavors

        when:
        run "installMainEnglishExecutable"

        then:
        executedAndNotSkipped ":compileHelloEnglishSharedLibraryHelloCpp", ":linkHelloEnglishSharedLibrary", ":helloEnglishSharedLibrary"
        executedAndNotSkipped ":compileMainEnglishExecutableMainCpp", ":linkMainEnglishExecutable", ":mainEnglishExecutable"

        and:
        executable(flavors.dir.file("build/exe/main/english/main")).assertExists()
        sharedLibrary(flavors.dir.file("build/libs/hello/shared/english/hello")).assertExists()

        and:
        installation(flavors.dir.file("build/install/main/english")).exec().out == "Hello world!\n"

        when:
        sample flavors
        run "installMainFrenchExecutable"

        then:
        executedAndNotSkipped ":compileHelloFrenchSharedLibraryHelloCpp", ":linkHelloFrenchSharedLibrary", ":helloFrenchSharedLibrary"
        executedAndNotSkipped ":compileMainFrenchExecutableMainCpp", ":linkMainFrenchExecutable", ":mainFrenchExecutable"

        and:
        executable(flavors.dir.file("build/exe/main/french/main")).assertExists()
        sharedLibrary(flavors.dir.file("build/libs/hello/shared/french/hello")).assertExists()

        and:
        installation(flavors.dir.file("build/install/main/french")).exec().out == "Bonjour monde!\n"
    }

    def variants() {
        given:
        sample variants

        when:
        run "assemble"

        then:
        final debugX86 = executable(variants.dir.file("build/exe/main/x86/debug/main"))
        final releaseX86 = executable(variants.dir.file("build/exe/main/x86/release/main"))
        final debugX64 = executable(variants.dir.file("build/exe/main/x64/debug/main"))
        final releaseX64 = executable(variants.dir.file("build/exe/main/x64/release/main"))
        final debugIA64 = executable(variants.dir.file("build/exe/main/itanium/debug/main"))
        final releaseIA64 = executable(variants.dir.file("build/exe/main/itanium/release/main"))

        debugX86.arch.name == "x86"
        debugX86.assertDebugFileExists()
        debugX86.exec().out == "Hello world!\n"

        releaseX86.arch.name == "x86"
        releaseX86.assertDebugFileDoesNotExist()
        releaseX86.exec().out == "Hello world!\n"

        // x86_64 binaries not supported on MinGW or cygwin
        if (toolChain.id == "mingw" || toolChain.id == "gcccygwin") {
            debugX64.assertDoesNotExist()
            releaseX64.assertDoesNotExist()
        } else {
            debugX64.arch.name == "x86_64"
            releaseX64.arch.name == "x86_64"
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
        executable(toolChains.dir.file("build/exe/main/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    def multiProject() {
        given:
        sample multiProject

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary(multiProject.dir.file("lib/build/libs/main/shared/main")).assertExists()
        executable(multiProject.dir.file("exe/build/exe/main/main")).assertExists()
        installation(multiProject.dir.file("exe/build/install/main")).exec().out == "Hello, World!\n"
    }

    @RequiresInstalledToolChain(GCC_COMPATIBLE)
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
        run "installMainArmExecutable", "installMainSparcExecutable"

        then:
        executable(targetPlatforms.dir.file("build/exe/main/arm/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
        executable(targetPlatforms.dir.file("build/exe/main/arm/main")).arch.isI386()

        executable(targetPlatforms.dir.file("build/exe/main/sparc/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
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

        executable(prebuilt.dir.file("build/exe/main/debug/main")).exec().out ==
"""Built with Boost version: 1_55
Util build type: DEBUG
"""
        executable(prebuilt.dir.file("build/exe/main/release/main")).exec().out ==
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
        executedAndNotSkipped(":compileMainExecutableMainExecutablePlatform$platformName", ":installMainExecutable")

        and:
        executable(sourcesetVariant.dir.file("build/exe/main/main")).assertExists()
        installation(sourcesetVariant.dir.file("build/install/main")).exec().out.contains("Attributes of '$platformName' platform")
    }

    def customcheck() {
        given:
        sample customCheck

        when:
        run 'check'

        then:
        executedAndNotSkipped(':myCustomCheck')

        and:
        sample customCheck

        when:
        run ':checkHelloSharedLibrary'

        then:
        executedAndNotSkipped(':myCustomCheck')
    }
}
