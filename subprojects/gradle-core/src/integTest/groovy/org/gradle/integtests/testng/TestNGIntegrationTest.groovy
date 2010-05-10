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

import org.gradle.api.Project
import org.gradle.integtests.DistributionIntegrationTestRunner
import org.gradle.integtests.GradleDistribution
import org.gradle.integtests.GradleExecuter
import org.gradle.integtests.TestExecutionResult
import org.gradle.util.TestFile
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.integtests.testng.TestNGIntegrationProject.*
import static org.junit.Assert.*
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import org.gradle.integtests.ExecutionResult
import org.junit.Rule

/**
 * @author Tom Eyckmans
 */
@RunWith(DistributionIntegrationTestRunner.class)
public class TestNGIntegrationTest {
    static final String GROOVY = "groovy"
    static final String JAVA = "java"
    static final String JDK14 = "jdk14"
    static final String JDK15 = "jdk15"

    static final GROOVY_JDK15_FAILING = failingIntegrationProject(GROOVY, JDK15, { name, projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
    })
    static final GROOVY_JDK15_PASSING = passingIntegrationProject(GROOVY, JDK15, { name, TestFile projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.OkTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
    })
    static final JAVA_JDK14_FAILING = failingIntegrationProject(JAVA, JDK14, { name, projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.BadTest')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
    })
    static final JAVA_JDK14_PASSING = passingIntegrationProject(JAVA, JDK14, { name, projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.OkTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
    })
    static final JAVA_JDK15_FAILING = failingIntegrationProject(JAVA, JDK15, { name, projectDir, TestExecutionResult result, ExecutionResult execution ->
        result.assertTestClassesExecuted('org.gradle.BadTest', 'org.gradle.TestWithBrokenSetup', 'org.gradle.BrokenAfterSuite')
        result.testClass('org.gradle.BadTest').assertTestFailed('failingTest', equalTo('broken'))
        result.testClass('org.gradle.TestWithBrokenSetup').assertConfigMethodFailed('setup')
        result.testClass('org.gradle.BrokenAfterSuite').assertConfigMethodFailed('cleanup')
        assertThat(execution.error, containsString('Test org.gradle.BadTest FAILED'))
        assertThat(execution.error, containsString('Test org.gradle.TestWithBrokenSetup FAILED'))
        assertThat(execution.error, containsString('Test org.gradle.BrokenAfterSuite FAILED'))
    })
    static final JAVA_JDK15_PASSING = passingIntegrationProject(JAVA, JDK15, { name, projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.ConcreteTest', 'org.gradle.SuiteSetup', 'org.gradle.SuiteCleanup', 'org.gradle.TestSetup', 'org.gradle.TestCleanup')
        result.testClass('org.gradle.OkTest').assertTestsExecuted('passingTest', 'expectedFailTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('expectedFailTest')
        result.testClass('org.gradle.ConcreteTest').assertTestsExecuted('ok', 'alsoOk')
        result.testClass('org.gradle.ConcreteTest').assertTestPassed('ok')
        result.testClass('org.gradle.ConcreteTest').assertTestPassed('alsoOk')
        result.testClass('org.gradle.SuiteSetup').assertConfigMethodPassed('setupSuite')
        result.testClass('org.gradle.SuiteCleanup').assertConfigMethodPassed('cleanupSuite')
        result.testClass('org.gradle.TestSetup').assertConfigMethodPassed('setupTest')
        result.testClass('org.gradle.TestCleanup').assertConfigMethodPassed('cleanupTest')
    })
    static final JAVA_JDK15_PASSING_NO_REPORT = passingIntegrationProject(JAVA, JDK15, "-no-report", { name, TestFile projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.OkTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
        projectDir.file('build/reports/tests/index.html').assertDoesNotExist()
    })
    static final SUITE_XML_BUILDER = new TestNGIntegrationProject("suitexmlbuilder", false, null, { name, projectDir, TestExecutionResult result ->
        result.assertTestClassesExecuted('org.gradle.testng.UserImplTest')
        result.testClass('org.gradle.testng.UserImplTest').assertTestsExecuted('testOkFirstName')
        result.testClass('org.gradle.testng.UserImplTest').assertTestPassed('testOkFirstName')
    })

    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer

    @Test
    public void executesTestsInCorrectEnvironment() {
        TestFile testDir = dist.testDir;
        TestFile buildFile = testDir.file('build.gradle');
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:5.8:jdk15' }
            test {
                useTestNG()
                systemProperties.testSysProperty = 'value'
                environment.TEST_ENV_VAR = 'value'
            }
        '''
        testDir.file("src/test/java/org/gradle/OkTest.java") << """
            package org.gradle;
            import static org.testng.Assert.*;
            public class OkTest {
                @org.testng.annotations.Test public void ok() throws Exception {
                    // check working dir
                    assertEquals("${testDir.absolutePath.replaceAll('\\\\', '\\\\\\\\')}", System.getProperty("user.dir"));
                    // check Gradle classes not visible
                    try { getClass().getClassLoader().loadClass("${Project.class.getName()}"); fail(); } catch(ClassNotFoundException e) { }
                    // check context classloader
                    assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
                    // check sys properties
                    assertEquals("value", System.getProperty("testSysProperty"));
                    // check env vars
                    assertEquals("value", System.getenv("TEST_ENV_VAR"));
                }
            }
        """
        executer.withTasks('build').run();

        new TestNgExecutionResult(testDir).testClass('org.gradle.OkTest').assertTestPassed('ok')
    }

    @Test
    public void canListenerForTestResults() {
        TestFile testDir = dist.getTestDir();
        testDir.file('src/main/java/AppException.java').writelns(
                "public class AppException extends Exception { }"
        );

        testDir.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest {",
                "@org.testng.annotations.Test public void pass() { }",
                "@org.testng.annotations.Test public void fail() { assert false; }",
                "@org.testng.annotations.Test public void knownError() { throw new RuntimeException(\"message\"); }",
                "@org.testng.annotations.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        );

        testDir.file('build.gradle') << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:5.8:jdk15' }
            def listener = new TestListenerImpl()
            test {
                useTestNG()
                addTestListener(listener)
                ignoreFailures = true
            }
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [$suite] [$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [$test] [$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.error]" }
            }
        '''

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
        assertThat(result.getOutput(), containsLine("FINISH [test method unknownError(SomeTest)] [unknownError] [org.gradle.messaging.dispatch.PlaceholderException: AppException: null]"));
    }

    @Test
    public void suiteXmlBuilder() {
        checkProject(SUITE_XML_BUILDER)
    }

    @Test
    public void groovyJdk15() {
        checkProject(GROOVY_JDK15_FAILING)
        checkProject(GROOVY_JDK15_PASSING)
    }

    @Test
    public void javaJdk14() {
        checkProject(JAVA_JDK14_PASSING)
        checkProject(JAVA_JDK14_FAILING)
    }

    @Test
    public void javaJdk15() {
        checkProject(JAVA_JDK15_PASSING)
        checkProject(JAVA_JDK15_FAILING)
    }

    @Ignore
    public void javaJdk15WithNoReports() {
        // TODO currently reports are always generated because the antTestNGExecute task uses the
        // default listeners and these generate reports by default. Enable the test when this has changed.
        checkProject(JAVA_JDK15_PASSING_NO_REPORT)
    }

    private def checkProject(TestNGIntegrationProject project) {
        final File projectDir = dist.samplesDir.file("testng", project.name)

        def result
        executer.inDirectory(projectDir).withTasks('clean', 'test')
        if (project.expectFailure) {
            result = executer.runWithFailure()
        } else {
            result = executer.run()
        }

        // output: output, error: error, command: actualCommand, unixCommand: unixCommand, windowsCommand: windowsCommand
        project.doAssert(projectDir, result)
    }
}