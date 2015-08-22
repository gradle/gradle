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

import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerArgumentsIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "can execute build without specifying any arguments"() {
        when:
        GradleRunner gradleRunner = runner()
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':help')
        !result.standardError
        result.tasks.collect { it.path } == [':help']
        result.taskPaths(SUCCESS) == [':help']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

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
        GradleRunner gradleRunner = runner('helloWorld', 'byeWorld')
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardOutput.contains(':byeWorld')
        result.standardOutput.contains('Bye world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld', ':byeWorld']
        result.taskPaths(SUCCESS) == [':helloWorld', ':byeWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    @Unroll
    def "can provide arguments #arguments for build execution"() {
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
        GradleRunner gradleRunner = runner(['helloWorld'] + arguments)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains(debugMessage) == hasDebugMessage
        result.standardOutput.contains(infoMessage) == hasInfoMessage
        result.standardOutput.contains(quietMessage) == hasQuietMessage
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty

        where:
        arguments                | hasDebugMessage | hasInfoMessage | hasQuietMessage
        []                       | false           | false          | true
        ['-PmyProp=hello']       | false           | false          | true
        ['-d', '-PmyProp=hello'] | true            | true           | true
        ['-i', '-PmyProp=hello'] | false           | true           | true
    }

}
