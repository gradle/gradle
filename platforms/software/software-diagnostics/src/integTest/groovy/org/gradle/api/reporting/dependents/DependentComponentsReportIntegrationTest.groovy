/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.reporting.dependents

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DependentComponentsReportIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "help displays dependents report task options"() {
        when:
        run "help", "--task", "dependentComponents"

        then:
        output.contains("Displays the dependent components of components in root project 'test'. [deprecated]")
        output.contains("--all     Show all components (non-buildable and test suites).")
        output.contains("--non-buildable     Show non-buildable components.")
        output.contains("--test-suites     Show test suites components.")
        output.contains("--component     Component to generate the report for (can be specified more than once).")
    }

    def "displays empty dependents report for an empty project"() {
        given:
        buildFile

        when:
        run "dependentComponents"

        then:
        output.contains("No components.");
    }
}
