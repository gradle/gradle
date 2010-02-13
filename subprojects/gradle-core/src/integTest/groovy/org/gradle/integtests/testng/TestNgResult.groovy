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

import org.gradle.util.TestFile
import org.gradle.integtests.TestResult
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import groovy.util.slurpersupport.GPathResult

class TestNgResult implements TestResult {
    private final TestFile projectDir
    private final GPathResult resultsXml

    def TestNgResult(projectDir) {
        this.projectDir = projectDir;
        resultsXml = new XmlSlurper().parse(projectDir.file('build/reports/tests/testng-results.xml').assertIsFile())
    }

    TestResult assertTestClassesExecuted(String ... testClasses) {
        projectDir.file('build/reports/tests/index.html').assertIsFile()
        def actualTestClasses = findTestClasses().keySet()
        assertThat(actualTestClasses, equalTo(testClasses as Set))
        this
    }

    TestResult assertTestsExecuted(String testClass, String ... testNames) {
        def actualTestMethods = findTestMethods(testClass).keySet()
        assertThat(actualTestMethods, equalTo(testNames as Set))
        this
    }

    TestResult assertTestPassed(String testClass, String testName) {
        def testMethodNode = findTestMethod(testClass, testName)
        assertEquals('PASS', testMethodNode.@status as String)
        this
    }

    TestResult assertTestFailed(String testClass, String testName) {
        def testMethodNode = findTestMethod(testClass, testName)
        assertEquals('FAIL', testMethodNode.@status as String)
        this
    }

    private def findTestMethod(String testClass, String testName) {
        def testMethods = findTestMethods(testClass)
        if (!testMethods.containsKey(testName)) {
            fail("Could not find test ${testClass}.${testName}. Found ${testMethods.keySet()}")
        }
        testMethods[testName]
    }

    private def findTestMethods(String testClass) {
        def testClassNode = findTestClass(testClass)
        Map testMethods = [:]
        testClassNode.'test-method'.findAll{ it.'@is-config' != 'true' }.each {
            testMethods.put(it.@name as String, it)
        }
        testMethods
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
