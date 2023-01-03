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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectReportsPluginIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: 'project-report'
        """
    }

    def "produces report files"() {
        when:
        succeeds("projectReport")

        then:
        file("build/reports/project/dependencies.txt").assertExists()
        file("build/reports/project/properties.txt").assertExists()
        file("build/reports/project/tasks.txt").assertExists()
        file("build/reports/project/dependencies").assertIsDir()
    }

    def "produces report files in custom directory"() {
        given:
        buildFile << """
            projectReportDirName = "custom"
        """

        when:
        succeeds("projectReport")

        then:
        file("build/reports/custom/dependencies.txt").assertExists()
        file("build/reports/custom/properties.txt").assertExists()
        file("build/reports/custom/tasks.txt").assertExists()
        file("build/reports/custom/dependencies").assertIsDir()
    }

    def "prints link to default #task"(String task) {
        when:
        succeeds(task)

        then:
        outputContains("See the report at:")

        where:
        task << ["taskReport", "propertyReport", "dependencyReport", "htmlDependencyReport"]
    }

    def "given no output file, does not print link to default #task"(String task) {
        given:
        buildFile << """
            ${task} {
                outputFile = null
            }
        """

        when:
        succeeds(task)

        then:
        !result.getOutput().contains("See the report at:")

        where:
        task << ["taskReport", "propertyReport", "dependencyReport"]
    }

    def "given no HTML report, does not print link to default HTML dependency report"() {
        given:
        buildFile << """
            htmlDependencyReport {
                reports.html.required = false
            }
        """

        when:
        succeeds("htmlDependencyReport")

        then:
        !result.getOutput().contains("See the report at:")
    }
}
