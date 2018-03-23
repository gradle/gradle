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

package org.gradle.internal.logging.console.taskgrouping

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.sink.GroupingProgressLogEventGenerator
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

abstract class AbstractBasicGroupedTaskLoggingFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    private static long sleepTimeout = GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT / 2 * 3

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "multi-project build tasks logs are grouped"() {
        given:
        settingsFile << "include '1', '2', '3'"

        buildFile << """
            subprojects {
                task log { doFirst { logger.quiet "Output from " + project.name } }
            }
        """

        when:
        succeeds('log')

        then:
        result.groupedOutput.taskCount == 3
        result.groupedOutput.task(':1:log').output == "Output from 1"
        result.groupedOutput.task(':2:log').output == "Output from 2"
        result.groupedOutput.task(':3:log').output == "Output from 3"
    }

    def "logs at execution time are grouped"() {
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
        succeeds('log')

        then:
        result.groupedOutput.task(':log').output == "First line of text\nSecond line of text"
    }

    def "system out and err gets grouped"() {
        given:
        buildFile << """
            task log {
                logger.quiet 'Logged during configuration'
                doFirst {
                    System.out.println("Standard out")
                    System.err.println("Standard err")
                }
            }
        """

        when:
        succeeds('log')

        then:
        result.groupedOutput.task(':log').output == "Standard out\nStandard err"
    }

    def "grouped output is displayed for failed tasks"() {
        given:
        buildFile << """
            task log {
                logger.quiet 'Logged during configuration'
                doFirst {
                    logger.quiet 'First line of text'
                    logger.quiet ''
                    logger.quiet ''
                    logger.quiet 'Last line of text'
                    throw new GradleException('Forced failure')
                }
            }
        """
        when:
        fails('log')

        then:
        result.groupedOutput.task(':log').output =~ /First line of text\n{3,}Last line of text/
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "long running task output correctly interleave with other tasks in parallel"() {
        given:
        buildFile << """import java.util.concurrent.Semaphore
            project(":a") {
                ext.lock = new Semaphore(0)
                task log {
                    doLast {
                        logger.quiet 'Before'
                        sleep($sleepTimeout)
                        lock.release()
                        project(':b').lock.acquire()
                        logger.quiet 'After'
                        assert false
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
        executer.withArguments("--parallel")
        fails('run')

        then:
        result.groupedOutput.task(':a:log').outputs.findAll { !it.isAllWhitespace() } == ['Before', 'After']
        result.groupedOutput.task(':a:log').outcome == 'FAILED'
        result.groupedOutput.task(':b:log').output == 'Interrupting output'
        result.groupedOutput.task(':b:log').outcome == null
    }

    def "long running task output are flushed after delay"() {
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
        GradleHandle gradle = executer.withTasks('log').start()
        def handle = server.expectAndBlock(server.resource('running'))

        when:
        handle.waitForAllPendingCalls()
        assertOutputContains(gradle, "Before${SystemProperties.instance.lineSeparator}")
        handle.releaseAll()
        result = gradle.waitForFinish()

        then:
        result.groupedOutput.task(':log').outputs.size() == 1
        result.groupedOutput.task(':log').outputs[0] =~ /Before\n+After/

        cleanup:
        gradle?.waitForFinish()
    }

    private static void assertOutputContains(GradleHandle gradle, String str) {
        ConcurrentTestUtil.poll(sleepTimeout/1000 as double) {
            assert gradle.standardOutput =~ /(?ms)$str/
        }
    }
}
