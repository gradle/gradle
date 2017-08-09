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

import org.fusesource.jansi.Ansi
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class BasicGroupedTaskLoggingFunctionalSpec extends AbstractConsoleGroupedTaskFunctionalTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "system out and err gets grouped together in rich console"() {
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

    @Issue("gradle/gradle#2038")
    def "tasks with no actions are not displayed in rich console"() {
        given:
        buildFile << "task log"

        when:
        succeeds('log')

        then:
        !result.groupedOutput.hasTask(':log')
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
        result.output.contains(styled("> Task :failing", Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD))
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
        result.output.contains(styled("> Task :succeeding", null, Ansi.Attribute.INTENSITY_BOLD))
    }

    private void assertOutputContains(GradleHandle gradle, String str) {
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput =~ /(?ms)$str/
        }
    }
}
