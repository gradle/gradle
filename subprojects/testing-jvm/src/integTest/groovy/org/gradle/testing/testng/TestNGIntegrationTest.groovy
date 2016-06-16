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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class TestNGIntegrationTest extends AbstractIntegrationTest {

    @Rule public TestResources resources = new TestResources(testDirectoryProvider)

    @Before
    public void before() {
        executer.noExtraLogging()
    }

    @Test
    void executesTestsInCorrectEnvironment() {
        executer.withTasks('test').run();

        new DefaultTestExecutionResult(testDirectory).testClass('org.gradle.OkTest').assertTestPassed('ok')
    }

    @Test
    void canListenForTestResults() {
        ExecutionResult result = executer.withTasks("test").run();

        assert containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]");
        assert containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test]");
        assert containsLine(result.getOutput(), "START [Gradle Test Executor 1] [Gradle Test Executor 1]");
        assert containsLine(result.getOutput(), "FINISH [Gradle Test Executor 1] [Gradle Test Executor 1]");
        assert containsLine(result.getOutput(), "START [Test suite 'Gradle suite'] [Gradle suite]");
        assert containsLine(result.getOutput(), "FINISH [Test suite 'Gradle suite'] [Gradle suite]");
        assert containsLine(result.getOutput(), "START [Test suite 'Gradle test'] [Gradle test]");
        assert containsLine(result.getOutput(), "FINISH [Test suite 'Gradle test'] [Gradle test]");
        assert containsLine(result.getOutput(), "START [Test method pass(SomeTest)] [pass]");
        assert containsLine(result.getOutput(), "FINISH [Test method pass(SomeTest)] [pass] [null]");
        assert containsLine(result.getOutput(), "START [Test method fail(SomeTest)] [fail]");
        assert containsLine(result.getOutput(), "FINISH [Test method fail(SomeTest)] [fail] [java.lang.AssertionError]");
        assert containsLine(result.getOutput(), "START [Test method knownError(SomeTest)] [knownError]");
        assert containsLine(result.getOutput(), "FINISH [Test method knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]");
        assert containsLine(result.getOutput(), "START [Test method unknownError(SomeTest)] [unknownError]");
        assert containsLine(result.getOutput(), "FINISH [Test method unknownError(SomeTest)] [unknownError] [AppException]");
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

    @Test
    void supportsTestFactory() {
        executer.withTasks("test").run()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.factory.FactoryTest')
        result.testClass('org.gradle.factory.FactoryTest').assertTestCount(2, 0, 0)
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(containsString('TestingFirst'))
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(containsString('TestingSecond'))
        result.testClass('org.gradle.factory.FactoryTest').assertStdout(not(containsString('Default test name')))
    }

    @Test
    @Issue("GRADLE-3315")
    @Ignore // Not fixed yet.
    void picksUpChanges() {
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

        testDirectory.file('src/test/java/SomeTest.java') << """
import org.testng.Assert;
import org.testng.annotations.Test;

public class SomeTest {
	@Test(invocationCount = 2, threadPoolSize = 2)
	public void someTest() {
		Assert.assertTrue(false);
	}
}
"""
        executer.withTasks("test").runWithFailure().assertTestsFailed()
    }
}
