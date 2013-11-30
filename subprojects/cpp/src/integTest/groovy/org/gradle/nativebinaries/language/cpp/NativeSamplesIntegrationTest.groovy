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
package org.gradle.nativebinaries.language.cpp
import org.gradle.integtests.fixtures.Sample
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class NativeSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule public final Sample c = new Sample(temporaryFolder, 'native-binaries/c')
    @Rule public final Sample assembler = new Sample(temporaryFolder, 'native-binaries/assembler')
    @Rule public final Sample cpp = new Sample(temporaryFolder, 'native-binaries/cpp')
    @Rule public final Sample customLayout = new Sample(temporaryFolder, 'native-binaries/custom-layout')
    @Rule public final Sample cppExe = new Sample(temporaryFolder, 'native-binaries/cpp-exe')
    @Rule public final Sample cppLib = new Sample(temporaryFolder, 'native-binaries/cpp-lib')
    @Rule public final Sample multiProject = new Sample(temporaryFolder, 'native-binaries/multi-project')
    @Rule public final Sample flavors = new Sample(temporaryFolder, 'native-binaries/flavors')
    @Rule public final Sample variants = new Sample(temporaryFolder, 'native-binaries/variants')
    @Rule public final Sample toolChains = new Sample(temporaryFolder, 'native-binaries/tool-chains')
    @Rule public final Sample windowsResources = new Sample(temporaryFolder, 'native-binaries/windows-resources')

    def "assembler"() {
        given:
        sample assembler

        when:
        run "installMainExecutable"

        then:
        nonSkippedTasks.count { it.startsWith(":assembleMainExecutable") } == 1
        executedAndNotSkipped ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("native-binaries/assembler/build/install/mainExecutable").exec().out == "5 + 7 = 12\n"
    }

    def "c"() {
        given:
        sample c
        
        when:
        run "installMainExecutable"
        
        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloC", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("native-binaries/c/build/install/mainExecutable").exec().out == "Hello world!"
    }

    def "cpp"() {
        given:
        sample cpp

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("native-binaries/cpp/build/install/mainExecutable").exec().out == "Hello world!\n"
    }

    @RequiresInstalledToolChain("visual c++")
    def "windows resources"() {
        given:
        sample windowsResources

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryHelloCpp", ":resourceCompileHelloSharedLibraryHelloRc",
                              ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("native-binaries/windows-resources/build/install/mainExecutable").exec().out == "Hello world!\n"

        when:
        executer.usingBuildScript(windowsResources.dir.file('build-resource-only-dll.gradle'))
        run "helloResourcesSharedLibrary"

        then:
        file("native-binaries/windows-resources/build/binaries/helloResourcesSharedLibrary/helloResources.dll").assertExists()
    }

    def "custom layout"() {
        given:
        sample customLayout

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloStaticLibraryHelloC", ":createHelloStaticLibrary", ":helloStaticLibrary",
                              ":compileMainExecutableMainCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("native-binaries/custom-layout/build/install/mainExecutable").exec().out == "Hello world!"
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
        executable("native-binaries/cpp-exe/build/binaries/mainExecutable/sampleExe").exec().out == "Hello, World!\n"
        installation("native-binaries/cpp-exe/build/install/mainExecutable").exec().out == "Hello, World!\n"

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
        sharedLibrary("native-binaries/cpp-lib/build/binaries/mainSharedLibrary/sampleLib").assertExists()
        
        when:
        sample cppLib
        run "mainStaticLibrary"
        
        then:
        executedAndNotSkipped ":compileMainStaticLibraryMainCpp", ":createMainStaticLibrary", ":mainStaticLibrary"
        
        and:
        staticLibrary("native-binaries/cpp-lib/build/binaries/mainStaticLibrary/sampleLib").assertExists()
    }

    def flavors() {
        when:
        sample flavors
        run "installEnglishMainExecutable"

        then:
        executedAndNotSkipped ":compileEnglishHelloSharedLibraryLibCpp", ":linkEnglishHelloSharedLibrary", ":englishHelloSharedLibrary"
        executedAndNotSkipped ":compileEnglishMainExecutableExeCpp", ":linkEnglishMainExecutable", ":englishMainExecutable"

        and:
        executable("native-binaries/flavors/build/binaries/mainExecutable/english/main").assertExists()
        sharedLibrary("native-binaries/flavors/build/binaries/helloSharedLibrary/english/hello").assertExists()

        and:
        installation("native-binaries/flavors/build/install/mainExecutable/english").exec().out == "Hello world!\n"

        when:
        sample flavors
        run "installFrenchMainExecutable"

        then:
        executedAndNotSkipped ":compileFrenchHelloSharedLibraryLibCpp", ":linkFrenchHelloSharedLibrary", ":frenchHelloSharedLibrary"
        executedAndNotSkipped ":compileFrenchMainExecutableExeCpp", ":linkFrenchMainExecutable", ":frenchMainExecutable"

        and:
        executable("native-binaries/flavors/build/binaries/mainExecutable/french/main").assertExists()
        sharedLibrary("native-binaries/flavors/build/binaries/helloSharedLibrary/french/hello").assertExists()

        and:
        installation("native-binaries/flavors/build/install/mainExecutable/french").exec().out == "Bonjour monde!\n"
    }

    def variants() {
        when:
        sample variants
        run "buildExecutables"

        then:
        final debugX86 = executable("native-binaries/variants/build/binaries/mainExecutable/x86Debug/main")
        final releaseX86 = executable("native-binaries/variants/build/binaries/mainExecutable/x86Release/main")
        final debugX64 = executable("native-binaries/variants/build/binaries/mainExecutable/x64Debug/main")
        final releaseX64 = executable("native-binaries/variants/build/binaries/mainExecutable/x64Release/main")
        final debugIA64 = executable("native-binaries/variants/build/binaries/mainExecutable/itaniumDebug/main")
        final releaseIA64 = executable("native-binaries/variants/build/binaries/mainExecutable/itaniumRelease/main")

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

        // Itanium only supported on visualCpp
        if (toolChain.visualCpp) {
            debugIA64.binaryInfo.arch.name == "ia-64"
            releaseIA64.binaryInfo.arch.name == "ia-64"
        } else {
            debugIA64.assertDoesNotExist()
            releaseIA64.assertDoesNotExist()
        }
    }

    def "tool chains"() {
        given:
        sample toolChains

        when:
        run "installMainExecutable"

        then:
        executable("native-binaries/tool-chains/build/binaries/mainExecutable/main").exec().out == "Hello from ${toolChain.typeDisplayName}!\n"
    }

    def multiProject() {
        given:
        sample multiProject

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary("native-binaries/multi-project/lib/build/binaries/mainSharedLibrary/lib").assertExists()
        executable("native-binaries/multi-project/exe/build/binaries/mainExecutable/exe").assertExists()
        installation("native-binaries/multi-project/exe/build/install/mainExecutable").exec().out == "Hello, World!\n"
    }
}