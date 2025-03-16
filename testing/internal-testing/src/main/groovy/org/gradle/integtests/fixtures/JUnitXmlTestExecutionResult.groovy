/*
 * Copyright 2012 the original author or authors.
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

import groovy.xml.XmlParser
import groovy.xml.XmlSlurper
import org.gradle.test.fixtures.file.TestFile

import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.core.StringStartsWith.startsWith
import static org.hamcrest.MatcherAssert.assertThat

class JUnitXmlTestExecutionResult implements TestExecutionResult {
    private final TestFile testResultsDir
    private final TestResultOutputAssociation outputAssociation

    def JUnitXmlTestExecutionResult(TestFile projectDir, String testResultsDir = 'build/test-results/test') {
        this(projectDir, TestResultOutputAssociation.WITH_SUITE, testResultsDir)
    }

    def JUnitXmlTestExecutionResult(TestFile projectDir, TestResultOutputAssociation outputAssociation, String testResultsDir = 'build/test-results/test') {
        this.outputAssociation = outputAssociation
        this.testResultsDir = projectDir.file(testResultsDir)
    }

    boolean hasJUnitXmlResults() {
        testResultsDir.assertIsDir()
        testResultsDir.list().length > 0
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        Map<String, File> classes = findClasses()
        assertThat(classes.keySet(), equalTo(testClasses as Set))
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        if (testResultsDir.exists()) {
            Map<String, File> classes = findClasses()
            assertThat(classes.keySet(), not(hasItems(testClasses)))
            this
        }
        return this
    }

    String fromFileToTestClass(File junitXmlFile) {
        def xml = new XmlSlurper().parse(junitXmlFile)
        xml.@'name'.text()
    }

    boolean testClassExists(String testClass) {
        def classes = findClasses()
        return (classes.keySet().contains(testClass))
    }

    boolean testClassDoesNotExist(String testClass) {
        if (!testResultsDir.exists()) {
            return true
        } else {
            return !testClassExists(testClass)
        }
    }

    TestClassExecutionResult testClass(String testClass) {
        return new JUnitTestClassExecutionResult(findTestClass(testClass), testClass, testClass, outputAssociation)
    }

    TestClassExecutionResult testClassStartsWith(String testClass) {
        def matching = findTestClassStartsWith(testClass)
        return new JUnitTestClassExecutionResult(matching[1], matching[0], matching[0], outputAssociation)
    }

    @Override
    int getTotalNumberOfTestClassesExecuted() {
        return findClasses().size()
    }

    private def findTestClass(String testClass) {
        def classes = findClasses()
        assertThat(classes.keySet(), hasItem(testClass))
        def classFile = classes.get(testClass)
        assertThat(classFile, notNullValue())
        return new XmlSlurper().parse(classFile)
    }

    private def findTestClassStartsWith(String testClass) {
        def classes = findClasses()
        assertThat(classes.keySet(), hasItem(startsWith(testClass)))
        def classEntry = classes.find { it.key.startsWith(testClass) }
        def classFile = classEntry.value
        assertThat(classFile, notNullValue())
        return [classEntry.key, new XmlSlurper().parse(classFile)]
    }

    private def findClasses() {
        testResultsDir.assertIsDir()

        Map<String, File> classes = [:]
        testResultsDir.eachFile { File file ->
            def matcher = (file.name=~/TEST-(.+)\.xml/)
            if (matcher.matches()) {
                classes[fromFileToTestClass(file)] = file
            }
        }
        return classes
    }

    Optional<String> getSuiteStandardOutput(String testClass) {
        def xmlRoot = getTestClassXmlDoc(testClass)
        def suiteStandardOut = xmlRoot.'system-out'
        return getNodeText(suiteStandardOut)
    }

    Optional<String> getSuiteStandardError(String testClass) {
        def xmlRoot = getTestClassXmlDoc(testClass)
        def suiteStandardErr = xmlRoot.'system-err'
        return getNodeText(suiteStandardErr)
    }

    Optional<String> getTestCaseStandardOutput(String testClass, String testCase) {
        def xmlRoot = getTestClassXmlDoc(testClass)
        def testCaseNode = xmlRoot.'testcase'.find { it.@classname = 'OkTest' && it.@name == testCase }
        def testCaseStandardOut = testCaseNode.'system-out'
        return getNodeText(testCaseStandardOut)
    }

    Optional<String> getTestCaseStandardError(String testClass, String testCase) {
        def xmlRoot = getTestClassXmlDoc(testClass)
        def testCaseNode = xmlRoot.'testcase'.find { it.@classname = 'OkTest' && it.@name == testCase }
        def testCaseStandardOut = testCaseNode.'system-err'
        return getNodeText(testCaseStandardOut)
    }

    private Node getTestClassXmlDoc(String testClass) {
        Map<String, File> classes = findClasses()
        def xmlTest = classes[testClass].text
        def doc = new XmlParser().parseText(xmlTest)

        return doc.tap {
            assert it.name() == 'testsuite'
            assert it["@name"] == testClass
        }
    }

    private Optional<String> getNodeText(NodeList nodeList) {
        assert nodeList.size() <= 1
        if (nodeList.isEmpty()) {
            return Optional.empty()
        } else {
            return Optional.of(nodeList.text())
        }
    }
}
