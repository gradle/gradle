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

package org.gradle.api.tasks

import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CommandLineTaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    def "fails with badly formed task name"() {
        enableProblemsApiCheck()
        createDirs("a")
        settingsFile """
            rootProject.name = 'broken'
            include("a")
        """
        buildFile """
        """

        when:
        fails(taskName)

        then:
        failureDescriptionContains(message)
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == expectedFqid
            definition.id.displayName == expectedDisplayName
            contextualLabel == message
            additionalData.asMap == ['requestedPath': taskName]
        }

        where:
        taskName | message                                                                                                                  | expectedFqid                         | expectedDisplayName
        ""       | "Cannot locate matching tasks for an empty path. The path should include a task name (for example ':help' or 'help')."   | "task-selection:empty-path"          | "Empty path"
        ":"      | "Cannot locate tasks that match ':'. The path should include a task name (for example ':help' or 'help')."               | "task-selection:missing-task-name"   | "Missing task name"
        "::"     | "Cannot locate tasks that match '::'. The path should include a task name (for example ':help' or 'help')."              | "task-selection:missing-task-name"   | "Missing task name"
        ":a::"   | "Cannot locate tasks that match ':a::'. The path should not include an empty segment (try ':a' instead)."                | "task-selection:empty-segments"      | "Empty segments"
        ":a::b"  | "Cannot locate tasks that match ':a::b'. The path should not include an empty segment (try ':a:b' instead)."             | "task-selection:empty-segments"      | "Empty segments"
    }

    def "build logic can mutate the list of requested tasks"() {
        buildFile """
            gradle.startParameter.taskNames += ["last"]

            task one {
                doLast {}
            }

            task last {
                doLast {}
            }
        """

        expect:
        2.times {
            run("one")
            result.assertTasksScheduled(":one", ":last")
        }
    }
}
