/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.equalTo

class CancellationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    BuildOperationsFixture operations = new BuildOperationsFixture(executer, temporaryFolder)

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "task operations are closed even when interrupting the execution workers"() {
        def parallelTaskCount = 5
        buildFile << """
          ext.workerThreads = new HashSet<>()
          ext.latch = new java.util.concurrent.CountDownLatch(${parallelTaskCount} )
          tasks.register('interrupting') {
            doFirst {
              rootProject.ext.latch.await()
              rootProject.ext.workerThreads.each {
                if (it.name.contains('included builds')) {
                  it.interrupt()
                }
              }
              // We fail this task so the build fails consistently
              throw new RuntimeException("Interrupting done")
            }
          }
        """
        parallelTaskCount.times { project ->
            settingsFile << """include 'a$project'\n"""
            file("a$project/build.gradle") << """
                tasks.register('parallelTask') {
                    doFirst {
                        println 'executing a parallelTask in thread ' + Thread.currentThread()
                        rootProject.ext.workerThreads << Thread.currentThread()
                        rootProject.ext.latch.countDown()
                        Thread.sleep(1000)
                    }
                }
            """
        }

        when:
        fails('parallelTask', '--parallel', ':interrupting', "--console=plain", "--max-workers=${(parallelTaskCount / 2) + 2 as int}", '--continue')

        then:
        operations.danglingChildren.empty
        failure.assertThatAllDescriptions(anyOf(
            equalTo("Execution failed for task ':interrupting'."),
            equalTo("Execution failed for task ':a0:parallelTask'."),
            equalTo("Execution failed for task ':a1:parallelTask'."),
            equalTo("Execution failed for task ':a2:parallelTask'."),
            equalTo("Execution failed for task ':a3:parallelTask'."),
            equalTo("Execution failed for task ':a4:parallelTask'.")
        ))
    }
}
