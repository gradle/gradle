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
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeMultipleCppProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        settingsFile << """
            include 'app', 'greeter'
        """
    }

    def "create xcode project C++ executable inside composite build"() {
        given:
        settingsFile.text = """
            includeBuild 'greeter'
            rootProject.name = '${rootProjectName}'
        """
        buildFile << """
            apply plugin: 'cpp-executable'
            apply plugin: 'xcode'

            dependencies {
                implementation 'test:greeter:1.3'
            }
        """

        file("greeter/settings.gradle") << "rootProject.name = 'greeter'"
        file('greeter/build.gradle') << """
            apply plugin: 'cpp-library'
            apply plugin: 'xcode'

            group = 'test'
        """

        def app = new CppHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('greeter/greeter.xcodeproj')]*.absolutePath)

        buildSettings(xcodeProject("${rootProjectName}.xcodeproj").projectFile).HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("src/main/headers"), file("greeter/src/main/public"))
    }

    def "create xcode project C++ executable"() {
        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
            }
"""
        def app = new CppHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)

        buildSettings(xcodeProject("app/app.xcodeproj").projectFile).HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("greeter/src/main/public"))
    }
}
