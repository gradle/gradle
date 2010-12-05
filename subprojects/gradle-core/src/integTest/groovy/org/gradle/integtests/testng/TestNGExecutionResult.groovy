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


package org.gradle.integtests.testng

import groovy.util.slurpersupport.GPathResult
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.util.TestFile
import org.hamcrest.Matcher
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class TestNGExecutionResult implements TestExecutionResult {
    private final TestFile projectDir
    private final GPathResult resultsXml

    def TestNGExecutionResult(projectDir) {
        this.projectDir = projectDir;
        resultsXml = new XmlSlurper().parse(projectDir.file('build/reports/tests/testng-results.xml').assertIsFile())
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        projectDir.file('build/reports/tests/index.html').assertIsFile()
        def actualTestClasses = findTestClasses().keySet()
        assertThat(actualTestClasses, equalTo(testClasses as Set))
        this
    }

    TestClassExecutionResult testClass(String testClass) {
        return new TestNgTestClassExecutionResult(testClass, findTestClass(testClass))
    }

    private def findTestClass(String testClass) {
        def testClasses = findTestClasses()
        if (!testClasses.containsKey(testClass)) {
            fail("Could not find test class ${testClass}. Found ${testClasses.keySet()}")
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

    TestClassExecutionResult assertTestsExecuted(String... testNames) {
        def actualTestMethods = findTestMethods().keySet()
        assertThat(actualTestMethods, equalTo(testNames as Set))
        this
    }

    TestClassExecutionResult assertTestPassed(String name) {
        def testMethodNode = findTestMethod(name)
        assertEquals('PASS', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertTestSkipped(String name) {
        def testMethodNode = findTestMethod(name)
        assertEquals('SKIP', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
        def testMethodNode = findTestMethod(name)
        assertEquals('FAIL', testMethodNode.@status as String)

        def exceptions = testMethodNode.exception
        assertThat(exceptions.size(), equalTo(messageMatchers.length))

        for (int i = 0; i < messageMatchers.length; i++) {
            assertThat(exceptions[i].message[0].text().trim(), messageMatchers[i])
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
        assertEquals('PASS', testMethodNode.@status as String)
        this
    }

    TestClassExecutionResult assertConfigMethodFailed(String name) {
        def testMethodNode = findConfigMethod(name)
        assertEquals('FAIL', testMethodNode.@status as String)
        this
    }

    private def findConfigMethod(String testName) {
        def testMethods = findConfigMethods()
        if (!testMethods.containsKey(testName)) {
            fail("Could not find configuration method ${testClass}.${testName}. Found ${testMethods.keySet()}")
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
            fail("Could not find test ${testClass}.${testName}. Found ${testMethods.keySet()}")
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
