/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.tooling.r52

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.model.cpp.CppApplication
import org.gradle.tooling.model.cpp.CppExecutable
import org.gradle.tooling.model.cpp.CppLibrary
import org.gradle.tooling.model.cpp.CppProject
import org.gradle.tooling.model.cpp.CppSharedLibrary

@TargetGradleVersion(">=5.2")
@Requires(UnitTestPreconditions.NotMacOsM1) // TODO KM how to limit non-backwards compatible checks when aarch64 is not available on Gradle 7.5 and prior?
class CppModelCrossVersionSpec extends ToolingApiSpecification {
    def toolchain = AvailableToolChains.defaultToolChain

    def "can query model when root project applies C++ application plugin with multiple architecture"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'

            application {
                targetMachines = [machines.host().x86, machines.host().x86_64]
            }
        """
        def headerDir = file('src/main/headers')
        def src1 = file('src/main/cpp/app.cpp').createFile()
        def src2 = file('src/main/cpp/app-impl.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir

        project.mainComponent instanceof CppApplication
        project.mainComponent.name == 'main'
        project.mainComponent.baseName == 'app'

        project.mainComponent.binaries.size() == 4

        def debugX86Binary = project.mainComponent.binaries[0]
        debugX86Binary instanceof CppExecutable
        debugX86Binary.name == 'mainDebugX86'
        debugX86Binary.variantName == 'debugX86'
        debugX86Binary.baseName == 'app'
        debugX86Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        debugX86Binary.compilationDetails.sources.objectFile.each { assert it != null }
        debugX86Binary.compilationDetails.headerDirs == [headerDir] as Set
        debugX86Binary.compilationDetails.frameworkSearchPaths.empty
        !debugX86Binary.compilationDetails.systemHeaderSearchPaths.empty
        debugX86Binary.compilationDetails.userHeaderSearchPaths == [headerDir]
        debugX86Binary.compilationDetails.macroDefines.empty
        debugX86Binary.compilationDetails.macroUndefines.empty
        debugX86Binary.compilationDetails.additionalArgs.empty
        debugX86Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        debugX86Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/debug/x86")
        debugX86Binary.compilationDetails.compileTask.path == ":compileDebugX86Cpp"
        debugX86Binary.compilationDetails.compileTask.name == "compileDebugX86Cpp"
        debugX86Binary.linkageDetails.outputLocation == toolchain.executable(file("build/exe/main/debug/x86/app")).file
        debugX86Binary.linkageDetails.additionalArgs.empty
        debugX86Binary.linkageDetails.linkTask.path == ":linkDebugX86"
        debugX86Binary.linkageDetails.linkTask.name == "linkDebugX86"

        def debugX8664Binary = project.mainComponent.binaries[1]
        debugX8664Binary instanceof CppExecutable
        debugX8664Binary.name == 'mainDebugX86-64'
        debugX8664Binary.variantName == 'debugX86-64'
        debugX8664Binary.baseName == 'app'
        debugX8664Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        debugX8664Binary.compilationDetails.sources.objectFile.each { assert it != null }
        debugX8664Binary.compilationDetails.headerDirs == [headerDir] as Set
        debugX8664Binary.compilationDetails.frameworkSearchPaths.empty
        !debugX8664Binary.compilationDetails.systemHeaderSearchPaths.empty
        debugX8664Binary.compilationDetails.userHeaderSearchPaths == [headerDir]
        debugX8664Binary.compilationDetails.macroDefines.empty
        debugX8664Binary.compilationDetails.macroUndefines.empty
        debugX8664Binary.compilationDetails.additionalArgs.empty
        debugX8664Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        debugX8664Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/debug/x86-64")
        debugX8664Binary.compilationDetails.compileTask.path == ":compileDebugX86-64Cpp"
        debugX8664Binary.compilationDetails.compileTask.name == "compileDebugX86-64Cpp"
        debugX8664Binary.linkageDetails.outputLocation == toolchain.executable(file("build/exe/main/debug/x86-64/app")).file
        debugX8664Binary.linkageDetails.additionalArgs.empty
        debugX8664Binary.linkageDetails.linkTask.path == ":linkDebugX86-64"
        debugX8664Binary.linkageDetails.linkTask.name == "linkDebugX86-64"

        def releaseX86Binary = project.mainComponent.binaries[2]
        releaseX86Binary instanceof CppExecutable
        releaseX86Binary.name == 'mainReleaseX86'
        releaseX86Binary.variantName == 'releaseX86'
        releaseX86Binary.baseName == 'app'
        releaseX86Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        releaseX86Binary.compilationDetails.headerDirs == [headerDir] as Set
        releaseX86Binary.compilationDetails.frameworkSearchPaths.empty
        !releaseX86Binary.compilationDetails.systemHeaderSearchPaths.empty
        releaseX86Binary.compilationDetails.userHeaderSearchPaths == [headerDir]
        releaseX86Binary.compilationDetails.macroDefines.empty
        releaseX86Binary.compilationDetails.macroUndefines.empty
        releaseX86Binary.compilationDetails.additionalArgs.empty
        releaseX86Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        releaseX86Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/release/x86")
        releaseX86Binary.compilationDetails.compileTask.path == ":compileReleaseX86Cpp"
        releaseX86Binary.compilationDetails.compileTask.name == "compileReleaseX86Cpp"
        releaseX86Binary.linkageDetails.outputLocation == toolchain.executable(file("build/exe/main/release/x86/app")).strippedRuntimeFile
        releaseX86Binary.linkageDetails.additionalArgs.empty
        if (toolchain.visualCpp) {
            releaseX86Binary.linkageDetails.linkTask.path == ":linkReleaseX86"
            releaseX86Binary.linkageDetails.linkTask.name == "linkReleaseX86"
        } else {
            releaseX86Binary.linkageDetails.linkTask.path == ":stripSymbolsReleaseX86"
            releaseX86Binary.linkageDetails.linkTask.name == "stripSymbolsReleaseX86"
        }

        def releaseX8664Binary = project.mainComponent.binaries[3]
        releaseX8664Binary instanceof CppExecutable
        releaseX8664Binary.name == 'mainReleaseX86-64'
        releaseX8664Binary.variantName == 'releaseX86-64'
        releaseX8664Binary.baseName == 'app'
        releaseX8664Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        releaseX8664Binary.compilationDetails.headerDirs == [headerDir] as Set
        releaseX8664Binary.compilationDetails.frameworkSearchPaths.empty
        !releaseX8664Binary.compilationDetails.systemHeaderSearchPaths.empty
        releaseX8664Binary.compilationDetails.userHeaderSearchPaths == [headerDir]
        releaseX8664Binary.compilationDetails.macroDefines.empty
        releaseX8664Binary.compilationDetails.macroUndefines.empty
        releaseX8664Binary.compilationDetails.additionalArgs.empty
        releaseX8664Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        releaseX8664Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/release/x86-64")
        releaseX8664Binary.compilationDetails.compileTask.path == ":compileReleaseX86-64Cpp"
        releaseX8664Binary.compilationDetails.compileTask.name == "compileReleaseX86-64Cpp"
        releaseX8664Binary.linkageDetails.outputLocation == toolchain.executable(file("build/exe/main/release/x86-64/app")).strippedRuntimeFile
        releaseX8664Binary.linkageDetails.additionalArgs.empty
        if (toolchain.visualCpp) {
            releaseX8664Binary.linkageDetails.linkTask.path == ":linkReleaseX86-64"
            releaseX8664Binary.linkageDetails.linkTask.name == "linkReleaseX86-64"
        } else {
            releaseX8664Binary.linkageDetails.linkTask.path == ":stripSymbolsReleaseX86-64"
            releaseX8664Binary.linkageDetails.linkTask.name == "stripSymbolsReleaseX86-64"
        }

        project.testComponent == null
    }

    def "can query model when root project applies C++ library plugin with multiple architecture"() {
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                targetMachines = [machines.host().x86, machines.host().x86_64]
            }
        """
        def headerDir = file('src/main/headers')
        def apiHeaderDir = file('src/main/public')
        def src1 = file('src/main/cpp/lib.cpp').createFile()
        def src2 = file('src/main/cpp/lib-impl.cpp').createFile()

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.mainComponent.name == 'main'
        project.mainComponent.baseName == 'lib'

