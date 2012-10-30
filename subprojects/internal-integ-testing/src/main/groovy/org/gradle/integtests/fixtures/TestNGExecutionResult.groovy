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

import groovy.util.slurpersupport.GPathResult
import org.gradle.util.TestFile
import org.hamcrest.Matcher

/**
 * by Szczepan Faber, created at: 11/3/11
 */
class TestNGExecutionResult implements TestExecutionResult {
    private final TestFile projectDir
    private GPathResult resultsXml
    public static final String DEFAULT_TESTNG_REPORT = "build/reports/tests"

    def TestNGExecutionResult(projectDir) {
        this.projectDir = projectDir;
    }

    boolean hasTestNGXmlResults() {
        xmlReportFile().isFile()
    }

    boolean hasHtmlResults() {
        htmlReportFile().isFile()
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        parseResults()
        htmlReportFile().assertIsFile()
        def actualTestClasses = findTestClasses().keySet()
        org.junit.Assert.assertThat(actualTestClasses, org.hamcrest.Matchers.equalTo(testClasses as Set))
        this
    }

    private TestFile htmlReportFile() {
        projectDir.file('build/reports/tests/index.html')
    }

    TestClassExecutionResult testClass(String testClass) {
        parseResults()
        return new org.gradle.integtests.fixtures.TestNgTestClassExecutionResult(testClass, findTestClass(testClass))
    }

    private void parseResults() {
        resultsXml = new XmlSlurper().parse(xmlReportFile().assertIsFile())
    }

    private TestFile xmlReportFile() {
        projectDir.file("$DEFAULT_TESTNG_REPORT/testng-results.xml")
    }

    private def findTestClass(String testClass) {
        def testClasses = findTestClasses()
        if (!testClasses.containsKey(testClass)) {
            org.junit.Assert.fail("Could not find test class ${testClass}. Found ${testClasses.keySet()}")
        }
        testClasses[testClass]
    }

    private def findTestClasses() {
        Map testClasses = [:]
        resultsXml.suite.test.'class'.each {
            testClasses.put(it.@name as String, it)
        }
        testClasses
    }
}

private class TestNgTestClassExecutionResult implements TestClassExecutionResult {
    def String testClass
    def GPathResult testClassNode

    def TestNgTestClassExecutionResult(String testClass, GPathResult resultXml) {
        this.testClass = testClass
        this.testClassNode = resultXml
    }

    TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
        throw new RuntimeException("Unsupported. Implement if you need it.");
    }

    TestClassExecutionResult assertTestsExecuted(String... testNames) {
        def actualTestMethods = findTestMethods().keySet()
        org.junit.Assert.assertThat(actualTestMethods, org.hamcrest.Matchers.equalTo(testNames as Set))
        this
    }

    TestClassExecutionResult assertTestPassed(String name) {
        def testMethodNode = findTestMethod(name)
        org.junit.Assert.assertEquals('PASS', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertTestsSkipped(String... testNames) {
        throw new UnsupportedOperationException()
    }

    TestClassExecutionResult assertTestSkipped(String name) {
        def testMethodNode = findTestMethod(name)
        org.junit.Assert.assertEquals('SKIP', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
        def testMethodNode = findTestMethod(name)
        org.junit.Assert.assertEquals('FAIL', testMethodNode.@status as String)

        def exceptions = testMethodNode.exception
        org.junit.Assert.assertThat(exceptions.size(), org.hamcrest.Matchers.equalTo(messageMatchers.length))

        for (int i = 0; i < messageMatchers.length; i++) {
            org.junit.Assert.assertThat(exceptions[i].message[0].text().trim(), messageMatchers[i])
        }
        this
    }

    TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
        throw new UnsupportedOperationException();
    }

    TestClassExecutionResult assertConfigMethodPassed(String name) {
        def testMethodNode = findConfigMethod(name)
        org.junit.Assert.assertEquals('PASS', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertConfigMethodFailed(String name) {
        def testMethodNode = findConfigMethod(name)
        org.junit.Assert.assertEquals('FAIL', testMethodNode.@status as String)
        this
    }

    private def findConfigMethod(String testName) {
        def testMethods = findConfigMethods()
        if (!testMethods.containsKey(testName)) {
            org.junit.Assert.fail("Could not find configuration method ${testClass}.${testName}. Found ${testMethods.keySet()}")
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
            org.junit.Assert.fail("Could not find test ${testClass}.${testName}. Found ${testMethods.keySet()}")
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