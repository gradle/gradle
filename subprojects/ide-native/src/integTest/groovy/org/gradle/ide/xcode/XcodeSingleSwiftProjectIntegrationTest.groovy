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
import org.gradle.ide.xcode.fixtures.XcodebuildExecuter
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTestWithInfoPlist
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

class XcodeSingleSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def "can create xcode project for Swift executable"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        def app = new SwiftApp()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeSchemeAppExecutable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources'])
        project.sources.assertHasChildren(app.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]

        project.targets.size() == 2
        assertTargetIsTool(project.targets[0], 'App')
        assertTargetIsIndexer(project.targets[1], 'App')

        project.products.children.size() == 1
        project.products.children[0].path == exe("build/exe/main/debug/App").absolutePath

        rootXcodeProject.schemeFiles.size() == 1
        rootXcodeProject.schemeFiles[0].schemeXml.LaunchAction.BuildableProductRunnable.size() == 1
    }

    def "can create xcode project for Swift library"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeSchemeAppSharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources'])
        project.sources.assertHasChildren(lib.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]

        project.targets.size() == 2
        assertTargetIsDynamicLibrary(project.targets[0], 'App')
        assertTargetIsIndexer(project.targets[1], 'App')

        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("build/lib/main/debug/App").absolutePath

        rootXcodeProject.schemeFiles.size() == 1
        rootXcodeProject.schemeFiles[0].schemeXml.LaunchAction.BuildableProductRunnable.size() == 0
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "can create xcode project for Swift executable with xctest"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
apply plugin: 'xctest'
"""

        def app = new SwiftAppWithXCTest()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeSchemeAppExecutable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Tests'])
        project.sources.assertHasChildren(app.main.files*.name)
        project.tests.assertHasChildren(app.test.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]

        project.targets.size() == 4
        assertTargetIsTool(project.targets[0], 'App')
        assertTargetIsUnitTest(project.targets[1], 'AppTest')
        assertTargetIsIndexer(project.targets[2], 'App')
        assertTargetIsIndexer(project.targets[3], 'AppTest')

        project.products.children.size() == 1
        project.products.children[0].path == exe("build/exe/main/debug/App").absolutePath
    }

    @Unroll
    @Requires(TestPrecondition.MAC_OS_X)
    def "can create xcode project for #scenario and xctest"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
apply plugin: 'xctest'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeSchemeAppSharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Tests'])
        project.sources.assertHasChildren(lib.main.files*.name)
        project.tests.assertHasChildren(lib.test.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]

        project.targets.size() == 4
        assertTargetIsDynamicLibrary(project.targets[0], 'App')
        assertTargetIsUnitTest(project.targets[1], 'AppTest')
        assertTargetIsIndexer(project.targets[2], 'App')
        assertTargetIsIndexer(project.targets[3], 'AppTest')

        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("build/lib/main/debug/App").absolutePath

        where:
        scenario                  | lib
        "swift lib without plist" | new SwiftLibWithXCTest()
        "swift lib with plist"    | new SwiftLibWithXCTestWithInfoPlist()
    }

    @Requires(TestPrecondition.XCODE)
    def "returns meaningful errors from xcode when Swift executable product doesn't have test configured"() {
        useXcodebuildTool()

        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App Executable")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App Executable is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App Executable")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App Executable is not currently configured for the test action.")

        when:
        def resultRunner = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App Executable")
            .withConfiguration(DefaultXcodeProject.TEST_DEBUG)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRunner.error.contains("Scheme App Executable is not currently configured for the test action.")
    }

    @Requires(TestPrecondition.XCODE)
    def "returns meaningful errors from xcode when Swift library doesn't have test configured"() {
        useXcodebuildTool()

        given:
        buildFile << """
apply plugin: 'swift-library'
"""

        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App SharedLibrary")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App SharedLibrary is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App SharedLibrary")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App SharedLibrary is not currently configured for the test action.")
    }

    @Requires(TestPrecondition.XCODE)
    def "can configure test only when xctest plugin is applied"() {
        useXcodebuildTool()

        given:
        settingsFile.text = "rootProject.name = 'greeter'"
        buildFile << """
apply plugin: 'swift-library'
"""

        def lib = new SwiftLibWithXCTestWithInfoPlist()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebugWithoutXCTest = xcodebuild
            .withProject(xcodeProject("greeter.xcodeproj"))
            .withScheme("Greeter SharedLibrary")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebugWithoutXCTest.error.contains("Scheme Greeter SharedLibrary is not currently configured for the test action.")

        when:
        buildFile << "apply plugin: 'xctest'"
        succeeds("xcode")
        def resultDebugWithXCTest = xcodebuild
            .withProject(xcodeProject("greeter.xcodeproj"))
            .withScheme("Greeter SharedLibrary")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        !resultDebugWithXCTest.error.contains("Scheme Greeter SharedLibrary is not currently configured for the test action.")
        lib.assertTestCasesRan(resultDebugWithXCTest.output)
    }

    @Requires(TestPrecondition.XCODE)
    def "can run tests for Swift library from xcode"() {
        useXcodebuildTool()
        def lib = new SwiftLibWithXCTestWithInfoPlist()

        given:
        settingsFile.text = "rootProject.name = 'greeter'"
        buildFile << """
