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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Matchers
import spock.lang.Issue

class BuildOperationExecutorIntegrationTest extends AbstractIntegrationSpec {

    def "produces sensible error when there are failures both enqueuing and running operations" () {
        if (JavaVersion.current().isJava9Compatible() && GradleContextualExecuter.isConfigCache()) {
            // For java.util.concurrent.CountDownLatch being serialized reflectively by configuration cache
            executer.withArgument('-Dorg.gradle.jvmargs=--add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED')
        }

        buildFile << """
            import org.gradle.internal.operations.BuildOperationExecutor
            import org.gradle.internal.operations.RunnableBuildOperation
            import org.gradle.internal.operations.BuildOperationContext
            import org.gradle.internal.operations.BuildOperationDescriptor
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

    // This is the current behavior:
    // We need to make sure that the build operation ids of nested builds started by a GradleBuild task do not overlap.
    // Since we currently have no specific scope for "one build and the builds it started via GradleBuild tasks", we use the global scope.
    @Issue("https://github.com/gradle/gradle/issues/2622")
    def "build operations have unique ids within the global scope"() {
        when:
        settingsFile << ""
        buildFile << """
            import org.gradle.internal.operations.BuildOperationExecutor

            task checkOpId() {
                def buildOperationExecutor = gradle.services.get(BuildOperationExecutor)
                doLast() {
                    file(resultFile) << buildOperationExecutor.currentOperation.id
                }
            }

            task build1(type: GradleBuild) {
                tasks = ['checkOpId']
                startParameter.projectProperties = [resultFile: 'build1result.txt']
            }
            task build2(type: GradleBuild) {
                tasks = ['checkOpId']
                startParameter.projectProperties = [resultFile: 'build2result.txt']
                buildName = 'changed'
            }
        """
        succeeds "build1", "build2"

        then:
        file("build1result.txt").text != file("build2result.txt").text
    }
}
