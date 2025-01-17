/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PropertyReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            group = 'org.gradle.api'
            version = '1.2.3'
        """
    }

    def "lists all project properties"() {
        when:
        run "properties", "-q"
        then:
        outputContains 'version: 1.2.3'
        outputContains 'group: org.gradle.api'
        // Groovy 4 exposes static fields as properties, which we filter in BeanDynamicObject and should not see in the report.
        outputDoesNotContain 'DEFAULT_BUILD_DIR_NAME'
    }

    def "lists selected project property"() {
        when:
        run "properties", "-q", "--property=version"
        then:
        outputContains 'version: 1.2.3'
        outputDoesNotContain 'org.gradle.api'
    }

    def "lists unavailable project property"() {
        when:
        run "properties", "-q", "--property=nonexistent"
        then:
        outputContains 'nonexistent: null'
    }
}
