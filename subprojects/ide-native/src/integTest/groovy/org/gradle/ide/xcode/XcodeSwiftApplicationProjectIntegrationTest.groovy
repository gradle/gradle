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

package org.gradle.ide.xcode

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Ignore
import spock.lang.Issue

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeSwiftApplicationProjectIntegrationTest extends AbstractXcodeSwiftProjectIntegrationTest {
    @Override
    void makeSingleProject() {
        buildFile << """
            apply plugin: 'swift-application'
        """
    }

    @Override
    String getComponentUnderTestDsl() {
        return 'application'
    }

    @Override
    protected SwiftSourceElement getComponentUnderTest() {
        return new SwiftApp()
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    @Ignore("https://github.com/gradle/gradle-native-private/issues/273")
    def "can create xcode project for unbuildable Swift application with library"() {
        useXcodebuildTool()

        given:
        settingsFile << """
            include 'app', 'greeter'
        """

        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'

                application {
                    targetMachines = [machines.os('os-family')]
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
        """
        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file('greeter'))
        app.executable.writeToProject(file('app'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcode",
                ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme", ":greeter:xcode",
                ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
                .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == null
        // TODO: SWIFT_INCLUDE_PATHS should contains ("greeter/build/modules/main/debug")

        when:
        def resultApp = xcodebuild
                .withWorkspace(rootXcodeWorkspace)
                .withScheme('App')
                .fails()

        then:
        resultApp.error.contains('The workspace named "app" does not contain a scheme named "App".')

        when:
        def resultLib = xcodebuild
                .withWorkspace(rootXcodeWorkspace)
                .withScheme('Greeter')
                .succeeds()

        then:
        resultLib.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:linkDebug', ':greeter:_xcode___Greeter_Debug')
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle-native/issues/130")
    @Requires(UnitTestPreconditions.HasXCode)
    def "can create xcode project for Swift application with unbuildable library"() {
        useXcodebuildTool()

        given:
        settingsFile << """
            include 'app', 'greeter'
        """

        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'

                application {
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'

                library.targetMachines = [machines.os('os-family')]
            }
        """
        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file('greeter'))
        app.executable.writeToProject(file('app'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
                ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcode",
                ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
                .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("greeter/build/modules/main/debug"))

        when:
        def resultApp = xcodebuild
                .withWorkspace(rootXcodeWorkspace)
                .withScheme('App')
                .fails()

        then:
        resultApp.assertHasCause("Could not resolve all task dependencies for configuration ':app:nativeRuntimeDebug'.")
        resultApp.assertHasCause("Could not resolve project :greeter.")


        when:
        def resultLib = xcodebuild
                .withWorkspace(rootXcodeWorkspace)
                .withScheme('Greeter')
                .fails()

        then:
        resultLib.error.contains('The workspace named "app" does not contain a scheme named "Greeter".')
    }
}
