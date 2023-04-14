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

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.hamcrest.CoreMatchers
import org.junit.Assume

import static org.gradle.integtests.fixtures.DefaultTestExecutionResult.removeParentheses

abstract class AbstractTestFrameworkIntegrationTest extends AbstractIntegrationSpec {

    abstract void createPassingFailingTest()

    abstract void createEmptyProject()

    abstract void renameTests()

    abstract String getTestTaskName()

    abstract String getPassingTestCaseName()

    abstract String getFailingTestCaseName()

    String testSuite(String testSuite) {
        return testSuite
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
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
                doLast {
                    assert junitXmlOutputLocation.flatMap { it.asFile }.get() == file('build/test-results/${testTaskName}')
                    assert htmlOutputLocation.flatMap { it.asFile }.get() == file('build/reports/tests/${testTaskName}')
                    assert binaryResultsDirectory.flatMap { it.asFile }.get() == file('build/test-results/${testTaskName}/binary')
                }
            }
        """

        expect:
        succeeds "verifyTestResultConventions"
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
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
        testResult.assertTestClassesExecuted('SomeTest', 'SomeOtherTest')
        testResult.testClass('SomeTest').assertTestFailed(failingTestCaseName, CoreMatchers.containsString("test failure message"))
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
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
        testResult.testClass('SomeTest').assertStderr(CoreMatchers.containsString("some error output"))
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "failing tests cause report url to be printed"() {
        given:
        createPassingFailingTest()

        when:
        fails "check"

        then:
        failure.assertHasCause("There were failing tests. See the report at:")
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "lack of tests produce an empty report"() {
        given:
        createEmptyProject()

        when:
        succeeds "check"

        then:
        testResult.assertNoTestClassesExecuted()
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "adding and removing tests remove old tests from reports"() {
        given:
        createPassingFailingTest()
        fails("check")
        when:
        renameTests()
        fails("check")
        then:
        testResult.assertTestClassesExecuted('SomeTest', 'NewTest')
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "honors test case filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.${removeParentheses(passingTestCaseName)}"

        then:
        testResult.assertTestClassesExecuted('SomeOtherTest')
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "honors test suite filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.*"

        then:
        testResult.assertTestClassesExecuted('SomeOtherTest')
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
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

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "task is out of date when --tests argument changes"() {
        given:
        createPassingFailingTest()


        when:
        run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.${removeParentheses(passingTestCaseName)}")

        then:
        testResult.testClass("SomeOtherTest").assertTestsExecuted(passingTestCaseName)


        when:
        run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.${removeParentheses(passingTestCaseName)}")

        then:
        result.assertTaskSkipped(":$testTaskName") //up-to-date


        when:
        run(testTaskName, "--tests", "${testSuite('SomeTest')}.${removeParentheses(passingTestCaseName)}")

        then:
        result.assertTaskNotSkipped(":$testTaskName")
        testResult.testClass("SomeTest").assertTestsExecuted(passingTestCaseName)
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "can select multiple tests from command line #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(toTestArgs(desiredTestFilters))

        then:
        def results = testResult
        results.assertTestClassesExecuted(desiredTestFilters.keySet().toArray(new String[0]))
        desiredTestFilters.each { testClass, testCases ->
            results.testClass(testClass).assertTestsExecuted(*testCases)
        }

        where:
        scenario                      | desiredTestFilters
        "fail and SomeTest.pass"      | ['SomeTest': [failingTestCaseName, passingTestCaseName]]
        "fail and SomeOtherTest.pass" | ['SomeTest': [failingTestCaseName], 'SomeOtherTest': [passingTestCaseName]]
    }

    private String[] toTestArgs(Map<String, List<String>> desiredTestFilters) {
        def command = Lists.newArrayList()
        command.add(testTaskName)

        desiredTestFilters.each { testClass, testCases ->
            testCases.collect { testCase ->
                command.addAll(['--tests', testSuite(testClass) + "." + removeParentheses(testCase)])
            }
        }
        return command.toArray()
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = "XCTestTestFrameworkIntegrationTest")
    def "can deduplicate test filters when #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(*command)

        then:
        testResult.assertTestClassesExecuted('SomeTest')
        testResult.testClass("SomeTest").assertTestsExecuted(passingTestCaseName, failingTestCaseName)

        where:
        scenario                             | command
        "test suite appear before test case" | [testTaskName, "--tests", "${testSuite('SomeTest')}.*", "--tests", "${testSuite('SomeTest')}.$passingTestCaseName"]
        "test suite appear after test case"  | [testTaskName, "--tests", "${testSuite('SomeTest')}.$passingTestCaseName", "--tests", "${testSuite('SomeTest')}.*"]
    }

    protected DefaultTestExecutionResult getTestResult() {
        new DefaultTestExecutionResult(testDirectory, 'build', '', '', testTaskName)
    }

    protected boolean capturesTestOutput() {
        return true
    }
}
