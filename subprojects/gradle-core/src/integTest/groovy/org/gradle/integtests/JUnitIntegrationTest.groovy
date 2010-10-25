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
package org.gradle.integtests

import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.integtests.fixtures.*
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(DistributionIntegrationTestRunner.class)
public class JUnitIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final TestResources resources = new TestResources()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void executesTestsInCorrectEnvironment() {
        TestFile testDir = dist.testDir;
        executer.withTasks('build').run();

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('ok')
        result.testClass('org.gradle.OkTest').assertStdout(containsString('This is test stdout'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('no EOL'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('class loaded'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('test constructed'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('stdout from another thread'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('This is test stderr'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('this is a warning'))
        result.testClass('org.gradle.OtherTest').assertTestPassed('ok')
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('This is other stdout'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('other class loaded'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('other test constructed'))
        result.testClass('org.gradle.OtherTest').assertStderr(containsString('This is other stderr'))
        result.testClass('org.gradle.OtherTest').assertStderr(containsString('this is another warning'))
    }

    @Test
    public void reportsAndBreaksBuildWhenTestFails() {
        TestFile testDir = dist.getTestDir();
        TestFile buildFile = testDir.file('build.gradle');
        ExecutionFailure failure = executer.withTasks('build').runWithFailure();

        failure.assertHasFileName("Build file '${buildFile}'");
        failure.assertHasDescription("Execution failed for task ':test'.");
        failure.assertThatCause(startsWith('There were failing tests.'));

        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenTest FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenBefore FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenAfter FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenBeforeClass FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenAfterClass FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenConstructor FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.BrokenException FAILED'));
        assertThat(failure.getError(), containsLine('Test org.gradle.Unloadable FAILED'));

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir)
        result.assertTestClassesExecuted(
                'org.gradle.BrokenTest',
                'org.gradle.BrokenBefore',
                'org.gradle.BrokenAfter',
                'org.gradle.BrokenBeforeClass',
                'org.gradle.BrokenAfterClass',
                'org.gradle.BrokenConstructor',
                'org.gradle.BrokenException',
                'org.gradle.Unloadable')
        result.testClass('org.gradle.BrokenTest').assertTestFailed('failure', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenTest').assertTestFailed('broken', equalTo('java.lang.IllegalStateException'))
        result.testClass('org.gradle.BrokenBeforeClass').assertTestFailed('classMethod', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenAfterClass').assertTestFailed('classMethod', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenBefore').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenAfter').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenConstructor').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenException').assertTestFailed('broken', startsWith('Could not determine failure message for exception of type org.gradle.BrokenException$BrokenRuntimeException: '))
        result.testClass('org.gradle.Unloadable').assertTestFailed('initializationError', equalTo('java.lang.AssertionError: failed'))
    }

    @Test
    public void canRunSingleTests() {
        executer.withTasks('test').withArguments('-Dtest.single=Ok2').run()
        def result = new JUnitTestExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('Ok2')

        executer.withTasks('cleanTest', 'test').withArguments('-Dtest.single=Ok').run()
        result.assertTestClassesExecuted('Ok', 'Ok2')

        def failure = executer.withTasks('test').withArguments('-Dtest.single=DoesNotMatchAClass').runWithFailure()
        failure.assertHasCause('Could not find matching test for pattern: DoesNotMatchAClass')

        failure = executer.withTasks('test').withArguments('-Dtest.single=NotATest').runWithFailure()
        failure.assertHasCause('Could not find matching test for pattern: NotATest')
    }

    @Test
    public void canUseTestSuperClassesFromAnotherProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file('settings.gradle').write("include 'a', 'b'");
        testDir.file('b/build.gradle') << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { compile 'junit:junit:4.7' }
        '''
        testDir.file('b/src/main/java/org/gradle/AbstractTest.java') << '''
            package org.gradle;
            public abstract class AbstractTest {
                @org.junit.Test public void ok() { }
            }
        '''
        TestFile buildFile = testDir.file('a/build.gradle');
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile project(':b') }
        '''
        testDir.file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''

        executer.withTasks('a:test').run();

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir.file('a'))
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    @Test
    public void canExcludeSuperClassesFromExecution() {
        TestFile testDir = dist.getTestDir();
        TestFile buildFile = testDir.file('build.gradle');
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.7' }
            test { exclude '**/BaseTest.*' }
        '''
        testDir.file('src/test/java/org/gradle/BaseTest.java') << '''
            package org.gradle;
            public class BaseTest {
                @org.junit.Test public void ok() { }
            }
        '''
        testDir.file('src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends BaseTest {
            }
        '''

        executer.withTasks('test').run();

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    @Test
    public void detectsTestClasses() {
        executer.withTasks('test').run()

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.EmptyRunWithSubclass', 'org.gradle.TestsOnInner$SomeInner')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestsExecuted('ok')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner$SomeInner').assertTestPassed('ok')
    }

    @Test
    public void runsAllTestsInTheSameForkedJvm() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply plugin: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }"
        );
        testDir.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();

        TestFile results1 = testDir.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDir.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), equalTo(results2.linesThat(containsString('VM START TIME =')).get(0)));
    }

    @Test
    public void canSpecifyMaximumNumberOfTestClassesToExecuteInAForkedJvm() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply plugin: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }",
                "test.forkEvery = 1"
        );
        testDir.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();

        TestFile results1 = testDir.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDir.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), not(equalTo(results2.linesThat(
                containsString('VM START TIME =')).get(0))));
    }

    @Test
    public void canListenForTestResults() {
        TestFile testDir = dist.getTestDir();
        testDir.file('src/main/java/AppException.java').writelns(
                "public class AppException extends Exception { }"
        );

        testDir.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest {",
                "@org.junit.Test public void fail() { org.junit.Assert.fail(\"message\"); }",
                "@org.junit.Test public void knownError() { throw new RuntimeException(\"message\"); }",
                "@org.junit.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        );
        testDir.file('src/test/java/SomeOtherTest.java').writelns(
                "public class SomeOtherTest {",
                "@org.junit.Test public void pass() { }",
                "}"
        );

        testDir.file('build.gradle') << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.7' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [$suite] [$suite.name] [$result.resultType] [$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [$test] [$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.resultType] [$result.testCount] [$result.error]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assertThat(result.getOutput(), containsLine("START [tests] []"));
        assertThat(result.getOutput(), containsLine("FINISH [tests] [] [FAILURE] [4]"));

        assertThat(result.getOutput(), containsLine("START [test process 'Gradle Worker 1'] [Gradle Worker 1]"));
        assertThat(result.getOutput(), containsLine("FINISH [test process 'Gradle Worker 1'] [Gradle Worker 1] [FAILURE] [4]"));

        assertThat(result.getOutput(), containsLine("START [test class SomeOtherTest] [SomeOtherTest]"));
        assertThat(result.getOutput(), containsLine("FINISH [test class SomeOtherTest] [SomeOtherTest] [SUCCESS] [1]"));
        assertThat(result.getOutput(), containsLine("START [test pass(SomeOtherTest)] [pass]"));
        assertThat(result.getOutput(), containsLine("FINISH [test pass(SomeOtherTest)] [pass] [SUCCESS] [1] [null]"));

        assertThat(result.getOutput(), containsLine("START [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("FINISH [test class SomeTest] [SomeTest] [FAILURE] [3]"));
        assertThat(result.getOutput(), containsLine("START [test fail(SomeTest)] [fail]"));
        assertThat(result.getOutput(), containsLine("FINISH [test fail(SomeTest)] [fail] [FAILURE] [1] [java.lang.AssertionError: message]"));
        assertThat(result.getOutput(), containsLine("START [test knownError(SomeTest)] [knownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test knownError(SomeTest)] [knownError] [FAILURE] [1] [java.lang.RuntimeException: message]"));
        assertThat(result.getOutput(), containsLine("START [test unknownError(SomeTest)] [unknownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test unknownError(SomeTest)] [unknownError] [FAILURE] [1] [org.gradle.messaging.remote.internal.PlaceholderException: AppException: null]"));
    }

    @Test
    public void canListenForTestResultsWhenJUnit3IsUsed() {
        TestFile testDir = dist.getTestDir();
        testDir.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest extends junit.framework.TestCase {",
                "public void testPass() { }",
                "public void testFail() { junit.framework.Assert.fail(\"message\"); }",
                "public void testError() { throw new RuntimeException(\"message\"); }",
                "}"
        );

        testDir.file('build.gradle') << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:3.8' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [$suite] [$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [$test] [$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.error]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assertThat(result.getOutput(), containsLine("START [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("FINISH [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("START [test testPass(SomeTest)] [testPass]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testPass(SomeTest)] [testPass] [null]"));
        assertThat(result.getOutput(), containsLine("START [test testFail(SomeTest)] [testFail]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testFail(SomeTest)] [testFail] [junit.framework.AssertionFailedError: message]"));
        assertThat(result.getOutput(), containsLine("START [test testError(SomeTest)] [testError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]"));
    }

    @Test
    public void canHaveMultipleTestTaskInstances() {
        executer.withTasks('check').run()

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(dist.testDir)
        result.assertTestClassesExecuted('org.gradle.Test1', 'org.gradle.Test2')
        result.testClass('org.gradle.Test1').assertTestPassed('ok')
        result.testClass('org.gradle.Test2').assertTestPassed('ok')
    }
}
