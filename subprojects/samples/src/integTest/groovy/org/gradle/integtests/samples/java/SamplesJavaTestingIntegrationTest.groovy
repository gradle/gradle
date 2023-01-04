/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.java

import groovy.xml.XmlSlurper
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

class SamplesJavaTestingIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Requires(TestPrecondition.JDK9_OR_LATER)
    @UsesSample("java/basic")
    def "can execute simple Java tests with #dsl dsl"() {
        given:
        configureExecuterForToolchains('17')
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then:
        result.assertTaskExecuted(":test")

        and:
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.PersonTest")
        assertTestsRunCount(xmlResults, 1)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/filtering")
    def "can execute a subset of tests with filtering with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "both tests in SomeIntegTest run and pass"
        def xmlResults = getTestResultsFileAsXml(dslDir, "SomeIntegTest")
        assertTestsRunCount(xmlResults, 2)

        and: "only the quickUiCheck test runs in SomeOtherTest"
        def xmlResults2 = getTestResultsFileAsXml(dslDir, "SomeOtherTest")
        assertTestsRunCount(xmlResults2, 1)
        assertTestRun(xmlResults2, "quickUiCheck")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("java/customDirs")
    def "can change the destination for test results and reports with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = fails("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the test results are in the custom directory"
        dslDir.file("build/my-test-results/test").directory
        dslDir.file("build/test-results/test").assertDoesNotExist()

        and: "the reports are in the custom directory"
        dslDir.file("my-reports").directory
        getStandardTestReportDir(dslDir, "", "").assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/testReport")
    def "can create a custom TestReport task with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test", "testReport")

        then: "the test task is executed"
        result.assertTaskExecuted(":core:test")
        result.assertTaskExecuted(":util:test")

        and: "an aggregate report is created"
        dslDir.file("build/reports/allTests/index.html").assertExists()

        and: "no test reports in the subprojects are created"
        getStandardTestReportDir(dslDir, "core", "").file("index.html").assertDoesNotExist()
        getStandardTestReportDir(dslDir, "util", "").file("index.html").assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/junit-categories")
    def "can filter tests by JUnit category with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only the 'A' tests are run"
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.junit.CategorizedJUnitTest")
        assertTestsRunCount(xmlResults, 1)
        xmlResults.testcase.find { it.@name == "a" }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/junitplatform-tagging")
    def "can filter tests by JUnit Platform tag with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only the fast tests are run"
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.TagTest")
        assertTestsRunCount(xmlResults, 1)
        assertTestRun(xmlResults, "fastTest()")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/testng-groups")
    def "can filter tests by TestNG group with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the unit tests are run"
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.testng.SimpleUnitTest")
        assertTestsRunCount(xmlResults, 1)

        and: "the integration tests aren't run"
        getTestResultsFile(dslDir, "org.gradle.testng.SimpleIntegrationTest").assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/junitplatform-jupiter")
    def "can run tests using JUnit Jupiter with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the tests are run"
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.JupiterTest")
        // This expected count includes the skipped test
        assertTestsRunCount(xmlResults, 5)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/junitplatform-mix")
    def "can run older JUnit tests with JUnit Jupiter with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "all the tests are run"
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.JupiterTest"),
            1)
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.JUnit4Test"),
            1)
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.JUnit3Test"),
            1)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/junitplatform-engine")
    def "can run JUnit Platform tests with a subset of engines with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only vintage tests are run"
        getTestResultsFile(dslDir, "org.gradle.junitplatform.JupiterTest").assertDoesNotExist()
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.junitplatform.JUnit4Test"),
            1)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/testng-preserveorder")
    def "can use the preserveOrder option with TestNG tests with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        // At this point, it seems too difficult to verify the order of the stdout
        // output, which is the only way to verify that the `preserveOrder` property
        // is having an effect.
        and: "both tests are run"
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.testng.Test1"),
            2)
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.testng.Test2"),
            2)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("testing/testng-groupbyinstances")
    def "can use the groupByInstances option with TestNG tests with #dsl dsl"() {
        given:
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "both tests are run"
        def xmlResults = getTestResultsFileAsXml(dslDir, "org.gradle.testng.TestFactory")
        assertTestsRunCount(xmlResults, 4)
        xmlResults.testcase.@name*.text() == ["test1", "test2", "test1", "test2"]

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    @UsesSample("java/basic")
    def "can run simple Java integration tests with #dsl dsl"() {
        given:
        configureExecuterForToolchains('17')
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds("test", "integrationTest")

        then:
        result.assertTaskOrder(":test", ":integrationTest")

        and:
        assertTestsRunCount(
            getTestResultsFileAsXml(dslDir, "org.gradle.PersonIntTest", "integrationTest"),
            1)

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    @UsesSample("java/basic")
    def "can skip the tests with an `onlyIf` condition with #dsl dsl"() {
        given:
        configureExecuterForToolchains('17')
        TestFile dslDir = sample.dir.file(dsl)

        when: "run first time to populate configuration cache if it is enabled"
        executer.inDirectory(dslDir).withArgument("-PmySkipTests")
        def result = succeeds("build")

        then:
        result.assertTaskSkipped(":test")

        when: "run second time to restore from configuration cache if it is enabled"
        executer.inDirectory(dslDir).withArgument("-PmySkipTests")
        def secondResult = succeeds("build")

        then:
        secondResult.assertTaskSkipped(":test")

        where:
        dsl << ['groovy', 'kotlin']
    }

    /**
     * Loads the JUnit XML test results file for the given, named test case. It
     * assumes the file path to that file and loads it using Groovy's XmlSlurper
     * which can then be used to extract information from the XML.
     */
    private static getTestResultsFileAsXml(TestFile sampleDir, String testClassName, String taskName = "test") {
        return new XmlSlurper().parse(getTestResultsFile(sampleDir, testClassName, taskName))
    }

    /**
     * Returns the {@code TestFile} instance representing the required JUnit test
     * results file. Assumes the standard test results directory.
     */
    private static TestFile getTestResultsFile(TestFile sampleDir, String testClassName, String taskName = "test") {
        return sampleDir.file("build/test-results/$taskName/TEST-${testClassName}.xml")
    }

    /**
     * Returns the conventional location of the test reports for the given project
     * and source set. If the arguments are {@code null} or empty strings, then the
     * "test" source set and root project are used as the default values.
     */
    private static TestFile getStandardTestReportDir(TestFile sampleDir, String project, String sourceSet) {
        String path = "build/reports/tests/${sourceSet ?: 'test'}"
        if (project) {
            path = project + '/' + path
        }
        return sampleDir.file(path)
    }

    private static void assertTestsRunCount(resultsXml, int expectedCount) {
        assert resultsXml.@tests == expectedCount
    }

    private static void assertTestRun(resultsXml, String testName) {
        assert resultsXml.testcase.find { it.@name == testName }
    }
}
