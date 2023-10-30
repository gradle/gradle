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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
class XcodeMultipleProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'app', 'greeter'
        """
        requireSwiftToolChain()
    }

    @Override
    protected String getRootProjectName() {
        return "root"
    }

    @ToBeFixedForConfigurationCache
    def "create xcode workspace when no language plugins are applied"() {
        given:
        buildFile << """
            allprojects {
                apply plugin: 'xcode'
            }
        """

        createDirs("app")
        createDirs("greeter")

        when:
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":xcodeProject", ":xcodeProjectWorkspaceSettings",
            ":app:xcodeProjectWorkspaceSettings", ":app:xcodeProject",
            ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeProject",
            ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")

        def workspace = rootXcodeWorkspace
        workspace.contentFile.assertHasProjects("root.xcodeproj", "app/app.xcodeproj", "greeter/greeter.xcodeproj")

        def project = rootXcodeProject.projectFile
        project.mainGroup.assertHasChildren(['build.gradle'])
        project.assertNoTargets()
    }

    @ToBeFixedForConfigurationCache
    def "creates workspace with Xcode project for each project"() {
        given:
        settingsFile << """
            include 'empty'
        """

        createDirs("greeter")
        createDirs("app")
        createDirs("empty")

        buildFile << """
            allprojects {
                apply plugin: 'xcode'
            }
            apply plugin: 'swift-application'
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

        when:
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme",
            ":app:xcodeProjectWorkspaceSettings", ":app:xcodeProject", ":app:xcodeScheme",
            ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeProject", ":greeter:xcodeScheme",
            ":empty:xcodeProjectWorkspaceSettings", ":empty:xcodeProject",
            ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")

        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj', 'empty/empty.xcodeproj')

        xcodeProject("${rootProjectName}.xcodeproj")
        xcodeProject("app/app.xcodeproj")
        xcodeProject("greeter/greeter.xcodeproj")
        xcodeProject("empty/empty.xcodeproj")
    }

    @ToBeFixedForConfigurationCache
    def "Gradle project with added xcode plugin are included in the workspace"() {
        given:
        file('greeter/build.gradle') << """
            apply plugin: 'swift-library'
        """
        file('app/build.gradle') << """
            apply plugin: 'swift-application'
            dependencies {
                implementation project(':greeter')
            }
        """

        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        when:
        buildFile.text = """
            apply plugin: 'xcode'
            project('app') {
                apply plugin: 'xcode'
            }
        """
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":greeter:compileDebugSwift",
            ":xcodeProjectWorkspaceSettings", ":xcodeProject",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj')

        when:
        buildFile.text = """
            allprojects {
                apply plugin: 'xcode'
            }
        """
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme",
            ":xcodeProjectWorkspaceSettings", ":xcodeProject",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')
    }

    @ToBeFixedForConfigurationCache
    def "Gradle project with removed xcode plugin are not included in the workspace"() {
        given:
        file('greeter/build.gradle') << """
            apply plugin: 'swift-library'
        """
        file('app/build.gradle') << """
            apply plugin: 'swift-application'
            dependencies {
                implementation project(':greeter')
            }
        """

        def app = new SwiftAppWithLibrary()
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        when:
        buildFile.text = """
            allprojects {
                apply plugin: 'xcode'
            }
        """
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme",
            ":xcodeProjectWorkspaceSettings", ":xcodeProject",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        when:
        buildFile.text = """
            apply plugin: 'xcode'
            project('app') {
                apply plugin: 'xcode'
            }
        """
        succeeds(":xcode")

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":greeter:compileDebugSwift",
            ":xcodeProjectWorkspaceSettings", ":xcodeProject",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj')
    }
}
