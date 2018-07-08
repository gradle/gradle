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
import org.gradle.tooling.model.cpp.CppComponent
import org.gradle.tooling.model.cpp.CppComponentType

@ToolingApiVersion(">=4.10")
@TargetGradleVersion(">=4.10")
class CppModelCrossVersionSpec extends ToolingApiSpecification {
    def "can query model when root project applies C++ application plugin"() {
        buildFile << """
            apply plugin: 'cpp-application'
        """

        when:
        CppComponent component = withConnection { connection -> connection.getModel(CppComponent.class) }

        then:
        component.componentType == CppComponentType.APPLICATION
    }

    def "can query model when root project applies C++ library plugin"() {
        buildFile << """
            apply plugin: 'cpp-library'
        """

        when:
        CppComponent component = withConnection { connection -> connection.getModel(CppComponent.class) }

        then:
        component.componentType == CppComponentType.LIBRARY
    }

    def "has no model when root project does not apply any C++ plugin"() {
        buildFile << """
            apply plugin: 'java-library'
        """

        when:
        CppComponent component = withConnection { connection -> connection.getModel(CppComponent.class) }

        then:
        component == null
    }
}
