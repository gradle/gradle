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
    def "can query model when root project applies C++ application plugin"() {
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent != null
        project.mainComponent.componentType == CppComponentType.APPLICATION
        project.testComponent == null
    }

    def "can query model when root project applies C++ library plugin"() {
        buildFile << """
            apply plugin: 'cpp-library'
        """

        when:
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

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
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

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
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

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
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent != null
        project.testComponent != null
    }

    def "has empty model when root project does not apply any C++ plugins"() {
        buildFile << """
            apply plugin: 'java-library'
        """

        when:
        CppProject project = withConnection { connection -> connection.getModel(CppProject.class) }

        then:
        project.mainComponent == null
        project.testComponent == null
    }
}
