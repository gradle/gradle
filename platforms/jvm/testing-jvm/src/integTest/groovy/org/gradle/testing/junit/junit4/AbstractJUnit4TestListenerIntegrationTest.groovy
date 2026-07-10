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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.testing.junit.AbstractJUnitTestListenerIntegrationTest

import static org.gradle.util.Matchers.containsLine

abstract class AbstractJUnit4TestListenerIntegrationTest extends AbstractJUnitTestListenerIntegrationTest {
    abstract String getTestFrameworkJUnit3Dependencies()

    @Override
    String getAssertionError() {
        return "java.lang.AssertionError"
    }

    def "can listen for test results when JUnit3 is used"() {
        given:
        file('src/test/java/SomeTest.java') << """
            public class SomeTest extends junit.framework.TestCase {
                public void testPass() { }
                public void testFail() { junit.framework.Assert.fail(\"message\"); }
                public void testError() { throw new RuntimeException(\"message\"); }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkJUnit3Dependencies}
            }
            def listener = new TestListenerImpl()
            test {
                ${configureTestFramework}
                addTestListener(listener)
                ignoreFailures = true
            }
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START [\$suite] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH [\$suite] [\$suite.name]" }
                void beforeTest(TestDescriptor test) { println "START [\$test] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH [\$test] [\$test.name] [\$result.exception]" }
            }
        """.stripIndent()

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

    def "can listen for test events using closures"() {
        given:
        file('src/test/java/SomeTest.java') << """
            ${testFrameworkImports}
            public class SomeTest {
                @Test
                public void testPass() { }
                @Test
                public void testFail() { fail("Some reason"); }
                @Test
                public void testError() { throw new RuntimeException(\"message\"); }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                beforeTest { test -> println "START [\$test] [\$test.name]" }
                afterTest { test, result -> println "FINISH [\$test] [\$test.name] [\$result.exception]" }
                beforeSuite { suite -> println "START [\$suite] [\$suite.name]" }
                afterSuite { suite, result -> println "FINISH [\$suite] [\$suite.name]" }
                ignoreFailures = true
            }
        """.stripIndent()

        when:
        executer.expectDocumentedDeprecationWarning("The AbstractTestTask.beforeTest(Closure) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the addTestListener(TestListener) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_test_methods")
        executer.expectDocumentedDeprecationWarning("The AbstractTestTask.afterTest(Closure) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the addTestListener(TestListener) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_test_methods")
        executer.expectDocumentedDeprecationWarning("The AbstractTestTask.beforeSuite(Closure) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the addTestListener(TestListener) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_test_methods")
        executer.expectDocumentedDeprecationWarning("The AbstractTestTask.afterSuite(Closure) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the addTestListener(TestListener) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_test_methods")

        ExecutionResult result = executer.withTasks("test").run()

        then:
        containsLine(result.getOutput(), "START [Test class SomeTest] [SomeTest]")
        containsLine(result.getOutput(), "FINISH [Test class SomeTest] [SomeTest]")
        containsLine(result.getOutput(), "START [Test testPass(SomeTest)] [testPass]")
        containsLine(result.getOutput(), "FINISH [Test testPass(SomeTest)] [testPass] [null]")
        containsLine(result.getOutput(), "START [Test testFail(SomeTest)] [testFail]")
        containsLine(result.getOutput(), "FINISH [Test testFail(SomeTest)] [testFail] [java.lang.AssertionError: Some reason]")
        containsLine(result.getOutput(), "START [Test testError(SomeTest)] [testError]")
        containsLine(result.getOutput(), "FINISH [Test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]")
    }
}
