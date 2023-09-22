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

import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.logging.sink.GroupingProgressLogEventGenerator
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

abstract class AbstractBasicGroupedTaskLoggingFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    private static long sleepTimeout = GroupingProgressLogEventGenerator.HIGH_WATERMARK_FLUSH_TIMEOUT / 2 * 3

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "multi-project build tasks logs are grouped"() {
        server.start()

        given:
        settingsFile << "include '1', '2', '3'"

        buildFile << """
            subprojects {
                task log {
                    def projectName = project.name
                    doFirst {
                        logger.error "Error from " + projectName
                        logger.quiet "Output from " + projectName
                        new java.net.URL("${server.uri}/log" + projectName).openConnection().getContentLength()
                        logger.quiet "Done with " + projectName
                        logger.error "Done with " + projectName
                    }
                }
            }
        """

        when:
        server.expectConcurrent("log1", "log2", "log3")
        executer.withArgument("--parallel")
        // run build in another process to avoid interference from logging from test fixtures
        result = executer.withTasks("log").start().waitForFinish()

        then:
        result.groupedOutput.taskCount == 3
        if (errorsShouldAppearOnStdout()) {
            // both stdout and stderr are attached to the console
            assert result.groupedOutput.task(':1:log').output == "Error from 1\nOutput from 1\nDone with 1\nDone with 1"
            assert result.groupedOutput.task(':2:log').output == "Error from 2\nOutput from 2\nDone with 2\nDone with 2"
            assert result.groupedOutput.task(':3:log').output == "Error from 3\nOutput from 3\nDone with 3\nDone with 3"
        } else {
            assert result.groupedOutput.task(':1:log').output == "Output from 1\nDone with 1"
            assert result.groupedOutput.task(':2:log').output == "Output from 2\nDone with 2"
            assert result.groupedOutput.task(':3:log').output == "Output from 3\nDone with 3"

            ['Error from 1', 'Done with 1', 'Error from 2', 'Done with 2', 'Error from 3', 'Done with 3'].each(result.&assertHasErrorOutput)
        }
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
                    System.out.println("Standard out 1")
                    System.err.println("Standard err 1")
                    System.out.println("Standard out 2")
                    System.err.println("Standard err 2")
                }
            }
        """

        when:
        succeeds('log')

        then:
        if (errorsShouldAppearOnStdout()) {
            result.groupedOutput.task(':log').output == "Standard out 1\nStandard err 1\nStandard out 2\nStandard err 2"
        } else {
            result.groupedOutput.task(':log').output == "Standard out 1\nStandard out 2\n"
            result.assertHasErrorOutput("Standard err 1\nStandard err 2")
        }
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

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "long running task output correctly interleave with other tasks in parallel"() {
        given:
        server.start()

        buildFile << """
            project(":a") {
                task log {
                    doLast {
                        logger.quiet 'Before'
                        ${callFromBuild('a-waiting')}
                        logger.quiet 'After'
                        assert false
                    }
                }
            }

            project(":b") {
                task log {
                    doLast {
                        ${callFromBuild('b-waiting')}
                        logger.quiet 'Interrupting output'
                        ${callFromBuild('b-done')}
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
        def waiting = server.expectConcurrentAndBlock("a-waiting", "b-waiting")
        def done = server.expectAndBlock("b-done")
        def build = executer.withArguments("--parallel").withTasks("run").start()

        waiting.waitForAllPendingCalls()
        waiting.release("b-waiting")
        done.waitForAllPendingCalls()
        done.releaseAll()
        waiting.releaseAll()
        result = build.waitForFailure()

        then:
        result.groupedOutput.task(':a:log').output == 'Before\nAfter'
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
                    ${callFromBuild('running')}
                    logger.quiet 'After'
                }
            }
        """

        def handle = server.expectAndBlock(server.get('running'))
        def gradle = executer.withTasks('log').start()

        when:
        handle.waitForAllPendingCalls()
        assertOutputContains(gradle, "Before")
        handle.releaseAll()
        result = gradle.waitForFinish()

        then:
        result.groupedOutput.task(':log').output == 'Before\nAfter'

        cleanup:
        gradle?.waitForFinish()
    }

    String callFromBuild(String name) {
        return "new URL('${server.uri}/${name}').text"
    }

    protected static void assertOutputContains(GradleHandle gradle, String str) {
        ConcurrentTestUtil.poll(sleepTimeout / 1000 as double) {
            assert gradle.standardOutput =~ /(?ms)$str/
        }
    }
}
