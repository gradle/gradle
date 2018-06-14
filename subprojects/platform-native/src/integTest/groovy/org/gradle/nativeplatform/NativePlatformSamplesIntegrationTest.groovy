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
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TestRule

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class NativePlatformSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule final TestNameTestDirectoryProvider testDirProvider = new TestNameTestDirectoryProvider()
    final Sample sample = new Sample(testDirProvider)

    @Rule
    public final TestRule rules = sample.runInSampleDirectory(executer)

    private void inSampleDir() {
        inDirectory(sample.dir)
    }

    @UsesSample('native-binaries/cpp-exe')
    def "exe"() {
        given:
        // Need to PATH to be set to find the 'strip' executable
        toolChain.initialiseEnvironment()

        when:
        run "installMain"

        then:
        executedAndNotSkipped ":compileMainExecutableMainCpp", ":linkMainExecutable", ":stripMainExecutable", ":mainExecutable"

        and:
        executable(sample.dir.file("build/exe/main/main")).exec().out == "Hello, World!\n"
        installation(sample.dir.file("build/install/main")).exec().out == "Hello, World!\n"

        cleanup:
        toolChain.resetEnvironment()
    }

    @UsesSample('native-binaries/cpp-lib')
    def "lib"() {
        when:
        run "mainSharedLibrary"

        then:
        executedAndNotSkipped ":compileMainSharedLibraryMainCpp", ":linkMainSharedLibrary", ":mainSharedLibrary"

        and:
        sharedLibrary(sample.dir.file("build/libs/main/shared/main")).assertExists()

        when:
        inSampleDir()
        run "mainStaticLibrary"

        then:
        executedAndNotSkipped ":compileMainStaticLibraryMainCpp", ":createMainStaticLibrary", ":mainStaticLibrary"

        and:
        staticLibrary(sample.dir.file("build/libs/main/static/main")).assertExists()
    }

    @UsesSample("native-binaries/flavors")
    def flavors() {
        when:
        run "installMainEnglishExecutable"

        then:
        executedAndNotSkipped ":compileHelloEnglishSharedLibraryHelloCpp", ":linkHelloEnglishSharedLibrary", ":helloEnglishSharedLibrary"
        executedAndNotSkipped ":compileMainEnglishExecutableMainCpp", ":linkMainEnglishExecutable", ":mainEnglishExecutable"

        and:
        executable(sample.dir.file("build/exe/main/english/main")).assertExists()
        sharedLibrary(sample.dir.file("build/libs/hello/shared/english/hello")).assertExists()

        and:
        installation(sample.dir.file("build/install/main/english")).exec().out == "Hello world!\n"

        when:
        inSampleDir()
        run "installMainFrenchExecutable"

        then:
        executedAndNotSkipped ":compileHelloFrenchSharedLibraryHelloCpp", ":linkHelloFrenchSharedLibrary", ":helloFrenchSharedLibrary"
        executedAndNotSkipped ":compileMainFrenchExecutableMainCpp", ":linkMainFrenchExecutable", ":mainFrenchExecutable"

        and:
        executable(sample.dir.file("build/exe/main/french/main")).assertExists()
        sharedLibrary(sample.dir.file("build/libs/hello/shared/french/hello")).assertExists()

        and:
        installation(sample.dir.file("build/install/main/french")).exec().out == "Bonjour monde!\n"
    }

    @UsesSample("native-binaries/variants")
    def variants() {
        when:
        run "assemble"

        then:
        final debugX86 = executable(sample.dir.file("build/exe/main/x86/debug/main"))
        final releaseX86 = executable(sample.dir.file("build/exe/main/x86/release/main"))
        final debugX64 = executable(sample.dir.file("build/exe/main/x64/debug/main"))
        final releaseX64 = executable(sample.dir.file("build/exe/main/x64/release/main"))
        final debugIA64 = executable(sample.dir.file("build/exe/main/itanium/debug/main"))
        final releaseIA64 = executable(sample.dir.file("build/exe/main/itanium/release/main"))

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

    @UsesSample("native-binaries/tool-chains")
    def "tool chains"() {
        when:
        run "installMainExecutable"

        then:
        executable(sample.dir.file("build/exe/main/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    @UsesSample("native-binaries/multi-project")
    def multiProject() {
        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary(sample.dir.file("lib/build/libs/main/shared/main")).assertExists()
        executable(sample.dir.file("exe/build/exe/main/main")).assertExists()
        installation(sample.dir.file("exe/build/install/main")).exec().out == "Hello, World!\n"
    }

    @UsesSample("native-binaries/target-platforms")
    @RequiresInstalledToolChain(GCC_COMPATIBLE)
    def "target platforms"() {
        given:
        sample.dir.file("build.gradle") << """
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
        executable(sample.dir.file("build/exe/main/arm/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
        executable(sample.dir.file("build/exe/main/arm/main")).arch.isI386()

        executable(sample.dir.file("build/exe/main/sparc/main")).exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    @UsesSample("native-binaries/prebuilt")
    def prebuilt() {
        given:
        inDirectory(sample.dir.file("3rd-party-lib/util"))
        run "assemble"

        and:
        inSampleDir()

        when:
        succeeds "assemble"

        then:

        executable(sample.dir.file("build/exe/main/debug/main")).exec().out ==
"""Built with Boost version: 1_55
Util build type: DEBUG
"""
        executable(sample.dir.file("build/exe/main/release/main")).exec().out ==
"""Built with Boost version: 1_55
Util build type: RELEASE
"""
    }

    @UsesSample("native-binaries/sourceset-variant")
    def sourcesetvariant() {
        given:
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
        executable(sample.dir.file("build/exe/main/main")).assertExists()
        installation(sample.dir.file("build/install/main")).exec().out.contains("Attributes of '$platformName' platform")
    }

    @UsesSample("native-binaries/custom-check")
    def customcheck() {
        when:
        run 'check'

        then:
        executedAndNotSkipped(':myCustomCheck')

        and:
        inSampleDir()

        when:
        run ':checkHelloSharedLibrary'

        then:
        executedAndNotSkipped(':myCustomCheck')
    }
}
