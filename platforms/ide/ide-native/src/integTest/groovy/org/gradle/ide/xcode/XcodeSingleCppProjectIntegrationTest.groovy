/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.ide.xcode.fixtures.XcodebuildExecutor
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeSingleCppProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        def app = new CppApp()
        app.writeToProject(testDirectory)
        file('src/main/headers/ignore.cpp') << 'broken!'
        file('src/main/cpp/ignore.h') << 'broken!'
        file('src/main/cpp/ignore.swift') << 'broken!'

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Headers'])
        project.sources.assertHasChildren(app.sources.files*.name)
        project.headers.assertHasChildren(app.headers.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]

        project.targets.size() == 2
        assertTargetIsTool(project.targets[0], 'App', 'app')
        project.targets[1].assertIsIndexerFor(project.targets[0])

        project.products.children.size() == 1
        project.products.children[0].path == exe("build/install/main/debug/lib/app").absolutePath
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application with multiple architecture"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'

            application.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
        """

        def app = new CppApp()
        app.writeToProject(testDirectory)
        file('src/main/headers/ignore.cpp') << 'broken!'
        file('src/main/cpp/ignore.h') << 'broken!'
        file('src/main/cpp/ignore.swift') << 'broken!'

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Headers'])
        project.sources.assertHasChildren(app.sources.files*.name)
        project.headers.assertHasChildren(app.headers.files*.name)

        project.targets.size() == 2

        project.targets[0].productReference.path == exe("build/install/main/debug/x86-64/lib/app").absolutePath
        project.targets[0].buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'

        project.targets[0].assertIsTool()
        project.targets[0].name == "App"
        project.targets[0].assertProductNameEquals("App")
        project.targets[0].assertSupportedArchitectures(MachineArchitecture.X86, MachineArchitecture.X86_64)
        project.targets[0].buildConfigurationList.buildConfigurations.size() == 4

        project.targets[0].buildConfigurationList.buildConfigurations[0].name == "${DefaultXcodeProject.BUILD_DEBUG}X86"
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == "i386"
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/debug/x86/lib").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[1].name == "${DefaultXcodeProject.BUILD_DEBUG}X86-64"
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.ARCHS == "x86_64"
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/debug/x86-64/lib").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[2].name == "${DefaultXcodeProject.BUILD_RELEASE}X86"
        project.targets[0].buildConfigurationList.buildConfigurations[2].buildSettings.ARCHS == "i386"
        project.targets[0].buildConfigurationList.buildConfigurations[2].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/release/x86/lib").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[3].name == "${DefaultXcodeProject.BUILD_RELEASE}X86-64"
        project.targets[0].buildConfigurationList.buildConfigurations[3].buildSettings.ARCHS == "x86_64"
        project.targets[0].buildConfigurationList.buildConfigurations[3].buildSettings.CONFIGURATION_BUILD_DIR == file("build/install/main/release/x86-64/lib").absolutePath

        project.targets[1].assertIsIndexerFor(project.targets[0])

        project.products.children.size() == 1
        project.products.children[0].path == exe("build/install/main/debug/x86-64/lib/app").absolutePath
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ library"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
        """

        def lib = new CppLib()
        lib.writeToProject(testDirectory)
        file('src/main/public/ignore.cpp') << 'broken!'
        file('src/main/headers/ignore.cpp') << 'broken!'
        file('src/main/cpp/ignore.h') << 'broken!'
        file('src/main/cpp/ignore.swift') << 'broken!'

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Headers'])
        project.sources.assertHasChildren(lib.sources.files*.name)
        project.headers.assertHasChildren(lib.headers.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]

        project.targets.size() == 2
        assertTargetIsDynamicLibrary(project.targets[0], 'App', 'app')
        project.targets[1].assertIsIndexerFor(project.targets[0])
        project.targets[1].buildConfigurationList.buildConfigurations[0].buildSettings.HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("src/main/public"), file("src/main/headers"))

        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("build/lib/main/debug/app").absolutePath
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ library with multiple architecture"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'

            library.targetMachines = [machines.${currentHostOperatingSystemFamilyDsl}.x86, machines.${currentHostOperatingSystemFamilyDsl}.x86_64]
        """

        def lib = new CppLib()
        lib.writeToProject(testDirectory)
        file('src/main/public/ignore.cpp') << 'broken!'
        file('src/main/headers/ignore.cpp') << 'broken!'
        file('src/main/cpp/ignore.h') << 'broken!'
        file('src/main/cpp/ignore.swift') << 'broken!'

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Headers'])
        project.sources.assertHasChildren(lib.sources.files*.name)
        project.headers.assertHasChildren(lib.headers.files*.name)

        project.targets.size() == 2
        project.targets[0].productReference.path == sharedLib("build/lib/main/debug/x86-64/app").absolutePath
        project.targets[0].buildArgumentsString == '-Porg.gradle.internal.xcode.bridge.ACTION="${ACTION}" -Porg.gradle.internal.xcode.bridge.PRODUCT_NAME="${PRODUCT_NAME}" -Porg.gradle.internal.xcode.bridge.CONFIGURATION="${CONFIGURATION}" -Porg.gradle.internal.xcode.bridge.BUILT_PRODUCTS_DIR="${BUILT_PRODUCTS_DIR}" :_xcode__${ACTION}_${PRODUCT_NAME}_${CONFIGURATION}'

        project.targets[0].assertIsDynamicLibrary()
        project.targets[0].name == "App"
        project.targets[0].assertProductNameEquals("App")
        project.targets[0].assertSupportedArchitectures(MachineArchitecture.X86, MachineArchitecture.X86_64)
        project.targets[0].buildConfigurationList.buildConfigurations.size() == 4

        project.targets[0].buildConfigurationList.buildConfigurations[0].name == "${DefaultXcodeProject.BUILD_DEBUG}X86"
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.ARCHS == "i386"
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/debug/x86").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[1].name == "${DefaultXcodeProject.BUILD_DEBUG}X86-64"
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.ARCHS == "x86_64"
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/debug/x86-64").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[2].name == "${DefaultXcodeProject.BUILD_RELEASE}X86"
        project.targets[0].buildConfigurationList.buildConfigurations[2].buildSettings.ARCHS == "i386"
        project.targets[0].buildConfigurationList.buildConfigurations[2].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/release/x86/stripped").absolutePath

        project.targets[0].buildConfigurationList.buildConfigurations[3].name == "${DefaultXcodeProject.BUILD_RELEASE}X86-64"
        project.targets[0].buildConfigurationList.buildConfigurations[3].buildSettings.ARCHS == "x86_64"
        project.targets[0].buildConfigurationList.buildConfigurations[3].buildSettings.CONFIGURATION_BUILD_DIR == file("build/lib/main/release/x86-64/stripped").absolutePath

        project.targets[1].assertIsIndexerFor(project.targets[0])

        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("build/lib/main/debug/x86-64/app").absolutePath
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from xcode when C++ executable product doesn't have test configured"() {
        useXcodebuildTool()

        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        def app = new CppApp()
        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRunner = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.TEST_DEBUG)
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultRunner.error.contains("Scheme App is not currently configured for the test action.")
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "returns meaningful errors from xcode when C++ library doesn't have test configured"() {
        useXcodebuildTool()

        given:
        buildFile << """
            apply plugin: 'cpp-library'
        """

        def lib = new CppLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRunner = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.TEST_DEBUG)
            .fails(XcodebuildExecutor.XcodeAction.TEST)

        then:
        resultRunner.error.contains("Scheme App is not currently configured for the test action.")
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "can build C++ executable from Xcode"() {
        useXcodebuildTool()
        def app = new CppApp()
        def debugBinary = exe("build/exe/main/debug/App")
        def releaseBinary = exe("build/exe/main/release/App")

        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugCpp', ':linkDebug', ':installDebug', ':_xcode___App_Debug')
        resultDebug.assertTasksNotSkipped(':compileDebugCpp', ':linkDebug', ':installDebug', ':_xcode___App_Debug')
        debugBinary.exec().out == app.expectedOutput
        fixture(debugBinary).assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseCpp', ':linkRelease', ':stripSymbolsRelease', ':installRelease', ':_xcode___App_Release')
        resultRelease.assertTasksNotSkipped(':compileReleaseCpp', ':linkRelease', ':stripSymbolsRelease', ':installRelease', ':_xcode___App_Release')
        releaseBinary.exec().out == app.expectedOutput
        fixture(releaseBinary).assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
    }

    @Requires([
        UnitTestPreconditions.HasXCode,
        UnitTestPreconditions.NotMacOsM1
    ])
    @ToBeFixedForConfigurationCache
    def "can build C++ executable from Xcode with multiple architecture"() {
        useXcodebuildTool()
        def app = new CppApp()
        def debugBinary = exe("build/exe/main/debug/x86-64/App")
        def releaseBinary = exe("build/exe/main/release/x86-64/App")

        given:
        buildFile << """
            apply plugin: 'cpp-application'

            application.targetMachines = [machines.macOS.x86, machines.macOS.x86_64]
        """

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = xcodebuild
                .withProject(rootXcodeProject)
                .withScheme('App')
                .withConfiguration("DebugX86-64")
                .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugX86-64Cpp', ':linkDebugX86-64', ':installDebugX86-64', ':_xcode___App_DebugX86-64')
        resultDebug.assertTasksNotSkipped(':compileDebugX86-64Cpp', ':linkDebugX86-64', ':installDebugX86-64', ':_xcode___App_DebugX86-64')
        debugBinary.exec().out == app.expectedOutput
        fixture(debugBinary).assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
                .withProject(rootXcodeProject)
                .withScheme('App')
                .withConfiguration("ReleaseX86-64")
                .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseX86-64Cpp', ':linkReleaseX86-64', ':stripSymbolsReleaseX86-64', ':installReleaseX86-64', ':_xcode___App_ReleaseX86-64')
        resultRelease.assertTasksNotSkipped(':compileReleaseX86-64Cpp', ':linkReleaseX86-64', ':stripSymbolsReleaseX86-64', ':installReleaseX86-64', ':_xcode___App_ReleaseX86-64')
        releaseBinary.exec().out == app.expectedOutput
        fixture(releaseBinary).assertHasDebugSymbolsFor(app.sourceFileNamesWithoutHeaders)
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "can build C++ library from Xcode"() {
        useXcodebuildTool()
        def lib = new CppLib()
        def debugBinary = sharedLib("build/lib/main/debug/App")
        def releaseBinary = sharedLib("build/lib/main/release/App")

        given:
        buildFile << """
