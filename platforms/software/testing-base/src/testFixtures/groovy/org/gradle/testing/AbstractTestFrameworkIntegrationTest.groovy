/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.hamcrest.CoreMatchers
import org.junit.Assume

abstract class AbstractTestFrameworkIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {

    abstract void createPassingFailingTest()

    abstract void createEmptyProject()

    abstract void renameTests()

    abstract String getTestTaskName()

    /**
     * The name of the passing test method, without any parentheses.
     *
     * @return the name of the passing test method
     */
    abstract String getPassingTestMethodName()

    /**
     * The name of the failing test method, without any parentheses.
     *
     * @return the name of the failing test method
     */
    abstract String getFailingTestMethodName()

    private final String getPassingTestCaseName() {
        return getTestFramework().getTestCaseName(getPassingTestMethodName())
    }

    private final String getFailingTestCaseName() {
        return getTestFramework().getTestCaseName(getFailingTestMethodName())
    }

    abstract TestFramework getTestFramework()

    String testSuite(String testSuite) {
        return testSuite
    }

    def "can listen for test results"() {
        given:
        createPassingFailingTest()

        buildFile << """
            def listener = new TestListenerImpl()
            tasks.withType(AbstractTestTask) {
                addTestListener(listener)
                ignoreFailures = true
            }
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START Test Suite [\$suite.className] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH Test Suite [\$suite.className] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START Test Case [\$test.className] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH Test Case [\$test.className] [\$test.name] [\$result.resultType] [\$result.testCount]" }
            }
        """

        when:
        succeeds "check"

        then:

        outputContains "START Test Suite [null] [Gradle Test Run :$testTaskName]"
        outputContains "FINISH Test Suite [null] [Gradle Test Run :$testTaskName] [FAILURE] [3]"

        outputContains "START Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "FINISH Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "START Test Case [SomeOtherTest] [$passingTestCaseName]"
        outputContains "FINISH Test Case [SomeOtherTest] [$passingTestCaseName] [SUCCESS] [1]"

        outputContains "START Test Suite [SomeTest] [SomeTest]"
        outputContains "FINISH Test Suite [SomeTest] [SomeTest]"
        outputContains "START Test Case [SomeTest] [$failingTestCaseName]"
        outputContains "FINISH Test Case [SomeTest] [$failingTestCaseName] [FAILURE] [1]"
        outputContains "START Test Case [SomeTest] [$passingTestCaseName]"
        outputContains "FINISH Test Case [SomeTest] [$passingTestCaseName] [SUCCESS] [1]"
    }

    def "test results conventions are consistent"() {
        given:
        createPassingFailingTest()

        buildFile << """
            task verifyTestResultConventions {
                def junitXmlOutputLocation = provider { ${testTaskName}.reports.junitXml.outputLocation }
                def htmlOutputLocation = provider { ${testTaskName}.reports.html.outputLocation }
                def binaryResultsDirectory = provider { ${testTaskName}.binaryResultsDirectory }
                def buildDir = layout.buildDirectory
                def expectedJunitXmlOutputLocation = file('build/test-results/${testTaskName}')
                def expectedHtmlOutputLocation = file('build/reports/tests/${testTaskName}')
                def expectedBinaryResultsDirectory = file('build/test-results/${testTaskName}/binary')
                doLast {
                    assert junitXmlOutputLocation.flatMap { it.asFile }.get() == expectedJunitXmlOutputLocation
                    assert htmlOutputLocation.flatMap { it.asFile }.get() == expectedHtmlOutputLocation
                    assert binaryResultsDirectory.flatMap { it.asFile }.get() == expectedBinaryResultsDirectory
                }
            }
        """

        expect:
        succeeds "verifyTestResultConventions"
    }

