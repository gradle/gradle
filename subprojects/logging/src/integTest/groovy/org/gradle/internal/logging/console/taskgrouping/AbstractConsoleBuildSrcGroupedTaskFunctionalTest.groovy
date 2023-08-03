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

abstract class AbstractConsoleBuildSrcGroupedTaskFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {

    private static final String HELLO_WORLD_MESSAGE = 'Hello world'
    private static final String BYE_WORLD_MESSAGE = 'Bye world'

    def "can group task output in buildSrc"() {
        file('buildSrc/build.gradle') << """
            task helloWorld {
                doLast {
                    logger.quiet '$HELLO_WORLD_MESSAGE'
                }
            }

            jar.dependsOn helloWorld
        """
        buildFile << """
            task byeWorld {
                doLast {
                    logger.quiet '$BYE_WORLD_MESSAGE'
                }
            }
        """

        when:
        succeeds('byeWorld')

        then:
        result.groupedOutput.task(':buildSrc:helloWorld').output.contains(HELLO_WORLD_MESSAGE)
        result.groupedOutput.task(':byeWorld').output.contains(BYE_WORLD_MESSAGE)
    }
}
