/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.TestPathExecutionResult
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.testing.fixture.JvmBlockingTestClassGenerator
import org.junit.Rule

import static org.gradle.testing.fixture.JvmBlockingTestClassGenerator.DEFAULT_MAX_WORKERS
import static org.gradle.testing.fixture.JvmBlockingTestClassGenerator.FAILED_RESOURCE
import static org.gradle.testing.fixture.JvmBlockingTestClassGenerator.OTHER_RESOURCE

abstract class AbstractJvmFailFastIntegrationSpec extends AbstractTestingMultiVersionIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    JvmBlockingTestClassGenerator generator

    abstract GenericTestExecutionResult.TestFramework getTestFramework()

    def setup() {
        server.start()
        generator = new JvmBlockingTestClassGenerator(testDirectory, server, testFrameworkImports, testFrameworkDependencies, configureTestFramework)
    }

    def "all tests run with #description"() {
        given:
        buildFile.text = generator.initBuildFile()
        buildFile << buildConfig
        generator.withFailingTest()
        generator.withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(DEFAULT_MAX_WORKERS, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks(taskList).start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        testExecution.release(OTHER_RESOURCE)
        gradleHandle.waitForFailure()

        and:
        GenericTestExecutionResult testResults = resultsFor()
        testResults.assertTestPathsExecuted(":pkg.FailedTest:failTest", ":pkg.OtherTest:passingTest")

        TestPathExecutionResult gradleTest = testResults.testPath("")
        gradleTest.rootNames == ['Gradle Test Run :test']
        gradleTest.onlyRoot().assertChildCount(2, 1)

        TestPathExecutionResult failedTest = testResults.testPath("pkg.FailedTest")
        failedTest.onlyRoot().assertChildCount(1, 1)

        TestPathExecutionResult otherTest = testResults.testPath("pkg.OtherTest")
        otherTest.onlyRoot().assertChildCount(1, 0)

        where:
        description        | taskList                   | buildConfig
        'default config'   | ['test']                   | ''
        'failFast = false' | ['test']                   | 'test { failFast = false }'
    }

    def "stop test execution with #description"() {
        given:
        buildFile.text = generator.initBuildFile()
        buildFile << buildConfig
        generator.withFailingTest()
        generator.withNonfailingTest()
        def testExecution = server.expectOptionalAndBlock(DEFAULT_MAX_WORKERS, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks(taskList).start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        gradleHandle.waitForFailure()

        and:
        GenericTestExecutionResult testResults = resultsFor("tests/test", testFramework)
        TestPathExecutionResult failedTest = testResults.testPath("pkg.FailedTest")
        failedTest.onlyRoot().getFailedChildCount() == 1
        TestPathExecutionResult otherTest = testResults.testPath("pkg.OtherTest")
        otherTest.onlyRoot().getSkippedChildCount() == 1

        where:
        description       | taskList                   | buildConfig
        'failFast = true' | ['test']                   | 'test { failFast = true }'
        '--fail-fast'     | ['test', '--fail-fast']    | ''
    }

    def "ensure fail fast with forkEvery #forkEvery, maxWorkers #maxWorkers, omittedTests #testOmitted"() {
        given:
        buildFile.text = generator.initBuildFile(maxWorkers, forkEvery)
        def resourceForTest = generator.withFailingTests(testOmitted + 1)
        def testExecution = server.expectOptionalAndBlock(maxWorkers, resourceForTest.values() as String[])

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(1)
        gradleHandle.waitForFailure()

        and:
        GenericTestExecutionResult testResults = resultsFor("tests/test", testFramework)
        assert 1 == resourceForTest.keySet().sum {path ->
            if (testResults.testPathExists(path)) {
                TestPathExecutionResult test = testResults.testPath(path)
                test.onlyRoot().getFailedChildCount()
            } else {
                0
            }
        }
        resourceForTest.keySet().with {
            def doesntExist = count { path ->
                !testResults.testPathExists(path)
            }
            def zeroChildren = count {
                def path = it
                testResults.testPathExists(path) && testResults.testPath(path).rootNames.size() == 0
            }
            def skipped = count { path ->
                testResults.testPathExists(path) && testResults.testPath(path).onlyRoot().getSkippedChildCount()
            }
            assert testOmitted == (doesntExist + zeroChildren + skipped)
        }

        where:
        forkEvery | maxWorkers | testOmitted
        0         | 1          | 1
        1         | 1          | 1
        2         | 1          | 1
        0         | 2          | 5
        1         | 2          | 5
        2         | 2          | 5
    }

    def "fail fast console output shows failure"() {
        given:
        buildFile.text = generator.initBuildFile()
        generator.withFailingTest()
        generator.withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(DEFAULT_MAX_WORKERS, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        gradleHandle.waitForFailure()
        assert gradleHandle.standardOutput.matches(/(?s).*FailedTest.*failTest.*FAILED.*java.lang.RuntimeException at FailedTest.java.*/)
        assert !gradleHandle.standardOutput.contains('pkg.OtherTest')
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "fail fast console output shows test class in work-in-progress"() {
        given:
        executer.withConsole(ConsoleOutput.Rich).withArguments('--parallel', "--max-workers=$DEFAULT_MAX_WORKERS")
        buildFile.text = generator.initBuildFile()
        generator.withFailingTest()
        generator.withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(DEFAULT_MAX_WORKERS, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(gradleHandle, '> :test > Executing test pkg.FailedTest')
            RichConsoleStyling.assertHasWorkInProgress(gradleHandle, '> :test > Executing test pkg.OtherTest')
        }

        testExecution.release(FAILED_RESOURCE)
        gradleHandle.waitForFailure()
    }

    def "fail fast works with --tests filter"() {
        given:
        buildFile.text = generator.initBuildFile()
        def resourceForTest = generator.withFailingTests(5)
        def testExecution = server.expectOptionalAndBlock(DEFAULT_MAX_WORKERS, resourceForTest.values() as String[])

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast', '--tests=*OtherTest_*').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(DEFAULT_MAX_WORKERS)
        gradleHandle.waitForFailure()

        assert !gradleHandle.errorOutput.contains('No tests found for given includes:')
    }
}
