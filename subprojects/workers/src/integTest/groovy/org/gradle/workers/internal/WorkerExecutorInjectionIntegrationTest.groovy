/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.api.Project
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import spock.lang.Unroll

@IntegrationTestTimeout(120)
class WorkerExecutorInjectionIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Unroll
    def "workers cannot inject #forbiddenType"() {
        buildFile << """
            ${getRunnableInjecting("IsolationMode.NONE", forbiddenType.name)}
            task runInWorker(type: InjectingWorkerTask)
        """.stripIndent()

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Could not create an instance of type InjectingRunnable.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of $forbiddenType, or no service of type $forbiddenType")

        where:
        forbiddenType << [Project]
    }

    String getRunnableInjecting(String isolationMode, String injectedClass) {
        return """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            class InjectingRunnable implements Runnable {
                
                @Inject
                public InjectingRunnable($injectedClass injected) {
                }

                public void run() {
                }
            }

            class InjectingWorkerTask extends DefaultTask {

                WorkerExecutor executor

                @Inject
                InjectingWorkerTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                public void runInWorker() {
                    executor.submit(InjectingRunnable) {
                        isolationMode = $isolationMode
                    }
                }
            }
        """.stripIndent()
    }
}
