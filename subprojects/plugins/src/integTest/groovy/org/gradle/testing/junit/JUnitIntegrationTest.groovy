/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.containsText
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

public class JUnitIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider)

    @Before
    public void before() {
        executer.noExtraLogging()
    }

    @Test
    public void executesTestsInCorrectEnvironment() {
        executer.withTasks('build').run();

        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.OtherTest')

        result.testClass('org.gradle.OkTest').assertTestPassed('ok')
        result.testClass('org.gradle.OkTest').assertStdout(containsString('This is test stdout'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('non-asci char: ż'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('no EOL'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('class loaded'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('test constructed'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('stdout from another thread'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('This is test stderr'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('this is a warning'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('sys out from another test method'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('sys err from another test method'))

        result.testClass('org.gradle.OkTest').assertStdout(containsString('before class out'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('before class err'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('after class out'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('after class err'))

        result.testClass('org.gradle.OtherTest').assertTestPassed('ok')
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('This is other stdout'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('other class loaded'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('other test constructed'))
        result.testClass('org.gradle.OtherTest').assertStderr(containsString('This is other stderr'))
        result.testClass('org.gradle.OtherTest').assertStderr(containsString('this is another warning'))
    }

    @Test
    public void suitesOutputIsVisible() {
        executer.withTasks('test').run();
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ASuite', 'org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.ASuite').assertStdout(containsString('suite class loaded'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('before suite class out'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('non-asci char: ż'))
        result.testClass('org.gradle.ASuite').assertStderr(containsString('before suite class err'))
        result.testClass('org.gradle.ASuite').assertStdout(containsString('after suite class out'))
        result.testClass('org.gradle.ASuite').assertStderr(containsString('after suite class err'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('This is test stderr'))
        result.testClass('org.gradle.OkTest').assertStdout(containsString('sys out from another test method'))
        result.testClass('org.gradle.OkTest').assertStderr(containsString('sys err from another test method'))
        result.testClass('org.gradle.OtherTest').assertStdout(containsString('This is other stdout'))
    }

    @Test
    public void testClassesCanBeSharedByMultipleSuites() {
        executer.withTasks('test').run();
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass("org.gradle.SomeTest").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeTest").assertTestsExecuted("ok", "ok")
    }

    @Test
    public void canRunMixOfJunit3And4Tests() {
        resources.maybeCopy('JUnitIntegrationTest/junit3Tests')
        resources.maybeCopy('JUnitIntegrationTest/junit4Tests')
        executer.withTasks('check').run()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.Junit3Test', 'org.gradle.Junit4Test', 'org.gradle.IgnoredTest', 'org.gradle.CustomIgnoredTest')
        result.testClass('org.gradle.Junit3Test')
                .assertTestCount(1, 0, 0)
                .assertTestsExecuted('testRenamesItself')
                .assertTestPassed('testRenamesItself')
        result.testClass('org.gradle.Junit4Test')
                .assertTestCount(3, 0, 0)
                .assertTestsExecuted('ok')
                .assertTestPassed('ok')
                .assertTestsSkipped('broken', 'assumptionFailed')
        result.testClass('org.gradle.IgnoredTest').assertTestCount(1, 0, 0).assertTestsSkipped("testIgnored")
        result.testClass('org.gradle.CustomIgnoredTest').assertTestCount(3, 0, 0).assertTestsSkipped("first test run", "second test run", "third test run")
    }

    @Test
    public void canRunTestsUsingJUnit3() {
        resources.maybeCopy('JUnitIntegrationTest/junit3Tests')
        executer.withTasks('check').run()

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.Junit3Test')
        result.testClass('org.gradle.Junit3Test').assertTestsExecuted('testRenamesItself')
        result.testClass('org.gradle.Junit3Test').assertTestPassed('testRenamesItself')
    }

    @Test
    public void reportsAndBreaksBuildWhenTestFails() {
        executer.withTasks('build').runWithFailure().assertTestsFailed()

        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted(
                'org.gradle.ClassWithBrokenRunner',
                'org.gradle.CustomException',
                'org.gradle.BrokenTest',
                'org.gradle.BrokenBefore',
                'org.gradle.BrokenAfter',
                'org.gradle.BrokenBeforeClass',
                'org.gradle.BrokenAfterClass',
                'org.gradle.BrokenBeforeAndAfter',
                'org.gradle.BrokenConstructor',
                'org.gradle.BrokenException',
                'org.gradle.Unloadable',
                'org.gradle.UnserializableException')
        result.testClass('org.gradle.ClassWithBrokenRunner').assertTestFailed('initializationError', equalTo('java.lang.UnsupportedOperationException: broken'))
        result.testClass('org.gradle.BrokenTest')
                .assertTestCount(2, 2, 0)
                .assertTestFailed('failure', equalTo('java.lang.AssertionError: failed'))
                .assertTestFailed('broken', equalTo('java.lang.IllegalStateException: html: <> cdata: ]]>'))
        result.testClass('org.gradle.BrokenBeforeClass').assertTestFailed('classMethod', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenAfterClass').assertTestFailed('classMethod', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenBefore').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenAfter').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenBeforeAndAfter').assertTestFailed('ok', equalTo('java.lang.AssertionError: before failed'), equalTo('java.lang.AssertionError: after failed'))
        result.testClass('org.gradle.BrokenConstructor').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.BrokenException').assertTestFailed('broken', startsWith('Could not determine failure message for exception of type org.gradle.BrokenException$BrokenRuntimeException: java.lang.UnsupportedOperationException'))
        result.testClass('org.gradle.CustomException').assertTestFailed('custom', startsWith('Exception with a custom toString implementation'))
        result.testClass('org.gradle.Unloadable').assertTestFailed('initializationError', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.UnserializableException').assertTestFailed('unserialized', equalTo('org.gradle.UnserializableException$UnserializableRuntimeException: whatever'))
    }

    @Test
    public void canRunSingleTests() {
        executer.withTasks('test').withArguments('-Dtest.single=Ok2').run()
        def result = new DefaultTestExecutionResult(testDirectory)
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
        testDirectory.file('settings.gradle').write("include 'a', 'b'");
        testDirectory.file('b/build.gradle') << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { compile 'junit:junit:4.7' }
        '''
        testDirectory.file('b/src/main/java/org/gradle/AbstractTest.java') << '''
            package org.gradle;
            public abstract class AbstractTest {
                @org.junit.Test public void ok() { }
            }
        '''
        TestFile buildFile = testDirectory.file('a/build.gradle');
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile project(':b') }
        '''
        testDirectory.file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''

        executer.withTasks('a:test').run();

        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory.file('a'))
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    @Test
    public void canExcludeSuperClassesFromExecution() {
        TestFile buildFile = testDirectory.file('build.gradle');
        buildFile << '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.7' }
            test { exclude '**/BaseTest.*' }
        '''
        testDirectory.file('src/test/java/org/gradle/BaseTest.java') << '''
            package org.gradle;
            public class BaseTest {
                @org.junit.Test public void ok() { }
            }
        '''
        testDirectory.file('src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends BaseTest {
            }
        '''

        executer.withTasks('test').run();

        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    @Test
    public void detectsTestClasses() {
        executer.withTasks('test').run()

        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.EmptyRunWithSubclass', 'org.gradle.TestsOnInner', 'org.gradle.TestsOnInner$SomeInner')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestsExecuted('ok')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner$SomeInner').assertTestPassed('ok')
    }

    @Test
    public void runsAllTestsInTheSameForkedJvm() {
        testDirectory.file('build.gradle').writelns(
                "apply plugin: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }"
        );
        testDirectory.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDirectory.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDirectory.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();

        TestFile results1 = testDirectory.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDirectory.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), equalTo(results2.linesThat(containsString('VM START TIME =')).get(0)));
    }

    @Test
    public void canSpecifyMaximumNumberOfTestClassesToExecuteInAForkedJvm() {
        testDirectory.file('build.gradle').writelns(
                "apply plugin: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }",
                "test.forkEvery = 1"
        );
        testDirectory.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDirectory.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDirectory.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();

        TestFile results1 = testDirectory.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDirectory.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), not(equalTo(results2.linesThat(
                containsString('VM START TIME =')).get(0))));
    }

    @Test
    public void canListenForTestResults() {
        testDirectory.file('src/main/java/AppException.java').writelns(
                "public class AppException extends Exception { }"
        );

        testDirectory.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest {",
                "@org.junit.Test public void fail() { org.junit.Assert.fail(\"message\"); }",
                "@org.junit.Test public void knownError() { throw new RuntimeException(\"message\"); }",
                "@org.junit.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        );
        testDirectory.file('src/test/java/SomeOtherTest.java').writelns(
                "public class SomeOtherTest {",
                "@org.junit.Test public void pass() { }",
                "}"
        );

        testDirectory.file('build.gradle') << '''
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
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.resultType] [$result.testCount] [$result.exception]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assert containsLine(result.getOutput(), "START [tests] [Test Run]");
        assert containsLine(result.getOutput(), "FINISH [tests] [Test Run] [FAILURE] [4]");

        assert containsLine(result.getOutput(), "START [test process 'Gradle Worker 1'] [Gradle Worker 1]");
        assert containsLine(result.getOutput(), "FINISH [test process 'Gradle Worker 1'] [Gradle Worker 1] [FAILURE] [4]");

        assert containsLine(result.getOutput(), "START [test class SomeOtherTest] [SomeOtherTest]");
        assert containsLine(result.getOutput(), "FINISH [test class SomeOtherTest] [SomeOtherTest] [SUCCESS] [1]");
        assert containsLine(result.getOutput(), "START [test pass(SomeOtherTest)] [pass]");
        assert containsLine(result.getOutput(), "FINISH [test pass(SomeOtherTest)] [pass] [SUCCESS] [1] [null]");

        assert containsLine(result.getOutput(), "START [test class SomeTest] [SomeTest]");
        assert containsLine(result.getOutput(), "FINISH [test class SomeTest] [SomeTest] [FAILURE] [3]");
        assert containsLine(result.getOutput(), "START [test fail(SomeTest)] [fail]");
        assert containsLine(result.getOutput(), "FINISH [test fail(SomeTest)] [fail] [FAILURE] [1] [java.lang.AssertionError: message]");
        assert containsLine(result.getOutput(), "START [test knownError(SomeTest)] [knownError]");
        assert containsLine(result.getOutput(), "FINISH [test knownError(SomeTest)] [knownError] [FAILURE] [1] [java.lang.RuntimeException: message]");
        assert containsLine(result.getOutput(), "START [test unknownError(SomeTest)] [unknownError]");
        assert containsLine(result.getOutput(), "FINISH [test unknownError(SomeTest)] [unknownError] [FAILURE] [1] [AppException]");
    }

    @Test
    public void canListenForTestResultsWhenJUnit3IsUsed() {
        testDirectory.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest extends junit.framework.TestCase {",
                "public void testPass() { }",
                "public void testFail() { junit.framework.Assert.fail(\"message\"); }",
                "public void testError() { throw new RuntimeException(\"message\"); }",
                "}"
        );

        testDirectory.file('build.gradle') << '''
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
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [$test] [$test.name] [$result.exception]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assert containsLine(result.getOutput(), "START [test class SomeTest] [SomeTest]");
        assert containsLine(result.getOutput(), "FINISH [test class SomeTest] [SomeTest]");
        assert containsLine(result.getOutput(), "START [test testPass(SomeTest)] [testPass]");
        assert containsLine(result.getOutput(), "FINISH [test testPass(SomeTest)] [testPass] [null]");
        assert containsLine(result.getOutput(), "START [test testFail(SomeTest)] [testFail]");
        assert containsLine(result.getOutput(), "FINISH [test testFail(SomeTest)] [testFail] [junit.framework.AssertionFailedError: message]");
        assert containsLine(result.getOutput(), "START [test testError(SomeTest)] [testError]");
        assert containsLine(result.getOutput(), "FINISH [test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]");
    }

    @Test
    public void canHaveMultipleTestTaskInstances() {
        executer.withTasks('check').run()

        def result = new JUnitXmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.Test1', 'org.gradle.Test2')
        result.testClass('org.gradle.Test1').assertTestPassed('ok')
        result.testClass('org.gradle.Test2').assertTestPassed('ok')
    }

    @Test
    void canHandleMultipleThreadsWritingToSystemOut() {
        def result = executer.withTasks("test").run()
        assert result.getOutput().contains("thread 0 out")
        assert result.getOutput().contains("thread 1 out")
        assert result.getOutput().contains("thread 2 out")

        def junitResult = new DefaultTestExecutionResult(testDirectory)
        def testClass = junitResult.testClass("org.gradle.SystemOutTest")
        testClass.assertStdout(containsText("thread 0 out"))
        testClass.assertStdout(containsText("thread 1 out"))
        testClass.assertStdout(containsText("thread 2 out"))
    }

    @Test
    void canHandleMultipleThreadsWritingToSystemErr() {
        def result = executer.withTasks("test").run()
        assert result.getOutput().contains("thread 0 err")
        assert result.getOutput().contains("thread 1 err")
        assert result.getOutput().contains("thread 2 err")

        def junitResult = new DefaultTestExecutionResult(testDirectory)
        def testClass = junitResult.testClass("org.gradle.SystemErrTest")
        testClass.assertStderr(containsText("thread 0 err"))
        testClass.assertStderr(containsText("thread 1 err"))
        testClass.assertStderr(containsText("thread 2 err"))
    }

    @Test
    public void supportsJunit3Suites() {
        executer.withTasks('test').run();
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)

        result.assertTestClassesExecuted('org.gradle.SomeTest1', 'org.gradle.SomeTest2', 'org.gradle.SomeSuite')
        result.testClass("org.gradle.SomeTest1").assertTestCount(1, 0, 0)
        result.testClass("org.gradle.SomeTest1").assertTestsExecuted("testOk1")
        result.testClass("org.gradle.SomeTest2").assertTestCount(1, 0, 0)
        result.testClass("org.gradle.SomeTest2").assertTestsExecuted("testOk2")
        result.testClass("org.gradle.SomeSuite").assertTestCount(0, 0, 0)
        result.testClass("org.gradle.SomeSuite").assertStdout(containsString("stdout in TestSetup#setup"))
        result.testClass("org.gradle.SomeSuite").assertStdout(containsString("stdout in TestSetup#teardown"))
        result.testClass("org.gradle.SomeSuite").assertStderr(containsString("stderr in TestSetup#setup"))
        result.testClass("org.gradle.SomeSuite").assertStderr(containsString("stderr in TestSetup#teardown"))
    }
}
