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

package org.gradle.testkit.functional

class GradleRunnerExpectedFailureIntegrationTest extends AbstractGradleRunnerIntegrationTest {
    def "execute build for expected failure"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    throw new GradleException('Expected exception')
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.fails()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld FAILED')
        result.standardError.contains("Execution failed for task ':helloWorld'")
        result.standardError.contains('Expected exception')
        result.executedTasks == [':helloWorld']
        result.skippedTasks == [':helloWorld']
    }

    def "execute build for expected failure but succeeds"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.fails()

        then:
        Throwable t = thrown(UnexpectedBuildSuccess)
        t.message.contains('Unexpected build execution success')
    }
}
