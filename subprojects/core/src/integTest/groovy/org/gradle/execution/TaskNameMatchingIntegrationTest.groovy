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

package org.gradle.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskNameMatchingIntegrationTest extends AbstractIntegrationSpec {

    def "logs info message for exact match"() {
        setup:
        buildFile << """
            tasks.register("sanityCheck") {
                doLast { }
            }
            tasks.register("safetyCheck") {
                doLast { }
            }
        """

        when:
        run("sanityCheck", "--info")

        then:
        outputContains("Task name matched 'sanityCheck'")
        outputDoesNotContain("abbreviated")
        result.assertTaskExecuted(":sanityCheck")
        result.assertTaskNotExecuted(":safetyCheck")
    }

    def "logs info message for pattern match"() {
        setup:
        buildFile << """
            tasks.register("sanityCheck") {
                doLast { }
            }
            tasks.register("safetyCheck") {
                doLast { }
            }
        """

        when:
        run("sanC", "--info")

        then:
        outputContains("Abbreviated task name 'sanC' matched 'sanityCheck'")
        result.assertTaskExecuted(":sanityCheck")
        result.assertTaskNotExecuted(":safetyCheck")
    }

}
