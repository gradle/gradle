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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskReportTaskSubprojectsIntegrationTest extends AbstractIntegrationSpec {

    def "tasks command shows only current project tasks by default"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib1', 'lib2'
        """
        buildFile << """
            task rootTask {
                group = 'build'
                description = 'Root project task'
            }
        """
        file('lib1/build.gradle') << """
            task lib1Task {
                group = 'build'
                description = 'Lib1 task'
            }
        """
        file('lib2/build.gradle') << """
            task lib2Task {
                group = 'build'
                description = 'Lib2 task'
            }
        """

        when:
        succeeds 'tasks'

        then:
        output.contains('rootTask - Root project task')
        !output.contains('lib1Task')
        !output.contains('lib2Task')
    }

    def "tasks command with --include-subprojects shows tasks from subprojects"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib1', 'lib2'
        """
        buildFile << """
            task rootTask {
                group = 'build'
                description = 'Root project task'
            }
        """
        file('lib1/build.gradle') << """
            task lib1Task {
                group = 'build'
                description = 'Lib1 task'
            }
        """
        file('lib2/build.gradle') << """
            task lib2Task {
                group = 'build'
                description = 'Lib2 task'
            }
        """

        when:
        succeeds 'tasks', '--include-subprojects'

        then:
        output.contains('rootTask - Root project task')
        output.contains('lib1Task - Lib1 task')
        output.contains('lib2Task - Lib2 task')
    }

    def "tasks command on subproject shows only subproject tasks"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib1', 'lib2'
        """
        buildFile << """
            task rootTask {
                group = 'build'
                description = 'Root project task'
            }
        """
        file('lib1/build.gradle') << """
            task lib1Task {
                group = 'build'
                description = 'Lib1 task'
            }
        """
        file('lib2/build.gradle') << """
            task lib2Task {
                group = 'build'
                description = 'Lib2 task'
            }
        """

        when:
        succeeds ':lib1:tasks'

        then:
        output.contains('lib1Task - Lib1 task')
        !output.contains('rootTask')
        !output.contains('lib2Task')
    }

    def "tasks command fails when trying to run subproject task from parent"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib1'
        """
        file('lib1/build.gradle') << """
            task lib1Task {
                group = 'build'
                description = 'Lib1 task'
            }
        """

        when:
        fails 'lib1Task'

        then:
        failure.assertHasDescription("Task 'lib1Task' not found in root project 'root'")
    }
} 