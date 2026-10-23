/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39544")
class WorkActionConsoleIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "shows a line with the simple class name for each running work action of a task"() {
        given:
        server.start()
        buildFile << workActions("'a', 'b'")
        def calls = server.expectConcurrentAndBlock("a", "b")

        when:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        def running = executer.withArguments("--max-workers=2").withTasks('work').start()
        calls.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(running, "> :work > Executing work action CallAction")
        }

        cleanup:
        calls?.releaseAll()
        running?.waitForFinish()
    }

    def "does not show a line for the single work action of a task"() {
        given:
        server.start()
        buildFile << workActions("'a'")
        def call = server.expectAndBlock("a")

        when:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        def running = executer.withTasks('work').start()
        call.waitForAllPendingCalls()

        then:
        // The work action logs before it blocks, so the lines drawn after its log come after its start
        ConcurrentTestUtil.poll {
            def output = running.standardOutput
            assert output.contains("Calling a")
            assert output.substring(output.indexOf("Calling a")).contains(":work")
        }
        !running.standardOutput.contains("Executing work action")

        cleanup:
        call?.releaseAll()
        running?.waitForFinish()
    }

    private String workActions(String resources) {
        """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor

            interface CallParameters extends WorkParameters {
                Property<String> getResource()
            }

            class Actions {
                abstract static class CallAction implements WorkAction<CallParameters> {
                    void execute() {
                        println "Calling \${parameters.resource.get()}"
                        ${server.callFromBuildUsingExpression("parameters.resource.get()")}
                    }
                }
            }

            abstract class Work extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void run() {
                    def queue = workerExecutor.noIsolation()
                    [${resources}].each { name ->
                        queue.submit(Actions.CallAction) { it.resource = name }
                    }
                }
            }

            tasks.register('work', Work)
        """
    }
}
