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

package org.gradle.api.internal.tasks.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class ResolveTaskMutationsBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits operation for task execution"() {
        when:
        buildScript """
            task t {}
        """
        succeeds "t"

        then:
        assertResolveTaskMutationsBuildOperationEmitted(":t")
    }

    def "emits operation for failed task execution"() {
        when:
        buildScript """
            task t {
                doLast {
                    throw new RuntimeException("BOOM!")
                }
            }
        """
        fails "t"

        then:
        assertResolveTaskMutationsBuildOperationEmitted(":t")
    }

    def "emits operation when resolving mutations fails"() {
        when:
        buildScript """
            task t {
                outputs.files({ -> throw new RuntimeException("BOOM!") })
            }
        """
        fails "t"

        then:
        if (GradleContextualExecuter.configCache) {
            // Configuration caching resolves the outputs when storing to the configuration cache
            // This fails already, so we don't even get to resolving the mutations.
            failureDescriptionStartsWith("BOOM!")
        } else {
            assertResolveTaskMutationsBuildOperationEmitted(":t")
            failureDescriptionStartsWith("Execution failed for task ':t'.")
        }
    }

    void assertResolveTaskMutationsBuildOperationEmitted(String taskPath) {
        def op = operations.first(ResolveTaskMutationsBuildOperationType) {
            it.details.taskPath == taskPath
        }
        op.details.buildPath == ":"
        op.details.taskId != null
    }
}
