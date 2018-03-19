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

class RichConsoleBasicGroupedTaskLoggingFunctionalTest extends AbstractBasicGroupedTaskLoggingFunctionalTest {
    ConsoleOutput consoleType = ConsoleOutput.Rich

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

    def "configure project group header is printed red if configuration fails with additional failures"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("After Evaluate Failure...") }
            throw new RuntimeException('Config Failure...')
        """
        executer.withStacktraceDisabled()

        when:
        fails('failing')

        then:
        result.output.contains(styled("> Configure project :", Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD))
    }
}
