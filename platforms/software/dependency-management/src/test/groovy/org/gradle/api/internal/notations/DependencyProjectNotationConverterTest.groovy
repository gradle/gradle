/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.notations

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.test.fixtures.ExpectDeprecation
import spock.lang.Specification

class DependencyProjectNotationConverterTest extends Specification {

    def factory = Mock(DefaultProjectDependencyFactory)
    def converter = new DependencyProjectNotationConverter(factory)

    @ExpectDeprecation("Using a Project object as a dependency notation has been deprecated")
    def "emits deprecation warning when converting a Project to a dependency"() {
        given:
        def projectState = Mock(ProjectState)
        def project = Mock(ProjectInternal)
        project.getOwner() >> projectState
        def result = Mock(NotationConvertResult)
        def projectDependency = Mock(ProjectDependency)
        factory.create(projectState) >> projectDependency

        when:
        converter.convert(project, result)

        then:
        1 * result.converted(projectDependency)
    }
}
