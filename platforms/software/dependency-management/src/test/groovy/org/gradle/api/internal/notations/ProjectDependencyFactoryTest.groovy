/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.gradle.util.internal.GUtil
import spock.lang.Specification

class ProjectDependencyFactoryTest extends Specification {

    def projectDummy = Mock(ProjectInternal)
    def projectFinder = Mock(ProjectFinder)
    def capabilityNotationParser = new CapabilityNotationParserFactory(false).create()
    def projectStateRegistry = Mock(ProjectStateRegistry) {
        stateFor(Path.path(":foo")) >> Mock(ProjectState) {
            getMutableModel() >> projectDummy
        }
    }
    def depFactory = new DefaultProjectDependencyFactory(
        TestUtil.instantiatorFactory().decorateLenient(), true, capabilityNotationParser, TestUtil.objectFactory(),
        AttributeTestUtil.attributesFactory(), TestFiles.taskDependencyFactory(), projectStateRegistry
    )
    def factory = new ProjectDependencyFactory(depFactory)

    def "creates project dependency with map notation"() {
        given:
        boolean expectedTransitive = false;
        final Map<String, Object> mapNotation = GUtil.map("path", ":foo:bar", "configuration", "compile", "transitive", expectedTransitive);

        and:
        projectFinder.getProject(':foo:bar') >> projectDummy

        when:
        def projectDependency = factory.createFromMap(projectFinder, mapNotation);

        then:
        projectDependency.getDependencyProject() == projectDummy
        projectDependency.targetConfiguration == "compile"
        projectDependency.isTransitive() == expectedTransitive
    }

    def "fails with decent message if provided map is invalid"() {
        given:
        projectFinder.getProject(':foo:bar') >> projectDummy

        when:
        factory.createFromMap(projectFinder, GUtil.map("paths", ":foo:bar"));

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains("Required keys [path] are missing from map")
    }

    def "can create project dependency from path"() {
        expect:
        depFactory.create(Path.path(":foo")).dependencyProject == projectDummy
    }
}
