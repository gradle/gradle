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
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CppLib

class XcodeSingleCppProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def app = new CppHelloWorldApp()

    def "create xcode project C++ executable"() {
        given:
        buildFile << """
apply plugin: 'cpp-executable'
"""

        app.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme${rootProjectName}Executable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
        project.targets.size() == 2
        project.assertTargetsAreTools()
        project.targets.every { it.productName == rootProjectName }

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "create xcode project C++ library"() {
        given:
        buildFile << """
apply plugin: 'cpp-library'
"""

        app.library.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme${rootProjectName}SharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.files*.name)
        project.targets.size() == 2
        project.assertTargetsAreDynamicLibraries()
        project.targets.every { it.productName == rootProjectName }

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "new source files are included in the project"() {
        given:
        buildFile << """
apply plugin: 'cpp-executable'
"""

        when:
        app.library.writeSources(file('src/main'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.files*.name)

        when:
        app.writeSources(file('src/main'))
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
    }

    def "deleted source files are not included in the project"() {
        given:
        buildFile << """
apply plugin: 'cpp-executable'
"""

        when:
        app.writeSources(file('src/main'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)

        when:
        file('src/main').deleteDir()
        app.library.writeSources(file('src/main'))
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.files*.name)
    }

    def "executable source files in a non-default location are included in the project"() {
        given:
        buildFile << """
apply plugin: 'cpp-executable'

executable {
    source.from 'Sources'
}
"""

        when:
        def app = new CppApp()
        app.headers.writeToProject(testDirectory)
        app.sources.writeToSourceDir(file('Sources'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.files*.name)
    }

    def "library source files in a non-default location are included in the project"() {
        given:
        buildFile << """
apply plugin: 'cpp-library'

library {
    source.from 'Sources'
}
"""

        when:
        def lib = new CppLib()
        lib.headers.writeToProject(testDirectory)
        lib.sources.writeToSourceDir(file('Sources'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + lib.files*.name)
    }
}