        project.mainComponent.binaries.size() == 4
        def debugX86Binary = project.mainComponent.binaries[0]
        debugX86Binary instanceof CppSharedLibrary
        debugX86Binary.name == 'mainDebugX86'
        debugX86Binary.variantName == 'debugX86'
        debugX86Binary.baseName == 'lib'
        debugX86Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        debugX86Binary.compilationDetails.headerDirs == [apiHeaderDir, headerDir] as Set
        debugX86Binary.compilationDetails.frameworkSearchPaths.empty
        !debugX86Binary.compilationDetails.systemHeaderSearchPaths.empty
        debugX86Binary.compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]
        debugX86Binary.compilationDetails.macroDefines.empty
        debugX86Binary.compilationDetails.macroUndefines.empty
        debugX86Binary.compilationDetails.additionalArgs.empty
        debugX86Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        debugX86Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/debug/x86")
        debugX86Binary.compilationDetails.compileTask.path == ":compileDebugX86Cpp"
        debugX86Binary.compilationDetails.compileTask.name == "compileDebugX86Cpp"
        debugX86Binary.linkageDetails.outputLocation == toolchain.sharedLibrary(file("build/lib/main/debug/x86/lib")).linkFile
        debugX86Binary.linkageDetails.additionalArgs.empty
        debugX86Binary.linkageDetails.linkTask.path == ":linkDebugX86"
        debugX86Binary.linkageDetails.linkTask.name == "linkDebugX86"

        def debugX8664Binary = project.mainComponent.binaries[1]
        debugX8664Binary instanceof CppSharedLibrary
        debugX8664Binary.name == 'mainDebugX86-64'
        debugX8664Binary.variantName == 'debugX86-64'
        debugX8664Binary.baseName == 'lib'
        debugX8664Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        debugX8664Binary.compilationDetails.headerDirs == [apiHeaderDir, headerDir] as Set
        debugX8664Binary.compilationDetails.frameworkSearchPaths.empty
        !debugX8664Binary.compilationDetails.systemHeaderSearchPaths.empty
        debugX8664Binary.compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]
        debugX8664Binary.compilationDetails.macroDefines.empty
        debugX8664Binary.compilationDetails.macroUndefines.empty
        debugX8664Binary.compilationDetails.additionalArgs.empty
        debugX8664Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        debugX8664Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/debug/x86-64")
        debugX8664Binary.compilationDetails.compileTask.path == ":compileDebugX86-64Cpp"
        debugX8664Binary.compilationDetails.compileTask.name == "compileDebugX86-64Cpp"
        debugX8664Binary.linkageDetails.outputLocation == toolchain.sharedLibrary(file("build/lib/main/debug/x86-64/lib")).linkFile
        debugX8664Binary.linkageDetails.additionalArgs.empty
        debugX8664Binary.linkageDetails.linkTask.path == ":linkDebugX86-64"
        debugX8664Binary.linkageDetails.linkTask.name == "linkDebugX86-64"

        def releaseX86Binary = project.mainComponent.binaries[2]
        releaseX86Binary instanceof CppSharedLibrary
        releaseX86Binary.name == 'mainReleaseX86'
        releaseX86Binary.variantName == 'releaseX86'
        releaseX86Binary.baseName == 'lib'
        releaseX86Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        releaseX86Binary.compilationDetails.headerDirs == [apiHeaderDir, headerDir] as Set
        releaseX86Binary.compilationDetails.frameworkSearchPaths.empty
        !releaseX86Binary.compilationDetails.systemHeaderSearchPaths.empty
        releaseX86Binary.compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]
        releaseX86Binary.compilationDetails.macroDefines.empty
        releaseX86Binary.compilationDetails.macroUndefines.empty
        releaseX86Binary.compilationDetails.additionalArgs.empty
        releaseX86Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        releaseX86Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/release/x86")
        releaseX86Binary.compilationDetails.compileTask.path == ":compileReleaseX86Cpp"
        releaseX86Binary.compilationDetails.compileTask.name == "compileReleaseX86Cpp"
        releaseX86Binary.linkageDetails.outputLocation == toolchain.sharedLibrary(file("build/lib/main/release/x86/lib")).strippedLinkFile
        releaseX86Binary.linkageDetails.additionalArgs.empty
        if (toolchain.visualCpp) {
            releaseX86Binary.linkageDetails.linkTask.path == ":linkReleaseX86"
            releaseX86Binary.linkageDetails.linkTask.name == "linkReleaseX86"
        } else {
            releaseX86Binary.linkageDetails.linkTask.path == ":stripSymbolsReleaseX86"
            releaseX86Binary.linkageDetails.linkTask.name == "stripSymbolsReleaseX86"
        }

        def releaseX8664Binary = project.mainComponent.binaries[3]
        releaseX8664Binary instanceof CppSharedLibrary
        releaseX8664Binary.name == 'mainReleaseX86-64'
        releaseX8664Binary.variantName == 'releaseX86-64'
        releaseX8664Binary.baseName == 'lib'
        releaseX8664Binary.compilationDetails.sources.sourceFile as Set == [src1, src2] as Set
        releaseX8664Binary.compilationDetails.headerDirs == [apiHeaderDir, headerDir] as Set
        releaseX8664Binary.compilationDetails.frameworkSearchPaths.empty
        !releaseX8664Binary.compilationDetails.systemHeaderSearchPaths.empty
        releaseX8664Binary.compilationDetails.userHeaderSearchPaths == [apiHeaderDir, headerDir]
        releaseX8664Binary.compilationDetails.macroDefines.empty
        releaseX8664Binary.compilationDetails.macroUndefines.empty
        releaseX8664Binary.compilationDetails.additionalArgs.empty
        releaseX8664Binary.compilationDetails.compilerExecutable.name == toolchain.cppCompiler.name
        releaseX8664Binary.compilationDetails.compileWorkingDir == projectDir.file("build/obj/main/release/x86-64")
        releaseX8664Binary.compilationDetails.compileTask.path == ":compileReleaseX86-64Cpp"
        releaseX8664Binary.compilationDetails.compileTask.name == "compileReleaseX86-64Cpp"
        releaseX8664Binary.linkageDetails.outputLocation == toolchain.sharedLibrary(file("build/lib/main/release/x86-64/lib")).strippedLinkFile
        releaseX8664Binary.linkageDetails.additionalArgs.empty
        if (toolchain.visualCpp) {
            releaseX8664Binary.linkageDetails.linkTask.path == ":linkReleaseX86-64"
            releaseX8664Binary.linkageDetails.linkTask.name == "linkReleaseX86-64"
        } else {
            releaseX8664Binary.linkageDetails.linkTask.path == ":stripSymbolsReleaseX86-64"
            releaseX8664Binary.linkageDetails.linkTask.name == "stripSymbolsReleaseX86-64"
        }

        project.testComponent == null
    }
}
