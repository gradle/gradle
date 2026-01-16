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

import com.google.common.collect.ListMultimap
import com.google.common.collect.MultimapBuilder
import groovy.xml.XmlParser
import groovy.xml.XmlSlurper
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.CollectionUtils

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.core.StringStartsWith.startsWith

class JUnitXmlTestExecutionResult implements TestExecutionResult {
    private final TestFile testResultsDir
    private final TestResultOutputAssociation outputAssociation

    JUnitXmlTestExecutionResult(TestFile projectDir, String testResultsDir = 'build/test-results/test') {
        this(projectDir, TestResultOutputAssociation.WITH_SUITE, testResultsDir)
    }

    JUnitXmlTestExecutionResult(TestFile projectDir, TestResultOutputAssociation outputAssociation, String testResultsDir = 'build/test-results/test') {
        this.outputAssociation = outputAssociation
        this.testResultsDir = projectDir.file(testResultsDir)
    }

    boolean hasJUnitXmlResults() {
        testResultsDir.assertIsDir()
        testResultsDir.list().length > 0
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        Set<String> classes = findClasses().keySet()
        assertThat(classes, equalTo(testClasses as Set))
        return this
    }

    TestExecutionResult assertTestClassesNotExecuted(String... testClasses) {
        if (testResultsDir.exists()) {
            Set<String> classes = findClasses().keySet()
            assertThat(classes, not(hasItems(testClasses)))
            this
        }
        return this
    }

    String fromFileToTestClass(File junitXmlFile) {
        def xml = new XmlSlurper().parse(junitXmlFile)
        xml.@'name'.text()
    }

    boolean testClassExists(String testClass) {
        Set<String> classes = findClasses().keySet()
        return (classes.contains(testClass))
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

    TestClassExecutionResult testClass(String testClass, int run) {
        def classes = findClasses()
        assertThat(classes.keySet(), hasItem(testClass))
        def classFiles = classes.get(testClass).toSorted()
        assert classFiles.size() >= run

        def classFile = classFiles[run]
        def xml = new XmlSlurper().parse(classFile)

        return new JUnitTestClassExecutionResult(xml, testClass, testClass, outputAssociation)
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
        def classFile = CollectionUtils.single(classes.get(testClass))
        assertThat(classFile, notNullValue())
        return new XmlSlurper().parse(classFile)
    }

    private def findTestClassStartsWith(String testClass) {
        def classes = findClasses()
        assertThat(classes.keySet(), hasItem(startsWith(testClass)))
        def classEntry = classes.asMap().find { it.key.startsWith(testClass) }
        def classFile = CollectionUtils.single(classEntry.value)
        assertThat(classFile, notNullValue())
        return [classEntry.key, new XmlSlurper().parse(classFile)]
    }

    private def findClasses() {
        testResultsDir.assertIsDir()

        ListMultimap<String, File> classes = MultimapBuilder.linkedHashKeys().arrayListValues().build()

        testResultsDir.eachFile { File file ->
            def matcher = (file.name=~/TEST-(.+)\.xml/)
            if (matcher.matches()) {
                classes.put(fromFileToTestClass(file), file)
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
        def classes = findClasses()
        def xmlTest = CollectionUtils.single(classes.get(testClass)).text
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
