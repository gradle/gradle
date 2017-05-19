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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import spock.lang.Issue

class BasicGroupedTaskLoggingFunctionalSpec extends AbstractConsoleFunctionalSpec {

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

    @Issue("gradle/gradle#2038")
    def "tasks with no actions are not displayed"() {
        given:
        buildFile << "task log"

        when:
        succeeds('log')

        then:
        result.groupedOutput.task(':log').output.isEmpty()
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
        executer.withStackTraceChecksDisabled()
        fails('log')

        then:
        result.groupedOutput.task(':log').output == "First line of text\n\n\nLast line of text"
    }
}
