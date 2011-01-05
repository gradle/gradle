/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.integtests.testng

import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test
import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

/**
 * @author Tom Eyckmans
 */
public class TestNGIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()

    @Test
    public void executesTestsInCorrectEnvironment() {
        ExecutionResult result = executer.withTasks('test').run();

        assertThat(result.output, not(containsString('stdout')))
        assertThat(result.error, not(containsString('stderr')))
        assertThat(result.error, not(containsString('a warning')))

        new TestNGExecutionResult(dist.testDir).testClass('org.gradle.OkTest').assertTestPassed('ok')
    }

    @Test
    public void canListenForTestResults() {
        ExecutionResult result = executer.withTasks("test").run();

        assertThat(result.getOutput(), containsLine("START [tests] []"));
        assertThat(result.getOutput(), containsLine("FINISH [tests] []"));
        assertThat(result.getOutput(), containsLine("START [test process 'Gradle Worker 1'] [Gradle Worker 1]"));
        assertThat(result.getOutput(), containsLine("FINISH [test process 'Gradle Worker 1'] [Gradle Worker 1]"));
        assertThat(result.getOutput(), containsLine("START [test 'Gradle test'] [Gradle test]"));
        assertThat(result.getOutput(), containsLine("FINISH [test 'Gradle test'] [Gradle test]"));
        assertThat(result.getOutput(), containsLine("START [test method pass(SomeTest)] [pass]"));
        assertThat(result.getOutput(), containsLine("FINISH [test method pass(SomeTest)] [pass] [null]"));
        assertThat(result.getOutput(), containsLine("START [test method fail(SomeTest)] [fail]"));
        assertThat(result.getOutput(), containsLine("FINISH [test method fail(SomeTest)] [fail] [java.lang.AssertionError]"));
        assertThat(result.getOutput(), containsLine("START [test method knownError(SomeTest)] [knownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test method knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]"));
        assertThat(result.getOutput(), containsLine("START [test method unknownError(SomeTest)] [unknownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test method unknownError(SomeTest)] [unknownError] [org.gradle.messaging.remote.internal.PlaceholderException: AppException: null]"));
    }

    @Test
    public void groovyJdk15Failing() {
        executer.withTasks("test").runWithFailure().assertThatCause(startsWith('There were failing tests'))

        def result = new TestNGExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
    }

    @Test
    public void groovyJdk15Passing() {
        executer.withTasks("test").run()

        def result = new TestNGExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.OkTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
    }

    @Test
    public void javaJdk14Failing() {
        executer.withTasks("test").runWithFailure().assertThatCause(startsWith('There were failing tests'))

        def result = new TestNGExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
    }

    @Test
    public void javaJdk15Failing() {
        def execution = executer.withTasks("test").runWithFailure().assertThatCause(startsWith('There were failing tests'))

        def result = new TestNGExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.BadTest', 'org.gradle.TestWithBrokenSetup', 'org.gradle.BrokenAfterSuite', 'org.gradle.TestWithBrokenMethodDependency')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
        result.testClass('org.gradle.TestWithBrokenSetup').assertConfigMethodFailed('setup')
        result.testClass('org.gradle.BrokenAfterSuite').assertConfigMethodFailed('cleanup')
        result.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestFailed('broken', equalTo('broken'))
        result.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestSkipped('okTest')
        assertThat(execution.error, containsString('Test org.gradle.BadTest FAILED'))
        assertThat(execution.error, containsString('Test org.gradle.TestWithBrokenSetup FAILED'))
        assertThat(execution.error, containsString('Test org.gradle.BrokenAfterSuite FAILED'))
        assertThat(execution.error, containsString('Test org.gradle.TestWithBrokenMethodDependency FAILED'))
    }
}