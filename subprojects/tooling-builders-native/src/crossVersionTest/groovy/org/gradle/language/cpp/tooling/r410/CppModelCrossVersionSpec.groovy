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

package org.gradle.language.cpp.tooling.r410

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.cpp.CppApplication
import org.gradle.tooling.model.cpp.CppLibrary
import org.gradle.tooling.model.cpp.CppProject
import org.gradle.tooling.model.cpp.CppTestSuite

@ToolingApiVersion(">=4.10")
@TargetGradleVersion(">=4.10")
class CppModelCrossVersionSpec extends ToolingApiSpecification {
    def "has empty model when root project does not apply any C++ plugins"() {
        buildFile << """
            apply plugin: 'java-library'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir
        project.mainComponent == null
        project.testComponent == null
    }

    def "can query model when root project applies C++ application plugin"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir

        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'app'

        project.mainComponent.binaries.size() == 2
        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'app'
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'app'

        project.testComponent == null
    }

    def "can query model when root project applies C++ library plugin"() {
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.mainComponent.baseName == 'lib'

        project.mainComponent.binaries.size() == 2
        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'lib'
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'lib'

        project.testComponent == null
    }

    def "can query model when root project applies C++ unit test plugin"() {
        settingsFile << """
            rootProject.name = 'tests'
        """
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent == null
        project.testComponent instanceof CppTestSuite
        project.testComponent.baseName == 'testsTest'

        project.testComponent.binaries.size() == 1
        project.testComponent.binaries[0].name == 'testExecutable'
        project.testComponent.binaries[0].baseName == 'testsTest'
    }

    def "can query model when root project applies C++ application and unit test plugins"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'app'
        project.testComponent instanceof CppTestSuite
        project.testComponent.baseName == 'appTest'
    }

    def "can query model when root project applies C++ library and unit test plugins"() {
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.testComponent instanceof CppTestSuite
    }

    def "can query model for customized C++ application"() {
        settingsFile << """
            rootProject.name = 'app'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            application {
                baseName = 'some-app'
            }
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppApplication
        project.mainComponent.baseName == 'some-app'
        project.mainComponent.binaries.size() == 2

        project.mainComponent.binaries[0].name == 'mainDebug'
        project.mainComponent.binaries[0].baseName == 'some-app'
        project.mainComponent.binaries[1].name == 'mainRelease'
        project.mainComponent.binaries[1].baseName == 'some-app'
    }

    def "can query model for customized C++ library"() {
        settingsFile << """
            rootProject.name = 'lib'
        """
        buildFile << """
            apply plugin: 'cpp-library'
            library {
                baseName = 'some-lib'
                linkage = [Linkage.STATIC, Linkage.SHARED]
            }
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent instanceof CppLibrary
        project.mainComponent.baseName == 'some-lib'

        project.mainComponent.binaries.size() == 4

        project.mainComponent.binaries[0].name == 'mainDebugStatic'
        project.mainComponent.binaries[0].baseName == 'some-lib'
        project.mainComponent.binaries[1].name == 'mainDebugShared'
        project.mainComponent.binaries[1].baseName == 'some-lib'
        project.mainComponent.binaries[2].name == 'mainReleaseStatic'
        project.mainComponent.binaries[2].baseName == 'some-lib'
        project.mainComponent.binaries[3].name == 'mainReleaseShared'
        project.mainComponent.binaries[3].baseName == 'some-lib'
    }

    def "can query the models for each project in a build"() {
        settingsFile << """
            include 'app'
            include 'lib'
            include 'other'
        """
        buildFile << """
            project(':app') { 
                apply plugin: 'cpp-application' 
            }
            project(':lib') { 
                apply plugin: 'cpp-library' 
                apply plugin: 'cpp-unit-test' 
            }
        """

        when:
        def models = withConnection { connection -> connection.action(new FetchAllCppProjects()).run() }

        then:
        models.size() == 4
        models[0].projectIdentifier.projectPath == ':'
        models[0].mainComponent == null
        models[0].testComponent == null
        models[1].projectIdentifier.projectPath == ':app'
        models[1].mainComponent instanceof CppApplication
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':lib'
        models[2].mainComponent instanceof CppLibrary
        models[2].testComponent != null
        models[3].projectIdentifier.projectPath == ':other'
        models[3].mainComponent == null
        models[3].testComponent == null
    }

    def "can query the models for each project in a composite build"() {
        settingsFile << """
            include 'app'
            includeBuild 'lib'
        """
        buildFile << """
            project(':app') { 
                apply plugin: 'cpp-application' 
            }
        """
        file("lib/build.gradle") << """
                apply plugin: 'cpp-library' 
                apply plugin: 'cpp-unit-test' 
        """

        when:
        def models = withConnection { connection -> connection.action(new FetchAllCppProjects()).run() }

        then:
        models.size() == 3
        models[0].projectIdentifier.projectPath == ':'
        models[0].projectIdentifier.buildIdentifier.rootDir == projectDir
        models[0].mainComponent == null
        models[0].testComponent == null
        models[1].projectIdentifier.projectPath == ':app'
        models[1].projectIdentifier.buildIdentifier.rootDir == projectDir
        models[1].mainComponent instanceof CppApplication
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':'
        models[2].projectIdentifier.buildIdentifier.rootDir == file('lib')
        models[2].mainComponent instanceof CppLibrary
        models[2].testComponent != null
    }
}
