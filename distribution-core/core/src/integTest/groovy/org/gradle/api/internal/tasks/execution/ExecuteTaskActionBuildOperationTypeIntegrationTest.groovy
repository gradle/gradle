/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ExecuteTaskActionBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits operation for each task action execution"() {
        when:
        buildScript """
            task t {
                doLast {}
                doLast {}
            }
        """
        succeeds "t"

        then:
        def actions = operations.all(ExecuteTaskActionBuildOperationType)
        actions.size() == 2

        and:
        def task = operations.first(ExecuteTaskBuildOperationType) {
            it.details.taskPath == ":t"
        }
        actions.every { operations.parentsOf(it).contains(task) }
    }

    def "emits operation result for failed task action execution"() {
        when:
        buildScript """
            task t {
                doLast {
                    throw new RuntimeException("fail")
                }
            }
        """
        fails "t"

        then:
        operations.first(ExecuteTaskActionBuildOperationType).failure == "java.lang.RuntimeException: fail"
    }

    def "does not emit operation for non-executed task action"() {
        when:
        buildScript """
            task t {
                doLast {
                    throw new RuntimeException("fail")
                }
                doLast {}
            }
        """
        fails "t"

        then:
        operations.all(ExecuteTaskActionBuildOperationType).size() == 1
    }

}
