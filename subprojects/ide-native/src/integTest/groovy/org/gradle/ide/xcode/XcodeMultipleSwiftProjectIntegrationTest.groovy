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
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithDep
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraryTest
import org.gradle.nativeplatform.fixtures.app.SwiftGreeterUsingCppFunction
import org.gradle.nativeplatform.fixtures.app.SwiftSum
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

@Requires(TestPrecondition.XCODE)
class XcodeMultipleSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        settingsFile << """
            include 'app', 'greeter'
        """

        useXcodebuildTool()
    }

    def "can create xcode project for Swift application"() {
        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
        """
        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("greeter/build/modules/main/debug"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:linkDebug',
            ':app:compileDebugSwift', ':app:linkDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseApp.assertTasksExecuted(':greeter:compileReleaseSwift', ':greeter:linkRelease', ':greeter:stripSymbolsRelease',
            ':app:compileReleaseSwift', ':app:linkRelease', ':app:_xcode___App_Release')
    }

    def "can create xcode project for Swift application with transitive dependencies"() {
        def app = new SwiftAppWithLibraries()

        given:
        settingsFile.text =  """
            include 'app', 'log', 'hello'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
        """
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.application.writeToProject(file("app"))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":log:xcodeProject", ":log:xcodeProjectWorkspaceSettings", ":log:xcodeSchemeLogSharedLibrary", ":log:xcode",
            ":hello:xcodeProject", ":hello:xcodeProjectWorkspaceSettings", ":hello:xcodeSchemeHelloSharedLibrary", ":hello:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'log/log.xcodeproj', 'hello/hello.xcodeproj')

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("hello/build/modules/main/debug"), file("log/build/modules/main/debug"))
        def helloProject = xcodeProject("hello/hello.xcodeproj").projectFile
        helloProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("log/build/modules/main/debug"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':log:compileDebugSwift', ':log:linkDebug',
            ':hello:compileDebugSwift', ':hello:linkDebug',
            ':app:compileDebugSwift', ':app:linkDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseHello = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Hello SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseHello.assertTasksExecuted(':hello:compileReleaseSwift', ':hello:linkRelease',
            ':log:stripSymbolsRelease', ':log:compileReleaseSwift', ':log:linkRelease', ':hello:_xcode___Hello_Release')
    }

    def "can create xcode project for Swift application with dependency on c++ library"() {
        def cppGreeter = new CppGreeterFunction()
        def swiftGreeter = new SwiftGreeterUsingCppFunction(cppGreeter)
        def sumLibrary = new SwiftSum()
        def app = new SwiftAppWithDep(swiftGreeter, sumLibrary)
        app.main.greeterModule = "Hello"

        given:
        settingsFile.text =  """
            include 'app', 'cppGreeter', 'hello'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        swiftGreeter.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))
        sumLibrary.writeToProject(file("app"))
        app.writeToProject(file("app"))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":cppGreeter:xcodeProject", ":cppGreeter:xcodeProjectWorkspaceSettings", ":cppGreeter:xcodeSchemeCppGreeterSharedLibrary", ":cppGreeter:xcode",
            ":hello:xcodeProject", ":hello:xcodeProjectWorkspaceSettings", ":hello:xcodeSchemeHelloSharedLibrary", ":hello:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'cppGreeter/cppGreeter.xcodeproj', 'hello/hello.xcodeproj')

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("hello/build/modules/main/debug"), file("cppGreeter/build/map"))
        def helloProject = xcodeProject("hello/hello.xcodeproj").projectFile
        helloProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("cppGreeter/build/map"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(":cppGreeter:generateModuleMap", ":cppGreeter:dependDebugCpp", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ':hello:compileDebugSwift', ':hello:linkDebug',
            ':app:compileDebugSwift', ':app:linkDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseHello = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Hello SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseHello.assertTasksExecuted(':hello:compileReleaseSwift', ':hello:linkRelease',
            ":cppGreeter:generateModuleMap", ":cppGreeter:dependReleaseCpp", ":cppGreeter:compileReleaseCpp", ":cppGreeter:linkRelease", ":cppGreeter:stripSymbolsRelease",
            ':hello:_xcode___Hello_Release')
    }

    def "can clean xcode project with transitive dependencies"() {
        def app = new SwiftAppWithLibraries()

        given:
        settingsFile.text =  """
            include 'app', 'log', 'hello'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
        """
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))
        app.application.writeToProject(file("app"))
        succeeds("xcode")

        when:
        xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        exe("app/build/exe/main/debug/App").assertExists()
        sharedLib("hello/build/lib/main/debug/Hello").assertExists()
        sharedLib("log/build/lib/main/debug/Log").assertExists()

        when:
        xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds(XcodebuildExecuter.XcodeAction.CLEAN)

        then:
        exe("app/build/exe/main/debug/App").assertDoesNotExist()
        sharedLib("hello/build/lib/main/debug/Hello").assertExists()
        sharedLib("log/build/lib/main/debug/Log").assertExists()

        when:
        xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Hello SharedLibrary')
            .succeeds(XcodebuildExecuter.XcodeAction.CLEAN)

        then:
        exe("app/build/exe/main/debug/App").assertDoesNotExist()
        sharedLib("hello/build/lib/main/debug/Hello").assertDoesNotExist()
        sharedLib("log/build/lib/main/debug/Log").assertExists()
    }

    def "can create xcode project for Swift application inside composite build"() {
        useSwiftCompiler()

        given:
        settingsFile.text = """
            includeBuild 'greeter'
            rootProject.name = '${rootProjectName}'
        """
        buildFile << """
            apply plugin: 'swift-application'
            apply plugin: 'xcode'

            dependencies {
                implementation 'test:greeter:1.3'
            }
        """

        file("greeter/settings.gradle") << "rootProject.name = 'greeter'"
        file('greeter/build.gradle') << """
            apply plugin: 'swift-library'
            apply plugin: 'xcode'

            group = 'test'
        """

        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":greeter:compileDebugSwift", ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'greeter/greeter.xcodeproj')

        def project = rootXcodeProject.projectFile
        project.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("greeter/build/modules/main/debug"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:linkDebug', ':compileDebugSwift', ':linkDebug', ':_xcode___App_Debug')

        when:
        def resultReleaseGreeter = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Greeter SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseGreeter.assertTasksExecuted(':compileReleaseSwift', ':linkRelease', ':_xcode___Greeter_Release')
    }

    def "can run tests for Swift library within multi-project from xcode"() {
        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
                apply plugin: 'xctest'
            }
        """
        def app = new SwiftAppWithLibraryTest()
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))
        succeeds("xcode")

        when:
        def resultTestRunner = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Greeter SharedLibrary')
            .succeeds(XcodebuildExecuter.XcodeAction.TEST)

        then:
        resultTestRunner.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkTest',
            ':greeter:installTest', ':greeter:syncBundleToXcodeBuiltProductDir', ':greeter:_xcode__build_GreeterTest___GradleTestRunner_Debug')

        resultTestRunner.assertOutputContains("Test Case '-[GreeterTest.MultiplyTestSuite testCanMultiplyTotalOf42]' passed")
        resultTestRunner.assertOutputContains("Test Case '-[GreeterTest.SumTestSuite testCanAddSumOf42]' passed")
        resultTestRunner.assertOutputContains("** TEST SUCCEEDED **")
    }
}
