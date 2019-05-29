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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER
import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.startsWith
import static org.junit.Assert.assertThat

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class JUnitIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def setup() {
        executer.noExtraLogging()
    }

    def executesTestsInCorrectEnvironment() {
        given:
        buildFile << """
        test {
            systemProperties.isJava9 = ${JavaVersion.current().isJava9Compatible()}
        }
        """.stripIndent()

        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.OtherTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('ok')
        result.testClass('org.gradle.OtherTest').assertTestPassed('ok')
    }

    def suitesOutputIsVisible() {
        when:
        ignoreWhenJupiter()
        executer.withTasks('test').run()

        then:
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

    def testClassesCanBeSharedByMultipleSuites() {
        when:
        ignoreWhenJupiter()
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass("org.gradle.SomeTest").assertTestCount(2, 0, 0)
        result.testClass("org.gradle.SomeTest").assertTestsExecuted("ok", "ok")
    }

    def reportsAndBreaksBuildWhenTestFails() {
        when:
        executer.withTasks('build').runWithFailure().assertTestsFailed()

        then:
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
        result.testClass('org.gradle.Unloadable').assertTestFailed('ok', equalTo('java.lang.AssertionError: failed'))
        result.testClass('org.gradle.Unloadable').assertTestFailed('ok2', startsWith('java.lang.NoClassDefFoundError'))
        result.testClass('org.gradle.UnserializableException').assertTestFailed('unserialized', equalTo('org.gradle.UnserializableException$UnserializableRuntimeException: whatever'))
    }

    def canRunSingleTests() {
        when:
        succeeds("test", "--tests=Ok2*")

        then:
        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('Ok2')

        when:
        succeeds("cleanTest", "test", "--tests=Ok*")

        then:
        testResult.assertTestClassesExecuted('Ok', 'Ok2')

        when:
        fails("test", "--tests=DoesNotMatchAClass*")

        then:
        result.assertHasCause('No tests found for given includes: [DoesNotMatchAClass*](--tests filter)')

        when:
        fails("test", "--tests=NotATest*")
        then:
        result.assertHasCause('No tests found for given includes: [NotATest*](--tests filter)')
    }

    def canUseTestSuperClassesFromAnotherProject() {
        given:
        testDirectory.file('settings.gradle').write("include 'a', 'b'")
        testDirectory.file('b/build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { compile 'junit:junit:4.12' }
        """
        testDirectory.file('b/src/main/java/org/gradle/AbstractTest.java') << '''
            package org.gradle;
            public abstract class AbstractTest {
                @org.junit.Test public void ok() { }
            }
        '''
        TestFile buildFile = testDirectory.file('a/build.gradle')
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile project(':b') }
        """
        testDirectory.file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''

        when:
        executer.withTasks('a:test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory.file('a'))
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    def canExcludeSuperClassesFromExecution() {
        given:
        TestFile buildFile = testDirectory.file('build.gradle')
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
            test { exclude '**/BaseTest.*' }
        """
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

        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.SomeTest')
        result.testClass('org.gradle.SomeTest').assertTestPassed('ok')
    }

    def detectsTestClasses() {
        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.EmptyRunWithSubclass', 'org.gradle.TestsOnInner', 'org.gradle.TestsOnInner$SomeInner')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestsExecuted('ok')
        result.testClass('org.gradle.EmptyRunWithSubclass').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner').assertTestPassed('ok')
        result.testClass('org.gradle.TestsOnInner$SomeInner').assertTestPassed('ok')
    }

    @Issue("https://issues.gradle.org//browse/GRADLE-3114")
    def createsRunnerBeforeTests() {
        when:
        executer.withTasks('test').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.ExecutionOrderTest')
        result.testClass('org.gradle.ExecutionOrderTest').assertTestPassed('classUnderTestIsLoadedOnlyByRunner')
    }

    def runsAllTestsInTheSameForkedJvm() {
        given:
        testDirectory.file('build.gradle').writelns(
                "apply plugin: 'java'",
                mavenCentralRepository(),
                "dependencies { compile 'junit:junit:4.12' }"
        )
        testDirectory.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}")
        testDirectory.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}")
        testDirectory.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}")

        when:
        executer.withTasks('test').run()

        then:
        TestFile results1 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest.xml')
        TestFile results2 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest2.xml')
        results1.assertIsFile()
        results2.assertIsFile()
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), equalTo(results2.linesThat(containsString('VM START TIME =')).get(0)))
    }

    def canSpecifyMaximumNumberOfTestClassesToExecuteInAForkedJvm() {
        given:
        testDirectory.file('build.gradle').writelns(
                "apply plugin: 'java'",
                mavenCentralRepository(),
                "dependencies { compile 'junit:junit:4.12' }",
                "test.forkEvery = 1"
        )
        testDirectory.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}")
        testDirectory.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}")
        testDirectory.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}")

        when:
        executer.withTasks('test').run()

        then:
        TestFile results1 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest.xml')
        TestFile results2 = testDirectory.file('build/test-results/test/TEST-org.gradle.SomeTest2.xml')
        results1.assertIsFile()
        results2.assertIsFile()
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), not(equalTo(results2.linesThat(
                containsString('VM START TIME =')).get(0))))
    }

    def canListenForTestResults() {
        given:
        testDirectory.file('src/main/java/AppException.java').writelns(
                "public class AppException extends Exception { }"
        )

        testDirectory.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest {",
                "@org.junit.Test public void fail() { org.junit.Assert.fail(\"message\"); }",
                "@org.junit.Test public void knownError() { throw new RuntimeException(\"message\"); }",
                "@org.junit.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        )
        testDirectory.file('src/test/java/SomeOtherTest.java').writelns(
                "public class SomeOtherTest {",
                "@org.junit.Test public void pass() { }",
                "}"
        )

        testDirectory.file('build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.resultType] [\$result.testCount] [\$result.exception]" }
            }
        """

        when:
        ExecutionResult result = executer.withTasks("test").run()

        then:
        assert containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]")
        assert containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test] [FAILURE] [4]")

        assert containsLine(result.getOutput(), matchesRegexp("START \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\]"))
        assert containsLine(result.getOutput(), matchesRegexp("FINISH \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\] \\[FAILURE\\] \\[4\\]"))

        assert containsLine(result.getOutput(), "START [Test class SomeOtherTest] [SomeOtherTest]")
        assert containsLine(result.getOutput(), "FINISH [Test class SomeOtherTest] [SomeOtherTest] [SUCCESS] [1]")
        assert containsLine(result.getOutput(), "START [Test pass(SomeOtherTest)] [pass]")
        assert containsLine(result.getOutput(), "FINISH [Test pass(SomeOtherTest)] [pass] [SUCCESS] [1] [null]")

        assert containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        assert containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest] [FAILURE] [3]")
        assert containsLine(result.getOutput(), "START [Test fail(SomeTest)] [fail]")
        assert containsLine(result.getOutput(), "FINISH [Test fail(SomeTest)] [fail] [FAILURE] [1] [java.lang.AssertionError: message]")
        assert containsLine(result.getOutput(), "START [Test knownError(SomeTest)] [knownError]")
        assert containsLine(result.getOutput(), "FINISH [Test knownError(SomeTest)] [knownError] [FAILURE] [1] [java.lang.RuntimeException: message]")
        assert containsLine(result.getOutput(), "START [Test unknownError(SomeTest)] [unknownError]")
        assert containsLine(result.getOutput(), "FINISH [Test unknownError(SomeTest)] [unknownError] [FAILURE] [1] [AppException]")
    }

    def canListenForTestResultsWhenJUnit3IsUsed() {
        given:
        ignoreWhenJupiter()
        testDirectory.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest extends junit.framework.TestCase {",
                "public void testPass() { }",
                "public void testFail() { junit.framework.Assert.fail(\"message\"); }",
                "public void testError() { throw new RuntimeException(\"message\"); }",
                "}"
        )

        testDirectory.file('build.gradle') << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:3.8' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.exception]" }
            }
        """

        when:
        ExecutionResult result = executer.withTasks("test").run()

        then:
        assert containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        assert containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest]")
        assert containsLine(result.getOutput(), "START [Test testPass(SomeTest)] [testPass]")
        assert containsLine(result.getOutput(), "FINISH [Test testPass(SomeTest)] [testPass] [null]")
        assert containsLine(result.getOutput(), "START [Test testFail(SomeTest)] [testFail]")
        assert containsLine(result.getOutput(), "FINISH [Test testFail(SomeTest)] [testFail] [junit.framework.AssertionFailedError: message]")
        assert containsLine(result.getOutput(), "START [Test testError(SomeTest)] [testError]")
        assert containsLine(result.getOutput(), "FINISH [Test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]")
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def canHaveMultipleTestTaskInstances() {
        when:
        executer.withTasks('check').run()

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted('org.gradle.Test1')
        testResult.testClass('org.gradle.Test1').assertTestPassed('ok')

        def test2Result = new JUnitXmlTestExecutionResult(testDirectory, 'build/test-results/test2')
        test2Result.assertTestClassesExecuted('org.gradle.Test2')
        test2Result.testClass('org.gradle.Test2').assertTestPassed('ok')
    }

    def supportsJunit3Suites() {
        when:
        executer.withTasks('test').run()

        then:
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

    def "tries to execute unparseable test classes"() {
        given:
        testDirectory.file('build/classes/java/test/com/example/Foo.class').text = "invalid class file"
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile '$dependencyNotation'
            }
        """

        when:
        fails('test', '-x', 'compileTestJava')

        then:
        failureCauseContains("There were failing tests")
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        if (isVintage() || isJupiter()) {
            result.testClassStartsWith('Gradle Test Executor')
                .assertTestCount(1, 1, 0)
                .assertTestFailed("failed to execute tests", containsString("Could not execute test class 'com.example.Foo'"))
        } else {
            result.testClass('com.example.Foo')
                .assertTestCount(1, 1, 0)
                .assertTestFailed("initializationError", containsString('ClassFormatError'))
        }
    }
}
