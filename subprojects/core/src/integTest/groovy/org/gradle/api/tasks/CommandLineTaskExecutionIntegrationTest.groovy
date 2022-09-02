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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.ToBeImplemented

class CommandLineTaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    @ToBeImplemented
    def "fails with badly formed task name"() {
        settingsFile """
            rootProject.name = 'broken'
            include("a")
        """
        buildFile """
        """

        when:
        if (message.empty) {
            succeeds(taskName)
        } else {
            fails(taskName)
        }

        then:
        if (!message.empty) {
            failure.assertHasDescription(message)
        }

        where:
        taskName | message
        ""       | "A path must be specified!"
        ":"      | "Task '' not found in root project 'broken'."
        "::"     | "Task '' not found in root project 'broken'."
        ":a::"   | "Task '' not found in project ':a'."
        ":a::b"  | ""
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
            result.assertTasksExecuted(":one", ":last")
        }
    }
}
