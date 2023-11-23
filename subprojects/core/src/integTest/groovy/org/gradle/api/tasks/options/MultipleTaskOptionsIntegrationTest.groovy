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

package org.gradle.api.tasks.options

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.api.tasks.options.TaskOptionFixture.taskWithMultipleOptions

class MultipleTaskOptionsIntegrationTest extends AbstractIntegrationSpec {

    def "can configure tasks from command line in multiple projects"() {
        given:
        createDirs("project2")
        settingsFile << "include 'project2'"
        buildFile << """
            allprojects {
                task someTask(type: SomeTask)
            }
            task task1 //extra stress
            task task2

            ${taskWithMultipleOptions()}
        """

        when:
        run 'someTask'

        then:
        output.contains 'first=false,second=null'

        when:
        run 'task1', 'someTask', '--first', '--second', 'hey buddy', 'task2'

        then:
        output.count('first=true,second=hey buddy') == 2
        result.assertTasksExecuted(":task1", ":someTask", ":project2:someTask", ":task2")
    }

    def "tasks can be configured with different options"() {
        given:
        createDirs("project2")
        settingsFile << "include 'project2'"
        buildFile << """
            allprojects {
                task someTask(type: SomeTask)
            }

            ${taskWithMultipleOptions()}
        """

        when:
        run ':someTask', '--second', 'one', ':project2:someTask', '--second', 'two'

        then:
        result.assertTasksExecuted(":someTask", ":project2:someTask")
        output.count('second=one') == 1
        output.count('second=two') == 1
    }

    def "tasks are configured exclusively with their options"() {
        given:
        createDirs("project2")
        settingsFile << "include 'project2'"
        buildFile << """
            allprojects {
                task someTask(type: SomeTask)
            }

            ${taskWithMultipleOptions()}
        """

        when:
        run ':someTask', '--second', 'one', ':project2:someTask', '--first'

        then:
        result.assertTasksExecuted(":someTask", ":project2:someTask")
        output.count('first=false,second=one') == 1 //--first flag was set only on the :project2:someTask
        output.count('first=true,second=null') == 1 //--second option was set only on the :someTask
    }

    def "multiple different tasks configured at single command line"() {
        given:
        buildFile << """
            task foo
            task someTask(type: SomeTask)

            ${taskWithMultipleOptions()}
        """

        when:
        run 'someTask', '--second', 'foo', 'help'

        then:
        output.contains 'second=foo'
        result.assertTasksExecuted(":someTask", ":help")
    }

    def "task name that matches command value is not included in execution"() {
        given:
        buildFile << """
            task foo
            task someTask(type: SomeTask)

            ${taskWithMultipleOptions()}
        """

        when:
        run 'someTask', '--second', 'foo'

        then:
        output.contains 'second=foo'
        result.assertTasksExecuted(":someTask") //no 'foo' task
    }
}
