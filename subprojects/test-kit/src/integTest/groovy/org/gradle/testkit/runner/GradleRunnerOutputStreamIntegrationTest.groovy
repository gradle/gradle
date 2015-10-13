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

import static org.gradle.testkit.runner.TaskOutcome.*

class GradleRunnerOutputStreamIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "can specify System.out and System.err as output streams"() {
        given:
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutputStream(System.out)
        gradleRunner.withStandardErrorStream(System.err)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardError.contains('Some failure')
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    def "build result standard output and error capture the same output as output streams provided by user"() {
        given:
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        ByteArrayOutputStream standardError = new ByteArrayOutputStream()
        buildFile << helloWorldWithStandardOutputAndError()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        gradleRunner.withStandardOutputStream(standardOutput)
        gradleRunner.withStandardErrorStream(standardError)
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardError.contains('Some failure')
        result.standardOutput == standardOutput.toString()
        result.standardError == standardError.toString()
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    private String helloWorldWithStandardOutputAndError() {
        """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    System.err.println 'Some failure'
                }
            }
        """
    }
}
