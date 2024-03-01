/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.matchesRegexp

abstract class AbstractJUnitTestListenerIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    abstract String getAssertionError()

    def "can listen for test results"() {
        given:
        file('src/main/java/AppException.java') << """
            public class AppException extends Exception { }
        """.stripIndent()
        file('src/test/java/SomeTest.java') << """
            ${testFrameworkImports}

            public class SomeTest {
                @Test public void failing() { fail(\"message\"); }
                @Test public void knownError() { throw new RuntimeException(\"message\"); }
                @Test public void unknownError() throws AppException { throw new AppException(); }
            }
        """.stripIndent()
        file('src/test/java/SomeOtherTest.java') << """
            ${testFrameworkImports}

            public class SomeOtherTest {
                @Test public void pass() { }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }

            def listener = new TestListenerImpl()
            test {
                ${configureTestFramework}
                addTestListener(listener)
                ignoreFailures = true
            }

            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.resultType] [\$result.testCount] [\$result.exception]" }
            }
        """.stripIndent()

        when:
        def result = executer.withTasks("test").run()

        then:
        containsLine(result.getOutput(), "START [Gradle Test Run :test] [Gradle Test Run :test]")
        containsLine(result.getOutput(), "FINISH [Gradle Test Run :test] [Gradle Test Run :test] [FAILURE] [4]")

        containsLine(result.getOutput(), matchesRegexp("START \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\]"))
        containsLine(result.getOutput(), matchesRegexp("FINISH \\[Gradle Test Executor \\d+\\] \\[Gradle Test Executor \\d+\\] \\[FAILURE\\] \\[4\\]"))

        containsLine(result.getOutput(), "START [Test class SomeOtherTest] [SomeOtherTest]")
        containsLine(result.getOutput(), "FINISH [Test class SomeOtherTest] [SomeOtherTest] [SUCCESS] [1]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('pass')}(SomeOtherTest)] [${maybeParentheses('pass')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('pass')}(SomeOtherTest)] [${maybeParentheses('pass')}] [SUCCESS] [1] [null]")

        containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest] [FAILURE] [3]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('failing')}(SomeTest)] [${maybeParentheses('failing')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('failing')}(SomeTest)] [${maybeParentheses('failing')}] [FAILURE] [1] [${assertionError}: message]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('knownError')}(SomeTest)] [${maybeParentheses('knownError')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('knownError')}(SomeTest)] [${maybeParentheses('knownError')}] [FAILURE] [1] [java.lang.RuntimeException: message]")
        containsLine(result.getOutput(), "START [Test ${maybeParentheses('unknownError')}(SomeTest)] [${maybeParentheses('unknownError')}]")
        containsLine(result.getOutput(), "FINISH [Test ${maybeParentheses('unknownError')}(SomeTest)] [${maybeParentheses('unknownError')}] [FAILURE] [1] [AppException]")

        when:
        testDirectory.file('src/test/java/SomeOtherTest.java').delete()
        result = executer.withTasks("test").run()

        then:
        result.assertNotOutput("SomeOtherTest")
        containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
    }
}
