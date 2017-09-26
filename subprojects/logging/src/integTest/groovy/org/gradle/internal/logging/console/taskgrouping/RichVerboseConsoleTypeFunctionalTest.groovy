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

import spock.lang.Unroll

import static org.gradle.api.logging.configuration.ConsoleOutput.Rich
import static org.gradle.api.logging.configuration.ConsoleOutput.Verbose

class RichVerboseConsoleTypeFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    private static final String HELLO_WORLD_MESSAGE = 'Hello world'
    private static final String BYE_WORLD_MESSAGE = 'Bye world'

    def setup() {
        buildFile << """
            task helloWorld {
                doLast {
                    logger.quiet '$HELLO_WORLD_MESSAGE'
                }
            }
            task byeWorld {
                doLast {
                    logger.quiet '$BYE_WORLD_MESSAGE'
                }
            }
            
            task silence {}
            
            task all {
                dependsOn helloWorld, byeWorld, silence
            }
        """
    }

    @Unroll
    def "can have verbose task output according to --console"() {
        when:
        executer.withConsole(mode)
        succeeds('all')

        then:
        result.groupedOutput.task(':helloWorld').output == HELLO_WORLD_MESSAGE
        result.groupedOutput.task(':byeWorld').output == BYE_WORLD_MESSAGE
        hasSilenceTaskOutput == result.groupedOutput.hasTask(':silence')

        where:
        mode    | hasSilenceTaskOutput
        Rich    | false
        Verbose | true
    }
}
