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

package org.gradle.plugins.ide

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

abstract class AbstractIdeLifecycleIntegrationTest extends AbstractIdeProjectIntegrationTest {
    abstract String[] getGenerationTaskNames(String projectPath)

    def setup() {
        project("root") {
            project("foo") {
                project("bar") {}
            }
        }
    }

    @ToBeFixedForConfigurationCache
    def "lifecycle task is added and generates metadata"() {
        when:
        run lifeCycleTaskName

        then:
        executed ":${lifeCycleTaskName}"
        executed getGenerationTaskNames(":")
        executed getGenerationTaskNames(":foo")
        executed getGenerationTaskNames(":foo:bar")

        and:
        projectName(".") == "root"
        projectName("foo") == "foo"
        projectName("foo/bar") == "bar"
    }

    @ToBeFixedForConfigurationCache
    def "clean tasks always run before generation tasks when specified on the command line"() {
        when:
        run cleanTaskName, lifeCycleTaskName

        then:
        assertCleanTasksRunBeforeGenerationTasks()

        when:
        run lifeCycleTaskName, cleanTaskName

        then:
        assertGenerationTasksRunBeforeCleanTasks()
    }

    @ToBeFixedForConfigurationCache
    def "clean tasks always run before generation tasks when modeled as a dependency"() {
        given:
        buildFile << """
            allprojects {
                tasks.${lifeCycleTaskName}.dependsOn tasks.${cleanTaskName}
            }
        """

        when:
        run lifeCycleTaskName

        then:
        assertCleanTasksRunBeforeGenerationTasks()

        and:
        projectName(".") == "root"
        projectName("foo") == "foo"
        projectName("foo/bar") == "bar"
    }

    def assertCleanTasksRunBeforeGenerationTasks() {
        [":", ":foo", ":foo:bar"].each { projectPath ->
            getGenerationTaskNames(projectPath).each { taskName ->
                result.assertTaskOrder(getCleanTaskName(taskName), taskName)
            }
        }
    }

    def assertGenerationTasksRunBeforeCleanTasks() {
        [":", ":foo", ":foo:bar"].each { projectPath ->
            getGenerationTaskNames(projectPath).each { taskName ->
                result.assertTaskOrder(taskName, getCleanTaskName(taskName))
            }
        }
    }

    String getCleanTaskName(String generationTaskName) {
        int lastSeparator = generationTaskName.lastIndexOf(":")
        String taskName = generationTaskName.substring(lastSeparator + 1)
        String cleanTaskName = "clean${taskName.capitalize()}"
        return generationTaskName.substring(0, lastSeparator + 1) + cleanTaskName
    }
}
