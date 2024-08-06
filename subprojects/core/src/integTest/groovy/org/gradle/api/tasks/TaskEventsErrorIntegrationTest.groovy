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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache


@UnsupportedWithConfigurationCache(because = "uses task event listener")
class TaskEventsErrorIntegrationTest extends AbstractIntegrationSpec {
    def "reports task as failed when beforeTask closure fails"() {
        when:
        buildFile << """
    gradle.taskGraph.beforeTask {
        throw new RuntimeException("beforeTask failure")
    }
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("Execution failed for task ':test'.")
                .assertHasCause("beforeTask failure")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(3)
    }

    def "reports task as failed when afterTask closure fails"() {
        when:
        buildFile << """
    gradle.taskGraph.afterTask {
        throw new RuntimeException("afterTask failure")
    }
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("Execution failed for task ':test'.")
                .assertHasCause("afterTask failure")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(3)
    }

    def "invokes afterTask action after task action fails"() {
        when:
        buildFile << """
    gradle.taskGraph.afterTask {
        println "afterTask action"
    }
    gradle.taskGraph.beforeTask {
        println "beforeTask action"
    }
    task test {
        doLast {
            println "task action"
            throw new RuntimeException("broken")
        }
    }
"""
        then:
        fails('test')
        result.groupedOutput.task(":test").output == """beforeTask action
task action
afterTask action"""
    }

    def "reports task as failed when task action and afterTask closure fails"() {
        when:
        buildFile << """
    gradle.taskGraph.afterTask {
        throw new RuntimeException("afterTask failure")
    }
    task test {
        doLast {
            throw new RuntimeException("task action failure")
        }
    }
"""
        then:
        fails('test')
        failure.assertHasDescription("Execution failed for task ':test'.")
                .assertHasCause("afterTask failure")
                .assertHasCause("task action failure")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(7)
    }
}
