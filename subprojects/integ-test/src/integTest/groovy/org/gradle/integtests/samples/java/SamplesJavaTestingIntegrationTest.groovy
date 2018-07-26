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

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

class SamplesJavaTestingIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("userguide/java/basic")
    def "can execute simple Java tests"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then:
        result.assertTaskExecuted(":test")

        and:
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.PersonTest")
        assertTestsRunCount(xmlResults, 1)
    }

    @UsesSample("testing/filtering")
    def "can execute a subset of tests with filtering"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "both tests in SomeIntegTest run and pass"
        def xmlResults = getTestResultsFileAsXml(sample, "SomeIntegTest")
        assertTestsRunCount(xmlResults, 2)

        and: "only the quickUiCheck test runs in SomeOtherTest"
        def xmlResults2 = getTestResultsFileAsXml(sample, "SomeOtherTest")
        assertTestsRunCount(xmlResults2, 1)
        assertTestRun(xmlResults2, "quickUiCheck")
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("userguide/java/customDirs")
    def "can change the destination for test results and reports"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = fails("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the test results are in the custom directory"
        sample.dir.file("build/my-test-results/test").directory
        sample.dir.file("build/test-results/test").assertDoesNotExist()

        and: "the reports are in the custom directory"
        sample.dir.file("my-reports").directory
        getStandardTestReportDir("", "").assertDoesNotExist()
    }

    @UsesSample("testing/testReport")
    def "can create a custom TestReport task"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test", "testReport")

        then: "the test task is executed"
        result.assertTaskExecuted(":core:test")
        result.assertTaskExecuted(":util:test")

        and: "an aggregate report is created"
        sample.dir.file("build/reports/allTests/index.html").assertExists()

        and: "no test reports in the subprojects are created"
        getStandardTestReportDir("core", "").file("index.html").assertDoesNotExist()
        getStandardTestReportDir("util", "").file("index.html").assertDoesNotExist()
    }

    @UsesSample("testing/junit/categories")
    def "can filter tests by JUnit category"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only the 'A' tests are run"
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.junit.CategorizedJUnitTest")
        assertTestsRunCount(xmlResults, 1)
        xmlResults.testcase.find { it.@name == "a" }
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("testing/junitplatform/tagging")
    def "can filter tests by JUnit Platform tag"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only the fast tests are run"
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.junitplatform.TagTest")
        assertTestsRunCount(xmlResults, 1)
        assertTestRun(xmlResults, "fastTest()")
    }

    @UsesSample("testing/testng/groups")
    def "can filter tests by TestNG group"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the unit tests are run"
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.testng.SimpleUnitTest")
        assertTestsRunCount(xmlResults, 1)

        and: "the integration tests aren't run"
        getTestResultsFile(sample, "org.gradle.testng.SimpleIntegrationTest").assertDoesNotExist()
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("testing/junitplatform/jupiter")
    def "can run tests using JUnit Jupiter"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "the tests are run"
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.junitplatform.JupiterTest")
        // This expected count includes the skipped test
        assertTestsRunCount(xmlResults, 5)
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("testing/junitplatform/mix")
    def "can run older JUnit tests with JUnit Jupiter"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "all the tests are run"
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.junitplatform.JupiterTest"),
            1)
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.junitplatform.JUnit4Test"),
            1)
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.junitplatform.JUnit3Test"),
            1)
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("testing/junitplatform/engine")
    def "can run JUnit Platform tests with a subset of engines"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "only vintage tests are run"
        getTestResultsFile(sample, "org.gradle.junitplatform.JupiterTest").assertDoesNotExist()
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.junitplatform.JUnit4Test"),
            1)
    }

    @UsesSample("testing/testng/preserveorder")
    def "can use the preserveOrder option with TestNG tests"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        // At this point, it seems too difficult to verify the order of the stdout
        // output, which is the only way to verify that the `preserveOrder` property
        // is having an effect.
        and: "both tests are run"
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.testng.Test1"),
            2)
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.testng.Test2"),
            2)
    }

    @UsesSample("testing/testng/groupbyinstances")
    def "can use the groupByInstances option with TestNG tests"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test")

        then: "the test task is executed"
        result.assertTaskExecuted(":test")

        and: "both tests are run"
        def xmlResults = getTestResultsFileAsXml(sample, "org.gradle.testng.TestFactory")
        assertTestsRunCount(xmlResults, 4)
        xmlResults.testcase.@name*.text() == ["test1", "test2", "test1", "test2"]
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("userguide/java/basic")
    def "can run simple Java integration tests"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        def result = succeeds("test", "integrationTest")

        then:
        result.assertTaskExecuted(":test")
        result.assertTaskExecuted(":integrationTest")
        result.executedTasks.indexOf(":test") < result.executedTasks.indexOf(":integrationTest")

        and:
        assertTestsRunCount(
            getTestResultsFileAsXml(sample, "org.gradle.PersonIntTest", "integrationTest"),
            1)
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    @UsesSample("userguide/java/basic")
    def "can skip the tests with an `onlyIf` condition"() {
        given:
        executer.inDirectory(sample.dir).withArgument("-PmySkipTests")

        when:
        def result = succeeds("build")

        then:
        result.assertTaskSkipped(":test")
    }

    /**
     * Loads the JUnit XML test results file for the given, named test case. It
     * assumes the file path to that file and loads it using Groovy's XmlSlurper
     * which can then be used to extract information from the XML.
     */
    private getTestResultsFileAsXml(Sample sample, String testClassName, String taskName = "test") {
        return new XmlSlurper().parse(getTestResultsFile(sample, testClassName, taskName))
    }

    /**
     * Returns the {@code TestFile} instance representing the required JUnit test
     * results file. Assumes the standard test results directory.
     */
    private TestFile getTestResultsFile(Sample sample, String testClassName, String taskName = "test") {
        return sample.dir.file("build/test-results/$taskName/TEST-${testClassName}.xml")
    }

    /**
     * Returns the conventional location of the test reports for the given project
     * and source set. If the arguments are {@code null} or empty strings, then the
     * "test" source set and root project are used as the default values.
     */
    private TestFile getStandardTestReportDir(String project, String sourceSet) {
        String path = "build/reports/tests/${sourceSet ?: 'test'}"
        if (project) {
            path = project + '/' + path
        }
        return sample.dir.file(path)
    }

    private void assertTestsRunCount(resultsXml, int expectedCount) {
        assert resultsXml.@tests == expectedCount
    }

    private void assertTestRun(resultsXml, String testName) {
        assert resultsXml.testcase.find { it.@name == testName }
    }
}
