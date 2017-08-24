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
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftLib

class XcodeSingleSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def "create xcode project executable"() {
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

        def project = xcodeProject("app.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
        project.targets.size() == 2
        project.assertTargetsAreTools()
        project.targets.every { it.productName == 'App' }
        project.targets[0].name == 'App Executable'
        project.targets[0].productReference.path == exe("build/exe/main/debug/App").absolutePath
        project.targets[1].name == '[INDEXING ONLY] App Executable'
        project.products.children.size() == 1
        project.products.children[0].path == exe("build/exe/main/debug/App").absolutePath

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "create xcode project library"() {
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

        def project = xcodeProject("app.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + lib.files*.name)
        project.targets.size() == 2
        project.assertTargetsAreDynamicLibraries()
        project.targets.every { it.productName == "App" }
        project.targets[0].name == 'App SharedLibrary'
        project.targets[0].productReference.path == sharedLib("build/lib/main/debug/App").absolutePath
        project.targets[1].name == '[INDEXING ONLY] App SharedLibrary'
        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("build/lib/main/debug/App").absolutePath

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "new source files are included in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + lib.files*.name)

        when:
        def app = new SwiftApp()
        app.writeToProject(testDirectory)
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
    }

    def "deleted source files are not included in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        def app = new SwiftApp()
        app.writeToProject(testDirectory)
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)

        when:
        file('src/main').deleteDir()
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + lib.files*.name)
    }

    def "executable source files in a non-default location are included in the project"() {
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
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
    }

    def "library source files in a non-default location are included in the project"() {
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
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + lib.files*.name)
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

        def project = xcodeProject("app.xcodeproj").projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == 'App' }
        project.targets[0].name == 'App Executable'
        project.targets[0].productReference.path == exe("output/exe/main/debug/TestApp").absolutePath
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

        def project = xcodeProject("app.xcodeproj").projectFile
        project.targets.size() == 2
        project.targets.every { it.productName == "App" }
        project.targets[0].name == 'App SharedLibrary'
        project.targets[0].productReference.path == sharedLib("output/lib/main/debug/TestLib").absolutePath
        project.targets[1].name == '[INDEXING ONLY] App SharedLibrary'
        project.products.children.size() == 1
        project.products.children[0].path == sharedLib("output/lib/main/debug/TestLib").absolutePath

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }
}
