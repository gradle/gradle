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

import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.InspectsExecutedTasks

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.normalize
import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class GradleRunnerBuildFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {

    /*
        Note: these tests are very granular to ensure coverage for versions that
              don't support querying the output or tasks.
     */

    def "does not throw exception when build fails expectantly"() {
        given:
        buildScript """
            task helloWorld {
                doLast {
                    throw new GradleException('Expected exception')
                }
            }
        """

        when:
        runner('helloWorld').buildAndFail()

        then:
        noExceptionThrown()
    }

    @InspectsBuildOutput
    @InspectsExecutedTasks
    def "exposes result when build fails expectantly"() {
        given:
        buildScript """
            task helloWorld {
                doLast {
                    throw new GradleException('Expected exception')
                }
            }
        """

        when:
        def result = runner('helloWorld').buildAndFail()

        then:
        result.taskPaths(FAILED) == [':helloWorld']
        result.output.contains("Expected exception")
    }

    def "throws when build is expected to fail but does not"() {
        given:
        buildScript helloWorldTask()

        when:
        runner('helloWorld').buildAndFail()

        then:
        def t = thrown UnexpectedBuildSuccess
        t.buildResult != null
    }

    @InspectsBuildOutput
    @InspectsExecutedTasks
    def "exposes result when build is expected to fail but does not"() {
        given:
        buildScript helloWorldTask()

        when:
        def runner = runner('helloWorld')
        runner.buildAndFail()

        then:
        def t = thrown UnexpectedBuildSuccess
        def expectedOutput = """Task :helloWorld
Hello world!


BUILD SUCCESSFUL"""
        def expectedMessage = """Unexpected build execution success in ${testDirectory.canonicalPath} with arguments ${runner.arguments}

Output:"""

        def normalizedMessage = normalize(t.message)
        normalizedMessage.startsWith(expectedMessage)
        normalizedMessage.contains(expectedOutput)
        normalize(t.buildResult.output.trim()).startsWith(expectedOutput)
        t.buildResult.taskPaths(SUCCESS) == [':helloWorld']
    }

    def "throws when build is expected to succeed but fails"() {
        given:
        buildScript """
            task helloWorld {
                doLast {
                    throw new GradleException('Unexpected exception')
                }
            }
        """

        when:
        runner('helloWorld').build()

        then:
        def t = thrown UnexpectedBuildFailure
        t.buildResult != null
    }

    @InspectsExecutedTasks
    @InspectsBuildOutput
    def "exposes result with build is expected to succeed but fails"() {
        given:
        buildScript """
            task helloWorld {
                doLast {
                    throw new GradleException('Unexpected exception')
                }
            }
        """

        when:
        def runner = runner('helloWorld')
        runner.build()

        then:
        UnexpectedBuildFailure t = thrown(UnexpectedBuildFailure)
        String expectedOutput = """Task :helloWorld FAILED

FAILURE: Build failed with an exception.

* Where:
Build file '${buildFile.canonicalPath}' line: 4

* What went wrong:
Execution failed for task ':helloWorld'.
> Unexpected exception
"""
        String expectedMessage = """Unexpected build execution failure in ${testDirectory.canonicalPath} with arguments ${runner.arguments}"""

        def normalizedMessage = normalize(t.message)
        normalizedMessage.startsWith(expectedMessage)
        def result = t.buildResult
        normalize(result.output.trim()).startsWith(expectedOutput)
        result.taskPaths(FAILED) == [':helloWorld']
    }
}
