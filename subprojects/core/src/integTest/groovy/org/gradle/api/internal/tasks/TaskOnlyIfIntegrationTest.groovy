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

package org.gradle.api.internal.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType

class TaskOnlyIfIntegrationTest extends AbstractIntegrationSpec {

    def 'can use a Provider for #taskSkippingApi'() {
        given:
        buildFile << """
            def dependencyTask = tasks.register("dependency") {
                doFirst {
                    println("Dependency action")
                }
            }

            tasks.register("dependent") {
                it.${taskSkippingApi}(dependencyTask.map { false })
            }
        """

        when:
        run "dependent"

        then:
        result.assertTaskSkipped(":dependent")
        result.assertTaskExecuted(":dependency")

        where:
        taskSkippingApi << ["onlyIf", "setOnlyIf"]
    }

    def 'task skipped by #condition reports "#reason"'() {
        buildFile("""
            tasks.register("task") {
                $condition
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        assertTaskSkippedWithMessage(reason, ":task")

        where:
        condition                                                                           | reason
        "onlyIf('condition1') { false }"                                                    | "condition1"
        "onlyIf('...') { true }\nonlyIf('condition2') { false }"                            | "condition2"
        "onlyIf('...') { false }\nsetOnlyIf('condition3') { false }"                        | "condition3"
        "onlyIf { false }"                                                                  | "Task satisfies onlyIf closure"
        "onlyIf(new Spec<Task>() { boolean isSatisfiedBy(Task task) { return false } })"    | "Task satisfies onlyIf spec"
        "onlyIf(project.provider({ false }))"                                               | "Task satisfies onlyIf provider"
        "setOnlyIf { false }"                                                               | "Task satisfies onlyIf closure"
        "setOnlyIf(new Spec<Task>() { boolean isSatisfiedBy(Task task) { return false } })" | "Task satisfies onlyIf spec"
        "setOnlyIf(project.provider({ false }))"                                            | "Task satisfies onlyIf provider"
    }

    private void assertTaskSkippedWithMessage(
        String message,
        String taskPath
    ) {
        outputContains("Skipping task '$taskPath' as task onlyIf '$message' is false")
        operations.only(ExecuteTaskBuildOperationType, {
            if (taskPath != it.details.taskPath) {
                return false
            }
            it.result.skipReasonMessage == "'$message' not satisfied"
        })
    }

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)
}
