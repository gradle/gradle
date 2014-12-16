/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert

class JUnitTestClassExecutionResult implements TestClassExecutionResult {
    GPathResult testClassNode
    String testClassName
    boolean checked
    TestResultOutputAssociation outputAssociation

    def JUnitTestClassExecutionResult(GPathResult testClassNode, String testClassName, TestResultOutputAssociation outputAssociation) {
        this.outputAssociation = outputAssociation
        this.testClassNode = testClassNode
        this.testClassName = testClassName
    }

    def JUnitTestClassExecutionResult(String content, String testClassName, TestResultOutputAssociation outputAssociation) {
        this(new XmlSlurper().parse(new StringReader(content)), testClassName, outputAssociation)
    }

    TestClassExecutionResult assertTestsExecuted(String... testNames) {
        Map<String, Node> testMethods = findTests().findAll { name, element ->
            element."skipped".size() == 0 // Exclude skipped test.
        }
        Assert.assertThat(testMethods.keySet(), Matchers.equalTo(testNames as Set))
        this
    }

    TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
        assert testClassNode.@tests == tests
        assert testClassNode.@failures == failures
        assert testClassNode.@errors == errors
        this
    }
    
    TestClassExecutionResult assertTestCount(int tests, int skipped, int failures, int errors) {
        assert testClassNode.@tests == tests
        assert testClassNode.@skipped == skipped
        assert testClassNode.@failures == failures
        assert testClassNode.@errors == errors
        this
    }

    TestClassExecutionResult withResult(Closure action) {
        action(testClassNode)
        this
    }

    TestClassExecutionResult assertTestPassed(String name) {
        Map<String, Node> testMethods = findTests()
        Assert.assertThat(testMethods.keySet(), Matchers.hasItem(name))
        Assert.assertThat(testMethods[name].failure.size(), Matchers.equalTo(0))
        this
    }

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
        Map<String, Node> testMethods = findTests()
        Assert.assertThat(testMethods.keySet(), Matchers.hasItem(name))

        def failures = testMethods[name].failure
        Assert.assertThat("Expected ${messageMatchers.length} failures. Found: $failures", failures.size(), Matchers.equalTo(messageMatchers.length))

        for (int i = 0; i < messageMatchers.length; i++) {
            Assert.assertThat(failures[i].@message.text(), messageMatchers[i])
        }
        this
    }

    TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher) {
        Map<String, Node> testMethods = findTests()
        String failureMethodName = "execution failure"
        Assert.assertThat(testMethods.keySet(), Matchers.hasItem(failureMethodName))

        String causeLinePrefix = "Caused by: "
        def failures = testMethods[failureMethodName].failure
        def cause = failures[0].text().readLines().find { it.startsWith causeLinePrefix }?.substring(causeLinePrefix.length())

        Assert.assertThat(cause, causeMatcher)
        this
    }

    TestClassExecutionResult assertTestSkipped(String name) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertTestsSkipped(String... testNames) {
        Map<String, Node> testMethods = findTests().findAll { name, element ->
            element."skipped".size() > 0 // Include only skipped test.
        }
        
        Assert.assertThat(testMethods.keySet(), Matchers.equalTo(testNames as Set))
        this
    }

    TestClassExecutionResult assertConfigMethodPassed(String name) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertConfigMethodFailed(String name) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
        def stdout = testClassNode.'system-out'[0].text();
        Assert.assertThat(stdout, matcher)
        this
    }

    TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
        def stderr = testClassNode.'system-err'[0].text();
        Assert.assertThat(stderr, matcher)
        this
    }

    TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
        def stderr = testCase(testCaseName).'system-err'[0].text();
        Assert.assertThat(stderr, matcher)
        this
    }

    TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
        def stderr = testCase(testCaseName).'system-out'[0].text();
        Assert.assertThat(stderr, matcher)
        this
    }

    private NodeChild testCase(String name) {
        testClassNode.testcase.find { it.@name == name }
    }

    private def findTests() {
        if (!checked) {
            Assert.assertThat(testClassNode.name(), Matchers.equalTo('testsuite'))
            Assert.assertThat(testClassNode.@name.text(), Matchers.equalTo(testClassName))
            Assert.assertThat(testClassNode.@tests.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@skipped.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@failures.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@errors.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@time.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@timestamp.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.@hostname.text(), Matchers.not(Matchers.equalTo('')))
            Assert.assertThat(testClassNode.properties.size(), Matchers.equalTo(1))
            testClassNode.testcase.each { node ->
                Assert.assertThat(node.@classname.text(), Matchers.equalTo(testClassName))
                Assert.assertThat(node.@name.text(), Matchers.not(Matchers.equalTo('')))
                Assert.assertThat(node.@time.text(), Matchers.not(Matchers.equalTo('')))
                node.failure.each { failure ->
                    Assert.assertThat(failure.@message.size(), Matchers.equalTo(1))
                    Assert.assertThat(failure.@type.text(), Matchers.not(Matchers.equalTo('')))
                    Assert.assertThat(failure.text(), Matchers.not(Matchers.equalTo('')))
                }
                def matcher = Matchers.equalTo(outputAssociation == TestResultOutputAssociation.WITH_TESTCASE ? 1 : 0)
                Assert.assertThat(node.'system-err'.size(), matcher)
                Assert.assertThat(node.'system-out'.size(), matcher)
            }
            if (outputAssociation == TestResultOutputAssociation.WITH_SUITE) {
                Assert.assertThat(testClassNode.'system-out'.size(), Matchers.equalTo(1))
                Assert.assertThat(testClassNode.'system-err'.size(), Matchers.equalTo(1))
            }
            checked = true
        }
        Map testMethods = [:]
        testClassNode.testcase.each { testMethods[it.@name.text()] = it }
        return testMethods
    }
}
