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
package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.logging.sink.OperationGroupingOutputEventListener
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ GradleContextualExecuter.parallel })
class LogGroupingFunctionalTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer(50000)

    def setup() {
        executer.withArgument('--parallel')
    }

    @Unroll
    def "multi-project build tasks logs are grouped in #consoleType console"() {
        given:
        settingsFile << "include '1', '2', '3'"

        buildFile << """
            subprojects {
                task log { doFirst { logger.quiet "Output from " + project.name } }
            }
        """

        when:
        executer.withArgument("--console=$consoleType")
        succeeds('log')

        then:
        result.groupedOutput.taskCount == 3
        result.groupedOutput.task(':1:log').output == "Output from 1"
        result.groupedOutput.task(':2:log').output == "Output from 2"
        result.groupedOutput.task(':3:log').output == "Output from 3"

        where:
        consoleType  | _
        'plain'      | _
        'rich'       | _
    }

    @Unroll
    def "logs at execution time are grouped in #consoleType console"() {
        given:
        buildFile << """
            task log {
                logger.quiet 'Logged during configuration'
                doFirst {
                    logger.quiet 'First line of text'
                    logger.quiet 'Second line of text'
                }
            }
        """

        when:
        executer.withArgument("--console=$consoleType")
        succeeds('log')

        then:
        result.groupedOutput.task(':log').output == "First line of text\nSecond line of text"

        where:
        consoleType  | _
        'plain'      | _
        'rich'       | _
    }

    @Unroll
    def "long-running task logs are flushed periodically in #consoleType console"() {
        server.start()

        given:
        buildFile << """
            task log {
                doLast {
                    logger.quiet 'Before'
                    new URL('${server.uri('running')}').text
                    logger.quiet 'After'
                }
            }
        """
        GradleHandle gradle = executer.withTasks('log').withArgument("--console=$consoleType").start()
        def serverHandler = server.expectAndBlock(server.resource('running'))

        when:
        serverHandler.waitForAllPendingCalls()
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput.contains('Before')
        }
        serverHandler.releaseAll()
        result = gradle.waitForFinish()

        then:
        result.groupedOutput.task(':log').outputs.size() == 1
        result.groupedOutput.task(':log').outputs[0] =~ /Before\n+After/

        cleanup:
        gradle?.waitForFinish()

        where:
        consoleType  | _
        'plain'      | _
        'rich'       | _
    }

    @Unroll
    def "long running task output correctly interleave with other tasks in parallel in #consoleType console"() {
        given:
        def sleepTime = OperationGroupingOutputEventListener.LONG_RUNNING_TASK_OUTPUT_FLUSH_PERIOD / 2 * 3
        buildFile << """import java.util.concurrent.Semaphore
            project(":a") {
                ext.lock = new Semaphore(0)
                task log {
                    doLast {
                        logger.quiet 'Before'
                        sleep($sleepTime)
                        lock.release()
                        project(':b').lock.acquire()
                        logger.quiet 'After'
                    }
                }
            }

            project(":b") {
                ext.lock = new Semaphore(0)

                task finalizer {
                    doLast {
                        lock.release()
                    }
                }

                task log {
                    finalizedBy finalizer
                    doLast {
                        project(':a').lock.acquire()
                        logger.quiet 'Interrupting output'
                    }
                }
            }


            task run {
                dependsOn project(':a').log, project(':b').log
            }
        """

        settingsFile << """
        include 'a', 'b'
        """

        when:
        succeeds('run')

        then:
        result.groupedOutput.task(':a:log').outputs == ['Before', 'After']
        result.groupedOutput.task(':b:log').output == 'Interrupting output'

        where:
        consoleType  | _
        'plain'      | _
        'rich'       | _
    }
}
