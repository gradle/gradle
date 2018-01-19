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
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary

class XcodeMultipleProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        settingsFile << """
            include 'app', 'greeter'
        """
        requireSwiftToolChain()
    }

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
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj')

        when:
        buildFile.text = """
            allprojects {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')
    }

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
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        when:
        buildFile.text = """
            apply plugin: 'xcode'
            project('app') {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj')
    }
}
