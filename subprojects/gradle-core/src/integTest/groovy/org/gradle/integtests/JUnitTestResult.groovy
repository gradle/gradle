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
import org.hamcrest.Matcher
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class JUnitTestResult implements TestResult {
    private final TestFile projectDir

    def JUnitTestResult(TestFile projectDir) {
        this.projectDir = projectDir
    }

    TestResult assertTestClassesExecuted(String... testClasses) {
        Map<String, File> classes = findClasses()
        assertThat(classes.keySet(), equalTo(testClasses as Set));
        this
    }

    TestResult assertTestsExecuted(String testClass, String... testNames) {
        throw new UnsupportedOperationException();
    }

    TestResult assertTestPassed(String testClass, String name) {
        Map<String, Node> testMethods = findTests(testClass)
        assertThat(testMethods.keySet(), hasItem(name))
        assertThat(testMethods[name].failure.size(), equalTo(0))
        this
    }

    TestResult assertTestFailed(String testClass, String name) {
        Map<String, Node> testMethods = findTests(testClass)
        assertThat(testMethods.keySet(), hasItem(name))
        assertThat(testMethods[name].failure.size(), equalTo(1))
        this
    }

    TestResult assertConfigMethodPassed(String testClass, String name) {
        throw new UnsupportedOperationException();
    }

    TestResult assertConfigMethodFailed(String testClass, String name) {
        throw new UnsupportedOperationException();
    }

    TestResult assertStdout(String testClass, Matcher<String> stdoutMatcher) {
        def testClassNode = findTestClass(testClass)
        def stdout = testClassNode.'system-out'[0].text();
        assertThat(stdout, stdoutMatcher)
        this
    }

    TestResult assertStderr(String testClass, Matcher<String> stdoutMatcher) {
        def testClassNode = findTestClass(testClass)
        def stdout = testClassNode.'system-err'[0].text();
        assertThat(stdout, stdoutMatcher)
        this
    }

    private def findTests(String testClass) {
        def testClassNode = findTestClass(testClass)
        Map testMethods = [:]
        testClassNode.testcase.each { testMethods[it.@name.text()] = it }
        return testMethods
    }

    private def findTestClass(String testClass) {
        def classes = findClasses()
        def classFile = classes.get(testClass)
        assertThat(classFile, notNullValue())
        return new XmlSlurper().parse(classFile)
    }

    private def findClasses() {
        projectDir.file('build/test-results').assertIsDir()
        projectDir.file('build/test-results/TESTS-TestSuites.xml').assertIsFile()
        projectDir.file('build/reports/tests/index.html').assertIsFile()

        Map<String, File> classes = [:]
        projectDir.file('build/test-results').eachFile { File file ->
            def matcher = (file.name =~ /TEST-(.+)\.xml/)
            if (matcher.matches()) {
                classes[matcher.group(1)] = file
            }
        }
        return classes
    }
}
