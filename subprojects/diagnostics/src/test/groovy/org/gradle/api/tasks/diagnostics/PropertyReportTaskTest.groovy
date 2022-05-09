/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class PropertyReportTaskTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private ProjectInternal project
    private PropertyReportRenderer renderer
    private PropertyReportTask task

    def setup() {
        project = Mock()
        renderer = Mock()
        task = TestUtil.create(temporaryFolder).task(PropertyReportTask.class)
        task.setRenderer(renderer)
    }

    def "passes each project property to renderer"() {
        given:
        project.properties >> ['b': 'value2', 'a': 'value1']

        when:
        task.generate(project)

        then:
        1 * renderer.addProperty('a', 'value1')
        1 * renderer.addProperty('b', 'value2')
    }

    def "uses placeholder for rendering 'properties' property"() {
        given:
        project.properties >> ['prop': 'value', 'properties': 'prop']

        when:
        task.generate(project)

        then:
        1 * renderer.addProperty('prop', 'value')
        1 * renderer.addProperty('properties', '{...}')
    }

    def "can show a single property"() {
        given:
        project.properties >> ['b': 'value2', 'a': 'value1']
        task.property = 'b'

        when:
        task.generate(project)

        then:
        1 * renderer.addProperty('b', 'value2')
        0 * renderer.addProperty('a', 'value2')
    }

    def "uses placeholder for rendering 'properties' property even if it's selected via PropertyReportTask.property"() {
        given:
        project.properties >> ['prop': 'value', 'properties': 'prop']
        task.property = 'properties'

        when:
        task.generate(project)

        then:
        0 * renderer.addProperty('prop', 'value')
        1 * renderer.addProperty('properties', '{...}')
    }

    def "passes unavailable properties to renderer"() {
        given:
        project.properties >> [:]
        task.property = 'not-found'

        when:
        task.generate(project)

        then:
        1 * renderer.addProperty('not-found', null)
    }
}
