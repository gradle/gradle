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

import org.gradle.ide.xcode.fixtures.XcodeProjectPackage
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp

class XcodeSingleProjectIntegrationTest extends AbstractIntegrationSpec {
    private static final String PROJECT_NAME = "app"

    def "create xcode project Swift executable"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
apply plugin: 'xcode'
"""

        settingsFile << """
rootProject.name = "${PROJECT_NAME}"
"""

        def app = new SwiftHelloWorldApp()
        app.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then: 'tasks are executed as expected'
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme${PROJECT_NAME}Executable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        def project = xcodeProject("${PROJECT_NAME}.xcodeproj").projectFile

        and: 'source files are properly attached to the project'
        project.mainGroup.children.size() == app.sourceFiles.size() + 2
        project.mainGroup.children*.name == ['Products', 'build.gradle', 'hello.swift', 'main.swift', 'sum.swift']

        and: 'targets are properly created'
        project.targets.size() == 2
        project.targets*.productType == [PBXTarget.ProductType.TOOL.identifier] * 2
        project.targets*.productName == [PROJECT_NAME] * 2

        def gradleTargets = project.targets.findAll(gradleTargets())
        gradleTargets.size() == 1

        def indexTargets = project.targets.findAll(indexTargets())
        indexTargets.size() == 1
    }

    def "create xcode project Swift module"() {
        given:
        buildFile << """
apply plugin: 'swift-module'
apply plugin: 'xcode'
"""

        settingsFile << """
rootProject.name = "${PROJECT_NAME}"
"""

        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then: 'tasks are executed as expected'
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme${PROJECT_NAME}SharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")
        def project = xcodeProject("${PROJECT_NAME}.xcodeproj").projectFile

        and: 'source files are properly attached to the project'
        project.mainGroup.children.size() == app.library.sourceFiles.size() + 2
        project.mainGroup.children*.name == ['Products', 'build.gradle', 'hello.swift', 'sum.swift']

        and: 'targets are properly created'
        project.targets.size() == 2
        project.targets*.productType == [PBXTarget.ProductType.DYNAMIC_LIBRARY.identifier] * 2
        project.targets*.productName == [PROJECT_NAME] * 2

        def gradleTargets = project.targets.findAll(gradleTargets())
        gradleTargets.size() == 1

        def indexTargets = project.targets.findAll(indexTargets())
        indexTargets.size() == 1
    }

    def "create empty xcode project when no language plugins are applied"() {
        given:
        buildFile << """
apply plugin: 'xcode'
"""

        settingsFile << """
rootProject.name = "${PROJECT_NAME}"
"""

        when:
        succeeds("xcode")

        then: 'tasks are executed as expected'
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcode")
        def project = xcodeProject("${PROJECT_NAME}.xcodeproj").projectFile

        and: 'only the build script is attached to the project'
        project.mainGroup.children.size() == 1
        project.mainGroup.children*.name == ['build.gradle']

        and: 'no targets are created'
        project.targets.size() == 0
    }

    private XcodeProjectPackage xcodeProject(String path) {
        new XcodeProjectPackage(file(path))
    }

    private static def gradleTargets() {
        return {
            it.isa == 'PBXLegacyTarget'
        }
    }

    private static def indexTargets() {
        return {
            it.isa == 'PBXNativeTarget'
        }
    }
}
