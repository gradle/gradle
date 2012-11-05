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
package org.gradle.integtests.fixtures

import groovy.util.slurpersupport.GPathResult
import org.gradle.util.TestFile
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class JUnitTestExecutionResult implements TestExecutionResult {
    private final TestFile buildDir

    def JUnitTestExecutionResult(TestFile projectDir, String buildDirName = 'build') {
        this.buildDir = projectDir.file(buildDirName)
    }

    boolean hasJUnitXmlResults() {
        xmlResultsDir().list().length > 0
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        Map<String, File> classes = findClasses()
        assertThat(classes.keySet(), equalTo(testClasses as Set));
        this
    }

    TestClassExecutionResult testClass(String testClass) {
        return new JUnitTestClassExecutionResult(findTestClass(testClass), testClass)
    }

    private def findTestClass(String testClass) {
        def classes = findClasses()
        assertThat(classes.keySet(), hasItem(testClass))
        def classFile = classes.get(testClass)
        assertThat(classFile, notNullValue())
        return new XmlSlurper().parse(classFile)
    }

    private def findClasses() {
        xmlResultsDir().assertIsDir()
        buildDir.file('reports/tests/index.html').assertIsFile()

        Map<String, File> classes = [:]
        buildDir.file('test-results').eachFile { File file ->
            def matcher = (file.name =~ /TEST-(.+)\.xml/)
            if (matcher.matches()) {
                classes[matcher.group(1)] = file
            }
        }
        return classes
    }

    private TestFile xmlResultsDir() {
        buildDir.file('test-results')
    }
}

class JUnitTestClassExecutionResult implements TestClassExecutionResult {
    GPathResult testClassNode
    String testClassName
    boolean checked

    def JUnitTestClassExecutionResult(GPathResult testClassNode, String testClassName) {
        this.testClassNode = testClassNode
        this.testClassName = testClassName
    }

    TestClassExecutionResult assertTestsExecuted(String... testNames) {
        Map<String, Node> testMethods = findTests()
        assertThat(testMethods.keySet(), equalTo(testNames as Set))
        this
    }

    TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
        assert testClassNode.@tests == tests
        assert testClassNode.@failures == failures
        assert testClassNode.@errors == errors
        this
    }

    TestClassExecutionResult assertTestPassed(String name) {
        Map<String, Node> testMethods = findTests()
        assertThat(testMethods.keySet(), hasItem(name))
        assertThat(testMethods[name].failure.size(), equalTo(0))
        this
    }

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
        Map<String, Node> testMethods = findTests()
        assertThat(testMethods.keySet(), hasItem(name))

        def failures = testMethods[name].failure
        assertThat("Expected ${messageMatchers.length} failures. Found: $failures", failures.size(), equalTo(messageMatchers.length))

        for (int i = 0; i < messageMatchers.length; i++) {
            assertThat(failures[i].@message.text(), messageMatchers[i])
        }
        this
    }

    TestClassExecutionResult assertTestSkipped(String name) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertTestsSkipped(String... testNames) {
        Map<String, Node> testMethods = findIgnoredTests()
        assertThat(testMethods.keySet(), equalTo(testNames as Set))
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
        assertThat(stdout, matcher)
        this
    }

    TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
        def stderr = testClassNode.'system-err'[0].text();
        assertThat(stderr, matcher)
        this
    }

    private def findTests() {
        if (!checked) {
            assertThat(testClassNode.name(), equalTo('testsuite'))
            assertThat(testClassNode.@name.text(), equalTo(testClassName))
            assertThat(testClassNode.@tests.text(), not(equalTo('')))
            assertThat(testClassNode.@failures.text(), not(equalTo('')))
            assertThat(testClassNode.@errors.text(), not(equalTo('')))
            assertThat(testClassNode.@time.text(), not(equalTo('')))
            assertThat(testClassNode.@timestamp.text(), not(equalTo('')))
            assertThat(testClassNode.@hostname.text(), not(equalTo('')))
            assertThat(testClassNode.properties.size(), equalTo(1))
            testClassNode.testcase.each { node ->
                assertThat(node.@classname.text(), equalTo(testClassName))
                assertThat(node.@name.text(), not(equalTo('')))
                assertThat(node.@time.text(), not(equalTo('')))
                node.failure.each { failure ->
                    assertThat(failure.@message.size(), equalTo(1))
                    assertThat(failure.@type.text(), not(equalTo('')))
                    assertThat(failure.text(), not(equalTo('')))
                }
            }
            assertThat(testClassNode.'system-out'.size(), equalTo(1))
            assertThat(testClassNode.'system-err'.size(), equalTo(1))
            checked = true
        }
        Map testMethods = [:]
        testClassNode.testcase.each { testMethods[it.@name.text()] = it }
        return testMethods
    }

    private def findIgnoredTests() {
        Map testMethods = [:]
        testClassNode."ignored-testcase".each { testMethods[it.@name.text()] = it }
        return testMethods
    }
}