apply plugin: 'cpp-library'
"""

        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugCpp', ':linkDebug', ':_xcode___App_Debug')
        resultDebug.assertTasksNotSkipped(':compileDebugCpp', ':linkDebug', ':_xcode___App_Debug')
        debugBinary.assertExists()
        fixture(debugBinary).assertHasDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseCpp', ':linkRelease', ':stripSymbolsRelease', ':_xcode___App_Release')
        resultRelease.assertTasksNotSkipped(':compileReleaseCpp', ':linkRelease', ':stripSymbolsRelease', ':_xcode___App_Release')
        releaseBinary.assertExists()
        fixture(releaseBinary).assertHasDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)
    }

    @Requires([
        UnitTestPreconditions.HasXCode,
        UnitTestPreconditions.NotMacOsM1
    ])
    @ToBeFixedForConfigurationCache
    def "can build C++ library from Xcode with multiple architecture"() {
        useXcodebuildTool()
        def lib = new CppLib()
        def debugBinary = sharedLib("build/lib/main/debug/x86-64/App")
        def releaseBinary = sharedLib("build/lib/main/release/x86-64/App")

        given:
        buildFile << """
            apply plugin: 'cpp-library'

            library.targetMachines = [machines.macOS.x86, machines.macOS.x86_64]
        """

        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        debugBinary.assertDoesNotExist()
        def resultDebug = xcodebuild
                .withProject(rootXcodeProject)
                .withScheme('App')
                .withConfiguration("DebugX86-64")
                .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugX86-64Cpp', ':linkDebugX86-64', ':_xcode___App_DebugX86-64')
        resultDebug.assertTasksNotSkipped(':compileDebugX86-64Cpp', ':linkDebugX86-64', ':_xcode___App_DebugX86-64')
        debugBinary.assertExists()
        fixture(debugBinary).assertHasDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
                .withProject(rootXcodeProject)
                .withScheme('App')
                .withConfiguration("ReleaseX86-64")
                .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseX86-64Cpp', ':linkReleaseX86-64', ':stripSymbolsReleaseX86-64', ':_xcode___App_ReleaseX86-64')
        resultRelease.assertTasksNotSkipped(':compileReleaseX86-64Cpp', ':linkReleaseX86-64', ':stripSymbolsReleaseX86-64', ':_xcode___App_ReleaseX86-64')
        releaseBinary.assertExists()
        fixture(releaseBinary).assertHasDebugSymbolsFor(lib.sourceFileNamesWithoutHeaders)
    }

    @ToBeFixedForConfigurationCache
    def "adds new source files in the project"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        def app = new CppApp()
        app.writeToProject(testDirectory)
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources
            .assertHasChildren(app.sources.files*.name)

        when:
        file("src/main/cpp/new.cpp") << "include <iostream>\n"
        succeeds('xcode')

        then:
        rootXcodeProject.projectFile.sources
            .assertHasChildren(['new.cpp'] + app.sources.files*.name)
    }

    @ToBeFixedForConfigurationCache
    def "removes deleted source files from the project"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        def app = new CppApp()
        app.writeToProject(testDirectory)
        file("src/main/cpp/old.cpp") << "include <iostream>\n"
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources
            .assertHasChildren(['old.cpp'] + app.sources.files*.name)

        when:
        file('src/main/cpp/old.cpp').delete()
        succeeds('xcode')

        then:
        rootXcodeProject.projectFile.sources
            .assertHasChildren(app.sources.files*.name)
    }

    @ToBeFixedForConfigurationCache
    def "includes source files in a non-default location in C++ executable project"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'

            application {
                source.from 'Sources'
                privateHeaders.from 'Sources/include'
            }
        """

        when:
        def app = new CppApp()
        app.headers.writeToSourceDir(file('Sources/include'))
        app.sources.writeToSourceDir(file('Sources'))
        file("src/main/headers/ignore.h") << 'broken!'
        file('src/main/cpp/ignore.cpp') << 'broken!'
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(app.sources.files*.name)
        rootXcodeProject.projectFile.headers.assertHasChildren(app.headers.files*.name)
    }

    @ToBeFixedForConfigurationCache
    def "includes source files in a non-default location in C++ library project"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                source.from 'Sources'
                privateHeaders.from 'Sources/include'
                publicHeaders.from 'Includes'
            }
        """

        when:
        def lib = new CppLib()
        lib.publicHeaders.writeToSourceDir(file('Includes'))
        lib.privateHeaders.writeToSourceDir(file('Sources/include'))
        lib.sources.writeToSourceDir(file('Sources'))
        file('src/main/headers/ignore.h') << 'broken!'
        file('src/main/public/ignore.h') << 'broken!'
        file('src/main/cpp/ignore.cpp') << 'broken!'
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(lib.sources.files*.name)
        rootXcodeProject.projectFile.headers.assertHasChildren(lib.headers.files*.name)
    }

    @ToBeFixedForConfigurationCache
    def "honors changes to application output locations"() {
        given:
        buildFile << """
            apply plugin: 'cpp-application'
            buildDir = 'output'
            application.baseName = 'test_app'
        """

        def app = new CppApp()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2

        project.targets[0].name == 'Test_app'
        project.targets[0].productReference.path == exe("output/install/main/debug/lib/test_app").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/install/main/debug/lib").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/install/main/release/lib").absolutePath

        project.products.children.size() == 1
        project.products.children[0].path == exe("output/install/main/debug/lib/test_app").absolutePath
    }

    @ToBeFixedForConfigurationCache
    def "honors changes to library output locations"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
            buildDir = 'output'
            library.baseName = 'test_lib'
        """

        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2

        project.targets[0].name == 'Test_lib'
        project.targets[0].productReference.path == sharedLib("output/lib/main/debug/test_lib").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/debug").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/release/stripped").absolutePath

        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("output/lib/main/debug/test_lib").absolutePath
    }
}
