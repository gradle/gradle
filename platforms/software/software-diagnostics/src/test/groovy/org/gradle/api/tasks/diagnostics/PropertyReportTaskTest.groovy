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

import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class PropertyReportTaskTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private PropertyReportRenderer renderer
    private PropertyReportTask task

    def setup() {
        renderer = Mock()
        task = TestUtil.create(temporaryFolder).task(PropertyReportTask.class)
        task.setRenderer(renderer)
    }

    def "passes each project property to renderer"() {
        when:
        task.action()

        then:
        1 * renderer.addProperty('group', '')
        1 * renderer.addProperty('version', 'unspecified')
    }

    def "uses placeholder for rendering 'properties' property"() {
        when:
        task.action()

        then:
        1 * renderer.addProperty('properties', '{...}')
    }

    def "can show a single property"() {
        given:
        task.property = 'version'

        when:
        task.action()

        then:
        1 * renderer.addProperty('version', 'unspecified')
        0 * renderer.addProperty('group', _)
    }

    def "uses placeholder for rendering 'properties' property even if it's selected via PropertyReportTask.property"() {
        given:
        task.property = 'properties'

        when:
        task.action()

        then:
        0 * renderer.addProperty('version', _)
        1 * renderer.addProperty('properties', '{...}')
    }

    def "passes unavailable properties to renderer"() {
        given:
        task.property = 'not-found'

        when:
        task.action()

        then:
        1 * renderer.addProperty('not-found', 'null')
    }
}