apply plugin: 'swift-library'
apply plugin: 'xctest'
"""

        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultTestRunner = xcodebuild
            .withProject(xcodeProject("greeter.xcodeproj"))
            .withScheme("Greeter SharedLibrary")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultTestRunner.assertTasksExecuted(':compileDebugSwift', ':compileTestSwift', ':linkTest', ':bundleSwiftTest',
            ':syncTestBundleToXcodeBuiltProductDir', ':buildXcodeTestProduct')
        lib.assertTestCasesRan(resultTestRunner.output)
    }

    @Requires(TestPrecondition.XCODE)
    def "can run tests for Swift executable from xcode"() {
        useXcodebuildTool()
        def app = new SwiftAppWithXCTest()

        given:
        settingsFile.text = """
rootProject.name = 'app'
"""
        buildFile << """
apply plugin: 'swift-executable'
apply plugin: 'xctest'
"""

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultTestRunner = xcodebuild
            .withProject(xcodeProject("app.xcodeproj"))
            .withScheme("App Executable")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultTestRunner.assertTasksExecuted(':compileDebugSwift', ':compileTestSwift', ':linkTest', ':bundleSwiftTest',
            ':syncTestBundleToXcodeBuiltProductDir', ':buildXcodeTestProduct')
        app.assertTestCasesRan(resultTestRunner.output)
    }

    @Requires(TestPrecondition.XCODE)
    def "can build Swift executable from xcode"() {
        useXcodebuildTool()
        def app = new SwiftApp()

        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        exe("build/exe/main/debug/App").assertDoesNotExist()
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugSwift', ':linkDebug')
        resultDebug.assertTasksNotSkipped(':compileDebugSwift', ':linkDebug')
        exe("build/exe/main/debug/App").exec().out == app.expectedOutput

        when:
        exe("build/exe/main/release/App").assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App Executable')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseSwift', ':linkRelease')
        resultRelease.assertTasksNotSkipped(':compileReleaseSwift', ':linkRelease')
        exe("build/exe/main/release/App").exec().out == app.expectedOutput
    }

    @Requires(TestPrecondition.XCODE)
    def "can build Swift library from xcode"() {
        useXcodebuildTool()
        def lib = new SwiftLib()

        given:
        buildFile << """
apply plugin: 'swift-library'
"""

        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        sharedLib("build/lib/main/debug/App").assertDoesNotExist()
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App SharedLibrary')
            .succeeds()

        then:
        resultDebug.assertTasksExecuted(':compileDebugSwift', ':linkDebug')
        resultDebug.assertTasksNotSkipped(':compileDebugSwift', ':linkDebug')
        sharedLib("build/lib/main/debug/App").assertExists()

        when:
        sharedLib("build/lib/main/release/App").assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseSwift', ':linkRelease')
        resultRelease.assertTasksNotSkipped(':compileReleaseSwift', ':linkRelease')
        sharedLib("build/lib/main/release/App").assertExists()
    }

    def "adds new source files in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(lib.files*.name)

        when:
        def app = new SwiftApp()
        app.writeToProject(testDirectory)
        succeeds('xcode')

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(app.files*.name)
    }

    def "removes deleted source files from the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        def app = new SwiftApp()
        app.writeToProject(testDirectory)
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(app.files*.name)

        when:
        file('src/main').deleteDir()
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds('xcode')

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(lib.files*.name)
    }

    def "includes source files in a non-default location in Swift executable project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'

executable {
    source.from 'Sources'
}
"""

        when:
        def app = new SwiftApp()
        app.writeToSourceDir(file('Sources'))
        file('src/main/swift/ignore.swift') << 'broken!'
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(app.files*.name)
    }

    def "includes source files in a non-default location in Swift library project"() {
        given:
        buildFile << """
apply plugin: 'swift-library'

library {
    source.from 'Sources'
}
"""

        when:
        def lib = new SwiftLib()
        lib.writeToSourceDir(file('Sources'))
        file('src/main/swift/ignore.swift') << 'broken!'
        succeeds("xcode")

        then:
        rootXcodeProject.projectFile.sources.assertHasChildren(lib.files*.name)
    }

    def "honors changes to executable output file locations"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
buildDir = 'output'
executable.module = 'TestApp'
"""

        def app = new SwiftApp()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeSchemeAppExecutable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == 'App' }
        project.targets[0].name == 'App Executable'
        project.targets[0].productReference.path == exe("output/exe/main/debug/TestApp").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/exe/main/debug").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/exe/main/release").absolutePath
        project.targets[1].name == '[INDEXING ONLY] App Executable'
        project.products.children.size() == 1
        project.products.children[0].path == exe("output/exe/main/debug/TestApp").absolutePath
    }

    def "honors changes to library output file locations"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
buildDir = 'output'
library.module = 'TestLib'
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeSchemeAppSharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == "App" }
        project.targets[0].name == 'App SharedLibrary'
        project.targets[0].productReference.path == sharedLib("output/lib/main/debug/TestLib").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/debug").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/release").absolutePath
        project.targets[1].name == '[INDEXING ONLY] App SharedLibrary'
        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("output/lib/main/debug/TestLib").absolutePath
    }
}
