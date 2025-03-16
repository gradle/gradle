/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Matchers

class TestVerificationFailureHandlingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // create single project with java plugin
        buildFile << """
            plugins {
              id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
        """
    }

    def 'task does not execute when it has a test task output dependency and VM exits unexpectedly'() {
        given:
        withFatalTestExecutionError()
        withCustomTaskInputFromTestTaskOutput()

        expect:
        fails('customTask')
        result.assertTaskExecuted(':test')
        result.assertTaskNotExecuted(':customTask')
        assertFatalTestExecutionError()
    }

    def 'task does not execute when it has a test task output dependency with failing test(s)'() {
        given:
        withTestVerificationFailure()
        withCustomTaskInputFromTestTaskOutput()

        expect:
        fails('customTask')
        result.assertTaskExecuted(':test')
        result.assertTaskNotExecuted(':customTask')
        failure.assertTestsFailed()
    }

    def 'task executes when it has a test task output dependency with failing test(s) and --continue'() {
        given:
        withTestVerificationFailure()
        withCustomTaskInputFromTestTaskOutput()

        expect:
        fails('customTask', '--continue')
        result.assertTaskExecuted(':test')
        result.assertTaskExecuted(':customTask')
        failure.assertTestsFailed()
    }

    def 'task does not execute when it dependsOn test with failing test(s) and --continue'() {
        given:
        withTestVerificationFailure()
        withCustomTaskDependsOnTestTask()

        expect:
        fails('customTask', '--continue')
        result.assertTaskExecuted(':test')
        result.assertTaskNotExecuted(':customTask')
        failure.assertTestsFailed()
    }

    def 'task does not execute when it has a test task output dependency and redundant dependsOn test with failing test(s) and --continue'() {
        given:
        withTestVerificationFailure()
        withCustomTaskDependsOnTestTaskAndTestTaskOutput()

        expect:
        fails('customTask', '--continue')
        result.assertTaskExecuted(':test')
        result.assertTaskNotExecuted(':customTask')
        failure.assertTestsFailed()
    }

    // helpers

    def withPassingTest() {
        file('src/test/java/example/PassingUnitTest.java').java '''
            package example;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertTrue;

            public class PassingUnitTest {
                @Test
                public void unitTest() {
                    assertTrue(true);
                }
            }
        '''
    }

    /**
     * Cause the test VM to fail at startup by providing an invalid JVM argument.
     */
    def withFatalTestExecutionError() {
        executer.withStackTraceChecksDisabled() // ignore additional ST
        withPassingTest()
        buildFile << '''
            tasks.named('test', Test).configure {
                jvmArgs('-XX:UnknownArgument')
            }
        '''
    }

    void assertFatalTestExecutionError() {
        failure.assertThatCause(Matchers.matchesRegexp("Process 'Gradle Test Executor \\d+' finished with non-zero exit value \\d+"))
    }

    def withTestVerificationFailure() {
        file('src/test/java/example/UnitTestWithVerificationFailure.java').java '''
            package example;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.fail;

            public class UnitTestWithVerificationFailure {
                @Test
                public void unitTest() {
                    fail("intentional verification failure");
                }
            }
        '''
    }

    def withCustomTaskDependsOnTestTask() {
        buildFile << '''
            tasks.register('customTask') {
                dependsOn tasks.named('test', Test)
            }
        '''
    }

    def withCustomTaskInputFromTestTaskOutput() {
        buildFile << '''
            abstract class CustomTask extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getCustomInput()

                @TaskAction
                public void doAction() {
                    // no-op
                }
            }

            def testTask = tasks.named('test', Test)

            tasks.register('customTask', CustomTask) {
                customInput.from(testTask.flatMap { it.binaryResultsDirectory })
            }
        '''
    }

    /**
     * Helper method to setup a custom task wired to the test in two ways:
     * <ol>
     *     <li>A direct dependency via dependsOn declarartion</li>
     *     <li>Via Test#binaryResultsDirectory output wired to customTask#customInput</li>
     * </ol>
     *
     * This intentional redundancy is to test automatic exclusion of direct dependsOn relationships.
     */
    def withCustomTaskDependsOnTestTaskAndTestTaskOutput() {
        withCustomTaskInputFromTestTaskOutput()
        buildFile << '''
            tasks.named('customTask', CustomTask).configure {
                dependsOn tasks.named('test', Test)
            }
        '''
    }

}
