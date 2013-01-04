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
package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestNGExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

/**
 * @author Tom Eyckmans
 */
class TestNGIntegrationTest extends AbstractIntegrationTest {

    @Rule public TestResources resources = new TestResources()

    @Before
    public void before() {
        executer.allowExtraLogging = false
    }

    @Test
    void executesTestsInCorrectEnvironment() {
        ExecutionResult result = executer.withTasks('test').run();

        assertThat(result.output, not(containsString('stdout')))
        assertThat(result.error, not(containsString('stderr')))
        assertThat(result.error, not(containsString('a warning')))

        new DefaultTestExecutionResult(testDirectory).testClass('org.gradle.OkTest').assertTestPassed('ok')
    }

    @Test
    void canListenForTestResults() {
        ExecutionResult result = executer.withTasks("test").run();

        assert containsLine(result.getOutput(), "START [tests] [Test Run]");
        assert containsLine(result.getOutput(), "FINISH [tests] [Test Run]");
        assert containsLine(result.getOutput(), "START [test process 'Gradle Worker 1'] [Gradle Worker 1]");
        assert containsLine(result.getOutput(), "FINISH [test process 'Gradle Worker 1'] [Gradle Worker 1]");
        assert containsLine(result.getOutput(), "START [test 'Gradle test'] [Gradle test]");
        assert containsLine(result.getOutput(), "FINISH [test 'Gradle test'] [Gradle test]");
        assert containsLine(result.getOutput(), "START [test method pass(SomeTest)] [pass]");
        assert containsLine(result.getOutput(), "FINISH [test method pass(SomeTest)] [pass] [null]");
        assert containsLine(result.getOutput(), "START [test method fail(SomeTest)] [fail]");
        assert containsLine(result.getOutput(), "FINISH [test method fail(SomeTest)] [fail] [java.lang.AssertionError]");
        assert containsLine(result.getOutput(), "START [test method knownError(SomeTest)] [knownError]");
        assert containsLine(result.getOutput(), "FINISH [test method knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]");
        assert containsLine(result.getOutput(), "START [test method unknownError(SomeTest)] [unknownError]");
        assert containsLine(result.getOutput(), "FINISH [test method unknownError(SomeTest)] [unknownError] [AppException]");
    }

    @Test
    void groovyJdk15Failing() {
        executer.withTasks("test").runWithFailure().assertTestsFailed()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('java.lang.IllegalArgumentException: broken'))
    }

    @Test
    void groovyJdk15Passing() {
        executer.withTasks("test").run()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.OkTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
    }

    @Test
    void javaJdk14Failing() {
        executer.withTasks("test").runWithFailure().assertTestsFailed()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('java.lang.IllegalArgumentException: broken'))
    }

    @Issue("GRADLE-1822")
    @Test
    void javaJdk15Failing() {
        doJavaJdk15Failing("5.14.10")
        doJavaJdk15Failing("6.3.1")
    }

    private void doJavaJdk15Failing(String testNGVersion) {
        executer.withTasks("test").withArguments("-PtestNGVersion=$testNGVersion").runWithFailure().assertTestsFailed()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.BadTest', 'org.gradle.TestWithBrokenSetup', 'org.gradle.BrokenAfterSuite', 'org.gradle.TestWithBrokenMethodDependency')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('java.lang.IllegalArgumentException: broken'))
        result.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestFailed('broken', equalTo('java.lang.RuntimeException: broken'))
        result.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestsSkipped('okTest')

        def ngResult = new TestNGExecutionResult(testDirectory)
        ngResult.testClass('org.gradle.TestWithBrokenSetup').assertConfigMethodFailed('setup')
        ngResult.testClass('org.gradle.BrokenAfterSuite').assertConfigMethodFailed('cleanup')
        ngResult.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestFailed('broken', equalTo('broken'))
        ngResult.testClass('org.gradle.TestWithBrokenMethodDependency').assertTestSkipped('okTest')
    }

    @Issue("GRADLE-1532")
    @Test
    void supportsThreadPoolSize() {
        testDirectory.file('src/test/java/SomeTest.java') << """
import org.testng.Assert;
import org.testng.annotations.Test;

public class SomeTest {
	@Test(invocationCount = 2, threadPoolSize = 2)
	public void someTest() {
		Assert.assertTrue(true);
	}
}
"""

        testDirectory.file("build.gradle") << """
apply plugin: "java"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
	testCompile 'org.testng:testng:6.3.1'
}

test {
 	useTestNG()
}

"""
        executer.withTasks("test").run()
    }
    
    @Test
    void supportsTestGroups() {
        executer.withTasks("test").run()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.groups.SomeTest')
        result.testClass('org.gradle.groups.SomeTest').assertTestsExecuted("databaseTest")
    }
}