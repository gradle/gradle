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
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class XcodeSingleSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {

    def "can create xcode project for Swift application"() {
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'
"""

        def app = new SwiftApp()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

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
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-library'
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

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

    def "can create xcode project for Swift static library"() {
        requireSwiftToolChain()

        given:
        buildFile << """
            apply plugin: 'swift-library'
            library.linkage = [Linkage.STATIC]
        """
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources'])
        project.sources.assertHasChildren(lib.files*.name)
        project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE]

        project.targets.size() == 2
        assertTargetIsStaticLibrary(project.targets[0], 'App')
        assertTargetIsIndexer(project.targets[1], 'App')

        project.products.children.size() == 1
        project.products.children[0].path == staticLib("build/lib/main/debug/App").absolutePath

        rootXcodeProject.schemeFiles.size() == 1
        rootXcodeProject.schemeFiles[0].schemeXml.LaunchAction.BuildableProductRunnable.size() == 0
    }

    @Requires(TestPrecondition.XCODE)
    def "can build Swift static library from xcode"() {
        useXcodebuildTool()
        def lib = new SwiftLib()
        def debugBinary = staticLib("build/lib/main/debug/App")
        def releaseBinary = staticLib("build/lib/main/release/App")

        given:
        buildFile << """
            apply plugin: 'swift-library'
            library.linkage = [Linkage.STATIC]
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
        resultDebug.assertTasksExecuted(':compileDebugSwift', ':createDebug', ':_xcode___App_Debug')
        resultDebug.assertTasksNotSkipped(':compileDebugSwift', ':createDebug', ':_xcode___App_Debug')
        debugBinary.assertExists()

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseSwift', ':createRelease', ':_xcode___App_Release')
        resultRelease.assertTasksNotSkipped(':compileReleaseSwift', ':createRelease', ':_xcode___App_Release')
        releaseBinary.assertExists()
    }

    def "can create xcode project for Swift application with xctest"() {
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'
apply plugin: 'xctest'
"""

        def app = new SwiftAppWithXCTest()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        if (OperatingSystem.current().isMacOsX()) {
            def project = rootXcodeProject.projectFile
            project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Tests'])
            project.sources.assertHasChildren(app.main.files*.name)
            project.tests.assertHasChildren(app.test.files*.name)
            project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]

            project.targets.size() == 4
            assertTargetIsTool(project.targets[0], 'App')
            assertTargetIsUnitTest(project.targets[1], 'AppTest')
            assertTargetIsIndexer(project.targets[2], 'App')
            assertTargetIsIndexer(project.targets[3], 'AppTest', '"' + file('build/modules/main/debug').absolutePath + '"')

            project.products.children.size() == 1
            project.products.children[0].path == exe("build/exe/main/debug/App").absolutePath
        }
    }

    def "can create xcode project for Swift library and xctest"() {
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-library'
apply plugin: 'xctest'
"""
        def lib = new SwiftLibWithXCTest()
        lib.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

        if (OperatingSystem.current().isMacOsX()) {
            def project = rootXcodeProject.projectFile
            project.mainGroup.assertHasChildren(['Products', 'build.gradle', 'Sources', 'Tests'])
            project.sources.assertHasChildren(lib.main.files*.name)
            project.tests.assertHasChildren(lib.test.files*.name)
            project.buildConfigurationList.buildConfigurations.name == [DefaultXcodeProject.BUILD_DEBUG, DefaultXcodeProject.BUILD_RELEASE, DefaultXcodeProject.TEST_DEBUG]

            project.targets.size() == 4
            assertTargetIsDynamicLibrary(project.targets[0], 'App')
            assertTargetIsUnitTest(project.targets[1], 'AppTest')
            assertTargetIsIndexer(project.targets[2], 'App')
            assertTargetIsIndexer(project.targets[3], 'AppTest', '"' + file('build/modules/main/debug').absolutePath + '"')

            project.products.children.size() == 1
            project.products.children[0].path == sharedLib("build/lib/main/debug/App").absolutePath
        }
    }

    @Requires(TestPrecondition.XCODE)
    def "returns meaningful errors from xcode when Swift application product doesn't have test configured"() {
        useXcodebuildTool()

        given:
        buildFile << """
apply plugin: 'swift-application'
"""

        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebug = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRunner = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.TEST_DEBUG)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRunner.error.contains("Scheme App is not currently configured for the test action.")
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
            .withScheme("App")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebug.error.contains("Scheme App is not currently configured for the test action.")

        when:
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultRelease.error.contains("Scheme App is not currently configured for the test action.")
    }

    @Requires(TestPrecondition.XCODE)
    def "can configure test only when xctest plugin is applied"() {
        useXcodebuildTool()

        given:
        settingsFile.text = "rootProject.name = 'greeter'"
        buildFile << """
apply plugin: 'swift-library'
"""

        def lib = new SwiftLibWithXCTest()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultDebugWithoutXCTest = xcodebuild
            .withProject(xcodeProject("greeter.xcodeproj"))
            .withScheme("Greeter")
            .fails(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultDebugWithoutXCTest.error.contains("Scheme Greeter is not currently configured for the test action.")

        when:
        buildFile << "apply plugin: 'xctest'"
        succeeds("xcode")
        def resultDebugWithXCTest = xcodebuild
            .withProject(xcodeProject("greeter.xcodeproj"))
            .withScheme("Greeter")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        !resultDebugWithXCTest.error.contains("Scheme Greeter is not currently configured for the test action.")
        resultDebugWithXCTest.assertOutputContains("Test Case '-[GreeterTest.MultiplyTestSuite testCanMultiplyTotalOf42]' passed")
        resultDebugWithXCTest.assertOutputContains("Test Case '-[GreeterTest.SumTestSuite testCanAddSumOf42]' passed")
        resultDebugWithXCTest.assertOutputContains("** TEST SUCCEEDED **")
    }

    @Requires(TestPrecondition.XCODE)
    def "can run tests for Swift library from xcode"() {
        useXcodebuildTool()
        def lib = new SwiftLibWithXCTest()

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
            .withScheme("Greeter")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultTestRunner.assertTasksExecuted(':compileDebugSwift', ':compileTestSwift', ':linkTest', ':installTest',
            ':syncBundleToXcodeBuiltProductDir', ':_xcode__build_GreeterTest___GradleTestRunner_Debug')
        resultTestRunner.assertOutputContains("Test Case '-[GreeterTest.MultiplyTestSuite testCanMultiplyTotalOf42]' passed")
        resultTestRunner.assertOutputContains("Test Case '-[GreeterTest.SumTestSuite testCanAddSumOf42]' passed")
        resultTestRunner.assertOutputContains("** TEST SUCCEEDED **")
    }

    @Requires(TestPrecondition.XCODE)
    def "can run tests for Swift application from xcode"() {
        useXcodebuildTool()
        def app = new SwiftAppWithXCTest()

        given:
        settingsFile.text = """
rootProject.name = 'app'
"""
        buildFile << """
apply plugin: 'swift-application'
apply plugin: 'xctest'
"""

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        def resultTestRunner = xcodebuild
            .withProject(xcodeProject("app.xcodeproj"))
            .withScheme("App")
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultTestRunner.assertTasksExecuted(':compileDebugSwift', ':compileTestSwift', ":relocateMainForTest", ':linkTest', ':installTest',
            ':syncBundleToXcodeBuiltProductDir', ':_xcode__build_AppTest___GradleTestRunner_Debug')
        resultTestRunner.assertOutputContains("Test Case '-[AppTest.MultiplyTestSuite testCanMultiplyTotalOf42]' passed")
        resultTestRunner.assertOutputContains("Test Case '-[AppTest.SumTestSuite testCanAddSumOf42]' passed")
        resultTestRunner.assertOutputContains("** TEST SUCCEEDED **")
    }

    @Requires(TestPrecondition.XCODE)
    def "can build Swift application from xcode"() {
        useXcodebuildTool()
        def app = new SwiftApp()
        def debugBinary = exe("build/exe/main/debug/App")
        def releaseBinary = exe("build/exe/main/release/App")

        given:
        buildFile << """
apply plugin: 'swift-application'
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
        resultDebug.assertTasksExecuted(':compileDebugSwift', ':linkDebug', ':_xcode___App_Debug')
        resultDebug.assertTasksNotSkipped(':compileDebugSwift', ':linkDebug', ':_xcode___App_Debug')
        debugBinary.exec().out == app.expectedOutput
        fixture(debugBinary).assertHasDebugSymbolsFor(app.sourceFileNames)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseSwift', ':linkRelease', ':_xcode___App_Release')
        resultRelease.assertTasksNotSkipped(':compileReleaseSwift', ':linkRelease', ':_xcode___App_Release')
        releaseBinary.exec().out == app.expectedOutput
        fixture(releaseBinary).assertHasDebugSymbolsFor(app.sourceFileNames)
    }

    @Requires(TestPrecondition.XCODE)
    def "produces reasonable message when xcode uses outdated xcode configuration"() {
        useXcodebuildTool()
        def app = new SwiftApp()

        given:
        buildFile << """
apply plugin: 'swift-application'
"""

        app.writeToProject(testDirectory)
        succeeds("xcode")
        settingsFile.text = "rootProject.name = 'NotApp'"

        when:
        def result = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .fails()
        then:
        result.assertOutputContains("Unknown Xcode target 'App', do you need to re-generate Xcode configuration?")
    }

    @Requires(TestPrecondition.XCODE)
    def "can clean from xcode"() {
        useXcodebuildTool()
        def app = new SwiftApp()

        given:
        buildFile << """
apply plugin: 'swift-application'
"""

        app.writeToProject(testDirectory)
        succeeds("xcode")

        when:
        exe("build/exe/main/debug/App").assertDoesNotExist()
        xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .succeeds()
        then:
        exe("build/exe/main/debug/App").exec().out == app.expectedOutput

        when:
        xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .succeeds(XcodebuildExecuter.XcodeAction.CLEAN)
        then:
        file("build").assertDoesNotExist()
    }

    @Requires(TestPrecondition.XCODE)
    def "can build Swift library from xcode"() {
        useXcodebuildTool()
        def lib = new SwiftLib()
        def debugBinary = sharedLib("build/lib/main/debug/App")
        def releaseBinary = sharedLib("build/lib/main/release/App")

        given:
        buildFile << """
apply plugin: 'swift-library'
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
        resultDebug.assertTasksExecuted(':compileDebugSwift', ':linkDebug', ':_xcode___App_Debug')
        resultDebug.assertTasksNotSkipped(':compileDebugSwift', ':linkDebug', ':_xcode___App_Debug')
        debugBinary.assertExists()
        fixture(debugBinary).assertHasDebugSymbolsFor(lib.sourceFileNames)

        when:
        releaseBinary.assertDoesNotExist()
        def resultRelease = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultRelease.assertTasksExecuted(':compileReleaseSwift', ':linkRelease', ':stripSymbolsRelease', ':_xcode___App_Release')
        resultRelease.assertTasksNotSkipped(':compileReleaseSwift', ':linkRelease', ':stripSymbolsRelease', ':_xcode___App_Release')
        releaseBinary.assertExists()
        fixture(releaseBinary).assertHasDebugSymbolsFor(lib.sourceFileNames)
    }

    def "adds new source files in the project"() {
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'
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
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'
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

    def "includes source files in a non-default location in Swift application project"() {
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'

application {
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
        requireSwiftToolChain()

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
        requireSwiftToolChain()

        given:
        buildFile << """
apply plugin: 'swift-application'
buildDir = 'output'
application.module = 'TestApp'
"""

        def app = new SwiftApp()
        app.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == 'TestApp' }
        project.targets[0].name == 'TestApp'
        project.targets[0].productReference.path == exe("output/exe/main/debug/TestApp").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/exe/main/debug").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/exe/main/release").absolutePath
        project.targets[1].name == '[INDEXING ONLY] TestApp'
        project.products.children.size() == 1
        project.products.children[0].path == exe("output/exe/main/debug/TestApp").absolutePath
    }

    def "honors changes to library output file locations"() {
        requireSwiftToolChain()

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
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = rootXcodeProject.projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == "TestLib" }
        project.targets[0].name == 'TestLib'
        project.targets[0].productReference.path == sharedLib("output/lib/main/debug/TestLib").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[0].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/debug").absolutePath
        project.targets[0].buildConfigurationList.buildConfigurations[1].buildSettings.CONFIGURATION_BUILD_DIR == file("output/lib/main/release/stripped").absolutePath
        project.targets[1].name == '[INDEXING ONLY] TestLib'
        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("output/lib/main/debug/TestLib").absolutePath
    }
}
