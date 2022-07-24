/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskRemovalIntegrationTest extends AbstractIntegrationSpec {
    def "throws exception when removing a task with #description"() {
        given:
        buildFile << """
            task foo(type: Zip) {}
            ${code}

            // need at least one task to execute anything
            task dummy
        """

        when:
        fails ("dummy")

        then:
        failure.assertHasCause("Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead.")

        where:
        description                                | code
        "TaskContainer.remove(Object)"             | "tasks.remove(foo)"
        "TaskContainer.removeAll(Collection)"      | "tasks.removeAll([foo])"
        "TaskContainer.clear()"                    | "tasks.clear()"
        "TaskContainer.retainAll(Collection)"      | "tasks.retainAll([foo])"
        "TaskContainer.iterator()#remove()"        | "def it = tasks.iterator(); it.next(); it.remove()"
    }

    def "throws exception when using whenObjectRemoved"() {
        given:
        buildFile << """
            task foo(type: Zip) {}
            tasks.whenObjectRemoved new Action<Task>() { void execute(Task t) {} }

            // need at least one task to execute anything
            task dummy
        """

        when:
        fails ("dummy")

        then:
        failure.assertHasCause("Registering actions on task removal is not supported.")
    }
}
