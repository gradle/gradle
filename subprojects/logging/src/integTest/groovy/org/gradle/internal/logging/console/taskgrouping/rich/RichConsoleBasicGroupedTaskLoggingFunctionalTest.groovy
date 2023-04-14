/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping.rich

import org.fusesource.jansi.Ansi
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.logging.console.taskgrouping.AbstractBasicGroupedTaskLoggingFunctionalTest
import spock.lang.Issue

@SuppressWarnings("IntegrationTestFixtures")
class RichConsoleBasicGroupedTaskLoggingFunctionalTest extends AbstractBasicGroupedTaskLoggingFunctionalTest {
    ConsoleOutput consoleType = ConsoleOutput.Rich

    private final StyledOutput failingTask = styled(Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD).text("> Task :failing").styled(null).text(" FAILED").off()
    private final StyledOutput succeedingTask = styled(Ansi.Attribute.INTENSITY_BOLD).text("> Task :succeeding").off()
    private final StyledOutput configuringProject = styled(Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD).text("> Configure project :").off()

    @Issue("gradle/gradle#2038")
    def "tasks with no actions are not displayed"() {
        given:
        buildFile << "task log"

        when:
        succeeds('log')

        then:
        !result.groupedOutput.hasTask(':log')
    }

    def "group header is printed red if task failed"() {
        given:
        buildFile << """
            task failing { doFirst {
                logger.quiet 'hello'
                throw new RuntimeException('Failure...')
            } }
        """

        when:
        fails('failing')

        then:
        result.groupedOutput.task(':failing').output == 'hello'
        result.formattedOutput.contains(failingTask.output)
    }

    def "group header is printed red if task failed and there is no output"() {
        given:
        buildFile << """
            task failing { doFirst {
                throw new RuntimeException('Failure...')
            } }
        """

        when:
        fails('failing')

        then:
        result.formattedOutput.contains(failingTask.output)
    }

    def "group header is printed white if task succeeds"() {
        given:
        buildFile << """
            task succeeding { doFirst {
                logger.quiet 'hello'
            } }
        """

        when:
        succeeds('succeeding')

        then:
        result.formattedOutput.contains(succeedingTask.output)
    }

    def "configure project group header is printed red if configuration fails with additional failures"() {
        given:
        buildFile << """
            afterEvaluate {
                println "executing after evaluate..."
                throw new RuntimeException("After Evaluate Failure...")
            }
            throw new RuntimeException('Config Failure...')
        """

        when:
        fails('failing')

        then:
        result.formattedOutput.contains(configuringProject.output)
        failure.assertHasFailures(2)
    }

    def "tasks that complete without output do not break up other task output"() {
        server.start()

        given:
        settingsFile << "include ':a', ':b'"
        buildFile << """
            project(':a') {
                task longRunning {
                    doLast {
                        println "longRunning has started..."
                        ${callFromBuild('longRunningStart')}
                        ${callFromBuild('longRunningFinish')}
                        println "longRunning has finished..."
                    }
                }
            }
            project(':b') {
                task task1 {
                    doLast {
                        ${callFromBuild('task1')}
                    }
                }
                task task2 {
                    dependsOn task1
                    doLast {
                        ${callFromBuild('task2')}
                    }
                }
            }
        """

        when:
        def handle = server.expectConcurrentAndBlock('longRunningStart', 'task1')
        def gradle = executer.withArgument('--parallel').withTasks('longRunning', 'task2').start()

        then:
        handle.waitForAllPendingCalls()
        assertOutputContains(gradle, "longRunning has started...")

        when:
        server.expectConcurrent('longRunningFinish', 'task2')
        handle.releaseAll()
        result = gradle.waitForFinish()

        then:
        result.groupedOutput.task(':a:longRunning').outputs.size() == 1
        result.groupedOutput.task(':a:longRunning').output == "longRunning has started...\nlongRunning has finished..."

        cleanup:
        gradle?.waitForFinish()
    }
}
