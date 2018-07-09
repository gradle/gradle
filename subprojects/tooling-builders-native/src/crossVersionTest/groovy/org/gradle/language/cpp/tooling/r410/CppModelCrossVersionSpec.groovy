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
import org.gradle.tooling.model.cpp.CppComponentType
import org.gradle.tooling.model.cpp.CppProject

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
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.projectIdentifier.projectPath == ':'
        project.projectIdentifier.buildIdentifier.rootDir == projectDir
        project.mainComponent != null
        project.mainComponent.componentType == CppComponentType.APPLICATION
        project.testComponent == null
    }

    def "can query model when root project applies C++ library plugin"() {
        buildFile << """
            apply plugin: 'cpp-library'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent != null
        project.mainComponent.componentType == CppComponentType.LIBRARY
        project.testComponent == null
    }

    def "can query model when root project applies C++ unit test plugin"() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent == null
        project.testComponent != null
    }

    def "can query model when root project applies C++ application and unit test plugins"() {
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent != null
        project.testComponent != null
    }

    def "can query model when root project applies C++ library and unit test plugins"() {
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
        """

        when:
        def project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent != null
        project.testComponent != null
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
        models[1].mainComponent != null
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':lib'
        models[2].mainComponent != null
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
        models[1].mainComponent != null
        models[1].testComponent == null
        models[2].projectIdentifier.projectPath == ':'
        models[2].projectIdentifier.buildIdentifier.rootDir == file("lib")
        models[2].mainComponent != null
        models[2].testComponent != null
    }
}