    def "test results show passing and failing tests"() {
        given:
        createPassingFailingTest()

        buildFile << """
            tasks.withType(AbstractTestTask) {
                ignoreFailures = true
            }
        """

        when:
        succeeds "check"

        then:
        testResult.assertAtLeastTestPathsExecuted('SomeTest', 'SomeTest', 'SomeOtherTest')
        def failingTestPath = testResult.testPath('SomeTest', failingTestMethodName).onlyRoot()
        failingTestPath.assertHasResult(TestResult.ResultType.FAILURE)
        if (capturesTestOutput()) {
            failingTestPath.assertStderr(CoreMatchers.containsString("some error output"))
        }
        testResult.testPath('SomeOtherTest', passingTestMethodName).onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "test results capture test output"() {
        Assume.assumeTrue(capturesTestOutput())
        given:
        createPassingFailingTest()

        buildFile << """
            tasks.withType(AbstractTestTask) {
                ignoreFailures = true
            }
        """

        when:
        succeeds "check"

        then:
        testResult.assertAtLeastTestPathsExecuted('SomeTest')
            .testPath('SomeTest', failingTestMethodName).onlyRoot().assertStderr(CoreMatchers.containsString("some error output"))
    }

    def "failing tests cause report url to be printed"() {
        given:
        createPassingFailingTest()

        when:
        fails "check"

        then:
        failure.assertHasCause("There were failing tests. See the report at:")
    }

    def "lack of tests when sources are present and no filters causes failure"() {
        given:
        createEmptyProject()

        when:
        fails "check"

        then:
        failure.assertHasCause("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration.")
    }

    def "adding and removing tests remove old tests from reports"() {
        given:
        createPassingFailingTest()
        fails("check")
        when:
        renameTests()
        fails("check")
        then:
        testResult.assertAtLeastTestPathsExecuted('SomeTest', 'NewTest')
    }

    def "honors test case filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.${passingTestMethodName}"

        then:
        testResult.assertAtLeastTestPathsExecuted('SomeOtherTest')
        testResult.testPath('SomeOtherTest', passingTestMethodName).onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "honors test suite filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.*"

        then:
        testResult.assertAtLeastTestPathsExecuted('SomeOtherTest')
        testResult.testPath('SomeOtherTest', passingTestMethodName).onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "reports when no matching methods found"() {
        given:
        createPassingFailingTest()

        //by command line
        when:
        fails(testTaskName, "--tests", "${testSuite('SomeTest')}.missingMethod")
        then:
        failure.assertHasCause("No tests found for given includes: [${testSuite('SomeTest')}.missingMethod](--tests filter)")

        //by build script
        when:
        buildFile << "tasks.withType(AbstractTestTask) { filter.includeTestsMatching '${testSuite('SomeTest')}.missingMethod' }"
        fails(testTaskName)
        then:
        failure.assertHasCause("No tests found for given includes: [${testSuite('SomeTest')}.missingMethod](filter.includeTestsMatching)")
    }

    def "task is out of date when --tests argument changes"() {
        given:
        createPassingFailingTest()


        when:
        run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.${passingTestMethodName}")

        then:
        testResult.assertAtLeastTestPathsExecuted('SomeOtherTest')
        testResult.testPath('SomeOtherTest', passingTestMethodName).onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)


        when:
        run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.${passingTestMethodName}")

        then:
        result.assertTaskSkipped(":$testTaskName") //up-to-date


        when:
        run(testTaskName, "--tests", "${testSuite('SomeTest')}.${passingTestMethodName}")

        then:
        result.assertTaskExecuted(":$testTaskName")
        testResult.assertAtLeastTestPathsExecuted('SomeTest')
        testResult.testPath('SomeTest', passingTestMethodName).onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    def "can select multiple tests from command line #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(toTestArgs(desiredTestFilters))

        then:
        testResult.assertAtLeastTestPathsExecuted(desiredTestFilters.keySet().toArray(new String[0]))
        desiredTestFilters.each { testClass, testCases ->
            testCases.each { testCase ->
                testResult.assertAtLeastTestPathsExecuted(testClass + ":" + testCase)
            }
        }

        where:
        scenario                      | desiredTestFilters
        "fail and SomeTest.pass"      | ['SomeTest': [failingTestMethodName, passingTestMethodName]]
        "fail and SomeOtherTest.pass" | ['SomeTest': [failingTestMethodName], 'SomeOtherTest': [passingTestMethodName]]
    }

    private String[] toTestArgs(Map<String, List<String>> desiredTestFilters) {
        def command = new ArrayList<>()
        command.add(testTaskName)

        desiredTestFilters.each { testClass, testCases ->
            testCases.collect { testCase ->
                command.addAll(['--tests', testSuite(testClass) + "." + testCase])
            }
        }
        return command.toArray()
    }

    def "can deduplicate test filters when #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(*command)

        then:
        testResult.testPath('SomeTest').onlyRoot().assertChildrenExecuted(passingTestCaseName, failingTestCaseName)

        where:
        scenario                             | command
        "test suite appear before test case" | [testTaskName, "--tests", "${testSuite('SomeTest')}.*", "--tests", "${testSuite('SomeTest')}.$passingTestCaseName"]
        "test suite appear after test case"  | [testTaskName, "--tests", "${testSuite('SomeTest')}.$passingTestCaseName", "--tests", "${testSuite('SomeTest')}.*"]
    }

    protected GenericHtmlTestExecutionResult getTestResult() {
        resultsFor(testDirectory, "tests/$testTaskName", testFramework)
    }

    protected boolean capturesTestOutput() {
        return true
    }
}
