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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(60)
class WorkerExecutorParallelIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule BlockingHttpServer blockingHttpServer = new BlockingHttpServer()

    def setup() {
        blockingHttpServer.start()
        withParallelRunnableInBuildScript()
    }

    @Unroll
    def "multiple work items can be executed in parallel (wait for results: #waitForResults)"() {
        given:
        buildFile << """
            task parallelWorkTask(type: WorkTask) {
                itemsOfWork = 3
                waitForResults = ${waitForResults}
            }
        """
        blockingHttpServer.expectConcurrentExecution("workItem0", "workItem1", "workItem2")

        expect:
        args("--max-workers=4")
        succeeds("parallelWorkTask")

        where:
        waitForResults << [ true, false ]
    }

    String getTaskTypeUsingWorkerDaemon() {
        withParameterClassInBuildSrc()

        return """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            @ParallelizableTask
            class WorkTask extends DefaultTask {
                def additionalForkOptions = {}
                def runnableClass = TestParallelRunnable.class
                def additionalClasspath = project.files()
                def itemsOfWork = 1
                def waitForResults = true

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    def results = []
                    itemsOfWork.times { itemNum ->
                        results += workerExecutor.submit(runnableClass) { config ->
                            config.forkOptions(additionalForkOptions)
                            config.classpath(additionalClasspath)
                            config.params = [ "workItem\${itemNum}".toString() ]
                        }
                    }
                    if (waitForResults) {
                        workerExecutor.await(results)
                    }
                }
            }
        """
    }

    def getParallelRunnable() {
        return """
            import java.net.URI

            public class TestParallelRunnable implements Runnable {
                final String itemName 

                public TestParallelRunnable(String itemName) {
                    this.itemName = itemName
                }
                
                public void run() {
                    System.out.println("Running \${itemName}...")
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${itemName}", null, null).toURL().text
                }
            }
        """
    }

    def withParallelRunnableInBuildScript() {
        buildFile << """
            $parallelRunnable
        """
    }
}
