/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.operations

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Matchers

class BuildOperationExecutorParallelExecutionIntegrationTest extends AbstractIntegrationSpec {

    def "produces sensible error when there are failures both enqueueing and running operations" () {
        buildFile << """
            import org.gradle.internal.operations.BuildOperationExecutor
            import org.gradle.internal.operations.RunnableBuildOperation
            import org.gradle.internal.operations.BuildOperationContext
            import org.gradle.internal.progress.BuildOperationDescriptor
            import java.util.concurrent.CountDownLatch

            def startedLatch = new CountDownLatch(2)
            task causeErrors {
                doLast {
                    def buildOperationExecutor = services.get(BuildOperationExecutor)
                    buildOperationExecutor.runAll { queue ->
                        queue.add(new TestOperation(startedLatch))
                        queue.add(new TestOperation(startedLatch))
                        startedLatch.await()
                        throw new Exception("queue failure")
                    }
                }
            }

            class TestOperation implements RunnableBuildOperation {
                final CountDownLatch startedLatch

                TestOperation(CountDownLatch startedLatch) {
                    this.startedLatch = startedLatch
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("test operation");
                }

                @Override
                public void run(BuildOperationContext context) {
                    startedLatch.countDown()
                    throw new Exception("operation failure")
                }
            }
        """

        when:
        fails("causeErrors")

        then:
        failure.assertHasCause("There was a failure while populating the build operation queue:")
        failure.assertThatCause(Matchers.containsText("Multiple build operations failed"));
    }
}
