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

import org.gradle.ide.xcode.fixtures.XcodeWorkspacePackage
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp
import spock.lang.Unroll

class XcodeMultiProjectIntegrationTest extends AbstractIntegrationSpec {
    private static final String WORKSPACE_NAME = "workspace"

    @Unroll
    def "create xcode project #languageName executable"() {
        given:
        settingsFile << """
include 'app', 'greeter'
rootProject.name = "${WORKSPACE_NAME}"
"""
        buildFile << """
            allprojects {
                apply plugin: 'xcode'
            }
            project(':app') {
                apply plugin: '${languageName}-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: '${languageName}-library'
            }
"""
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        succeeds("xcode")

        then: 'tasks are executed as expected'
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemegreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        def workspaceXml = xcodeWorkspace("${WORKSPACE_NAME}.xcworkspace").contentFile.contentXml

        and:
        workspaceXml.FileRef.size() == 3
        workspaceXml.FileRef*.@location*.replaceAll('absolute:', '').containsAll([file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)

        where:
        app                      | languageName
        new SwiftHelloWorldApp() | "swift"
        new CppHelloWorldApp()   | "cpp"
    }

    private XcodeWorkspacePackage xcodeWorkspace(String path) {
        new XcodeWorkspacePackage(file(path))
    }
}
