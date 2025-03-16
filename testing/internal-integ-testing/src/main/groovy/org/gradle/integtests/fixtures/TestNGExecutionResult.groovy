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

package org.gradle.integtests.fixtures

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matcher

import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class TestNGExecutionResult implements TestExecutionResult {
    private final TestFile projectDir
    private GPathResult resultsXml
    public static final String DEFAULT_TESTNG_REPORT = "build/reports/tests/test"
    private final String outputDirectory

    TestNGExecutionResult(projectDir, String outputDirectory = DEFAULT_TESTNG_REPORT) {
        this.projectDir = projectDir
        this.outputDirectory = outputDirectory
    }

    boolean hasTestNGXmlResults() {
        xmlReportFile().isFile()
    }

    boolean hasJUnitResultsGeneratedByTestNG() {
        def dir = projectDir.file("$outputDirectory/junitreports")
        dir.isDirectory() && dir.list().length > 0
    }

    boolean hasHtmlResults() {
        htmlReportFile().isFile()
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        Set actualTestClasses = getExecutedTestClasses()
        assert actualTestClasses == testClasses as Set
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        Set actualTestClasses = getExecutedTestClasses()
        assertThat(actualTestClasses, not(hasItems(testClasses)))
        return this
    }

    private Set<String> getExecutedTestClasses() {
        parseResults()
        htmlReportFile().assertIsFile()
        def actualTestClasses = findTestClasses().keySet()
        actualTestClasses
    }

    private TestFile htmlReportFile() {
        projectDir.file("$outputDirectory/index.html")
    }

    boolean testClassExists(String testClass) {
        throw new UnsupportedOperationException("Unsupported. Implement if you need it.")
    }

    boolean testClassDoesNotExist(String testClass) {
        throw new UnsupportedOperationException("Unsupported. Implement if you need it.")
    }

    TestClassExecutionResult testClass(String testClass) {
        parseResults()
        return new TestNgTestClassExecutionResult(testClass, findTestClass(testClass))
    }

    @Override
    TestClassExecutionResult testClassStartsWith(String testClass) {
        def matching = findTestClassStartsWith(testClass)
        return new TestNgTestClassExecutionResult(matching.key, matching.value)
    }

    @Override
    int getTotalNumberOfTestClassesExecuted() {
        return getExecutedTestClasses().size()
    }

    private void parseResults() {
        resultsXml = new XmlSlurper().parse(xmlReportFile().assertIsFile())
    }

    private TestFile xmlReportFile() {
        projectDir.file("$outputDirectory/testng-results.xml")
    }

    private def findTestClass(String testClass) {
        def testClasses = findTestClasses()
        if (!testClasses.containsKey(testClass)) {
            throw new AssertionError("Could not find test class ${testClass}. Found ${testClasses.keySet()}")
        }
        testClasses[testClass]
    }

    private def findTestClassStartsWith(String testClass) {
        def testClasses = findTestClasses()
        def matching = testClasses.find { it.key.startsWith(testClass) }
        if (!matching) {
            throw new AssertionError("Could not find test class matching ${testClass}. Found ${testClasses.keySet()}")
        }
        matching
    }

    private def findTestClasses() {
        Map testClasses = [:]
        resultsXml.suite.test.'class'.each {
            testClasses.put(it.@name as String, it)
        }
        testClasses
    }
}

class TestNgTestClassExecutionResult implements TestClassExecutionResult {
    def String testClass
    def GPathResult testClassNode

    def TestNgTestClassExecutionResult(String testClass, GPathResult resultXml) {
        this.testClass = testClass
        this.testClassNode = resultXml
    }

    TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
        throw new RuntimeException("Unsupported. Implement if you need it.");
    }

    int getTestCount() {
        throw new UnsupportedOperationException("Unsupported.  Implement if you need it.")
    }

    TestClassExecutionResult assertTestsExecuted(String... testNames) {
        def actualTestMethods = findTestMethods().keySet()
        assert actualTestMethods == testNames as Set
        this
    }

    @Override
    TestClassExecutionResult assertTestsExecuted(TestCase ... testCases) {
        throw new UnsupportedOperationException("Unsupported.  Implement if you need it.")
    }

    TestClassExecutionResult assertTestPassed(String name) {
        def testMethodNode = findTestMethod(name)
        assert testMethodNode.@status as String == 'PASS'
        this
    }

    @Override
    TestClassExecutionResult assertTestFailed(String name, String displayName, Matcher<? super String>... messageMatchers) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertTestsSkipped(String... testNames) {
        throw new UnsupportedOperationException()
    }

    @Override
    TestClassExecutionResult assertTestPassed(String name, String displayName) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertTestSkipped(String name) {
        def testMethodNode = findTestMethod(name)
        assert testMethodNode.@status as String == 'SKIP'
        this
    }

    int getTestSkippedCount() {
        throw new UnsupportedOperationException("Unsupported.  Implement if you need it.")
    }

    @Override
    TestClassExecutionResult assertTestFailedIgnoreMessages(String name) {
        def testMethodNode = findTestMethod(name)
        assert testMethodNode.@status as String == 'FAIL'
        return this
    }

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
        def testMethodNode = findTestMethod(name)
        assert testMethodNode.@status as String == 'FAIL'

        def exceptions = testMethodNode.exception
        assert exceptions.size() == messageMatchers.length

        for (int i = 0; i < messageMatchers.length; i++) {
            assert messageMatchers[i].matches(exceptions[i].message[0].text().trim())
        }
        this
    }

    boolean testFailed(String name, Matcher<? super String>... messageMatchers) {
        throw new UnsupportedOperationException("Unsupported.  Implement if you need it.")
    }

    @Override
    TestClassExecutionResult assertTestSkipped(String name, String displayName) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertTestCaseStdout(String testCaseName, Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertTestCaseStderr(String testCaseName, Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    TestClassExecutionResult assertExecutionFailedWithCause(Matcher<? super String> causeMatcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    TestClassExecutionResult assertDisplayName(String classDisplayName) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertConfigMethodPassed(String name) {
        def testMethodNode = findConfigMethod(name)
        assert testMethodNode.@status as String == 'PASS'
        this
    }

    TestClassExecutionResult assertConfigMethodFailed(String name) {
        def testMethodNode = findConfigMethod(name)
        assert testMethodNode.@status as String == 'FAIL'
        this
    }

    private def findConfigMethod(String testName) {
        def testMethods = findConfigMethods()
        if (!testMethods.containsKey(testName)) {
            throw new AssertionError("Could not find configuration method ${testClass}.${testName}. Found ${testMethods.keySet()}")
        }
        testMethods[testName]
    }

    private def findConfigMethods() {
        Map testMethods = [:]
        testClassNode.'test-method'.findAll { it.'@is-config' == 'true' }.each {
            testMethods.put(it.@name as String, it)
        }
        testMethods
    }

    private def findTestMethod(String testName) {
        def testMethods = findTestMethods()
        if (!testMethods.containsKey(testName)) {
            throw new AssertionError("Could not find test ${testClass}.${testName}. Found ${testMethods.keySet()}")
        }
        testMethods[testName]
    }

    private def findTestMethods() {
        Map testMethods = [:]
        testClassNode.'test-method'.findAll { it.'@is-config' != 'true' }.each {
            testMethods.put(it.@name as String, it)
        }
        testMethods
    }

}
