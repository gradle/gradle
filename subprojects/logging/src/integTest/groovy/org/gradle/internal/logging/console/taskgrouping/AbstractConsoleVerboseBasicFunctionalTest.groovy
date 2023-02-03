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

import static org.gradle.api.logging.configuration.ConsoleOutput.Auto
import static org.gradle.api.logging.configuration.ConsoleOutput.Plain
import static org.gradle.api.logging.configuration.ConsoleOutput.Verbose

abstract class AbstractConsoleVerboseBasicFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    def "can have verbose task output according to --console"() {
        given:
        def helloWorldMessage= 'Hello world'
        def byeWorldMessage= 'Bye world'
        def hasSilenceTaskOutput = consoleType in [Verbose, Plain] || consoleType == Auto && !consoleAttachment.stdoutAttached

        buildFile << """
            task helloWorld {
                doLast {
                    logger.quiet '$helloWorldMessage'
                }
            }
            task byeWorld {
                doLast {
                    logger.quiet '$byeWorldMessage'
                }
            }
            
            task silence {}
            
            task all {
                dependsOn helloWorld, byeWorld, silence
            }
        """
        when:
        succeeds('all')

        then:
        result.groupedOutput.task(':helloWorld').output == helloWorldMessage
        result.groupedOutput.task(':byeWorld').output == byeWorldMessage
        hasSilenceTaskOutput == result.groupedOutput.hasTask(':silence')
    }

    def 'failed task result can be rendered'() {
        given:
        buildFile << '''
            task myFailure {
                doLast {
                    assert false
                }
            }
        '''

        when:
        fails('myFailure')

        then:
        result.groupedOutput.task(':myFailure').outcome == 'FAILED'
    }

}
