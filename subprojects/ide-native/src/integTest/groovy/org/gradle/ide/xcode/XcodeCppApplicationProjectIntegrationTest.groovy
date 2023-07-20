/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.CppSourceElement
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeCppApplicationProjectIntegrationTest extends AbstractXcodeCppProjectIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-application'
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return 'application'
    }

    @Override
    protected CppSourceElement getComponentUnderTest() {
        return new CppApp()
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "can create xcode project for unbuildable C++ application with library"() {
        useXcodebuildTool()

        given:
        settingsFile << """
            include 'app', 'greeter'
        """

        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'

                application {
                    targetMachines = [machines.os('os-family')]
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
            }
        """
        def app = new CppAppWithLibrary()
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcode",
                ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme", ":greeter:xcode",
                ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
                .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"))
        // TODO: HEADER_SEARCH_PATHS should contains file("greeter/src/main/public")

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
        resultLib.assertTasksExecuted(':greeter:compileDebugCpp', ':greeter:linkDebug', ':greeter:_xcode___Greeter_Debug')
    }

    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application with unbuildable library"() {
        useXcodebuildTool()

        given:
        settingsFile << """
            include 'app', 'greeter'
        """

        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'

                application {
                    dependencies {
                        implementation project(':greeter')
                    }
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'

                library.targetMachines = [machines.os('os-family')]
            }
        """
        def app = new CppAppWithLibrary()
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
                ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcode",
                ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
                .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("greeter/src/main/public"))

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
