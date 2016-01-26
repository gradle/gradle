/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.annotations.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.annotations.InspectsExecutedTasks

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@InspectsExecutedTasks
class GradleRunnerArgumentsIntegrationTest extends GradleRunnerIntegrationTest {

    def "can execute build without specifying any arguments"() {
        when:
        def result = runner().build()

        then:
        result.task(":help")
    }

    @InspectsBuildOutput
    def "execute build for multiple tasks"() {
        given:
        buildFile << helloWorldTask()
        buildFile << """
            task byeWorld {
                doLast {
                    println 'Bye world!'
                }
            }
        """

        when:
        def result = runner('helloWorld', 'byeWorld').build()

        then:
        result.output.contains('Hello world!')
        result.output.contains('Bye world!')
        result.taskPaths(SUCCESS) == [':helloWorld', ':byeWorld']
    }

    @InspectsBuildOutput
    def "can provide arguments for build execution"() {
        given:
        final String debugMessage = 'Some debug message'
        final String infoMessage = 'My property: ${project.hasProperty("myProp") ? project.getProperty("myProp") : null}'
        final String quietMessage = 'Log in any case'

        buildFile << """
            task helloWorld {
                doLast {
                    logger.debug '$debugMessage'
                    logger.info '$infoMessage'
                    logger.quiet '$quietMessage'
                }
            }
        """

        when:
        def result = runner(['helloWorld'] + arguments).build()

        then:
        result.output.contains(debugMessage) == hasDebugMessage
        result.output.contains(infoMessage) == hasInfoMessage
        result.output.contains(quietMessage) == hasQuietMessage
        result.taskPaths(SUCCESS) == [':helloWorld']

        where:
        arguments                | hasDebugMessage | hasInfoMessage | hasQuietMessage
        []                       | false           | false          | true
        ['-PmyProp=hello']       | false           | false          | true
        ['-d', '-PmyProp=hello'] | true            | true           | true
        ['-i', '-PmyProp=hello'] | false           | true           | true
    }

}
